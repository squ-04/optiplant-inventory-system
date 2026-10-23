# Ejecutar y verificar la base técnica

Esta base conecta React con Spring Boot y PostgreSQL por REST. Es preparación técnica: no acredita la implementación de ventas, compras, transferencias, dashboards ni reportes completos. Las decisiones vigentes están en [arquitectura](../04-arquitectura/arquitectura-y-diseno-tecnico.md) y [modelo de datos](../06-modelo-base-datos/modelo-datos.md).

## Arranque local

Requisitos del equipo: Docker con Compose y motor activo, Git y Python 3.10 o posterior. Java, Maven, Node y PostgreSQL se ejecutan dentro de contenedores; la primera construcción requiere acceso a los registros públicos y puede tardar varios minutos.

```sh
python3 scripts/init_environment.py
docker compose up --build --wait
```

Abre `http://localhost:5173`. La API está en `http://localhost:8080`, su salud en `/actuator/health`, la especificación en `/v3/api-docs` y Swagger UI en `/swagger-ui/index.html`. No mezcles `localhost` y `127.0.0.1` en las URL del navegador: cookies y CORS deben conservar el mismo hostname.

La inicialización crea `.env` ignorado por Git, con permisos `0600` y secretos aleatorios individuales. Nunca reemplaza un archivo existente. Consulta **localmente** `APP_ADMIN_CODE` y `APP_ADMIN_PASSWORD` para acceder; no copies esos valores en tickets, capturas, logs o commits. El primer acceso exige cambiar la contraseña antes de consultar el recurso protegido. `.env.example` documenta variables, no proporciona credenciales utilizables.

Si cambias los puertos `FRONTEND_PORT` o `BACKEND_PORT`, reconstruye/recrea los servicios y utiliza las URL resultantes. PostgreSQL no publica puertos al host.

## Datos y persistencia

- Flyway crea el esquema y las tablas oficiales de Spring Session JDBC. Hibernate valida, no reconstruye tablas; no hay inicialización paralela del esquema de sesiones.
- `APP_DEMO_DATA=true` habilita las semillas locales separadas de la estructura. Son datos de demostración, no historia productiva ni evidencia de casos de uso implementados.
- El administrador inicial proviene de configuración privada, no de contraseñas públicas en SQL. Reiniciar no debe restablecer su contraseña ni duplicar registros.
- PostgreSQL inicializa el grupo `optiplant_runtime` sin login, el login limitado `optiplant_app` y el propietario de migraciones `optiplant_migrator`, ambos sin privilegios administrativos. La API utiliza `optiplant_app`; Flyway utiliza las credenciales independientes del migrador. El administrador del clúster también tiene un secreto distinto. No reutilices contraseñas entre esos roles.
- El volumen `pgdata` conserva la base tras `docker compose down` y reconstrucciones. **`docker compose down -v` elimina datos**: no lo utilices para un reinicio normal.
- No elimines migraciones ya aplicadas ni alteres sus checksums para ocultar fallos. Un error de migración debe impedir el arranque; no se reconstruye silenciosamente la base.

## Comprobaciones compartidas con CI

```sh
python3 scripts/run_checks.py all
# También se puede ejecutar un grupo concreto:
python3 scripts/run_checks.py repository
python3 scripts/run_checks.py documentation
python3 scripts/run_checks.py backend
python3 scripts/run_checks.py frontend
python3 scripts/run_checks.py integration
```

| Grupo | Prueba real |
|---|---|
| `repository` | Política de archivos y sintaxis JSON/Excalidraw; no compila aplicaciones |
| `documentation` | Regresiones Python de documentación, herramientas y hook |
| `backend` | Maven `verify` en contenedor, incluidas las pruebas de seguridad/persistencia |
| `frontend` | Instalación con lockfile congelado, pruebas, TypeScript y construcción Vite |
| `integration` | Construcción y arranque de los tres servicios y humo HTTP con datos aislados |

El registro `scripts/run_checks.py` es único para ejecución manual, pre-push y GitHub Actions. Los grupos de aplicación solo indican «no aplica» en snapshots documentales anteriores que no contengan ningún marcador de aplicación; un snapshot parcialmente incorporado falla. La ausencia de Docker o un error de construcción no se convierte en aprobación.

El hook sigue validando los bytes de los commits enviados, no los cambios sin commit. Necesita Docker activo y acceso de lectura al socket local para las pruebas de integración. No se debe ejecutar contra un motor remoto. Los grupos se ejecutan en paralelo y pueden consumir recursos durante varios minutos; un fallo cancela el push.

## Aislamiento y límites de la evidencia

La prueba de humo genera credenciales y un nombre Compose exclusivos, publica puertos locales libres y utiliza un volumen nuevo. Al terminar elimina **solo el volumen desechable de esa prueba**, nunca el `pgdata` del entorno personal. El directorio temporal externo se informa para limpieza posterior; puede contener secretos efímeros y no debe publicarse.

La demostración HTTP local no acredita HTTPS productivo. El navegador no guarda contraseñas ni identificadores de sesión en almacenamiento web. La API conserva CSRF, cookies `HttpOnly` y CORS explícito con credenciales. Las reglas de negocio pendientes no deben inferirse del endpoint técnico `/api/v1/bootstrap`.

Los resultados observados y cualquier comprobación pendiente se registran en [las tareas de preparación](../../odd/tasks/preparacion-tecnica.md). Ejecutar localmente un grupo no demuestra que GitHub Actions haya corrido; no se publica ni dispara CI remoto como parte de esta preparación.
