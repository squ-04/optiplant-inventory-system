# Probar acceso, usuarios, sucursales y consultas

Implementación sobre el esquema PostgreSQL y la sesión JDBC existentes, sin nuevas dependencias. Este documento describe la entrega; no modifica el catálogo funcional aprobado.

## Recorrido manual

1. Preparar el entorno local con `python3 scripts/init_environment.py` si todavía no existe `.env`. No sobrescribir secretos existentes. Ejecutar `docker compose up --build --wait` y abrir `http://localhost:5173` (o los puertos de `.env`). Usar siempre `localhost` en ambos servicios.
2. Autenticarse con el código y la contraseña individuales provisionados en `.env`. El primer ingreso exige cambiar la contraseña (15–128 caracteres, incluidos espacios); volver a iniciar sesión con la nueva. También se puede cambiar voluntariamente acreditando la contraseña vigente.
3. Como administrador, crear dos sucursales con códigos diferentes. Consultarlas, cambiar sus nombres y comprobar que el identificador público y código de sucursal no cambian. Repetir un código debe mostrar un error sin crear otro registro.
4. Crear un gerente y un operador con sucursal activa, y otro administrador sin sucursal obligatoria. Entregar cada contraseña temporal individual por un canal privado: no se podrá consultar posteriormente. Comprobar edición de nombre/código/perfil/asignación. No existe eliminación física ni formulario de claves internas.
5. En otra ventana privada, iniciar sesión con el operador, completar su cambio inicial y consultar su sucursal. La selección de otra sucursal solo permite consultar existencias; no concede escritura ni acceso al historial comercial ajeno. Gerente y operador no ven formularios de administración.
6. Manteniendo una sesión del operador abierta, cambiar su perfil o sucursal desde el administrador. La siguiente solicitud de esa sesión debe exigir autenticación. Desactivar su cuenta debe conservar el registro, impedir el acceso y dejar su código reservado.
7. En **Visibilidad de la red**, el administrador puede consultar cualquiera de las sucursales: saldos, referencias de compras, ventas, transferencias y movimientos realmente persistidos. Sin registros se muestra ausencia de datos, no operaciones simuladas. Los valores decimales se conservan como cadenas exactas.
8. Desactivar una sucursal vacía sin compras operables, transferencias abiertas ni tránsito/faltantes pendientes. Debe conservar identidad e historial y no desactivar sus usuarios. Una sucursal con impedimentos debe rechazar la baja indicando el motivo, sin ajustes ni cancelaciones automáticas. Los datos de demostración, cuando están habilitados, permiten contrastar sucursales con operaciones; no borrar ni alterar manualmente una base de uso para preparar pruebas.
9. Cerrar sesión y comprobar que los datos protegidos ya no son accesibles. Al perder comunicación se muestra el error: no se confirma la operación ni se guarda para sincronización offline. Actualizar el listado antes de reintentar una solicitud cuyo resultado no se pudo recibir.

## Contrato REST

Todos los cambios usan cookie de sesión y CSRF; la autorización se comprueba en backend, no solo ocultando botones. Las referencias de rutas y respuestas son UUID públicos, nunca claves internas.

| Ruta `/api/v1` | Método y finalidad |
|---|---|
| `/users`, `/users/{uuid}` | GET: listado/detalle administrativos; POST en colección: alta con `code`, `fullName`, `role`, `branchExternalId`, `temporaryPassword` |
| `/users/{uuid}/update` | POST: `code`, `fullName`, `role`, `branchExternalId`; cambios sensibles revocan accesos previos |
| `/users/{uuid}/deactivate` | POST: baja lógica, sin contraseña en respuesta |
| `/branches`, `/branches/{uuid}` | GET: consultas autorizadas; POST en colección: alta con `code`, `name` |
| `/branches/{uuid}/update` | POST: únicamente `name`; no permite editar código/identidad |
| `/branches/{uuid}/deactivate` | POST: baja condicionada por datos reales de inventario, compras y transferencias |
| `/visibility/branches` | GET: directorio de sucursales para consultas autorizadas |
| `/visibility/branches/{uuid}` | GET: historial/saldos; administrador en red, perfiles locales solo su sucursal |
| `/visibility/inventory/{uuid}` | GET: existencias consultables entre sucursales, sin costos/valoración ni permiso de escritura |

Errores: `400` solicitud inválida; `401` sin sesión/vencida/revocada; `403` sin permiso o CSRF inválido; `404` recurso inexistente autorizado; `409` duplicación/impedimentos. Las respuestas usan ProblemDetail. Las bajas no admiten DELETE ni reactivación implícita. Los campos desconocidos o identidades enviadas como editables se rechazan.

## Alcance y límites verificables

- RF-0002 y RF-0003: administración integrada con persistencia y seguridad; RF-0006: ciclo de autenticación existente conectado a gestión/revocación y cambio voluntario visible.
- RF-0004: consultas de datos operativos persistidos en todas las sucursales. Esta vista no reemplaza los detalles, indicadores, dashboards ni reportes de los RF que los definen.
- RF-0005 y RF-0007: separación de consultas locales/globales y mantenimiento administrativo protegidos; consultar existencias ajenas no concede acceso al historial ajeno. La sucursal inactiva conserva consultas autorizadas.
- Los comandos de ingresos/retiros, compras, ventas, ajustes, etapas de transferencias y exportación todavía no existen. Por ello los criterios de RF-0005/RF-0007 que exigen ejecutar esos comandos y verificar sus efectos no pueden declararse cumplidos por esta entrega. Se aplicarán y probarán con sus módulos dependientes; aquí no se sustituyen por endpoints ficticios ni se amplía el alcance para implementarlos.
- La vista de consultas no usa polling ni promete cumplir las metas de frescura de RF-0015; requiere actualización explícita.

## Verificaciones

Backend: pruebas HTTP con PostgreSQL efímero/Testcontainers, más pruebas de política de cuenta; ejecutar el runner Maven Docker descrito en `backend/README.md`. Frontend: `pnpm test` y `pnpm build`, o `docker build --target build -t optiplant-frontend:check frontend`. Las pruebas del cliente con transportes controlados no sustituyen una comprobación de navegador con servicios reales. Los resultados observados se registran en `odd/tasks/rf-0002-0007.md`.

`scripts/check_identity_browser.py` automatiza diez escenarios UI/API con `playwright-cli` ya instalado, sin incorporarlo como dependencia de aplicación. Usarlo **solo contra una pila local desechable recién provisionada**, con administrador aún en cambio inicial: modifica contraseña, crea usuarios/sucursales y los desactiva. Requiere `OPTIPLANT_DISPOSABLE_TEST=1`, `OPTIPLANT_TEST_FRONTEND` (URL HTTP en `localhost`), `OPTIPLANT_TEST_ADMIN_CODE` y `OPTIPLANT_TEST_ADMIN_PASSWORD` suministrados en entorno privado de prueba. No reutilizar `.env`, sesiones ni volúmenes de trabajo. El script informa un directorio temporal con diagnósticos privados que quien ejecuta debe retirar tras inspección.

Resultados observados: 31 pruebas backend aprobadas; 14 pruebas frontend y construcción TypeScript/Vite aprobadas con la imagen Node fijada; diez escenarios de navegador con frontend/API reales aprobados; humo Compose y persistencia tras reinicio/down-up aprobados. La comprobación independiente confirmó permisos, revocación, impedimentos reales de baja, bloqueo transaccional y contrato previo de autenticación; dos bajas que ignoraban campos prohibidos se corrigieron y revalidaron con hashes intactos ante rechazo. El runner acepta confirmaciones automáticamente **solo en la página desechable de prueba** para evitar la suspensión de `run-code` por diálogos nativos; la aplicación conserva sus confirmaciones normales. Estos resultados no acreditan comandos operativos pendientes.
