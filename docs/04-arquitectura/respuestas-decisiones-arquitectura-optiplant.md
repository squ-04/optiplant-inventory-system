# OptiPlant — Respuestas aprobadas para consolidar la arquitectura y el diseño técnico

**Tipo de artefacto:** acta de decisiones arquitectónicas ratificadas e instrucciones de integración documental.  
**Fecha de ratificación:** 21 de octubre de 2026.  
**Estado:** ocho decisiones `PEN-001` a `PEN-008` **aprobadas por el responsable**.  
**Documento de destino:** `docs/arquitectura-y-diseno-tecnico.md` **o la ruta real** donde se encuentre el archivo titulado *Arquitectura y diseño técnico — OptiPlant*. No crear una segunda arquitectura ni asumir que la ruta indicada existe: resolverla desde el repositorio.  
**Versión fuente revisada:** borrador de arquitectura 0.1 del 21 de octubre de 2026.  
**Objeto:** cerrar la **selección y justificación documental** de las decisiones de la sección 8 de la prueba técnica. No acredita implementación, pruebas, modelo entidad-relación terminado ni diagramas de la sección 7.

> **IMPORTANTE PARA EL AGENTE:** este archivo contiene **decisiones humanas expresamente ratificadas**, no ideas para volver a someter a consideración. Incorpóralas al documento de arquitectura existente, conservando sus identificadores `ARQ-001` a `ARQ-010`, sus fundamentos, trazabilidad y estructura útil. Sustituye los estados «pendiente de aprobación» cuando la selección ya fue ratificada, pero conserva como «detalle por diseñar» o «verificación pendiente» todo aquello que realmente no ha sido diseñado, implementado ni medido. **No generes código, scripts ejecutables ni diagramas en esta tarea**, salvo que el responsable lo solicite expresamente después. No modificar los requisitos funcionales ni las decisiones históricas aprobadas para hacerlas coincidir con esta acta.

## 1. Instrucciones vinculantes de integración

1. **Leer, antes de editar**, `AGENTS.md`, el borrador actual de arquitectura, `docs/00-source/prueba-tecnica.md` (o ruta real), el catálogo de requerimientos funcionales vigente, el registro de decisiones aprobadas y los requisitos no funcionales. Usar las rutas reales del proyecto; las rutas expresadas en el borrador son referencias relativas a su ubicación actual. El PDF original de la prueba continúa siendo la fuente externa definitiva ante discrepancias con la versión Markdown; no editarlo.
2. **Precedencia:** conservar las correcciones humanas posteriores recogidas en RF §2.1, por encima de alternativas antiguas del registro D. Este documento decide **mecanismos arquitectónicos**, no nuevas reglas funcionales. Ante contradicción real no resuelta, no inventar una solución ni ocultarla: señalarla con precisión.
3. **Actualizar el documento de arquitectura existente**, no pegar este texto completo sin adaptación, ni sobrescribir apartados ajenos. Reconciliar las secciones §1–§6, cobertura §8.1/§8.2, entradas `ARQ-001` a `ARQ-010`, tabla de pendientes y conclusión.
4. **Estados separados:** a) tecnología/patrón elegido = aprobado; b) detalle de contrato/esquema y versiones = definido solo si figura aquí o en fuentes aprobadas; c) implementación/pruebas = pendientes si no hay evidencia real. No convertir decisiones de diseño en afirmaciones de funcionamiento ya comprobado.
5. **Documentar alternativas y motivos** de forma concisa en cada ARQ. No atribuir a la prueba técnica la imposición de React, Java, PostgreSQL u otras herramientas: son elecciones del proyecto. Mostrar expresamente las cinco justificaciones obligatorias de §8.2: lenguaje backend, motor/modelo de datos, autenticación/autorización, sincronización de sucursales y patrones empleados.
6. **No implementar infraestructura adicional** por comodidad del agente ni añadir servicios externos, sistemas offline, almacenamiento independiente por sucursal, mensajería, nuevos reportes o funcionalidades no autorizadas.
7. **No solicitar nuevos ciclos de decisión** para las ocho `PEN`: están ratificadas. Es legítimo dejar documentadas las verificaciones futuras y los detalles que solo pueden derivarse del modelo E-R, los requisitos completos o la implementación.

## 2. Resumen de decisiones ratificadas

| Pendiente | Sección de arquitectura | Decisión final aprobada |
|---|---|---|
| `PEN-001` | `ARQ-005` | Spring Security + Spring Session JDBC en PostgreSQL, cookies y CSRF, CORS restrictivo; sin JWT ni proxy inverso. |
| `PEN-002` | `ARQ-007` | Transacciones atómicas; PostgreSQL `READ COMMITTED`, bloqueos pesimistas, idempotencia y restricciones de integridad. |
| `PEN-003` | `ARQ-006` | Actualización por polling REST administrado con TanStack Query; intervalo inicial de 2 s, verificación del límite de 5 s. |
| `PEN-004` | `ARQ-008` (relacionada con `ARQ-002`) | Monolito modular por funcionalidades; nueve módulos con capas Java tradicionales propias; `transfers` y `logistics` separados. |
| `PEN-005` | `ARQ-003` y `ARQ-009` | Una base relacional PostgreSQL; identidad dual aprobada, precisión decimal exacta, historia de movimientos, instantes UTC y calendario `America/Bogota`. |
| `PEN-006` | `ARQ-001` | React/TypeScript/Vite, Tailwind, shadcn/ui, **React Router**, TanStack Query, Recharts, formularios y tablas; frontend modular. |
| `PEN-007` | `ARQ-004` | Compose con frontend, backend y PostgreSQL; **volumen nombrado Docker `pgdata`**, Flyway, SQL de esquema y semillas, healthchecks, Vite local, sin proxy. |
| `PEN-008` | `ARQ-002` y `ARQ-010` | API REST `/api/v1` con DTO, ProblemDetail, OpenAPI/Swagger y reportes XLSX con Apache POI. |

Estas ratificaciones mantienen vigente `DEC-TEC-ID-001`, previamente aprobada; **no crear una decisión de identidad competidora ni renumerarla**.

---

## 3. Decisiones detalladas para incorporar

### PEN-001 — ARQ-005: autenticación mediante sesiones persistidas, sin proxy inverso

**Estado:** APROBADA. **Clasificación:** justificación obligatoria §8.2 — autenticación y autorización.

**Decisión aprobada**

- Adoptar **Spring Security** para autenticación y autorización y **Spring Session JDBC** para persistir sesiones en la misma instancia PostgreSQL del sistema. No utilizar JWT como mecanismo principal, ni Redis ni repositorios externos de sesiones.
- React y Spring Boot se ejecutan como **servicios independientes en puertos distintos** del entorno local y se comunican por **HTTP directo**, sin NGINX, HAProxy ni proxy inverso.
- Configurar **CORS restrictivo** con orígenes locales explícitos y soporte para credenciales: nunca combinar credenciales con `Access-Control-Allow-Origin: *`.
- Usar cookie de sesión **`HttpOnly`**, **`SameSite=Lax`** y demás atributos de seguridad apropiados al entorno. En HTTP local no marcar `Secure` de modo que impida la sesión; exigir `Secure` al operar bajo HTTPS. No almacenar identificadores de sesión ni credenciales en `localStorage`.
- **Mantener CSRF habilitado** para peticiones que cambian estado. Proporcionar una obtención/intercambio de token CSRF compatible con React y el cliente HTTP centralizado; el token CSRF es distinto de la cookie de sesión `HttpOnly`.
- Usar contraseñas almacenadas con hash resistente apropiado (p. ej., Argon2id o BCrypt según compatibilidad y parámetros elegidos); no guardar contraseñas en claro. Registrar mecanismos de protección antiabuso de RF sin alterar sus umbrales aprobados.
- Conservar acceso por código único y contraseña; **un rol por usuario**, con administrador global y gerente/operador de una sucursal, y matriz de permisos de RF. Comprobar **rol, acción, recurso y sucursal** en backend en cada caso de uso; conocer el UUID público no autoriza acceder al recurso.
- Mantener la expiración **tras 30 minutos de inactividad** y **máximo absoluto de ocho horas**, según RF. Cerrar sesión, desactivar una cuenta o cambiar rol/sucursal debe invalidar accesos previos. La implementación debe controlar también el plazo absoluto, no confiar solo en un `maxInactiveInterval`.
- La **actividad automática** de polling aprobada en PEN-003 **no debe extender artificialmente** la sesión por inactividad. Distinguir y controlar la última actividad interactiva relevante con criterio aplicable en el servidor; no confiar ciegamente en una afirmación manipulable del cliente.
- Mantener contraseña temporal individual, cambio inicial y provisión segura de primer administrador según las decisiones funcionales; no sembrar credenciales universales en archivos públicos.

**Justificación.** El control centralizado de sesiones permite revocación, seguimiento de inactividad y autorización contextual sobre una arquitectura conectada de monolito modular. Reutiliza PostgreSQL y evita la complejidad de JWT con almacén de revocación, Redis o componentes proxy innecesarios.

**Cuidado de implementación/documentación.** `localhost:5173` y `localhost:8080` son orígenes distintos por puerto para CORS, aunque pueden compartir el mismo *site* para `SameSite`; evitar mezclar `localhost` con `127.0.0.1` entre las URLs del navegador si eso cambia el comportamiento de cookies. No afirmar HTTPS de extremo a extremo en una demostración que usa HTTP local.

**Verificación prevista:** autenticación, CSRF, cookies/CORS, permisos entre sucursales, revocación por cierre/desactivación/cambio de asignación, expiraciones de 30 minutos y ocho horas y sesiones que no se perpetúan con polling.

### PEN-002 — ARQ-007: atomicidad, concurrencia e idempotencia

**Estado:** APROBADA.

**Decisión aprobada**

- Delimitar las confirmaciones de negocio con **`@Transactional`** y PostgreSQL, asegurando **todo o nada** para documentos, movimientos, saldos y valoración. Configurar manejo de rollback coherente con las excepciones del servicio.
- Mantener aislamiento **`READ COMMITTED`** y utilizar **bloqueo pesimista** (`SELECT ... FOR UPDATE` / `PESSIMISTIC_WRITE`) sobre las filas pertinentes de existencias y estados documentales, cuando corresponda.
- Adquirir bloqueos de varias filas en **orden determinista** (por sucursal y producto cuando aplique) para reducir interbloqueos; conservar transacciones acotadas. Tratar conflictos y deadlocks sin confirmar efectos parciales.
- Proteger el **estado del documento** contra dobles confirmaciones: bloquear exclusivamente las existencias no basta para impedir duplicados del mismo documento.
- Implementar **idempotencia** para solicitudes críticas de creación/confirmación repetibles por fallo de red o reintento, con `Idempotency-Key` y registro persistido de ámbito, operación, huella de solicitud y resultado. Misma clave + mismo contenido: no repetir efectos y recuperar resultado; clave igual + contenido distinto: conflicto. Coordinar el registro con la transacción.
- Complementar las validaciones en aplicación con **PK, FK, `UNIQUE`, `CHECK` y `NOT NULL`** pertinentes. Resolver también la carrera de creación cuando todavía no existe fila producto-sucursal.
- No ejecutar **reintentos automáticos indiscriminados** de operaciones de inventario; solo contemplar reintento seguro de transacción completa con protección idempotente cuando el error sea transitorio y el mecanismo esté justificado.
- No permitir stock negativo ni reservas no aprobadas. El despacho reduce el origen y crea tránsito, la única llegada confirma la cantidad recibida —incluso cero— y preserva el faltante conforme a RF. La llegada de cero no crea movimiento físico de cantidad cero.

**Justificación.** Garantizar consistencia e integridad de datos y efectos ante operaciones simultáneas usando recursos nativos de Spring y PostgreSQL, sin mensajería, bases distribuidas ni infraestructura adicional.

**Verificación prevista:** dos ventas simultáneas de dos unidades sobre saldo tres, con solo una confirmada; repetición de compras/ventas/despachos/llegadas; doble llegada; rollback tras errores intermedios; bloqueos múltiples, ausencia de saldo negativo, compensaciones e historia según RF.

### PEN-003 — ARQ-006: actualización entre sucursales mediante polling REST

**Estado:** APROBADA. **Clasificación:** justificación obligatoria §8.2 — sincronización de inventario.

**Decisión aprobada**

- Mantener una **única plataforma conectada y base centralizada**. No hay bases independientes por sucursal, operación offline ni reconciliación diferida de escrituras. PEN-002 gobierna la consistencia de confirmaciones.
- Actualizar las vistas de inventario relevantes mediante **polling REST con TanStack Query** en React. Configurar **intervalo inicial de dos segundos** para las consultas de inventario que requieran frescura; no aplicar polling indiscriminado a todos los indicadores o catálogos.
- Tras mutaciones propias confirmadas, **invalidar y refrescar** las consultas afectadas. Reconsultar al recuperar conexión o foco de ventana y al volver a estar visible la pantalla.
- Seguir las cookies, CORS, CSRF y autorización contextual de PEN-001. El tráfico de polling **no debe perpetuar sesiones inactivas**.
- Informar al usuario cuando los datos sean potencialmente obsoletos por desconexión; no simular confirmaciones locales. Cada operación de escritura se vuelve a validar sobre el estado autoritativo del backend.
- **No incorporar SSE, WebSockets, Redis ni broker de mensajes** como mecanismo inicial.
- El **máximo de cinco segundos** se medirá **desde la confirmación persistida hasta la visualización efectiva en otra sesión conectada**, considerando espera del siguiente polling, red, procesamiento y renderizado. Dos segundos es un parámetro inicial, **no una garantía por sí sola**.
- Documentar límites del navegador sobre temporizadores en pestañas ocultas/suspendidas; no afirmar cumplimiento universal para sesiones inactivas sin pruebas o sin revisar qué condiciones contempla el RNF.

**Justificación.** Reutiliza la API REST y PostgreSQL, separa consistencia de datos y actualización de interfaz, y evita conexiones persistentes o nuevos servicios no justificados para una prueba local.

**Verificación prevista:** latencia extremo a extremo entre dos sesiones, errores/reconexión, focos de ventana, autorizaciones cruzadas, consumo de consultas y expiración real de sesión bajo polling.

### PEN-004 — ARQ-008: monolito modular por funcionalidades con capas Java convencionales

**Estado:** APROBADA. **Clasificación:** justificación obligatoria §8.2 — patrones empleados.

**Decisión aprobada**

- Una sola aplicación **Java/Spring Boot** desplegada como **monolito modular**, organizada primero por funcionalidad (**package-by-feature**).
- Mantener **nueve módulos**: `identity`, `catalog`, `inventory`, `purchases`, `sales`, `transfers`, `logistics`, `dashboard` y `reports`.
- **Mantener `transfers` y `logistics` separados**:
  - `transfers`: solicitud de traslado, autorización, estado global del proceso y coordinación de los casos de uso de transferencia.
  - `logistics`: despacho, confirmación única de llegada y registro/gestión de discrepancias y alertas conforme a RF.
  - `inventory`: único propietario de operaciones de modificación de existencias, movimientos, valoración y control del tránsito según las reglas funcionales.
  - Una coordinación desde `transfers` puede invocar contratos internos de `logistics` e `inventory`, dentro de la **misma transacción** cuando afecte a una confirmación indivisible. `logistics` no invoca clases internas de `transfers` ni cambia por su cuenta el estado global del traslado.
- En **cada módulo**, utilizar **arquitectura tradicional Java/Spring Boot en capas por subpaquetes**: `controller/`, `dto/` (p. ej. `request/`, `response/`), `entity/`, `service/`, `repository/`, `mapper/` y complementos (`enums/`, `exception/`, `api/` para contratos internos) **solo cuando hagan falta**. Esta es la estructura ratificada; **no sustituirla** por la organización obligatoria `web/application/domain/persistence` ni imponer arquitectura hexagonal estricta.
- Los controladores reciben HTTP, los DTO transportan contratos, los servicios aplican reglas/orquestan casos de uso y transacciones, los repositorios Spring Data JPA acceden a PostgreSQL, las entidades JPA representan persistencia y los mappers transforman representaciones. No exponer entidades JPA directamente por REST.
- Comunicación entre módulos por **servicios y contratos internos explícitos**; sin acceso directo a repositorios internos ajenos ni dependencias circulares. Los datos pertenecen lógicamente al módulo dueño aun compartiendo el mismo esquema SQL.
- Patrones/adopciones ratificados: **Repository, Service Layer, DTO, inyección de dependencias y separación por módulos**. No crear automáticamente parejas `Service`/`ServiceImpl` o interfaces redundantes sin necesidad real; no introducir CQRS completo, bus de eventos, microservicios, hexagonal estricta ni Spring Modulith de forma predeterminada.
- `dashboard` y `reports` consumen capacidades de consulta y no alteran directamente existencias. `common` o equivalente contiene utilidades técnicas estrictamente compartidas, no reglas de negocio huérfanas.
- **MapStruct fue recomendado pero no ratificado como dependencia obligatoria.** Puede utilizarse si encaja en el proyecto, sin convertir esa sugerencia en una nueva aprobación humana.

**Referencia estructural orientativa** (no crear carpetas vacías):

```text
backend/src/main/java/<paquete-base>/
├── identity/
├── catalog/
├── inventory/
│   ├── controller/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── entity/
│   ├── service/
│   ├── repository/
│   ├── mapper/
│   └── api/             # solo contratos internos necesarios
├── purchases/
├── sales/
├── transfers/
├── logistics/
├── dashboard/
├── reports/
└── common/              # solo aspectos transversales técnicos
```

**Justificación.** Conserva la familiaridad de los proyectos Java por capas, con cohesión por funcionalidad, límites revisables y transacciones comunes; evita abstracciones y despliegues distribuidos innecesarios.

**Verificación prevista:** pruebas de reglas modulares, inspección del grafo de dependencias, ausencia de ciclos, acceso por contratos y coordinación atómica entre transferencias, logística e inventario. ArchUnit es opcional como ayuda de pruebas, no una imposición ratificada.

### PEN-005 — ARQ-003 y ARQ-009: modelo relacional, identidad, precisión e historia

**Estado:** estrategia arquitectónica APROBADA. **Clasificación:** justificación obligatoria §8.2 — motor de datos y modelo adoptado. **Importante:** esquema lógico/físico completo y diagrama E-R aún no redactados/validados por esta aprobación.

**Decisión aprobada**

- Usar **una base PostgreSQL** con modelo relacional normalizado, inicialmente en esquema SQL común `public`; mantener **propiedad lógica por módulo**, transacciones compartidas, relaciones y claves foráneas consistentes.
- Mantener íntegramente `DEC-TEC-ID-001`: PK internas `BIGINT`, `external_id` **UUIDv4, único, no nulo e inmutable** en recursos expuestos individualmente por la API; códigos de negocio separados y FK internas referidas con `BIGINT`. No exponer ni aceptar las claves internas como identificación pública. La autorización sigue siendo obligatoria.
- En Java usar **`BigDecimal`** y en PostgreSQL **`NUMERIC(p,s)`**, no `float/double` para cantidades, costos o valores exactos de negocio. Respetar reglas aprobadas: hasta tres decimales para cantidades divisibles, costo promedio a seis, importes mostrados a dos, redondeo mitad hacia arriba, residuales y compensaciones conforme a RF. **La precisión de cálculo/almacenamiento no es necesariamente igual a la presentación**.
- Tipos tentativos a validar con los RF y extremos de cálculo: `NUMERIC(18,3)` para cantidades; `NUMERIC(24,6)` para costos promedios; `NUMERIC(24,2)` para importes finales **solo cuando correspondan realmente a resultados cuantizados a dos decimales**. **No elevar estos tamaños tentativos a límites funcionales ratificados**, y conservar mayor precisión interna si RF la exige.
- Representar los instantes con **Java `Instant` y PostgreSQL `TIMESTAMPTZ`**, manejados de forma inequívoca (UTC), y fechas de negocio sin hora con `LocalDate` / `DATE` cuando corresponda. **`America/Bogota`** rige interpretación comercial, filtros, cierres e indicadores; COP es moneda de demostración. Para fechas inclusivas utilizar intervalos de instantes con límite inicial inclusivo y final exclusivo del día siguiente cuando aplique.
- Mantener **catálogo de productos separado de existencias por producto-sucursal**. Conservar saldos actuales, movimientos históricos e instantáneas de valoración requeridas. El tránsito no es stock disponible en destino. No corregir movimientos pasados arbitrariamente; registrar operaciones autorizadas posteriores de forma trazable.
- Conservar semántica aprobada de cada despacho y su **única llegada**, inclusive cero unidades. «Recibida con discrepancia» es estado histórico; resolver su alerta no corrige automáticamente stock, valor ni estado. La valoración y compensaciones son las definidas en RF, no inventar nuevas.
- Aplicar restricciones `PRIMARY KEY`, `FOREIGN KEY`, `UNIQUE`, `CHECK`, `NOT NULL` e índices justificados por accesos reales; `UNIQUE(product_id, branch_id)` conceptual para la fila de saldo producto-sucursal y unicidad de llegada por despacho según su modelo. Resolver carreras para saldos aún inexistentes. Evitar cascadas de borrado que eliminen historial operativo.
- `dashboard` y `reports` operan inicialmente como consumidores de consultas sin tablas propias obligatorias.
- **Propiedad de sucursales:** se propuso ubicar los datos maestros en `catalog`, pero **no se ratificó de forma definitiva esa asignación particular**. Determinarla al derivar el E-R de los requisitos y los límites modulares; no presentarla como certeza aprobada.
- Generación de UUIDv4 preferentemente desde backend, con posibilidad de mecanismo SQL de respaldo cuando esté justificado; documentar la convención concreta al definir entidades y migraciones.

**Justificación.** Las relaciones y las confirmaciones atómicas del inventario favorecen la integridad del modelo relacional. `BigDecimal`/`NUMERIC` protege precisión; la identidad pública desacopla API y persistencia, y la historia posibilita trazabilidad sin imponer event sourcing.

**Verificación prevista:** casos extremos de valoración/redondeo, conversiones y filtros por calendario, autenticación por UUID y separación de sucursales, integridad referencial, historia y restricciones, consistencia saldo/movimientos. **Diseño de entidades, cardinalidades y DDL concretos:** actividad posterior basada en RF, no inventar aquí.

### PEN-006 — ARQ-001: frontend React modular, shadcn/ui y React Router

**Estado:** APROBADA.

**Decisión aprobada**

- Stack frontend: **React + TypeScript + Vite**; estilos **Tailwind CSS**; componentes **shadcn/ui**; iconos **Lucide React**.
- Navegación: **React Router**, preferentemente **modo declarativo**, **NO TanStack Router**. React Router y TanStack Query son compatibles y tienen responsabilidades complementarias: rutas/navegación frente a consultas/caché/polling, respectivamente.
- Datos/caché: **TanStack Query**, respetando PEN-003. Formularios: **React Hook Form + Zod**. Tablas: **TanStack Table** con paginación/filtros coordinados con el backend. Gráficos: **Recharts**; los indicadores se calculan en backend y se presentan en frontend.
- Cliente HTTP inicial **`fetch` centralizado**, con URL base configurable y credenciales de sesión incluidas, CORS restrictivo y token CSRF conforme a PEN-001. La seguridad visual de rutas y botones no reemplaza validación y autorización del backend.
- Organización **por módulos funcionales** (feature-first), con `pages`, `components`, `hooks`, `services`, `types`, `schemas` cuando hagan falta; layout/proveedores/navegación compartidos, `components/ui` para shadcn/ui y librería de cliente HTTP. No replicar literalmente `controller/entity/repository` de Java en React.
- Establecer un sistema visual común, responsivo y accesible, con navegación, tablas, validaciones, indicadores y estados de carga, error, vacío, desconexión y datos obsoletos. No mostrar éxito de una confirmación hasta recibir respuesta autoritativa del backend. No fijar una identidad gráfica institucional sin fuente aprobada.
- Las conversiones en React no definen autorización, saldos, costos, estados, valoración ni indicadores. **Sin conexión directa a PostgreSQL**.

**Referencia estructural orientativa:**

```text
frontend/src/
├── app/{router,providers,layouts}/
├── modules/
│   ├── auth/
│   ├── catalog/
│   ├── inventory/{pages,components,hooks,services,types,schemas}/
│   ├── purchases/
│   ├── sales/
│   ├── transfers/
│   ├── logistics/
│   ├── dashboard/
│   └── reports/
├── components/{ui,shared}/
├── lib/api/
└── styles/
```

**Justificación.** Tecnologías integrables en una SPA moderna y personalizable; separación funcional clara, reutilización visual, validación de formularios sin desplazar reglas de negocio y navegación compatible con la consulta remota.

**Verificación prevista:** rutas/roles, uso de cookies+CSRF, componentes consistentes, responsividad, errores/estados, tablas grandes y gráficos calculados por servidor.

### PEN-007 — ARQ-004: Docker Compose local, volumen Docker, Flyway y datos semilla

**Estado:** APROBADA. **Esta sección sustituye la propuesta anterior de `bind mount`; la decisión final es volumen nombrado Docker.**

**Decisión aprobada**

- Definir **tres servicios principales** Docker Compose: `frontend` (React **servido por Vite**), `backend` (Spring Boot) y `postgres` (PostgreSQL), ejecutados **todos localmente** con comando de referencia `docker compose up --build`.
- Utilizar un **volumen nombrado administrado por Docker, `pgdata`**, para la persistencia del clúster PostgreSQL. **No utilizar bind mount a carpeta del proyecto** como mecanismo elegido. Montarlo en la **ruta correcta según la versión de imagen**; no asumir que toda versión utiliza idéntica ruta o `PGDATA`.
- El volumen persiste entre `up`, `down` y reconstrucciones. Advertir que **`docker compose down -v` puede borrarlo**. No confundir archivos físicos del volumen con respaldo exportable; una copia portable se realiza con mecanismos PostgreSQL cuando se necesite.
- **Flyway integrado en Spring Boot**, sin servicio extra, como autoridad para crear/versionar el esquema mediante **archivos `.sql` versionados**, incluidos índices, restricciones y tablas de Spring Session JDBC. Las migraciones pendientes se aplican al iniciar y quedan registradas en el historial de Flyway; **Hibernate `ddl-auto=validate`**, sin `create`, `create-drop` o `update` automático sobre el esquema del negocio.
- Se cumple la intención de “crear el esquema si no existe” mediante **migración inicial** sobre una instalación limpia. **No programar un comprobador ad hoc de tablas ni ejecutar de nuevo todo `schema.sql` en cada inicio.** En instalaciones migradas con anomalías o tablas borradas accidentalmente, fallar con mensaje claro en vez de recrear silenciosamente un estado inconsistente.
- Incluir **datos semilla de demostración mediante SQL versionado/controlado por Flyway**, con sucursales, productos, existencias, operaciones e historia coherente suficiente para que la aplicación no inicie vacía. Evitar duplicarlos en reinicios. Diferenciar las semillas de demostración de las migraciones estructurales para no sembrar datos de demo por accidente en un entorno ajeno al local.
- Los datos semilla **deben satisfacer RF**, incluyendo valoración, tránsito, movimientos, estados y permisos: nada de inventar hechos o saldos contradictorios. No guardar secretos/contraseñas en claro ni credenciales universales dentro de archivos SQL públicos.
- Proveer **primer administrador** de forma segura, automatizada y controlada en el arranque inicial, con credencial individual desde configuración segura/entorno y cambio inicial obligatorio según RF; nunca imprimir secretos en logs ni fijarlos en el repositorio.
- Definir **healthchecks** para PostgreSQL y backend, y comprobar la disponibilidad del frontend. Compose debe esperar la salud de dependencias reales antes de considerar listo el backend; el healthcheck no reemplaza ejecutar migraciones y verificar esquema.
- React se sirve **mediante Vite en el contenedor frontend** para **desarrollo/demostración local**; exponerlo en puerto local propio, como `127.0.0.1:5173` (propuesto y configurable). Spring Boot en puerto local distinto, como `127.0.0.1:8080` (propuesto y configurable). Vite escucha dentro del contenedor en interfaz apta para publicación de Docker. PostgreSQL queda disponible en red interna de Compose, **sin publicación de puerto en el host por defecto**; usa internamente `5432`.
- **HTTP directo navegador → backend**, con CORS y sesiones/cookies/CSRF de PEN-001. Sin NGINX, HAProxy, Redis, servicio de mensajería ni proxy inverso. No confundir servir estáticos con Vite en local con una configuración de producción pública.
- Variables de entorno y `.env.example` documentados; **no versionar secretos reales**. Mantener el inicio reproducible con un comando y sin instalación local obligatoria de JDK, Node o PostgreSQL fuera de los contenedores.

**Esquema Compose conceptual, NO código ya implementado:**

```yaml
services:
  frontend:  # React + Vite, puerto local configurable
  backend:   # Spring Boot + Flyway, puerto local configurable
  postgres:  # PostgreSQL, sin puerto publicado por defecto
    volumes:
      - pgdata:<ruta-de-datos-segun-version-postgresql>
volumes:
  pgdata:
```

La ruta entre `<...>` es intencionalmente descriptiva, **no copiables sin verificar versión de imagen**. El usuario eligió `pgdata` administrado por Docker, no una carpeta local del repositorio.

**Justificación.** Un volumen Docker simplifica persistencia y permisos; Flyway automatiza creación y evolución SQL con trazabilidad; las semillas hacen demostrable el producto; healthchecks y Compose facilitan reproducibilidad. Cumple el alcance local sin servicios accesorios.

**Verificación prevista:** arranque de instalación limpia, tablas/sesiones/semillas presentes, reinicio sin duplicar datos, conservación con `down` (sin `-v`), reconstrucción, migración pendiente, fallos deliberados de migración con arranque detenido, conectividad HTTP y seguridad de configuración.

### PEN-008 — ARQ-002 y ARQ-010: contrato REST estandarizado y reportes XLSX

**Estado:** APROBADA. `ARQ-002` conserva además la justificación obligatoria de **Java/Spring Boot como lenguaje/plataforma de backend**: tipado, `BigDecimal`, integración Spring, monolito transaccional y REST, sin afirmar métricas no medidas.

**Decisión aprobada: API y validación**

- Adoptar **API REST** JSON con **prefijo `/api/v1`**, recursos de nombres plurales y propiedades JSON **`camelCase`**, con DTO explícitos `Request`/`Response`, sin serialización directa de entidades JPA ni fuga de columnas/ID `BIGINT`.
- Identificar recursos expuestos por **`externalId` UUIDv4** de acuerdo con `DEC-TEC-ID-001`; los códigos comerciales siguen independientes. Las relaciones entre entidades en el backend/PostgreSQL mantienen identificadores internos.
- Validar estructura/formatos con **Jakarta Bean Validation**; servicios de negocio gobiernan permisos, existencias, estados y cálculos. Paginación, filtros y ordenamiento pertinentes deben ejecutarse en backend y contar con DTO de respuesta estable para colecciones. No imponer envoltorio `{success,data}` a toda respuesta.
- Para valores decimales sensibles a precisión, **definir representación JSON que no degrade el decimal** al pasar por JavaScript (p. ej., cadenas decimales cuando corresponda); fijar el detalle de cada DTO y filtro al derivar contratos de RF, no usar indiscriminadamente `number` de JS para montos exactos.
- Emplear códigos HTTP semánticos (`200`, `201`, `204`, `400`, `401`, `403`, `404`, `409`, `429`, `500` cuando correspondan). Errores uniformes mediante **Spring `ProblemDetail` (RFC 9457)** y `@RestControllerAdvice`, con código estable de aplicación para distinguir errores; no filtrar trazas ni SQL.
- Documentar `Idempotency-Key` para solicitudes de creación/confirmación críticas, respetando la semántica ya aprobada en PEN-002. Los duplicados no producen nuevos efectos.
- Documentación mediante **OpenAPI 3 + springdoc-openapi + Swagger UI** dentro de Spring Boot, sin servicio extra. Mantener compatibilidad con cookie de sesión, CORS y CSRF; la documentación no justifica desactivar esas protecciones.
- Mantener frontend y backend desacoplados por contrato. React usa cliente `fetch` centralizado y maneja errores estructurados y descarga binaria autenticada.

**Decisión aprobada: exportaciones**

- Generar reportes de **movimientos, ventas y transferencias** en archivos **XLSX auténticos**, bajo demanda desde backend, mediante **Apache POI**, inicialmente **`XSSFWorkbook`**. Considerar `SXSSFWorkbook` solo si las pruebas reales de consumo de memoria justifican el cambio; es una opción técnica dentro de la misma biblioteca, no una nueva infraestructura.
- Respetar límites funcionales existentes: **hasta 10.000 filas** de negocio y **período máximo de 12 meses**, filtros, columnas, agrupación y permisos según RF, no según suposiciones del agente. Rechazar el exceso con error claro, **sin truncamiento silencioso**; archivo vacío con encabezados/nota válida. Consultas coherentes frente a modificaciones concurrentes.
- Evitar duplicar ventas/transferencias por `JOIN` con múltiples detalles. Verificar qué significa “una fila” para cada tipo de reporte consultando RF. Detectar excesos de modo acotado (p. ej. límite 10.001 cuando proceda), sin suponer que ese mecanismo por sí solo resuelve consistencia.
- Conservar precisión de cantidades/moneda, filtros de fechas inclusivos según calendario de `America/Bogota`, valorizaciones aprobadas y regla de indicadores sin historial suficiente (**«No calculable»**, no crear datos ficticios).
- Proteger archivos frente a contenido de usuario que pudiera interpretarse como **fórmulas de Excel**; tratar celdas/tipos sin degradar valores de negocio más allá de la precisión representable en Excel.
- Responder con MIME `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` y `Content-Disposition: attachment` con nombre de archivo apropiado. Descargar directamente desde React utilizando respuesta binaria (`Blob`) con credenciales de sesión.
- **Sin generación asíncrona, sin cola de trabajos, sin repositorio de archivos ni PDF adicional** por inferencia. `reports` lee capacidades de los módulos operativos, no muta saldos.

**Justificación.** Contratos explícitos y documentados evitan acoplar React a JPA, estandarizan errores y validaciones y preservan autorización. Apache POI produce archivos XLSX reales dentro de límites acotados, sin introducir servicios de exportación externos.

**Verificación prevista:** contratos OpenAPI, DTO sin ID internos, permisos y CSRF, errores HTTP uniformes, idempotencia por API; XLSX verificable, columnas/tipos/precisión, fechas, límite de filas/período, archivo vacío y rechazo de excedentes sin efectos en operaciones.

---

## 4. Restricciones funcionales previas que NO deben reinterpretarse

Al actualizar la arquitectura, respetar expresamente las decisiones ya vigentes del catálogo RF y su precedencia sobre antecedentes D:

1. **Solo el operador de la sucursal de destino** solicita transferencias; el administrador **no hereda automáticamente** ese permiso.
2. **Una sola confirmación de llegada por despacho**, incluso si la cantidad recibida es cero. No hay recepciones complementarias del mismo despacho.
3. El estado histórico **«Recibida con discrepancia»** permanece. Resolver la alerta **no modifica** stock, valor ni estado original del traslado; los ajustes autorizados se documentan como movimientos aparte.
4. No hay **reservas** ni **existencias negativas**. Una confirmación actualiza sus partes de forma indivisible y sus reintentos no duplican efectos.
5. **Residuales de valoración, compensaciones, indicadores, reasignación de usuarios, reactivación de productos, precisiones de cantidades y fechas** se obtienen de RF vigente; no extrapolar decisiones anteriores.
6. Los reportes exportables constituyen la funcionalidad adicional elegida; **no suponer integración con un sistema externo**, operación offline, mensajería distribuida, otros formatos exportables ni módulos adicionales.

## 5. Consistencia entre las ocho decisiones

Estas compatibilidades deben quedar explícitas en la versión final del documento:

- **PEN-001 ↔ PEN-003:** polling REST de 2 s no puede reactivar o prolongar inactividad de 30 min; diseñar/verificar expiración real sin confiar solo en tráfico HTTP.
- **PEN-001 ↔ PEN-006 ↔ PEN-007 ↔ PEN-008:** Vite/React en `localhost` puerto propio, backend en otro puerto, CORS restrictivo con credenciales, cookies `HttpOnly` de sesión, token CSRF accesible de modo seguro a React, cliente `fetch` sin proxy inverso. No introducir la estrategia de JWT/localStorage del proyecto de un tercero.
- **PEN-002 ↔ PEN-004 ↔ PEN-005:** los servicios de los módulos coordinan transacciones compartidas; `inventory` aplica valoración/existencias y es propietario de sus repositorios; `transfers` coordina `logistics`, con bloqueo e idempotencia adecuados y restricciones SQL que respaldan unicidad.
- **PEN-005 ↔ PEN-007:** Flyway crea y versiona SQL; JPA/Hibernate valida, no cambia autónomamente el esquema. Las tablas de Spring Session, relaciones y columnas públicas siguen la convención dual de identidad donde aplique.
- **PEN-005 ↔ PEN-008:** `BigDecimal`/`NUMERIC`, UUID públicos y fechas `America/Bogota` deben reflejarse correctamente en contratos REST, indicadores y exportaciones Excel.
- **PEN-003 ↔ PEN-008:** actualización visual por REST no equivale a fuente de verdad: siempre revalidar en backend al confirmar. Reportes son snapshots/consultas autorizadas, no cálculos del navegador.

## 6. Estados documentales resultantes y tratamiento de tareas posteriores

### 6.1. Decisiones cerradas

Marcar **APROBADAS** en la tabla original de pendientes las ocho entradas `PEN-001` ... `PEN-008`. La selección arquitectónica y sus fundamentos ya fueron ratificados; no conservar la frase «primera pendiente PEN-001» ni estados falsamente abiertos para React, autenticación, sincronización, capas o XLSX. El documento debe poder concluir que **la definición y aprobación de las ocho decisiones de la sección 8 está finalizada documentalmente**.

### 6.2. Trabajos y evidencias que aún NO están realizados

No declarar completados sin evidencia:

- Modelo lógico completo, cardinalidades definitivas, propietarios exactos de todas las entidades (incluida sucursal), precisión/tamaño SQL campo por campo e índices basados en consultas.
- Contratos REST exhaustivos, listado real de endpoints, DTO/códigos y su generación OpenAPI.
- Selección y verificación de **versiones concretas compatibles** de Java, Spring Boot, librerías React, PostgreSQL, Flyway, Spring Session y Apache POI; puertos finales si cambian por configuración.
- Mecanismo implementado para **8 h absolutas + 30 min de inactividad**, revocaciones, interacción con polling, adquisición ordenada de bloqueos, idempotencia y seguridad CORS/CSRF.
- Migraciones SQL y semillas realmente escritas, configuración Compose operativa, salud de servicios y administrador inicial aprovisionado.
- Pruebas funcionales, de seguridad, de concurrencia, de latencia de 5 s, de arranque limpio, persistencia o memoria y exactitud de XLSX.
- Diagramas obligatorios de la sección 7, incluyendo arquitectura y entidad-relación; este cierre **no los reemplaza**.

La documentación puede estar **cerrada en decisiones** y conservar una sección de **verificación/implementación pendiente** sin contradicción. No promover una recomendación no ratificada —como MapStruct obligatorio, propietario definitivo de sucursales, versión tecnológica o escala total `NUMERIC`— al estado de aprobación.

## 7. Cambios concretos solicitados al agente sobre el documento original

Aplicar estas operaciones dentro del documento existente:

1. En **§1**: actualizar versión y estado del documento para indicar que el responsable ratificó las decisiones `PEN-001`–`PEN-008` el 21/10/2026. Conserva el criterio de precedencia de RF y aclaración de evidencia no implementada.
2. En **§2**: actualizar cobertura del §8.1 y de las cinco justificaciones mínimas §8.2: elecciones fundamentadas/aprobadas; la conformidad ejecutable y los diagramas siguen pendientes.
3. En **§3**: actualizar presentación a shadcn/ui/Tailwind/React Router/TanStack Query, negocio Java/Spring Boot en monolito modular con capas tradicionales por módulo, PostgreSQL JPA/Flyway, infraestructura Compose Vite y `pgdata`.
4. En **§4**: conservar `ARQ-001`...`ARQ-010`; integrar sin duplicaciones:
   - `ARQ-001` ← PEN-006.
   - `ARQ-002` ← PEN-004 en estilo modular y PEN-008 en contrato REST; conservar justificación de Java/Spring Boot.
   - `ARQ-003` ← PEN-005.
   - `ARQ-004` ← PEN-007, indicando **volumen nombrado** y no bind mount.
   - `ARQ-005` ← PEN-001.
   - `ARQ-006` ← PEN-003.
   - `ARQ-007` ← PEN-002.
   - `ARQ-008` ← PEN-004 (nomenclatura exacta de capas tradicionales de Java).
   - `ARQ-009` ← PEN-005, con `DEC-TEC-ID-001` intacta.
   - `ARQ-010` ← PEN-008, con XSSFWorkbook y límites originales.
5. En **§5**: reemplazar el inventario de decisiones sin seleccionar por una **matriz de decisiones ratificadas** y, si se desea, un registro separado de **detalles/evidencias de implementación pendientes**, sin volver a poner en cuestión las tecnologías aprobadas.
6. En **§6**: dejar clara la ratificación documental completa, la trazabilidad, y distinguirla de código/DDL/Compose/diagramas/pruebas aún no ejecutados. No atribuir mediciones o implementaciones inexistentes.
7. **No modificar** `AGENTS.md`, los RF/RNF, D, archivos fuente ni diagramas dentro de esta tarea; el destinatario es exclusivamente el documento de arquitectura y diseño técnico, salvo instrucciones nuevas del responsable.
8. Al finalizar, **entregar una síntesis breve** de los cambios realizados, decisiones integradas, conflictos realmente detectados —si los hay— y detalles que todavía dependen de RF o de futuras pruebas. No abrir nuevas preguntas de selección ya ratificadas.

## 8. Criterios de aceptación de la actualización documental

- [ ] Las ocho `PEN` figuran **aprobadas** y se relacionan con las `ARQ` correctas, sin identificadores duplicados.
- [ ] Las cinco justificaciones obligatorias del §8.2 están explícitas y fundamentadas.
- [ ] El texto no confunde el cierre de decisiones con haber programado, probado o diagramado la solución.
- [ ] La arquitectura tiene **nueve módulos**, incluidos `transfers` y `logistics` separados, y **capas Java convencionales dentro de cada módulo**.
- [ ] PEN-001 documenta sesiones Spring Security/Spring Session JDBC, cookies, CORS, CSRF y revocación con expiraciones de 30 min y 8 h, **sin JWT ni proxy**.
- [ ] PEN-002 documenta `READ COMMITTED`, bloqueo pesimista, idempotencia, `@Transactional`, rollback y restricciones SQL.
- [ ] PEN-003 documenta polling de 2 s con TanStack Query y **prueba pendiente** del máximo de 5 s, sin prometer que 2 s lo garantiza ni prolongar sesiones.
- [ ] PEN-005 respeta identidad dual `BIGINT`/UUIDv4, precisión decimal y calendario `America/Bogota`; no presenta un E-R hipotético como aprobado.
- [ ] PEN-006 usa **React Router** (no TanStack Router), shadcn/ui, Tailwind, React Hook Form/Zod, TanStack Table, Recharts y TanStack Query.
- [ ] PEN-007 usa **`pgdata` como volumen Docker nombrado**, no bind mount, tres servicios locales, Vite, Flyway, SQL semilla, JPA `validate`, healthchecks y puertos propios sin proxy inverso.
- [ ] PEN-008 usa API `/api/v1`, DTO, `ProblemDetail`, OpenAPI y **Apache POI/XSSFWorkbook** con límites XLSX, sin tareas asíncronas ni reportes PDF inferidos.
- [ ] Se conservan matriz de permisos, única llegada, discrepancias históricas, residuales, no reservas/no stock negativo y restantes reglas vigentes de RF.
- [ ] Se distingue lo **ratificado**, lo **que debe especificarse usando RF** y lo **que solo se verificará con implementación/pruebas**.

---

**Instrucción final:** efectuar la consolidación directamente sobre el documento de arquitectura existente y dejar cerrada la **decisión documental** del punto 8. Mantener honestidad técnica: el cierre de este registro **no sustituye** el diseño entidad-relación, los diagramas ni las pruebas y la implementación posteriores.
