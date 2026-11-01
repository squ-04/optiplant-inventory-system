# OptiPlant — Sistema de Inventario Multi-Sucursal

Aplicación web para gestionar de forma centralizada el inventario de una red de sucursales: catálogo, existencias, compras, ventas, transferencias entre sucursales, logística, indicadores y reportes. Cada sucursal opera con autonomía local sobre una plataforma común, y un administrador supervisa toda la red.

Es la solución a la [prueba técnica de OptiPlant Consultores](docs/00-source/prueba-tecnica.pdf). Los 55 requerimientos funcionales (RF-0001–RF-0055) están implementados.

Perfiles del sistema:

| Perfil | Alcance |
|---|---|
| Administrador | Toda la red: configuración, sucursales, usuarios, catálogo, listas de precios, rutas y consulta global. |
| Gerente de sucursal | Su sucursal: supervisa, aprueba transferencias y consulta reportes. |
| Operador de inventario | Su sucursal: movimientos, compras, ventas y solicitud de transferencias. |

## Instalación

Solo se necesita Docker con Compose y Git. Java, Maven, Node y PostgreSQL se ejecutan dentro de contenedores.

```sh
git clone <url-del-repositorio>
cd optiplant-inventory-system
docker compose up --build --wait
```

`--build` construye las imágenes y `--wait` devuelve el control cuando los servicios están sanos. La primera construcción puede tardar varios minutos.

No hay configuración previa: el servicio de un solo uso `credentials` genera las credenciales en el primer arranque y las conserva en un volumen.

| Servicio | Dirección |
|---|---|
| Aplicación | <http://localhost:5173> |
| API | <http://localhost:8080> |
| Documentación de la API | <http://localhost:8080/swagger-ui/index.html> |

**Primer acceso.** El código es `ADMIN`. La contraseña inicial se consulta con el comando siguiente, y el primer ingreso exige cambiarla:

```sh
docker compose exec backend cat /run/optiplant/admin-password
```

Usa siempre `localhost`, no `127.0.0.1`: las cookies y CORS dependen del mismo nombre de host.

**Datos.** `docker compose down` conserva la base. `docker compose down -v` **elimina los datos y las credenciales generadas**.

**Configuración opcional.** Un archivo `.env` permite cambiar puertos y credenciales; sus valores prevalecen sobre los generados. `.env.example` documenta las variables. `python3 scripts/init_environment.py` lo crea con secretos aleatorios.

**Comprobaciones.** `python3 scripts/run_checks.py all` ejecuta las verificaciones del repositorio, la documentación, el backend, el frontend y un arranque de integración aislado. Requiere Python 3.10 o posterior.

Más detalle en [Ejecutar y verificar la base técnica](docs/07-colaboracion/preparacion-tecnica.md).

## Arquitectura

Monolito modular con tres servicios independientes orquestados por Docker Compose. El frontend se comunica con el backend exclusivamente por API REST, y PostgreSQL no publica puertos al host.

![Arquitectura general de OptiPlant](docs/04-arquitectura/diagramas/vistas-previas/01-arquitectura-general.png)

| Capa | Tecnología | Responsabilidad |
|---|---|---|
| Presentación | React 19, TypeScript 6, Vite 8, Tailwind CSS 4, shadcn/ui, Recharts 3 | Interfaz web responsiva y dashboards. |
| Negocio | Java 25, Spring Boot 4.1, Spring Security, Apache POI | API REST, reglas de negocio, autorización, cálculo de costos y reportes. |
| Datos | PostgreSQL 17, Spring Data JPA con Hibernate, Flyway | Persistencia relacional y migraciones versionadas. |
| Infraestructura | Docker Compose | Servicios aislados y arranque con un solo comando. |

El backend se organiza por funcionalidad (*package-by-feature*). Dentro de cada módulo, los controladores exponen la API, los servicios concentran reglas y transacciones, y los repositorios acceden a los datos.

Referencias: [arquitectura y diseño técnico](docs/04-arquitectura/arquitectura-y-diseno-tecnico.md), [modelo de datos y diagramas E-R](docs/06-modelo-base-datos/modelo-datos.md) e [índice de todos los diagramas y documentos](docs/indice-documental.md).

## Módulos implementados

| Módulo | Requerimientos | Funcionalidad |
|---|---|---|
| Identidad y administración (`identity`) | RF-0001–RF-0007 | Configuración, usuarios, sucursales, autenticación y permisos por perfil y sucursal. |
| Catálogo (`catalog`) | RF-0008–RF-0013 | Productos con baja lógica, unidades de medida, stock mínimo y catálogo local. |
| Inventario (`inventory`) | RF-0014–RF-0020 | Consulta entre sucursales, ingresos y retiros trazables, y alertas de reabastecimiento. |
| Compras (`purchases`) | RF-0021–RF-0026 | Proveedores, órdenes con condiciones, recepción, histórico y costo promedio ponderado. |
| Ventas (`sales`) | RF-0027–RF-0031 | Ventas con validación de stock, listas de precios, descuentos y comprobantes. |
| Transferencias (`transfers`) | RF-0032–RF-0039 | Solicitud, preparación, aprobación, despacho, recepción y tratamiento de faltantes. |
| Logística (`logistics`) | RF-0040–RF-0043 | Tiempos estimados y reales, rutas, estados y cumplimiento por sucursal y ruta. |
| Dashboard (`dashboard`) | RF-0044–RF-0049 | Ventas comparadas, rotación, demanda, transferencias, reabastecimiento y comparativa entre sucursales. |
| Reportes exportables (`reports`) | RF-0050–RF-0055 | **Funcionalidad adicional elegida:** Excel `.xlsx` de movimientos, ventas y transferencias por rango de fechas. |

## Decisiones de diseño

Cada decisión se desarrolla, con alternativas y consecuencias, en el [registro de arquitectura](docs/04-arquitectura/arquitectura-y-diseno-tecnico.md) (ARQ-001–ARQ-010).

### Decisiones técnicas

| Decisión | Elección | Por qué |
|---|---|---|
| Stack (ARQ-001, ARQ-002) | React con TypeScript; Java con Spring Boot; API REST | Tipado estático en ambos extremos y `BigDecimal` para expresar cantidades y costos con reglas explícitas. |
| Arquitectura (ARQ-002, ARQ-008) | Monolito modular de nueve módulos | Las operaciones cruzan módulos dentro de una misma transacción; los microservicios introducirían despliegues y fallos distribuidos que este alcance no necesita. |
| Base de datos (ARQ-003) | PostgreSQL relacional | Inventario, ventas y transferencias exigen integridad referencial y transacciones. |
| Autenticación y autorización (ARQ-005) | Spring Security con sesiones en PostgreSQL mediante Spring Session JDBC, cookies `HttpOnly` y CSRF | Permite revocar sesiones y controlar la inactividad, algo que un JWT validado solo por firma no resuelve. La autorización comprueba acción y sucursal. |
| Sincronización entre sucursales (ARQ-006) | Una base común y polling REST cada dos segundos con TanStack Query | Una sola fuente de verdad evita reconciliar copias; el polling da frescura sin conexiones persistentes. |
| Consistencia (ARQ-007) | Transacciones `READ COMMITTED`, bloqueo pesimista e idempotencia | Impide que dos operaciones consuman las mismas unidades y que un reintento duplique efectos. |
| Patrones (ARQ-008) | Repository, Service Layer y DTO | Separan acceso a datos, reglas y contrato de la API. CQRS completo, un bus de eventos o una arquitectura hexagonal estricta se descartaron por sobreingeniería. |
| Reportes (ARQ-010) | Excel `.xlsx` real generado con Apache POI | La prueba admite PDF o Excel; se ratificó solo Excel, con un archivo real y no un CSV renombrado (INC-020/D01). |

### Decisiones de alcance frente al enunciado

La prueba deja alternativas abiertas o incluye ejemplos que no son obligatorios. Estas son las decisiones propias del proyecto:

| Tema | Qué dice la prueba | Decisión | Fuente |
|---|---|---|---|
| Clasificación de rutas | Clasificar rutas «por prioridad, costo o tiempo» (§3.5) | Solo por **prioridad manual** alta, media o baja. Sin tarifas ni optimización; el tiempo se mide aparte. | INC-017/D02 |
| Urgencia en transferencias | Aparece en una historia de usuario de ejemplo (§6.3) | **No se incorpora** en esta versión. Es distinta de la prioridad de ruta, que sí existe. | INC-019/D03 |
| Órdenes de pago | Aparecen en una historia de usuario de ejemplo (§6.3) | **Quedan fuera**: no es una funcionalidad financiera obligatoria. Se conservan las condiciones de compra y el plazo de pago. | INC-019/D01 |
| Administrador en transferencias | La solicitud la genera «la sucursal destino (o un administrador)» (§3.4) | Solo la solicita el **operador de la sucursal destino**. El administrador supervisa, pero no solicita, aprueba ni rechaza por herencia de rol. | RF-0032, H v0.16 |

El detalle está en el [registro de decisiones aprobadas](docs/01-requerimientos/decisiones-aprobadas-optiplant-v1.0.md) y en los [requisitos funcionales](docs/01-requerimientos/requisitos-funcionales.md).

## Uso de inteligencia artificial

El proceso, los prompts utilizados y la evaluación crítica están en [Uso de inteligencia artificial](docs/09-uso-de-ia/uso-de-inteligencia-artificial.md).
