# Backend técnico verificable

Base mínima de S1–S3 de `odd/tasks/preparacion-tecnica.md`: API REST, sesiones JDBC, migraciones y datos sintéticos. No implementa comandos de inventario, compras, ventas, transferencias ni reportes.

La ampliación RF-0002–RF-0007 incorpora administración de usuarios/sucursales y consultas operativas sobre datos persistidos. Consultar [rutas, prueba manual y límites](../docs/08-implementacion/acceso-y-administracion.md). No se sustituyen los módulos operativos pendientes por simulaciones. No se modifican las migraciones ya aplicadas.

## Construcción y pruebas

Desde la raíz, con Docker disponible:

```sh
docker build --target build -t optiplant-backend-build:technical-bootstrap backend
docker run --rm --add-host host.docker.internal:host-gateway \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  optiplant-backend-build:technical-bootstrap \
  mvn --batch-mode --no-transfer-progress verify
docker build -t optiplant-backend:technical-bootstrap backend
```

`build` contiene Maven 3.9.16 y JDK Temurin 25.0.5+8. `runtime` es el destino predeterminado, ejecuta el JRE de esa misma versión con UID/GID 10001 y dispone de `curl` y healthcheck `/actuator/health`. No requiere Java ni Maven en el host. Las pruebas usan PostgreSQL 17.11 efímero y limpian sus contenedores mediante Testcontainers; nunca deben apuntar a una base real.

La imagen Maven fijada por digest trae otro parche Java: **no se utiliza ese JDK heredado**. Se instalan JDK/JRE oficiales 25.0.5+8 en rutas separadas y se fija `JAVA_HOME`/`PATH`. Esta equivalencia fue autorizada expresamente tras comprobar que Docker Hub no publicaba la etiqueta Temurin requerida. Soporta `arm64` y `amd64`; rechaza otras arquitecturas. Los cuatro SHA-256 están fijados en el Dockerfile y proceden de los assets de la [release oficial](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.5%2B8). Una descarga o suma incorrecta detiene la construcción. La base runtime es Ubuntu 24.04, no una supuesta etiqueta Temurin existente.

## Configuración segura y roles PostgreSQL

| Variable | Uso |
|---|---|
| `APP_DB_URL` | JDBC PostgreSQL, por ejemplo `jdbc:postgresql://postgres:5432/optiplant` |
| `APP_DB_USER`, `APP_DB_PASSWORD` | Login operativo sin privilegios administrativos |
| `APP_DB_MIGRATION_USER`, `APP_DB_MIGRATION_PASSWORD` | Login propietario del esquema para Flyway; usar credenciales distintas |
| `APP_ADMIN_CODE`, `APP_ADMIN_PASSWORD` | Provisión inicial individual; contraseña de 15–128 caracteres, cambio obligatorio |
| `APP_FRONTEND_ORIGIN` | Un origen exacto; defecto `http://localhost:5173` |
| `APP_DEMO_DATA` | `false` por defecto; `true` incorpora la ubicación Flyway de demostración |
| `APP_COOKIE_SECURE` | `false` para HTTP local; `true` bajo HTTPS |

Los secretos pertenecen a configuración local ignorada, nunca a SQL, argumentos de comandos publicados ni logs. Si no hay administrador activo y falta una credencial válida, el arranque no llega a disponibilidad. Reiniciar no restablece contraseñas ni duplica el administrador. Si ya existe un administrador activo, no es necesario volver a suministrar la contraseña inicial.

El inicializador PostgreSQL administrado por Compose debe crear los logins **con contraseñas obtenidas del entorno**, no literales públicas. Contrato SQL sin secretos (ejecutar conectado a la base objetivo; estos nombres de login son la referencia de integración):

```sql
CREATE ROLE optiplant_runtime NOLOGIN;
-- Crear optiplant_migrator LOGIN y optiplant_app LOGIN con secretos externos.
-- Ambos: NOSUPERUSER NOCREATEDB NOCREATEROLE; no conceder el rol migrador al operativo.
GRANT CONNECT ON DATABASE optiplant TO optiplant_migrator, optiplant_app;
GRANT USAGE, CREATE ON SCHEMA public TO optiplant_migrator;
GRANT optiplant_runtime TO optiplant_app;
```

Flyway se conecta como `optiplant_migrator` y crea sus tablas bajo ese propietario. V7 requiere que exista `optiplant_runtime` y le concede:

- `USAGE` sobre `public`, `USAGE, SELECT` sobre secuencias.
- `SELECT, INSERT, UPDATE` sobre las 32 tablas propias, **sin DELETE**.
- CRUD sobre las dos tablas Spring Session; solo SELECT sobre el historial Flyway.

El login operativo no debe ser propietario del esquema/tablas, miembro del migrador ni superusuario. La provisión inicial rechaza expresamente una conexión que tenga DELETE sobre `catalog_product`. V7 no crea roles ni contraseñas. Cada migración futura debe conceder los privilegios de sus tablas nuevas explícitamente. Hibernate usa `validate`; Spring Session nunca inicializa esquema en paralelo.

## Contrato HTTP mínimo

Todas las peticiones del cliente usan credenciales de sesión. No hay JWT, Basic ni formulario de login Spring. Cookie `SESSION`: HttpOnly y SameSite=Lax; mantener el mismo hostname para frontend/backend.

| Método y ruta | Solicitud / respuesta |
|---|---|
| GET `/api/v1/auth/csrf` | `{token,headerName}`; obtener antes de cualquier POST |
| POST `/api/v1/auth/login` | `{code,password}` → DTO de usuario |
| GET `/api/v1/auth/me` | DTO de usuario |
| POST `/api/v1/auth/password` | `{currentPassword,newPassword}` → 204 y autenticación nueva obligatoria |
| POST `/api/v1/auth/logout` | → 204; invalida la sesión utilizada |
| GET `/api/v1/bootstrap` | Prueba protegida de UUIDv4, instante UTC, importe decimal como cadena y contexto autorizado |

DTO de usuario: `{externalId,code,role,branchExternalId,mustChangePassword}`. No expone PK BIGINT, contraseña ni hash. Login y me siguen disponibles durante cambio obligatorio; bootstrap devuelve 403 hasta completarlo. El cambio de contraseña incrementa atómicamente `credentials_version`, invalida la sesión actual y hace rechazar las demás en su siguiente petición. Actualizaciones de rol/sucursal/activación también se contrastan en cada petición: conocer un UUID no concede acceso.

El cliente refresca CSRF después de login, password y logout; el token anterior al login deja de ser válido. Password invalida la autenticación, por lo que refrescar CSRF **no** evita volver a iniciar sesión. CSRF protege también login/logout. CORS admite exclusivamente el origen configurado, credenciales, GET/POST/OPTIONS y cabeceras Content-Type/X-CSRF-TOKEN. Documentación local accesible en `/v3/api-docs` y `/swagger-ui/index.html`; no desactiva protecciones.

`bootstrap?branchExternalId=<UUIDv4>&view=context|management` es una demostración técnica, no un permiso nuevo de negocio: ADMIN consulta cualquier contexto; MANAGER/OPERATOR solo su sucursal; OPERATOR no obtiene la vista management. No equivale a limitar la consulta de inventario entre sucursales permitida por RF. Su importe fijo sintético `123456789012345678.123456789012345678` demuestra transporte exacto y no representa un saldo real.

## Sesión, contraseñas y errores

- Argon2id: sal de 16 bytes, salida de 32 bytes, memoria 19456 KiB, dos iteraciones, paralelismo uno. Bouncy Castle 1.86 tiene versión explícita administrada en el POM; Boot no la administra. Se admiten espacios y no se truncan contraseñas.
- Expiración independiente del tráfico JDBC: 30 minutos desde actividad interactiva y ocho horas desde login. Todos los GET, incluidos bootstrap/me/csrf, son pasivos. La lista de actividad del servidor contiene únicamente POST password en esta base mínima, incluidos intentos estructuralmente válidos rechazados por credencial vigente incorrecta; los futuros comandos deben extenderla explícitamente, nunca mediante una cabecera de actividad confiada al cliente.
- Guardia persistente y bloqueada transaccionalmente por código exacto: cinco fallos en quince minutos; esperas progresivas 5/15/30 minutos, sin bloqueo permanente. Un éxito reinicia la escalada. El quinto fallo devuelve 401; durante la espera se devuelve 429. Se usa la precisión de microsegundos PostgreSQL para los límites temporales. Códigos desconocidos no crean cuentas ni sesiones autenticadas.
- Errores `application/problem+json`, RFC 9457, extensión estable `code`. Códigos principales: `AUTHENTICATION_REQUIRED`, `LOGIN_INVALID`, `LOGIN_THROTTLED`, `CSRF_INVALID`, `CORS_FORBIDDEN`, `SESSION_EXPIRED`, `SESSION_REVOKED`, `PASSWORD_CHANGE_REQUIRED`, `CURRENT_PASSWORD_INVALID`, `VALIDATION_FAILED`, `ROLE_FORBIDDEN`, `BRANCH_FORBIDDEN`, `INTERNAL_ERROR`. Sin SQL, trazas ni credenciales en respuestas.

## Migraciones y demostración

V1: identity (3 tablas); V2: catalog (5), purchases (4), sales (2); V3: transfers (3), logistics (7); V4: inventory (7) y app_idempotency (1). Total: 30 tablas de dominio y dos técnicas propias, con nombres del modelo aprobado. V5 copia byte por byte el script PostgreSQL oficial de Spring Session JDBC 4.1.1, incluidas sus dos tablas e índices. V6 incorpora índices de consultas/FK sin duplicar prefijos existentes. V7 asigna privilegios operativos. Flyway conserva su historial adicional.

V100 solo se localiza con demostración activada. Incluye dos sucursales, un producto/unidad y lista de precios, habilitación local, entrada sintética de 10 unidades a costo10 y venta de dos a precio15. Conserva dos operaciones, dos movimientos y componentes BASE100/−20; el saldo resultante es 8 unidades/valor80, costo promedio10 y venta neta30 COP. No inventa meses de historia. Los responsables históricos de demostración están inactivos y tienen marcador no autenticable, no contraseñas universales. No se generan usuarios demo utilizables ni transferencias/compras funcionales.

Las ubicaciones estructural y demo se mantienen separadas. Una vez aplicada V100, desactivar posteriormente la ubicación demo requiere planificar la validación del historial Flyway; no ocultar ni eliminar esa migración aplicada. Para dejar de mostrar demostración, usar una base limpia de otro entorno, no borrar historia silenciosamente.

## Límites de verificación

Las pruebas HTTP ejercitan cookies, CSRF, CORS, obligación inicial, revocaciones, polling, límites temporales, antiabuso, DTO/OpenAPI y permisos técnicos usando el login operativo restringido. Las pruebas de ciclo de vida arrancan contextos reales, provocan un fallo Flyway, comprueban falta de secreto inicial, reinicios sin duplicados, privilegios y concordancia entre libro/saldo de la semilla. Las aserciones SQL se limitan a contratos de persistencia y fixtures; no representan implementación de operaciones de negocio.

La prueba temporal mantiene actividad de comandos cada 29 minutos, comprueba acceso a las 7h59 y rechazo a las 8h con solo 16 minutos desde la última interacción; así distingue el límite absoluto del de inactividad. El reinicio conserva también una sesión autenticada real y la obligación de cambio inicial.

Esto no certifica seguridad completa, rendimiento, despliegue público, navegadores, concurrencia comercial, semántica de todos los 55 RF ni integridad de todos los futuros comandos. El flujo integrado Compose/frontend y los casos de uso comerciales requieren verificaciones adicionales del orquestador.
