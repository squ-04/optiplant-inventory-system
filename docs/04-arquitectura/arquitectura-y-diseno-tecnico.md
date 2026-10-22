# Arquitectura y diseño técnico — OptiPlant

**Estado:** decisiones arquitectónicas PEN-001–PEN-008 ratificadas por el responsable el 21 de octubre de 2026; definición y aprobación documental del punto 8 finalizadas. No acredita implementación, pruebas ni modelo E-R completo.  
**Versión:** 1.1 — 22 de octubre de 2026; incorpora la evaluación de versiones y compatibilidad de §5.4 sin modificar las decisiones ratificadas.  
**Ámbito:** documento único de decisiones arquitectónicas y diseño técnico de la sección 8; los detalles derivados del modelo/contratos, la implementación y los diagramas de la sección 7 corresponden a etapas posteriores.

## 1. Propósito, fuentes y lectura

La solución adopta un frontend React, un backend Java/Spring Boot como monolito modular y una base PostgreSQL, comunicados mediante API REST y ejecutados localmente con Docker Compose. Estas tecnologías son elecciones del proyecto, no imposiciones tecnológicas de OptiPlant. Este documento integra las ocho decisiones ratificadas, sus fundamentos y precisiones; distingue el cierre de selección de los detalles y evidencias que se producirán posteriormente.

| Clave | Fuente | Uso |
|---|---|---|
| M | [Prueba técnica en Markdown](../00-source/prueba-tecnica.md), §§5, 7 y 8 | Referencia operativa solicitada por el responsable para esta redacción. |
| E | [PDF original](../00-source/prueba-tecnica.pdf) | Fuente externa definitiva si aparece una discrepancia con M; no se modifica. |
| P | [Instrucciones del proyecto](../../AGENTS.md), §1 | Stack, alcance y propuestas técnicas previas. |
| RF | [Catálogo funcional vigente](../01-requerimientos/requisitos-funcionales.md), v1.2 | Comportamiento aprobado, matriz de permisos, precisiones humanas y criterios de aceptación; H v1.2/01 precisa rechazo de origen sin cambiar arquitectura. |
| D | [Decisiones ratificadas](../01-requerimientos/decisiones-aprobadas-optiplant-v1.0.md), v1.2 | Antecedentes aprobados, resoluciones funcionales posteriores y decisión técnica `DEC-TEC-ID-001`. |
| RNF | [Requisitos no funcionales](../01-requerimientos/requisitos-no-funcionales.md) | Condiciones documentadas de seguridad, precisión, capacidad, usabilidad y actualización. No implica aprobación arquitectónica. |
| A | [Acta de decisiones arquitectónicas ratificadas](respuestas-decisiones-arquitectura-optiplant.md), §§1–8 | Aprobación humana del 21/10/2026 e instrucciones de integración de PEN-001–PEN-008; fuente de las selecciones y precisiones incorporadas en esta versión. |

### 1.1. Estados y obligatoriedad

- **Decisión aprobada:** tecnología, patrón o mecanismo expresamente ratificado en A o en las fuentes previas aplicables; no se vuelve a someter a selección.
- **Detalle por especificar:** contrato, entidad, cardinalidad, versión o parámetro cuya concreción no está ratificada; se deriva de RF y del diseño posterior, sin inventar límites funcionales.
- **Verificación/implementación pendiente:** evidencia o artefacto todavía no producido. No reabre la selección arquitectónica aprobada.
- **Recomendación no ratificada:** posibilidad señalada expresamente como opcional o tentativa; no adquiere obligatoriedad por figurar en este documento.
- **Justificación obligatoria §8.2:** pertenece a una de las cinco categorías mínimas exigidas por M. Otras decisiones significativas también deben justificarse, aunque no formen parte de esa lista mínima.

El estado de selección y justificación documental es **aprobado** para las ocho PEN. Los detalles de contrato/esquema solo están definidos hasta donde lo indica A; §5.4 concreta versiones por autorización posterior del responsable, sin atribuirlas retrospectivamente al acta. Ninguna decisión acredita software implementado, pruebas ejecutadas o métricas medidas; las comprobaciones enumeradas son futuras.

### 1.2. Precedencia y discrepancias conocidas

RF §2.1 registra correcciones humanas posteriores que sustituyen detalles de D. Para este diseño se conservan esas correcciones explícitas, sin reescribir los antecedentes:

- Solo el operador del destino solicita transferencias; el administrador no hereda esa ejecución.
- Cada despacho tiene una única confirmación de llegada, incluso con cero unidades. No se admiten recepciones complementarias del mismo despacho.
- «Recibida con discrepancia» permanece como estado histórico; resolver su alerta no modifica stock, valor ni estado del traslado. Los movimientos autorizados se registran aparte.
- Los residuales de valoración, compensaciones, indicadores, reasignación de usuarios y reactivación de productos se rigen por las precisiones vigentes de RF, no por alternativas antiguas de D.

Estas diferencias están documentadas en RF; no son nuevas resoluciones arquitectónicas. Si aparece una contradicción no resuelta expresamente, debe elevarse al responsable antes de cerrar el diseño afectado.

A ratifica mecanismos arquitectónicos, no nuevas reglas funcionales. La intención previa de JWT en RF §8 queda sustituida, en la selección técnica, por sesiones persistidas; los efectos funcionales de acceso no cambian. P §1 y las [restricciones técnicas](../01-requerimientos/restricciones-tecnicas-y-de-negocio.md) aún conservan estilos/componentes como pendientes, y P mantiene los módulos como referencia propuesta y Recharts como alternativa inicial: son estados anteriores a A, ahora resueltos en ARQ-001 y ARQ-008. RNF remite la selección al diseño posterior, sin imponer JWT. Se conserva este desfase de actualización sin modificar esas fuentes ni ocultar su procedencia. No se identificó una contradicción funcional sin resolución entre A y RF vigente.

## 2. Cobertura de las obligaciones externas

| Obligación | Fuente | Tratamiento en este documento | Situación |
|---|---|---|---|
| Separar frontend, backend y base de datos | M §5 | §3 y ARQ-001–ARQ-004 | Base seleccionada; cumplimiento ejecutable pendiente. |
| API exclusiva, sin lógica de negocio en el cliente | M §§5 y 8.1 | §3, ARQ-001 y ARQ-002 | Responsabilidades delimitadas. |
| Interfaz responsiva | M §8.1 | ARQ-001 | Stack y organización aprobados; comprobación visual posterior. |
| Compose con servicios independientes y aislados; arranque con un comando | M §§5 y 8.1 | ARQ-004 | Topología local, persistencia y migraciones aprobadas; configuración y prueba posteriores. |
| Justificar lenguaje de backend | M §8.2 | ARQ-002 | Elección y justificación documental aprobadas. |
| Justificar motor y modelo de datos | M §8.2 | ARQ-003 | Estrategia relacional y fundamento aprobados; modelo lógico/físico completo y E-R posteriores. |
| Justificar autenticación y autorización | M §8.2 | ARQ-005 | Sesiones JDBC y seguridad aprobadas y justificadas; implementación y pruebas posteriores. |
| Justificar sincronización entre sucursales | M §8.2 | ARQ-006 | Polling REST aprobado y justificado; latencia real pendiente de medición. |
| Justificar patrones utilizados | M §8.2 | ARQ-008 | Patrones y capas tradicionales por módulo aprobados y fundamentados; inspección ejecutable posterior. |

M §7 exige diagramas de arquitectura y entidad-relación, entre otros. Este cierre documental no los sustituye ni declara completada esa sección. La conformidad ejecutable de §§5 y 8.1 permanece pendiente de implementación y pruebas.

## 3. Separación de responsabilidades por capas — §8.1

| Capa externa | Tecnología y condición | Responsabilidades | Límites |
|---|---|---|---|
| Presentación | React/TypeScript/Vite, Tailwind CSS, shadcn/ui, Lucide React, React Router, TanStack Query, React Hook Form/Zod, TanStack Table y Recharts: aprobados en A PEN-006. | Interacción, navegación, formularios, presentación responsiva, gráficos y consumo de API. | Sin acceso directo a PostgreSQL; no decide permisos, stock, costos, estados ni confirmaciones. |
| Negocio | Java/Spring Boot y REST `/api/v1`; monolito de nueve módulos con capas Java tradicionales propias, Spring Security y Spring Session JDBC. | Validaciones autoritativas, autorización, casos de uso, transferencias, costos, consultas y XLSX mediante Apache POI. | No delega reglas al navegador ni expone entidades JPA como contrato público. |
| Datos | Una base PostgreSQL, esquema inicial `public`; Spring Data JPA/Hibernate y Flyway ejecutados en el backend. | Persistencia relacional, sesiones JDBC, integridad y evolución SQL versionada; Hibernate valida el esquema. | Sin acceso directo desde frontend. Propiedad lógica por módulo; las restricciones complementan las reglas del negocio. |
| Infraestructura | Compose local con `frontend` servido por Vite, `backend` y `postgres`; volumen nombrado `pgdata`, healthchecks y puertos locales independientes. | Arranque reproducible, persistencia y configuración segura sin instalaciones locales obligatorias de JDK/Node/PostgreSQL. | HTTP directo navegador → backend; sin proxy inverso ni servicios accesorios. No se declara configuración de producción pública. |

El recorrido conceptual es presentación → API del backend → caso de uso autorizado → acceso a datos → PostgreSQL. La respuesta vuelve por la API. Las capas internas del backend descritas en ARQ-008 no reemplazan las cuatro responsabilidades externas de M §8.1.

## 4. Registro integrado de decisiones

Los identificadores `ARQ-NNN` son estables y propios de este documento. `DEC-TEC-ID-001` conserva su identificador y aprobación originales; ARQ-009 solo la referencia, sin crear otra decisión competidora.

### ARQ-001 — Presentación web y herramientas del frontend

**Estado:** stack y organización del frontend APROBADOS por A PEN-006; implementación y comprobaciones posteriores.  
**Clasificación:** decisión significativa; no pertenece a las cinco justificaciones mínimas específicas de §8.2.  
**Trazabilidad:** A PEN-006, relacionada con PEN-001, PEN-003 y PEN-007; P §1; D §2; M §§5 y 8.1; RNF-USA-01.

**Decisión aprobada.** Construir una interfaz web React + TypeScript + Vite, con Tailwind CSS, componentes shadcn/ui e iconos Lucide React. Adoptar React Router para rutas y navegación, preferentemente en modo declarativo; **no TanStack Router**. TanStack Query gestiona consultas, caché y polling, no reemplaza al router. Usar React Hook Form + Zod para formularios, TanStack Table para tablas con paginación/filtros coordinados con backend y Recharts para representar indicadores calculados por servidor.

Organizar el frontend por funcionalidad, con `pages`, `components`, `hooks`, `services`, `types` y `schemas` solo cuando hagan falta. La referencia orientativa es `app/{router,providers,layouts}`, `modules/{auth,catalog,inventory,purchases,sales,transfers,logistics,dashboard,reports}`, `components/{ui,shared}`, `lib/api` y `styles`. `components/ui` contiene los componentes shadcn/ui; layout, proveedores y navegación son compartidos. No copiar los subpaquetes Java `controller/entity/repository` ni crear carpetas vacías por reflejo.

El cliente inicial es `fetch` centralizado, con URL base configurable, credenciales de sesión y token CSRF conforme a ARQ-005. Se comunica exclusivamente con la API. Los controles de rutas, botones y formularios orientan al usuario, pero no sustituyen validación y autorización de backend. Ninguna conversión en React define saldos, costos, estados, valoración o indicadores; no existe conexión directa a PostgreSQL.

Establecer un sistema visual común, responsivo y accesible para navegación, tablas, validaciones e indicadores, con estados de carga, error, vacío, desconexión y datos obsoletos. No mostrar éxito antes de la confirmación autoritativa del backend ni fijar identidad gráfica institucional sin fuente aprobada.

**Fundamento.** React permite componentes reutilizables para catálogos, operaciones y dashboards; TypeScript aporta comprobaciones estáticas, no autorización ni validación automática de respuestas. Vite cubre desarrollo y construcción. Las herramientas seleccionadas separan navegación, consulta remota, formularios, tablas y gráficos, con personalización visual y cohesión funcional sin desplazar reglas de negocio al cliente. Recharts queda ratificado, no como selección abierta.

**Alternativas y consecuencias.** Vue o Angular satisfacen el enunciado, pero sustituir React reabriría una elección ya aprobada sin necesidad identificada. Mantener frontend separado exige gestionar estados de carga, errores y datos desactualizados; la selección tecnológica no garantiza por sí sola accesibilidad o responsividad.

**Detalles y verificación posteriores.** Concretar vistas, versiones compatibles y contratos de consumo. Verificar rutas/roles, cookies y CSRF, consistencia visual, responsividad, errores, tablas grandes y gráficos de servidor; manipular el navegador no debe eludir validaciones del backend.

### ARQ-002 — Java, Spring Boot, monolito modular y API REST

**Estado:** Java/Spring Boot, estilo modular y convenciones REST APROBADOS; contratos exhaustivos y versiones corresponden a detalle posterior.  
**Clasificación:** **JUSTIFICACIÓN OBLIGATORIA §8.2 — lenguaje de backend**; también explica el estilo del backend.  
**Trazabilidad:** A PEN-004 y PEN-008; M §§5, 8.1 y 8.2; P §1; D §2; RF §1.

**Decisión base.** Centralizar el negocio en un backend Java/Spring Boot, desplegado como una aplicación modular, con comunicación REST hacia React.

**Fundamento.** Java ofrece tipado estático y representación decimal mediante `BigDecimal`, adecuados para expresar estados, cantidades y costos con reglas explícitas. Spring Boot permite integrar el backend con el acceso JPA/Hibernate seleccionado y organizar sus componentes. Son ventajas de adecuación al problema, no evidencia de rendimiento ni una garantía automática de integridad.

Un monolito modular permite mantener juntas las operaciones que afectan documentos, saldos y valoración, sin introducir coordinación transaccional entre servicios independientes. Es proporcional a una plataforma conectada de una sola organización y al alcance de demostración. Los módulos siguen necesitando límites y dependencias claros; un único despliegue no los garantiza.

**Contrato REST aprobado.** API JSON con prefijo `/api/v1`, nombres plurales para recursos y propiedades `camelCase`. DTO explícitos `Request`/`Response`, sin serializar entidades JPA ni exponer claves `BIGINT`; la identificación pública es `externalId` UUIDv4, conforme a `DEC-TEC-ID-001` y ARQ-009. Las relaciones internas mantienen sus claves técnicas.

Jakarta Bean Validation valida estructura y formatos; los servicios gobiernan permisos, existencias, estados y cálculos. Paginación, filtros y ordenamiento pertinentes se ejecutan en backend con DTO estable para colecciones, sin imponer un envoltorio `{success,data}` a todas las respuestas. Definir por DTO y filtro una representación decimal que no pierda precisión al pasar por JavaScript, por ejemplo cadenas decimales donde corresponda; no usar indiscriminadamente `number` para valores exactos.

Usar códigos HTTP semánticos `200`, `201`, `204`, `400`, `401`, `403`, `404`, `409`, `429` y `500` cuando correspondan. Uniformar errores con Spring `ProblemDetail` (RFC 9457), `@RestControllerAdvice` y código estable de aplicación, sin filtrar SQL ni trazas. Documentar `Idempotency-Key` para creaciones/confirmaciones críticas según ARQ-007.

Documentar con OpenAPI 3, springdoc-openapi y Swagger UI dentro de Spring Boot, sin servicio extra y sin desactivar cookies, CORS o CSRF. El cliente `fetch` maneja errores estructurados y descargas binarias autenticadas; frontend y backend se desacoplan mediante contrato, no por compartir entidades persistentes.

**Alternativas y consecuencias.** Otros lenguajes y GraphQL son admisibles externamente, pero no existe autorización para sustituir las elecciones del proyecto. Microservicios introducirían despliegues y fallos distribuidos que el alcance no demuestra necesitar. El monolito implica despliegue conjunto y requiere disciplina para evitar acoplamiento entre módulos.

**Detalles y verificación posteriores.** Derivar de RF el inventario exhaustivo de endpoints, DTO, representaciones y códigos concretos; producir y comprobar OpenAPI. Revisar dependencias modulares, idempotencia, ausencia de claves internas, validaciones, errores HTTP, permisos y CSRF mediante API. Verificar versiones compatibles de Java/Spring Boot y bibliotecas; no prometer escalabilidad cuantitativa sin medirla.

### ARQ-003 — PostgreSQL y modelo relacional

**Estado:** estrategia relacional PostgreSQL/JPA y propiedad lógica por módulo APROBADAS; esquema lógico/físico completo y E-R posteriores.  
**Clasificación:** **JUSTIFICACIÓN OBLIGATORIA §8.2 — motor de base de datos y modelo adoptado**.  
**Trazabilidad:** A PEN-005, relacionada con PEN-002, PEN-004 y PEN-007; M §§8.1–8.2; P §1; D §2 y §9; RF §§5.1–5.4; RNF-PRE-01.

**Decisión aprobada.** Una base PostgreSQL con modelo relacional normalizado, inicialmente en esquema SQL común `public`, y acceso desde el backend mediante Spring Data JPA/Hibernate. Compartir esquema y transacciones no elimina la propiedad lógica de datos por módulo ni permite acceder a repositorios internos ajenos. Flyway crea/versiona el esquema y Hibernate lo valida, según ARQ-004.

**Fundamento.** Productos, sucursales, documentos, líneas y movimientos tienen relaciones e invariantes que necesitan identidad, referencias e integridad. Las operaciones requieren confirmar conjuntamente cambios relacionados o rechazarlos sin efectos parciales. El modelo relacional y las transacciones de PostgreSQL encajan con estas necesidades; la corrección depende también del diseño transaccional y concurrente, no solo del motor.

**Criterios aprobados del modelo.** Separar catálogo común de existencias producto-sucursal; distinguir documentos y líneas; conservar saldos actuales, movimientos e instantáneas de valoración requeridas; representar tránsito sin duplicarlo como disponible destino; preservar `DEC-TEC-ID-001` y la historia mediante operaciones posteriores autorizadas, sin event sourcing obligatorio. Mantener única llegada incluso cero, discrepancia histórica y tratamiento separado de alertas conforme a RF.

Aplicar `PRIMARY KEY`, `FOREIGN KEY`, `UNIQUE`, `CHECK`, `NOT NULL` e índices justificados por accesos reales. Se aprueba conceptualmente `UNIQUE(product_id, branch_id)` para saldo producto-sucursal y unicidad de llegada por despacho, a concretar con el modelo. Resolver carreras al crear saldos inexistentes y evitar cascadas que borren historial operativo. `dashboard` y `reports` consumen consultas, sin tablas propias obligatorias.

El propietario definitivo de los datos maestros de sucursales **no está ratificado**: su ubicación en `catalog` fue propuesta, no aprobada. Se determinará al derivar el E-R y responsabilidades completas de RF; no es una reapertura de los nueve módulos aprobados.

**Alternativas y consecuencias.** MySQL podría satisfacer un modelo relacional semejante; no se ha identificado una necesidad para sustituir PostgreSQL. NoSQL no está prohibido, pero no se ha justificado una ventaja que compense modelar estas relaciones y confirmaciones. JPA/Hibernate reduce acceso repetitivo, pero requiere revisar consultas, cargas y límites transaccionales; no elimina el conocimiento de SQL ni de concurrencia.

**Detalles y verificación posteriores.** Derivar entidades, cardinalidades, DDL, índices y precisión SQL campo por campo de RF y consultas reales. La estrategia y su justificación están aprobadas; el modelo completo no se presume realizado. Probar integridad referencial, restricciones, historia y consistencia saldo/movimientos con PostgreSQL, junto con las verificaciones numéricas y temporales de ARQ-009.

### ARQ-004 — Ejecución y aislamiento con Docker Compose

**Estado:** ejecución local, servicios, volumen nombrado, migraciones, semillas y controles de arranque APROBADOS por A PEN-007; configuración operativa no implementada en esta actividad.  
**Clasificación:** justificación de una decisión significativa y cumplimiento obligatorio de M §§5 y 8.1; no categoría mínima independiente de §8.2.  
**Trazabilidad:** A PEN-007, relacionada con PEN-001 y PEN-005; P §1; M §§5 y 8.1; RNF-SEG-02.

**Decisión aprobada.** Ejecutar localmente tres servicios principales Compose: `frontend` (React servido por Vite), `backend` (Spring Boot) y `postgres` (PostgreSQL). Comando de referencia: `docker compose up --build`, sin instalar JDK, Node o PostgreSQL fuera de los contenedores. Es una decisión documentada, no una configuración creada ni un comando ejecutado en esta actividad.

**Persistencia.** Volumen nombrado Docker **`pgdata`**, administrado por Docker, no bind mount a una carpeta del proyecto. Montarlo en la ruta correcta según versión de imagen y `PGDATA`; no asumir idéntica ruta en todas las versiones. Conserva datos entre `up`, `down` y reconstrucciones; advertir que **`docker compose down -v` puede borrarlos**. Los archivos físicos del volumen no son un respaldo portable: cuando se necesite una copia exportable se utilizarán mecanismos PostgreSQL.

**Esquema y semillas.** Flyway integrado en Spring Boot, sin servicio adicional, es la autoridad para crear y versionar esquema mediante SQL versionado, incluidos índices, restricciones y tablas Spring Session JDBC. Las migraciones pendientes se aplican al iniciar y se registran en su historial. Hibernate utiliza **`ddl-auto=validate`**, no `create`, `create-drop` o `update` automático sobre el negocio. Una migración inicial resuelve la instalación limpia; no crear un comprobador ad hoc de tablas ni ejecutar de nuevo todo `schema.sql` en cada arranque. Si una instalación migrada presenta anomalías o tablas borradas, detener el arranque con error claro, sin reconstrucción silenciosa inconsistente.

Incluir datos semilla de demostración por SQL versionado/controlado con Flyway, separados de migraciones estructurales para evitar sembrarlos accidentalmente fuera del entorno local. Deben incluir sucursales, productos, existencias, operaciones e historia coherente suficiente para no iniciar vacío, sin duplicarse en reinicios ni contradecir valoración, tránsito, movimientos, estados o permisos de RF. Identificarlos como demostración, nunca como historia productiva fabricada. No incluir secretos en claro ni credenciales universales en SQL público.

Proveer el primer administrador de forma segura, automatizada y controlada en el arranque inicial: credencial individual desde configuración segura/entorno y cambio inicial obligatorio. No fijar secretos en repositorio ni imprimirlos en logs. Documentar variables y `.env.example` sin versionar secretos reales; comprobar que el flujo completo conserva el inicio reproducible sin configuración manual local adicional exigido por M §5.

**Disponibilidad y conectividad.** Healthchecks para PostgreSQL y backend; comprobar también disponibilidad de frontend. Compose debe esperar salud de dependencias reales para considerar listo el backend; eso no sustituye migraciones ni validación del esquema. Vite escucha dentro del contenedor en una interfaz apta para publicación Docker. Puertos locales propios y configurables, por ejemplo `127.0.0.1:5173` para frontend y `127.0.0.1:8080` para backend; son referencias propuestas, no puertos finales inmutables. PostgreSQL usa internamente `5432` y no publica puerto al host por defecto.

El navegador accede al backend por HTTP directo, sin NGINX, HAProxy ni proxy inverso y con CORS/cookies/CSRF de ARQ-005. No introducir Redis, mensajería u otros servicios. Servir React mediante Vite es para desarrollo/demostración local, no una configuración de producción pública ni evidencia de HTTPS de extremo a extremo.

**Fundamento.** Compose permite declarar conjuntamente los servicios necesarios y su configuración de ejecución, reduciendo diferencias entre instalaciones. El aislamiento permite delimitar accesos y persistencia. No implica que el arranque correcto, las migraciones o la seguridad queden resueltos automáticamente.

Flyway aporta trazabilidad a la creación/evolución SQL; las semillas permiten demostrar el alcance y los healthchecks explicitan dependencias de arranque. El volumen nombrado simplifica persistencia y permisos sin vincular archivos físicos a una carpeta del repositorio. La elección sustituye la propuesta de bind mount indicada como antecedente en A; el borrador 0.1 no había seleccionado ese mecanismo.

**Alternativas y consecuencias.** Instalar Java, Node y PostgreSQL manualmente como requisito de ejecución incumpliría el objetivo de M §5. Kubernetes no aporta una necesidad demostrada para esta entrega. El almacenamiento persistente exige documentar su ciclo de vida; reiniciar contenedores no debe confundirse con borrar datos.

**Detalles y verificación posteriores.** Verificar imágenes/versiones compatibles, ruta del volumen, puertos finales y configuración de secretos. Escribir migraciones/semillas y Compose en una actividad posterior. Probar instalación limpia, tablas de sesiones, datos coherentes, reinicio sin duplicación, persistencia con `down` sin `-v`, reconstrucción y migración pendiente. Comprobar fallo deliberado de migración con arranque detenido, conectividad HTTP y seguridad de configuración. No se han ejecutado estas operaciones ni creado infraestructura en esta actividad.

### ARQ-005 — Autenticación, autorización y sesiones

**Estado:** Spring Security + Spring Session JDBC y estrategia de cookies/CSRF/CORS APROBADOS por A PEN-001; JWT descartado como mecanismo principal.  
**Clasificación:** **JUSTIFICACIÓN OBLIGATORIA §8.2 — autenticación y autorización**.  
**Trazabilidad:** A PEN-001 y su compatibilidad con PEN-003/PEN-006/PEN-007/PEN-008; M §8.2; RF-0006–RF-0007 y RF §5.2; D INC-003/D09–D14; RNF-SEG-01–RNF-SEG-05.

**Base que no se reabre.** Acceso con código único y contraseña; un rol por usuario; administrador global y gerente/operador de una sucursal. Aplicar la matriz de RF §5.2, sin herencia automática de ejecución. La autorización debe comprobar tanto acción como recurso y ámbito de sucursal; conocer un UUID no concede acceso.

Las sesiones deben expirar tras 30 minutos de inactividad u 8 horas absolutas. Salir, desactivar una cuenta o cambiar su rol/sucursal debe revocar accesos previos. Se mantienen contraseña temporal individual, cambio inicial y provisión segura del primer administrador. Los límites y la política inicial antiabuso ya documentados no se sustituyen por valores nuevos.

**Decisión aprobada.** Spring Security para autenticación/autorización y Spring Session JDBC para sesiones persistidas en la misma instancia PostgreSQL. Sin JWT principal, Redis ni repositorios externos de sesiones. React y Spring Boot son servicios independientes en puertos distintos, HTTP directo y sin proxy inverso.

Configurar CORS restrictivo con orígenes locales explícitos y soporte de credenciales: nunca `Access-Control-Allow-Origin: *` con credenciales. Cookie de sesión `HttpOnly`, `SameSite=Lax` y atributos adecuados al entorno. En HTTP local no aplicar `Secure` de manera que impida usar la sesión; exigirlo bajo HTTPS. No almacenar identificadores de sesión ni credenciales en `localStorage`.

Mantener CSRF habilitado para cambios de estado, con obtención/intercambio seguro de token compatible con React y el cliente `fetch` centralizado. El token CSRF es distinto de la cookie de sesión `HttpOnly`. La documentación OpenAPI no autoriza desactivar protecciones. `localhost:5173` y `localhost:8080` son orígenes distintos por puerto para CORS, aunque pueden compartir *site* para `SameSite`; mantener un hostname coherente y evitar mezclar `localhost` y `127.0.0.1` si cambia el comportamiento de cookies.

Guardar contraseñas con hash resistente apropiado; Argon2id o BCrypt son ejemplos según compatibilidad y parámetros elegidos, no selección cerrada de algoritmo. Conservar política de 15–128 caracteres incluidos espacios, sin rotación arbitraria, y protección antiabuso inicial de cinco fallos en quince minutos, con esperas progresivas de cinco, quince y hasta treinta minutos, sin bloqueo permanente automático. Concretar implementación sin alterar los umbrales ratificados.

Verificar rol, acción, recurso y sucursal en backend en cada caso de uso. Implementar revocación por salida/desactivación/cambio de rol o sucursal, límite absoluto de ocho horas y treinta minutos sin actividad interactiva relevante: **no basta `maxInactiveInterval`**. El polling automático no debe reiniciar artificialmente la inactividad. El criterio de actividad debe ser aplicable y controlado en servidor, sin confiar ciegamente en una afirmación manipulable del cliente. Conservar provisión individual segura, contraseña temporal y cambio inicial.

**Fundamento, alternativas y consecuencias.** Centralizar sesiones facilita revocación, seguimiento de actividad y autorización contextual en la plataforma conectada; reutiliza PostgreSQL. JWT solo validado por firma y vencimiento no resuelve revocación ni inactividad y añadiría gestión de tokens/estado de revocación. La selección sustituye aquella intención, evita Redis y proxy innecesarios, y exige gestionar persistencia y ciclo de vida de sesiones correctamente. No se afirma HTTPS en la demostración HTTP local.

**Detalles y verificación posteriores.** Concretar algoritmo/parámetros de hash, secretos, intercambio CSRF y criterio servidor de actividad/expiración; implementar los controles ya seleccionados. Probar autenticación, cookies/CORS/CSRF, acceso cruzado por UUID, revocaciones y ambas expiraciones, incluidos clientes con polling continuo que no perpetúen la sesión.

### ARQ-006 — Consistencia y actualización del inventario entre sucursales

**Estado:** polling REST con TanStack Query e intervalo inicial de dos segundos APROBADOS por A PEN-003; máximo de cinco segundos pendiente de comprobación real.  
**Clasificación:** **JUSTIFICACIÓN OBLIGATORIA §8.2 — sincronización de inventario**.  
**Trazabilidad:** A PEN-003, relacionada con PEN-001/PEN-002/PEN-006/PEN-008; D INC-004/D01–D04; RF-0015 y RF §5.3; RNF-REN-01.

**Decisión base.** La autonomía de las sucursales significa operar con permisos locales sobre la plataforma común. No implica bases autónomas por sucursal, operación offline ni reconciliación diferida de escrituras. El backend confirma el estado persistido; una pantalla desactualizada no autoriza una salida de stock.

**Fundamento.** Evitar copias de escritura independientes reduce la necesidad de resolver conflictos distribuidos y mantiene un punto de confirmación coherente con el stack aprobado. Deben distinguirse dos problemas: la consistencia al confirmar operaciones, tratada en ARQ-007, y la propagación visual del dato confirmado a otras pantallas.

**Decisión aprobada.** Actualizar mediante polling REST con TanStack Query las consultas de inventario que requieren frescura, con intervalo inicial de **dos segundos**. No aplicarlo indiscriminadamente a catálogos o todos los indicadores. Tras mutaciones propias confirmadas, invalidar/refrescar las consultas afectadas; reconsultar al recuperar conexión o foco y al volver a estar visible la pantalla.

Aplicar cookies, CORS, CSRF y autorización de ARQ-005 sin prolongar la inactividad con tráfico automático. Informar datos potencialmente obsoletos por desconexión; no simular confirmaciones locales y revalidar cada escritura sobre el backend autoritativo.

**Alternativas y consecuencias.** Reutilizar REST/PostgreSQL separa consistencia y frescura de interfaz sin introducir conexiones persistentes. Eventos podrían reducir consultas innecesarias, a costa de conexión, reconexión y recuperación de cambios. No se incorporan inicialmente SSE, WebSockets, Redis ni broker. Polling consume solicitudes y está condicionado por temporizadores del navegador en pestañas ocultas/suspendidas; documentar esas limitaciones sin prometer cumplimiento universal en sesiones inactivas ni reinterpretar el RNF.

**Verificación posterior.** Definir carga/entorno y medir el máximo de cinco segundos desde confirmación **persistida** hasta visualización efectiva en otra sesión conectada: espera hasta siguiente polling, red, procesamiento y renderizado. Dos segundos es un parámetro inicial, **no garantía** del límite. Probar errores/reconexión, foco/visibilidad, permisos cruzados, consumo de consultas y expiración bajo polling. Escala y cumplimiento real quedan pendientes de evidencia.

### ARQ-007 — Confirmaciones atómicas, concurrencia y duplicados

**Estado:** atomicidad, aislamiento, bloqueo pesimista e idempotencia APROBADOS por A PEN-002; implementación y pruebas posteriores.  
**Clasificación:** decisión significativa relacionada con ARQ-003 y ARQ-006; requiere justificación propia por su impacto en integridad.  
**Trazabilidad:** A PEN-002, relacionada con PEN-004/PEN-005/PEN-008; D INC-008, INC-011/D04, INC-014/D03–D04, INC-015/D08; RF §§2.1 y 5.3.

**Base que no se reabre.** No hay reservas ni stock negativo. Confirmar una operación exige validar existencias y registrar conjuntamente documentos, movimientos, saldos y valor afectados. Una petición repetida no duplica efectos. El despacho reduce origen y crea tránsito; la única llegada registra lo recibido y conserva el faltante, sin convertirlo automáticamente en pérdida. Una llegada de cero unidades no genera movimiento físico de cantidad cero.

**Decisión aprobada.** Delimitar confirmaciones con `@Transactional` y PostgreSQL, todo o nada para documentos, movimientos, saldos y valoración, con rollback coherente con las excepciones del servicio. Mantener aislamiento **`READ COMMITTED`** y bloqueo pesimista `SELECT ... FOR UPDATE` / `PESSIMISTIC_WRITE` sobre filas pertinentes de existencias y estados documentales cuando corresponda. Proteger el documento frente a doble confirmación: bloquear solo el saldo no basta.

Adquirir varias filas en orden determinista por sucursal/producto cuando aplique, con transacciones acotadas. Tratar conflictos y deadlocks sin efectos parciales. Complementar validación con PK, FK, `UNIQUE`, `CHECK` y `NOT NULL` pertinentes y resolver la carrera de creación cuando aún no existe saldo producto-sucursal.

Implementar idempotencia de solicitudes críticas de creación/confirmación repetibles ante fallos de red o reintentos: **`Idempotency-Key`** y registro persistido de ámbito, operación, huella de solicitud y resultado, coordinado con la transacción. Misma clave y contenido recupera el resultado sin repetir efectos; misma clave con contenido distinto produce conflicto. No ejecutar reintentos automáticos indiscriminados: solo considerar una transacción completa protegida por idempotencia ante error transitorio y mecanismo justificado.

**Fundamento, alternativas y consecuencias.** Comprobar stock y guardar sin protección permite consumir las mismas unidades concurrentemente. El mecanismo elegido utiliza recursos nativos Spring/PostgreSQL sin mensajería ni bases distribuidas. Frente al control optimista, el bloqueo pesimista serializa el acceso a filas en disputa, a costa de esperas y riesgo de interbloqueo; orden y duración acotada reducen ese riesgo, sin eliminar la necesidad de tratar errores. Rollback de una confirmación fallida no equivale a borrar historia: las compensaciones posteriores autorizadas conservan su trazabilidad según RF.

**Verificación posterior.** Dos ventas simultáneas de dos unidades sobre saldo tres: solo una confirmada y ninguna huella parcial de la rechazada. Probar repetición de compras/ventas/despachos/llegadas, doble llegada, huellas distintas bajo una clave, rollback intermedio, bloqueos múltiples, carreras de saldo inexistente, ausencia de stock negativo, compensaciones e historia conforme a RF.

### ARQ-008 — Capas internas, módulos y patrones del backend

**Estado:** nueve módulos por funcionalidad, capas Java tradicionales y patrones APROBADOS por A PEN-004.  
**Clasificación:** **JUSTIFICACIÓN OBLIGATORIA §8.2 — patrones utilizados**.  
**Trazabilidad:** A PEN-004, relacionada con PEN-002/PEN-005; M §8.2; P §1; D §2; ARQ-002–ARQ-003.

**Decisión aprobada.** Una aplicación Java/Spring Boot, organizada primero por funcionalidad (**package-by-feature**), con nueve módulos: `identity`, `catalog`, `inventory`, `purchases`, `sales`, `transfers`, `logistics`, `dashboard` y `reports`. En cada módulo, subpaquetes Java/Spring convencionales solo cuando hagan falta; no crear carpetas vacías ni imponer `web/application/domain/persistence` o arquitectura hexagonal estricta.

| Subpaquete aprobado por responsabilidad | Función y límite |
|---|---|
| `controller/` | Recibe HTTP y representa resultados; sin lógica de stock/costos. |
| `dto/`, con `request/` y `response/` cuando corresponda | Transporta contratos explícitos; no expone entidades JPA. |
| `entity/` | Entidades JPA de persistencia. |
| `service/` | Reglas, coordinación de casos de uso, autorización contextual y transacciones. |
| `repository/` | Acceso Spring Data JPA a PostgreSQL; no concede permisos por devolver una entidad. |
| `mapper/` | Transforma entidades y contratos. |
| `enums/`, `exception/`, `api/` | Complementos solo necesarios; `api/` para contratos internos explícitos. |

La referencia estructural es `backend/src/main/java/<paquete-base>/<modulo>/<subpaquete>/`; el paquete base concreto sigue por definir. `common` o equivalente contiene utilidades técnicas estrictamente compartidas, nunca reglas de negocio sin propietario.

**Propiedad y coordinación aprobadas.** `transfers` y `logistics` permanecen separados: el primero gobierna solicitud, autorización, estado global y coordinación del proceso; el segundo despacho, confirmación única de llegada, discrepancias y alertas según RF. `inventory` es el **único propietario** de operaciones que modifican existencias, movimientos, valoración y control de tránsito; compras/ventas/transferencias reutilizan esa capacidad, no la reimplementan.

`transfers` puede invocar contratos internos de `logistics` e `inventory` dentro de la **misma transacción** para confirmaciones indivisibles. `logistics` no accede a clases internas de `transfers` ni cambia por su cuenta el estado global. Comunicación por servicios/contratos explícitos, sin acceso directo a repositorios internos ajenos ni ciclos. Compartir esquema SQL no elimina propiedad lógica. `dashboard` y `reports` consumen consultas y no alteran directamente existencias. Esta propiedad técnica no amplía permisos de actores de RF.

**Patrones y fundamentos aprobados.** Repository concentra acceso a datos y lo separa de controladores/coordinación; no garantiza independencia absoluta del motor. Service Layer concentra reglas, orquestación y límites transaccionales. DTO desacopla representación REST de JPA y evita exponer claves/campos internos. La inyección de dependencias explicita colaboraciones y permite sustituir dependencias en pruebas. Separar por módulos favorece cohesión funcional y límites revisables conservando familiaridad de capas Java y transacciones comunes. Exige mapeos, disciplina y revisión de dependencias; no garantiza por sí sola aislamiento.

**Alternativas y límites.** No crear por defecto parejas `Service`/`ServiceImpl` o interfaces redundantes. No introducir CQRS completo, Factory innecesario, bus de eventos, microservicios, hexagonal estricta ni Spring Modulith automáticamente. M §8.2 exige justificar patrones usados, no adoptar todos sus ejemplos. MapStruct fue recomendado, **no ratificado como dependencia obligatoria**; ArchUnit es una ayuda opcional de pruebas, no una imposición.

**Verificación posterior.** Pruebas de reglas modulares, revisión de grafo sin ciclos, contratos internos, propiedad de repositorios y coordinación atómica de transferencias/logística/inventario. Las entidades concretas, incluido propietario definitivo de sucursales, se derivarán con el modelo, no se inventan en este cierre.

### ARQ-009 — Identidad pública, precisión y calendario

**Estado:** `DEC-TEC-ID-001`, representación decimal y temporal APROBADAS; precisión SQL por campo y entidades completas son detalles posteriores.  
**Clasificación:** decisiones transversales significativas de datos y contrato; complementan ARQ-003.  
**Trazabilidad:** A PEN-005, relacionada con PEN-007/PEN-008; D §9; RF §§2.1, 5.3 y 5.4; RNF-PRE-01.

**Identidad.** Se mantiene íntegramente `DEC-TEC-ID-001`, registrada en D §9: PK `BIGINT`, FK internas a esas claves y `external_id` UUIDv4 único, no nulo e inmutable en recursos expuestos individualmente; no exigirlo por reflejo en toda tabla de apoyo/detalle sin identidad API independiente. API identifica mediante **`externalId`**, sin exponer ni aceptar claves internas como identificación pública; SKU y otros códigos de negocio son independientes. Generación preferentemente desde backend, con mecanismo SQL de respaldo solo cuando esté justificado; documentar la convención concreta al definir entidades/migraciones. Desacoplar referencias públicas de claves relacionales evita exponer secuencias internas, pero añade columna, índice y mapeo, y no sustituye autorización por recurso/sucursal. No se renumera ni crea una decisión de identidad competidora.

**Precisión aprobada.** `BigDecimal` en Java y `NUMERIC(p,s)` en PostgreSQL, no `float`/`double` para cantidades, costos o valores exactos de negocio. El fundamento es preservar reglas aprobadas sin errores de fracciones decimales en representación binaria. Mantener hasta tres decimales para cantidades divisibles, promedio a seis e importes mostrados a dos, con mitad hacia arriba, residuales y compensaciones según RF. Precisión de cálculo/almacenamiento y presentación no son necesariamente iguales.

Tamaños **tentativos por validar**, no límites funcionales ratificados: `NUMERIC(18,3)` para cantidades, `NUMERIC(24,6)` para costos promedio y `NUMERIC(24,2)` para importes finales **solo si realmente son resultados cuantizados a dos decimales**. Validar extremos de cálculo contra RF y conservar mayor precisión interna si lo exige; no adoptar las propuestas como DDL definitivo.

**Historia y calendario aprobados.** Java `Instant` y PostgreSQL `TIMESTAMPTZ` para instantes inequívocos manejados en UTC; `LocalDate` / `DATE` para fechas de negocio sin hora cuando corresponda. `America/Bogota` rige interpretación comercial, filtros, cierres e indicadores y COP es moneda de demostración. Fechas inclusivas se traducen, cuando aplique, a intervalos de instantes con inicio inclusivo y fin exclusivo al comenzar el día siguiente. Tipos/representaciones por DTO se concretarán sin pérdida decimal, según ARQ-002.

Conservar catálogo y saldo separados, movimientos e instantáneas históricas requeridas. La única llegada incluye cero unidades; «Recibida con discrepancia» es historia, no una autorización de recepción complementaria. Resolver alerta no cambia stock/valor/estado. Respetar residuales y compensaciones ya resueltos en RF, con operaciones posteriores trazables, sin reescribir movimientos pasados ni inventar políticas nuevas.

**Detalles y verificación posteriores.** Enumerar recursos y referencias concretos, generación UUID y restricciones con el modelo; `externalId` ya es la convención DTO aprobada. Validar extremos de cantidades/costos, valoración/redondeo/compensaciones, conversiones, instantes y filtros por calendario. Probar que no se filtran ni aceptan claves internas como identificación pública y que manipular UUID no elude autorización ni separación por sucursal. No se ha producido el E-R ni DDL concreto.

### ARQ-010 — Consultas, dashboards y reportes exportables

**Estado:** consultas autorizadas y exportación Apache POI/`XSSFWorkbook` APROBADAS por A PEN-008; contratos/consultas exhaustivos e implementación posteriores.  
**Clasificación:** decisión significativa; complementa responsabilidades de negocio y presentación de §8.1.  
**Trazabilidad:** A PEN-008, relacionada con PEN-001/PEN-002/PEN-005/PEN-006; RF-0040–RF-0055 y RF §2.1; D INC-020; RNF-CAP-01; P §1.

**Decisión aprobada.** El backend calcula indicadores, aplica visibilidad y genera XLSX auténtico de movimientos, ventas y transferencias bajo demanda con **Apache POI**, inicialmente **`XSSFWorkbook`**. Considerar `SXSSFWorkbook` solo si pruebas reales de memoria justifican el cambio dentro de la misma biblioteca, no como nueva infraestructura. React presenta gráficos con Recharts ratificado y descarga el archivo; no calcula reglas de negocio. `reports` consume capacidades de consulta de módulos operativos y no muta saldos.

**Fundamento.** Centralizar cálculos y autorización evita diferencias entre pantalla y exportación y evita confiar en filtros manipulables del navegador. El XLSX aporta un resultado utilizable fuera del dashboard, con filtros, columnas y permisos ya aprobados; no se reemplaza por CSV renombrado ni se añade PDF por inferencia.

**Reglas conservadas.** Descarga directa, hasta 10.000 filas de negocio y 12 meses; filtros, columnas y permisos de RF. Fechas obligatorias inclusivas en `America/Bogota`: registro para movimientos, confirmación para ventas y solicitud para transferencias. Administrador exporta una/todas las sucursales, gerente solo propia y operador no exporta. Movimiento estándar omite costos; ventas tienen una fila por línea, sin total de cabecera repetido como importe sumable; transferencias una fila por línea, sin duplicarla por recepciones. Respetar todas las columnas de RF-0050–RF-0055, no sustituirlas por esta síntesis.

Rechazar exceso de filas/período con error claro sin truncar ni modificar operaciones; sin datos, XLSX válido con encabezados y nota explícita. Evitar multiplicación de filas por `JOIN` con varios detalles; detectar excesos de forma acotada, por ejemplo límite 10.001 cuando proceda, sin afirmar que eso garantiza coherencia concurrente. Las consultas deben producir resultados coherentes frente a modificaciones simultáneas. Conservar precisión de cantidades/moneda, valoración aprobada y «No calculable» ante historia insuficiente, sin fabricar datos.

**Contrato de descarga y seguridad.** MIME `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition: attachment` y nombre apropiado. React recibe binario `Blob` mediante el cliente con credenciales de sesión y protecciones aplicables; no confundir descarga binaria con DTO JSON. Proteger frente a contenido de usuario interpretado como fórmulas Excel, con celdas/tipos adecuados y sin degradar valores de negocio más allá de la precisión representable en Excel; comprobar exactitud y representación.

**Alternativas y consecuencias.** Apache POI genera XLSX real sin servicio externo. `XSSFWorkbook` mantiene el libro en memoria y requiere medir consumo; la alternativa de streaming se evaluará solo con evidencia. CSV renombrado no satisface XLSX. Sin generación asíncrona, colas, repositorio de archivos ni PDF adicional por inferencia; se conserva el valor incremental del adicional frente al dashboard.

**Verificación posterior.** Contratos OpenAPI/DTO/errores y acceso autenticado según ARQ-002/ARQ-005; XLSX verificable, columnas, tipos, precisión, contenido de usuario, fechas y límites; vacío válido y exceso rechazado sin efectos. Comprobar coherencia de consultas y memoria con volumen representativo sin prometer tiempos no medidos.

## 5. Matriz de decisiones ratificadas y trabajos posteriores

### 5.1. Selección y justificación cerradas

Las ocho PEN fueron ratificadas el **21/10/2026** en A; conservan sus identificadores históricos y dejan de ser pendientes de selección. Todas están incorporadas en el registro ARQ sin crear una segunda arquitectura.

| ID | Estado | Decisión ratificada | Trazabilidad de integración |
|---|---|---|---|
| PEN-001 | APROBADA | Spring Security + Spring Session JDBC/PostgreSQL, cookies, CSRF y CORS restrictivo; sin JWT principal ni proxy. | A §3 PEN-001 → ARQ-005 |
| PEN-002 | APROBADA | `@Transactional`, `READ COMMITTED`, bloqueo pesimista ordenado, idempotencia persistida e integridad SQL. | A §3 PEN-002 → ARQ-007 |
| PEN-003 | APROBADA | Polling REST/TanStack Query, dos segundos iniciales; medir cinco segundos y no perpetuar sesiones. | A §3 PEN-003 → ARQ-006 |
| PEN-004 | APROBADA | Nueve módulos por funcionalidad, capas Java convencionales propias; `transfers`/`logistics` separados e `inventory` propietario del stock. | A §3 PEN-004 → ARQ-008 y ARQ-002 |
| PEN-005 | APROBADA | Base relacional común, identidad dual, decimales exactos, historia, UTC y calendario `America/Bogota`. | A §3 PEN-005 → ARQ-003 y ARQ-009 |
| PEN-006 | APROBADA | React/TypeScript/Vite, Tailwind/shadcn/ui, React Router y herramientas de datos, formularios, tablas y gráficos. | A §3 PEN-006 → ARQ-001 |
| PEN-007 | APROBADA | Tres servicios locales, Vite, volumen Docker `pgdata`, Flyway/SQL/semillas, Hibernate `validate` y healthchecks; sin proxy. | A §3 PEN-007 → ARQ-004 |
| PEN-008 | APROBADA | REST `/api/v1`, DTO/ProblemDetail/OpenAPI, Apache POI/`XSSFWorkbook` y descarga binaria XLSX. | A §3 PEN-008 → ARQ-002 y ARQ-010 |

### 5.2. Compatibilidades transversales aprobadas

- **PEN-001 ↔ PEN-003:** polling automático cada dos segundos no debe prolongar la inactividad de treinta minutos; controlar actividad interactiva y expiraciones en servidor.
- **PEN-001 ↔ PEN-006 ↔ PEN-007 ↔ PEN-008:** navegador/Vite y backend en puertos distintos, hostname coherente, HTTP directo, `fetch` con credenciales, cookie `HttpOnly`, CORS restrictivo y token CSRF separado; sin JWT principal, sin credenciales/identificadores de sesión en `localStorage` y sin proxy inverso.
- **PEN-002 ↔ PEN-004 ↔ PEN-005:** servicios por contratos coordinan confirmaciones en una transacción; `inventory` modifica stock/valor/tránsito, `transfers` coordina `logistics`, y bloqueos/idempotencia/restricciones respaldan integridad.
- **PEN-005 ↔ PEN-007:** Flyway versiona SQL y tablas Spring Session; Hibernate valida sin alterar autónomamente el esquema. La identidad dual se aplica a recursos de negocio según su exposición, no por reflejo a toda tabla técnica.
- **PEN-005 ↔ PEN-008:** UUID públicos, decimales exactos y calendario comercial deben conservarse en REST, indicadores y Excel.
- **PEN-003 ↔ PEN-008:** la interfaz refrescada no es fuente de verdad; backend revalida escrituras, y reportes son consultas/snapshots autorizados, no cálculos del navegador.

### 5.3. Detalles y evidencias de etapas posteriores

| Trabajo posterior | Alcance que no se presume completado | Referencias |
|---|---|---|
| Modelo de datos y E-R | Entidades, cardinalidades, propietario exacto de sucursales y otras entidades, precisión/tamaño por campo, índices según consultas y DDL. Los `NUMERIC` tentativos no son límites de RF. | ARQ-003 y ARQ-009; A §6.2 |
| Contratos exhaustivos | Listado real de endpoints, DTO/filtros, decimales y códigos por operación, parámetros de idempotencia y generación OpenAPI; las convenciones generales ya están aprobadas. | ARQ-002 y ARQ-010 |
| Compatibilidad técnica ejecutable | Resolver y comprobar el conjunto de versiones de §5.4; verificar transitivas, compilación, integración, imágenes/digests, ruta de datos y puertos finales configurables. La selección documental no equivale a estas pruebas. | §5.4; ARQ-001–ARQ-005 y ARQ-010 |
| Seguridad implementada | Ocho horas absolutas, treinta minutos de inactividad real sin extensión por polling, revocaciones, CSRF/CORS/cookies, hash/parámetros y protección antiabuso. | ARQ-005 y ARQ-006 |
| Consistencia implementada | Orden de bloqueos, rollback, carreras de saldos inexistentes, registro idempotente, conflicto y restricciones efectivas. | ARQ-007 y ARQ-008 |
| Infraestructura escrita y ejecutada | SQL de migraciones/semillas, configuración Compose operativa, administrador inicial seguro, salud y esquema/sesiones validados. | ARQ-004 |
| Pruebas y mediciones | Funcionales, seguridad, concurrencia, latencia extremo a extremo ≤5 s, arranque limpio, persistencia, migraciones, coherencia, memoria y exactitud XLSX. | Comprobaciones previstas en cada ARQ |
| Diagramas de §7 | Arquitectura, E-R y restantes diagramas obligatorios; no generados ni sustituidos por este registro. | M §7 |

No se convierte MapStruct ni ArchUnit en dependencia obligatoria, ni se aprueba propietario definitivo de sucursales, identidad gráfica, algoritmo concreto de hash o tamaños SQL tentativos por inferencia. Las versiones se concretan expresamente en §5.4; estos detalles no reabren las ocho selecciones ratificadas.

### 5.4. Versiones y compatibilidad tecnológica

**Corte de investigación: 22/10/2026.** Se adopta la siguiente base documental de versiones estables publicadas, no una promesa de utilizar siempre la última versión disponible. Se contrastaron registros oficiales npm, POM de Maven Central, documentación de fabricantes y avisos de seguridad. Todas las versiones exactas propuestas se localizaron; se corrige **Spring Security 7.0.x por 7.1.1**, conforme al BOM de Spring Boot 4.1.1, y se concretan los parches de Java/Node y el CLI de shadcn. No se adoptan versiones preliminares ni tecnologías diferentes.

La evaluación acredita disponibilidad y compatibilidad declarada; **no acredita resolución efectiva de dependencias, compilación, ejecución ni ausencia de vulnerabilidades**. La combinación springdoc/Jackson requiere especialmente la comprobación de integración descrita más abajo. Las referencias a versiones por verificar en las ARQ se concretan documentalmente aquí; sus pruebas posteriores siguen pendientes.

#### 5.4.1. Backend, persistencia y herramientas de construcción

| Tecnología o artefacto | Versión seleccionada | Gestión y consideración de compatibilidad |
|---|---|---|
| Java — Eclipse Temurin | **25.0.5+8, Java 25 LTS** | Concreta Java 25 LTS. Boot 4.1.1 admite Java 17–26; no requiere bajar a Java 21. Verificar disponibilidad de imagen y arquitectura antes de fijar su digest. |
| Spring Boot | **4.1.1** | Base estable y autoridad de versiones del ecosistema Spring mediante `spring-boot-dependencies`. No combinar arbitrariamente componentes de otras líneas. |
| Apache Maven | **3.9.16** | Supera el mínimo 3.6.3 declarado por Boot; fijar la distribución de construcción, sin depender del Maven instalado en cada equipo. |
| Spring Framework | **7.0.9**, gestionada | Referencia observada en el BOM de Boot; no fijarla por separado. |
| Spring Security | **7.1.1**, gestionada | Sustituye 7.0.x: alineación con Boot y correcciones de seguridad de esta entrega. No mantener una excepción manual a 7.0 por la propuesta inicial. |
| Spring Session JDBC | **4.1.1**, gestionada | Misma línea que Boot; sesiones en PostgreSQL conforme a ARQ-005. No confundir persistencia JDBC con serialización JSON. |
| PostgreSQL | **17.11** | Parche estable de la rama 17; conserva el modelo relacional, `NUMERIC`, `TIMESTAMPTZ`, transacciones y bloqueos aprobados. El servidor y el driver tienen numeraciones independientes. |
| Driver PostgreSQL JDBC | **42.7.13**, gestionada | Versión administrada por Boot; comprobar conexión y tipos con el servidor 17.11. |
| Spring Data JPA | **4.1.1**, gestionada | Proviene de Spring Data BOM **2026.0.1**, importado por Boot. No declarar una versión JPA independiente. |
| Hibernate ORM | **7.4.5.Final**, gestionada | Conservar integración Jakarta y validación del esquema. No copiar dependencias antiguas `javax.persistence` de ejemplos previos. |
| Flyway | **12.4.0**, gestionada | Alinear `flyway-core` y `flyway-database-postgresql`; este último es el módulo del motor seleccionado, no una nueva tecnología ni servicio. El driver JDBC sigue siendo necesario. |
| springdoc-openapi | **3.1.1** | Línea 3 para Boot 4; el POM de springdoc referencia Boot 4.1.0. Utilizar su integración WebMVC/Swagger UI para la API Servlet, no la línea 2 de Boot 3 ni WebFlux por inferencia. Ver cautela Jackson en §5.4.3. |
| Apache POI | **5.5.1**, `poi-ooxml` | Permite el `XSSFWorkbook` aprobado y XLSX auténtico. Revisar transitivas efectivas; no agregar una versión diferente de `poi` ni módulos completos innecesarios. |
| Jackson principal REST | **3.1.5**, gestionada | BOM `tools.jackson:jackson-bom`; Boot 4 prefiere Jackson 3. No imponer un `ObjectMapper` de Jackson 2 a la configuración REST de Jackson 3. |
| Jackson 2 coexistente | **2.21.5**, gestionada | BOM `com.fasterxml.jackson:jackson-bom`; puede seguir presente por Swagger. La coexistencia no significa que ambas generaciones compartan configuración ni que deban excluirse mutuamente. |

Los números gestionados son una **instantánea informativa del BOM 4.1.1**, no instrucciones de sobrescritura. Una actualización de Boot exige regenerar esta comprobación como conjunto. No se cambia de rama de PostgreSQL ni de arquitectura para obtener parches.

#### 5.4.2. Frontend y herramientas de construcción

| Tecnología o paquete | Versión seleccionada | Compatibilidad y uso aprobado |
|---|---|---|
| Node.js | **24.22.1 LTS** | Concreta Node 24 LTS; entrega de seguridad. Satisface los requisitos Node de pnpm, Vite y su plugin React. |
| pnpm | **11.24.0** | Requiere Node ≥22.13; Node 24 seleccionado cumple. Fijar gestor y lockfile, no depender de una instalación global variable. |
| `react` / `react-dom` | **19.2.8 / 19.2.8** | Mantener ambos en la misma versión; `react-dom` declara React `^19.2.8`. SPA cliente, sin incorporar React Server Components. |
| `typescript` | **6.0.3** | Estable; la compatibilidad declarada no sustituye comprobar tipos de la aplicación y bibliotecas. Conservar comprobación estricta para Zod. |
| `vite` | **8.3.4** | Requiere Node `^20.19.0` o `≥22.12.0`; Node 24 cumple. Servidor limitado a desarrollo/demostración local según ARQ-004. |
| `@vitejs/plugin-react` | **6.1.0** | Declara Vite `^8`; coincide con 8.3.4. Sus integraciones opcionales no autorizan incorporar compiladores adicionales. |
| `tailwindcss` | **4.3.3** | Compatible con la selección shadcn/Tailwind 4. Si se utiliza su adaptador oficial `@tailwindcss/vite`, alinearlo a **4.3.3**, cuyo rango incluye Vite 8; no es otro sistema de estilos. |
| `react-router` | **7.18.4** | Node ≥20 y React/DOM ≥18; cumple el stack. Mantener el modo declarativo preferido en ARQ-001, sin introducir un servidor de framework. |
| `@tanstack/react-query` | **5.102.6** | Declara React 18/19; consultas, invalidación y polling, no autorización ni cálculo de stock. |
| `@tanstack/react-table` | **8.21.3** | Su rango React/DOM ≥16.8 incluye React 19. Su antigüedad respecto a otros paquetes no es por sí sola una incompatibilidad ni motivo para cambiar de tecnología. |
| `react-hook-form` | **7.86.0** | Incluye React 19 y requiere Node ≥18. Validación de interacción, sin reemplazar validación del backend. |
| `@hookform/resolvers` | **5.9.1** | Declara React Hook Form `^7.55.0` y compatibilidad Zod 3.25/4; las versiones seleccionadas cumplen. No instalar validadores alternativos opcionales. |
| `zod` | **4.4.3** | Documentación exige TypeScript ≥5.5 con `strict`; TS 6.0.3 cumple el mínimo. Comprobar inferencias y tipos reales en implementación. |
| `recharts` | **3.10.1** | Incluye React/DOM y `react-is` 19 en sus rangos. Verificar que `react-is` se resuelva coherentemente con React 19; no forzar una versión antigua mediante overrides. |
| `lucide-react` | **1.34.0** | Su rango de compatibilidad incluye React 19. Mantener importaciones de iconos concretos y comprobar los usados. |
| shadcn/ui | **CLI `shadcn` 4.22.1**, componentes versionados | No existe una única versión runtime de todos los componentes: el CLI incorpora código del registro. Fijar el CLI no congela el registro remoto; registrar los componentes incorporados y versionar su código y dependencias. |

Los rangos de `engines` y `peerDependencies` consultados no muestran un conflicto entre estas versiones. Esto no garantiza compatibilidad de todos los componentes shadcn que se incorporen después ni de sus dependencias concretas. Los paquetes de tipos requeridos deberán resolverse para React 19/TypeScript 6 sin inventar aquí parches no contrastados. Tailwind 4 requiere navegadores modernos; verificar la matriz real de navegadores de la demostración, sin atribuir soporte universal.

#### 5.4.3. Compatibilidad transversal y puntos de atención

| Decisiones preservadas | Evaluación y comprobación pendiente |
|---|---|
| ARQ-001 — SPA y herramientas visuales | Los rangos publicados permiten React 19, Vite 8, Tailwind 4 y las bibliotecas seleccionadas. Comprobar tipos, componentes, formularios Zod, navegación, tablas, responsividad y gráficos mediante construcción y navegador reales. |
| ARQ-002 — REST, DTO, errores y OpenAPI | **Jackson 3/2 es el principal riesgo de integración.** springdoc 3.1.1 incorpora `swagger-core-jakarta:2.2.55`, dependiente de Jackson 2; el padre Swagger declara Jackson **2.22.1**, mientras Boot administra **2.21.5**. Es una diferencia comprobada de gestión, no una incompatibilidad reproducida. Inspeccionar el árbol efectivo y probar arranque, `/v3/api-docs`, Swagger UI, DTO, UUID, fechas, decimales y `ProblemDetail`. No excluir Jackson 2 globalmente ni actualizar sus módulos aisladamente para ocultar el desacuerdo. |
| ARQ-003 y ARQ-004 — Persistencia y arranque | Flyway necesita el módulo PostgreSQL alineado con core. Versionar por Flyway las tablas de negocio y de Spring Session, tomando el esquema JDBC correspondiente a la versión seleccionada; desactivar la inicialización paralela de tablas de sesión. Mantener Hibernate `validate`. Verificar migración limpia, reinicios, imagen PostgreSQL, ruta `PGDATA` y persistencia de `pgdata`. |
| ARQ-005 — Sesiones y seguridad | Spring Session JDBC serializa por defecto atributos mediante **serialización JDK a bytes**, no mediante Jackson. Los atributos personalizados deben ser serializables; cambios de clases pueden invalidar sesiones persistidas. No introducir JSON ni guardar entidades JPA completas por inferencia. Probar autenticación, revocación, CSRF/CORS y expiración absoluta/inactiva; el soporte de biblioteca no implementa por sí solo estas reglas. |
| ARQ-006 — Polling | Query 5 admite el mecanismo seleccionado; el tráfico periódico no debe mantener viva la sesión. Mantener dos segundos iniciales y medir el límite de cinco segundos, sin prometerlo por versión. |
| ARQ-007 y ARQ-008 — Transacciones y módulos | Spring/JPA/Hibernate/PostgreSQL permiten transacciones y bloqueo pesimista aprobados. Sus versiones no garantizan orden de bloqueos, rollback, idempotencia, ausencia de ciclos ni propiedad de inventario: requieren diseño y pruebas de concurrencia. |
| ARQ-009 — Precisión e identidad | Mantener `BigDecimal`/`NUMERIC`, UUID, `Instant`/`TIMESTAMPTZ`, fechas de negocio y `America/Bogota`. Verificar serialización con Jackson 3 y documentación con springdoc sin convertir importes exactos a `number` por conveniencia. |
| ARQ-010 — Excel y consultas | POI 5.5.1 cubre generación XLSX; comprobar tipos de celda, precisión, contenido interpretado como fórmula, columnas/permisos, límites y memoria de `XSSFWorkbook`. Revisar XMLBeans, Commons Compress y demás transitivas resueltas; no suponer que Boot administra todo el árbol POI. |

**Seguridad y vigencia.** La revisión fue acotada, no un escaneo completo del software futuro. Spring Security 7.1.1 y Spring Data JPA 4.1.1 incluyen correcciones publicadas el 20/08/2026; el aviso de JPA **CVE-2026-47834** refuerza la necesidad de no aceptar ordenamiento nativo no confiable. Vite 8.3.4 queda fuera del rango 8.3.0–8.3.2 afectado por **GHSA-rq7h-c2jc-7f22**, corregido en 8.3.3; React Router 7.18.4 es posterior a la corrección 7.18.2 de **GHSA-qwww-vcr4-c8h2**. Esto no elimina la necesidad de restringir los servicios al entorno local aprobado.

El aviso React **GHSA-xqc2-xf4c-x3h3** del 14/10/2026 afecta paquetes `react-server-dom-*`, incluida su versión 19.2.8: no debe atribuirse directamente a `react`/`react-dom` de esta SPA sin RSC, ni ignorarse si una dependencia futura introduce esos paquetes. Node 24.22.1 es una entrega de seguridad y PostgreSQL 17.11 incorpora correcciones de su rama. No se declara el conjunto «libre de vulnerabilidades»; antes de implementar o actualizar, revisar avisos y dependencias efectivas, especialmente Swagger/Jackson y las transitivas de POI.

#### 5.4.4. Convenciones de gestión de dependencias

1. **Una autoridad para Spring.** Utilizar el parent/BOM de Boot 4.1.1 para sus componentes administrados: Security, Session, Spring Data, Hibernate, Flyway, JDBC y Jackson. No repetir sus versiones en dependencias directas ni importar BOM competidores. Fijar explícitamente springdoc 3.1.1 y POI 5.5.1, que aquí se seleccionan fuera de esa autoridad. Cualquier excepción necesita causa, árbol efectivo y comprobación de integración; un override global no es una solución de compatibilidad demostrada.
2. **Resolución reproducible del frontend.** Fijar versiones directas y gestor en el manifiesto, conservar `pnpm-lock.yaml` y usar instalación con lockfile congelado. Revisar peers incompatibles, opcionales realmente utilizados y duplicados; no silenciar avisos mediante overrides indiscriminados. Mantener React/DOM iguales y sus tipos coherentes. No usar `latest` como especificación de construcción.
3. **Entorno e imágenes reproducibles.** Fijar JDK, Node, Maven e imágenes de los tres servicios con versión concreta y, una vez comprobados, digests y plataformas compatibles. No se han verificado etiquetas/digests Docker ni una versión exacta de Docker Engine/Compose en esta evaluación; esas comprobaciones siguen en §5.3. No introducir un cuarto servicio ni cambiar Vite local por un proxy.
4. **Actualizar como conjunto comprobado.** Revisar primero avisos oficiales, BOM, requisitos de motor y peers; después resolver árboles, compilar y probar. Conservar evidencia del árbol Maven, lockfile y versiones utilizadas. Los parches no se aplican automáticamente por fecha ni modifican reglas funcionales. No se añaden herramientas de análisis como dependencias obligatorias por esta recomendación.
5. **Pruebas de aceptación posteriores.** Verificar API/OpenAPI y serialización; sesiones y seguridad con polling; migraciones/validación y persistencia; concurrencia/idempotencia; tipos y construcción frontend; descarga XLSX y precisión. Ninguna se ejecutó para esta actualización exclusivamente documental.

#### 5.4.5. Fuentes y alcance de la investigación

Consultadas el **22/10/2026**. Los POM y metadatos de registro se priorizaron frente a ejemplos históricos o respuestas de búsqueda; la existencia de un artefacto no demuestra su integración en OptiPlant.

| Fuente primaria | Evidencia utilizada |
|---|---|
| [BOM Boot 4.1.1](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom), [requisitos Boot 4.1](https://docs.spring.io/spring-boot/4.1/system-requirements.html) y [BOM Spring Data 2026.0.1](https://repo.maven.apache.org/maven2/org/springframework/data/spring-data-bom/2026.0.1/spring-data-bom-2026.0.1.pom) | Versiones administradas y requisitos Java/Maven. El BOM con versión fija es la referencia de esta instantánea. |
| [Temurin 25.0.5+8](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.5%2B8), [Maven 3.9.16](https://maven.apache.org/docs/3.9.16/release-notes.html), [PostgreSQL 17.11](https://www.postgresql.org/docs/release/17.11/) | Disponibilidad y notas de las versiones seleccionadas. |
| [springdoc 3.1.1](https://repo.maven.apache.org/maven2/org/springdoc/springdoc-openapi/3.1.1/springdoc-openapi-3.1.1.pom), [compatibilidad springdoc](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot), [Swagger Core Jakarta 2.2.55](https://repo.maven.apache.org/maven2/io/swagger/core/v3/swagger-core-jakarta/2.2.55/swagger-core-jakarta-2.2.55.pom) y [padre Swagger](https://repo.maven.apache.org/maven2/io/swagger/core/v3/swagger-project/2.2.55/swagger-project-2.2.55.pom) | Línea Boot 4, dependencia Swagger y diferencia de gestión Jackson. |
| [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html), [Flyway/PostgreSQL](https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database), [POM POI OOXML 5.5.1](https://repo.maven.apache.org/maven2/org/apache/poi/poi-ooxml/5.5.1/poi-ooxml-5.5.1.pom) | Serialización de atributos, soporte modular del motor y dependencias de XLSX. |
| [Índice de distribuciones Node](https://nodejs.org/dist/index.json), [Node 24.22.1](https://nodejs.org/en/blog/release/v24.22.1) | Versión LTS y entrega de seguridad. Índice fechado 20/10 y anuncio 21/10: no se confunden esas fechas ni se infiere una incompatibilidad. |
| Registros npm: [pnpm](https://registry.npmjs.org/pnpm), [React](https://registry.npmjs.org/react), [React DOM](https://registry.npmjs.org/react-dom), [TypeScript](https://registry.npmjs.org/typescript), [Vite](https://registry.npmjs.org/vite), [plugin React](https://registry.npmjs.org/@vitejs%2Fplugin-react), [Tailwind](https://registry.npmjs.org/tailwindcss), [adaptador Vite](https://registry.npmjs.org/@tailwindcss%2Fvite) | Campos `versions`, `time`, `engines` y `peerDependencies` de las versiones exactas seleccionadas, no solo la etiqueta móvil `latest`. |
| Registros npm: [Router](https://registry.npmjs.org/react-router), [Query](https://registry.npmjs.org/@tanstack%2Freact-query), [Table](https://registry.npmjs.org/@tanstack%2Freact-table), [Hook Form](https://registry.npmjs.org/react-hook-form), [resolvers](https://registry.npmjs.org/@hookform%2Fresolvers), [Zod](https://registry.npmjs.org/zod), [Recharts](https://registry.npmjs.org/recharts), [Lucide](https://registry.npmjs.org/lucide-react), [shadcn](https://registry.npmjs.org/shadcn) | Publicación y restricciones declaradas de las bibliotecas restantes. |
| [Zod: requisitos](https://zod.dev/), [shadcn React 19](https://ui.shadcn.com/docs/react-19), [Tailwind: navegadores](https://tailwindcss.com/docs/compatibility) | Requisitos de TypeScript, componentes y límites de navegador; no pruebas de esta aplicación. |
| [Spring Security CVE-2026-41707](https://spring.io/security/cve-2026-41707), [CVE-2026-47841](https://spring.io/security/cve-2026-47841), [CVE-2026-47842](https://spring.io/security/cve-2026-47842), [JPA CVE-2026-47834](https://spring.io/security/cve-2026-47834) | Correcciones publicadas para las versiones Spring seleccionadas; no implica que OptiPlant utilice WebAuthn o DPoP. |
| [Aviso Vite](https://github.com/vitejs/vite/security/advisories/GHSA-rq7h-c2jc-7f22), [aviso Router](https://github.com/remix-run/react-router/security/advisories/GHSA-qwww-vcr4-c8h2), [aviso React RSC](https://github.com/react/react/security/advisories/GHSA-xqc2-xf4c-x3h3) | Alcance y versiones corregidas de los avisos citados; revisión acotada, no certificación exhaustiva. |

**Valoración técnica:** base estable publicada y coherente con ARQ-001–ARQ-010, conservando todas sus elecciones y límites. Se recomienda mantener las versiones propuestas con la corrección de Security y las concreciones indicadas, condicionando la validación ejecutable a §5.3. La mayor incertidumbre es la combinación efectiva springdoc/Swagger/Jackson; no hay evidencia reproducida que justifique cambiar de tecnología o declarar resuelto ese riesgo solo por sus números de versión.

## 6. Verificación documental, evidencia y cierre

### 6.1. Alcance de la consolidación

- La versión 1.0 conserva las secciones §1–§6 y los diez identificadores ARQ, e integra A PEN-001–PEN-008 con trazabilidad y estado APROBADA en §5.1.
- La versión 1.1 agrega §5.4 por solicitud del responsable: matriz de versiones verificadas, compatibilidad transversal, fuentes y gestión de dependencias. Conserva íntegro el contenido de ARQ-001–ARQ-010 y distingue esta concreción de la ratificación histórica.
- Las cuatro capas de §8.1 y las cinco justificaciones obligatorias de §8.2 quedan explícitas: lenguaje/plataforma en ARQ-002, motor/modelo relacional en ARQ-003, autenticación/autorización en ARQ-005, sincronización en ARQ-006 y patrones en ARQ-008.
- Se mantienen matriz de permisos, única llegada incluso cero, discrepancias históricas, residuales/compensaciones, no reservas y no stock negativo conforme a RF. No se cambian funciones, reglas ni antecedentes para ajustarlos al diseño.
- Se identifican en §1.2 los estados documentales anteriores y las decisiones históricas sustituidas; no se detectó un conflicto funcional sin resolver. Esos documentos permanecen intactos por el alcance autorizado.

### 6.2. Evidencia y límites

La consulta de fuentes y el cotejo documental verifican integración y trazabilidad, **no comportamiento ejecutable**. Todas las pruebas de cada ARQ son previstas. No se generan código, scripts ejecutables, DDL, Compose, migraciones, semillas ni diagramas en esta actividad; no se afirma ejecución de servicios, provisión de administrador, exactitud de XLSX, latencia o seguridad comprobadas.

**Evidencia auténtica de IA:** el responsable autorizó priorizar §8 y crear un documento único con M como referencia operativa; posteriormente aportó A con las ocho decisiones ratificadas y ordenó integrarlas directamente, sin nuevas rondas de aprobación. La asistencia se utilizó para contrastar fuentes vigentes, reconciliar estados y consolidar fundamentos/especificaciones. La ratificación proviene del responsable y del acta, no del agente; no se fabrican resultados de implementación o pruebas.

Para la versión 1.1, el responsable proporcionó las versiones de partida y autorizó corregirlas con evidencia suficiente, limitando el cambio a este documento. Se utilizó investigación de solo lectura con fuentes oficiales, registros npm, POM de Maven Central y consulta documental Context7; una comprobación adicional del BOM Boot y metadatos Vite confirmó la alineación de Security y requisitos Node. No se instalaron dependencias ni se ejecutaron compilaciones, servicios o escáneres de vulnerabilidades. Las conclusiones de compatibilidad declarada y sus límites constan en §5.4.

**Cierre:** la definición y aprobación documental de las ocho decisiones arquitectónicas del **punto 8 está finalizada**. §5.4 concreta la base de versiones y sus cautelas. Permanecen los detalles y evidencias de §5.3 para etapas posteriores, incluido el modelo completo, contratos, validación ejecutable de versiones, implementación, pruebas y diagramas de §7. Este cierre no declara esas actividades realizadas ni convierte recomendaciones tentativas en nuevas decisiones aprobadas.
