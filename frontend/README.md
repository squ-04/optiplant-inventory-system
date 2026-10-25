# Frontend de acceso y administración

Pantallas React de acceso, cambio inicial/voluntario de contraseña, gestión administrativa de usuarios/sucursales y consulta de datos operativos persistidos. Véanse [recorrido manual y límites de RF-0002–RF-0007](../docs/08-implementacion/acceso-y-administracion.md). La autorización y las reglas de seguridad pertenecen al backend; no hay comandos de inventario, ventas, compras, transferencias ni exportación en esta entrega.

## Ejecución local con Docker

Desde la raíz del repositorio:

```sh
docker build --target dev -t optiplant-frontend:dev frontend
docker run --rm -p 127.0.0.1:5173:5173 -e VITE_API_BASE_URL=http://localhost:8080 optiplant-frontend:dev
```

Abrir `http://localhost:5173`. El backend debe aceptar ese origen explícito mediante CORS con credenciales. Usar el mismo hostname para ambos servicios; no mezclar `localhost` con `127.0.0.1`. No hay proxy de Vite. La integración de los tres servicios y sus variables corresponde al Compose de la raíz.

1. Iniciar sesión con el código y la contraseña individual provisionados por el backend; no existen credenciales universales en este frontend.
2. Si el servidor exige cambio inicial, completar el formulario (15–128 caracteres, sin recortar espacios). La API valida la política definitiva.
3. Como administrador, gestionar usuarios/sucursales y consultar la red. Gerente y operador consultan su ámbito y existencias de otras sucursales sin adquirir permiso de modificación. Los decimales recibidos se conservan como cadenas sin conversión a `number`.
4. Cerrar sesión. Un `401` elimina el contexto visual; un `403` muestra el detalle ProblemDetail y, para `PASSWORD_CHANGE_REQUIRED`, vuelve al cambio obligatorio.

Se muestran errores de red y respuestas inválidas sin simular confirmaciones. No hay reintentos automáticos de mutaciones ni polling. Tras recargar, `/auth/me` restaura el contexto de la sesión que siga vigente.

## Versiones y construcción

| Herramienta | Versión fijada |
|---|---|
| Node / pnpm | 24.22.1 / 11.24.0 |
| React / React DOM | 19.2.8 / 19.2.8 |
| TypeScript / Vite / plugin React | 6.0.3 / 8.3.4 / 6.1.0 |
| Tipos Node / React / React DOM | 24.9.2 / 19.2.9 / 19.2.7 |

Las versiones fueron resueltas en el registro público npm. Los tipos se seleccionaron dentro de las ramas 24 y 19.2, respectivamente. `pnpm-lock.yaml` es generado por pnpm 11.24.0 y conserva integridades y transitivas; el Dockerfile instala con `--frozen-lockfile`. Tailwind y su adaptador Vite están fijados en 4.3.3. Los componentes shadcn/ui incorporados, sus adaptaciones y sus dependencias exactas se documentan en el [sistema visual](../docs/08-implementacion/sistema-visual.md#componentes-oficiales-incorporados). No se incorporan bibliotecas para funcionalidades que esta pantalla no necesita.

La imagen `node:24.22.1-bookworm-slim` está fijada también al digest de índice `sha256:55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205`, contrastado en Docker Hub y descargado durante la verificación. El índice publica Linux arm64 y amd64. Vite escucha en `0.0.0.0:5173` con `strictPort`; el healthcheck usa `fetch` nativo de Node sobre `/`. Esta imagen sirve desarrollo/demostración local, no producción pública.

Para verificar desde la raíz sin Node ni pnpm locales:

```sh
docker build --target build -t optiplant-frontend:check frontend
```

Ese target ejecuta `pnpm test` (runner nativo de Node con eliminación de tipos) y `pnpm build` (`tsc -b && vite build`). No requiere un framework de pruebas adicional. Las pruebas mockean fetch: credenciales, JSON, CSRF y rotación tras login/cambio/salida, ProblemDetail, expiración `401`, validación DTO y precisión decimal. El navegador con cookies y backend reales se verifica por separado mediante Compose; las pruebas unitarias no acreditan CORS ni atributos de cookies reales.

## Ejecución nativa en macOS ARM64

La comprobación compartida `python3 scripts/run_checks.py frontend` se ejecuta desde la raíz en Docker y copia únicamente las fuentes a un directorio del contenedor. Excluye `node_modules`, salidas, cachés y `.env*` del host antes de instalar con lockfile congelado; evita mezclar binarios nativos macOS y Linux sin modificar la instalación local.

Usar las mismas versiones fijadas que en Docker. Con Node 24.22.1 instalado y `nvm` disponible en la terminal, ejecutar desde la raíz:

```sh
nvm use 24.22.1
cd frontend
corepack enable pnpm
pnpm install --frozen-lockfile
pnpm test
pnpm build
pnpm dev --host 127.0.0.1
```

Ejecutar pnpm dentro de `frontend`: Corepack lee allí `packageManager` y selecciona 11.24.0. Desde la raíz, sin ese contexto, puede elegir otra versión aunque se pase `--dir frontend`. No es necesario cambiar la versión predeterminada global de Node o pnpm.

No reutilizar `node_modules` de Docker/Linux en macOS. Rolldown usa un binario específico de plataforma; la instalación nativa con el lockfile congelado recupera la variante adecuada sin cambiar versiones. Se verificaron Node 24.22.1, pnpm 11.24.0, las 14 pruebas y el build en macOS ARM64 el 25 de octubre de 2026.

## Organización y contrato

- `src/app`: pantalla y estado efímero de presentación.
- `src/components/shared`: App Shell con navegación lateral persistente y pestañas contextuales accesibles. Las vistas y paneles ocultos conservan formularios sin enviar; no se altera el ámbito autorizado del usuario.
- `src/components/ui`: Button, Input, Badge, Table y Tabs adaptados del registro oficial shadcn/ui, con licencia conservada. Revisar este inventario antes de añadir componentes.
- `src/lib/api/client.ts`: único cliente fetch, DTO y validación de respuestas.
- `src/styles`: tokens y patrones accesibles y responsivos del [sistema visual](../docs/08-implementacion/sistema-visual.md), aplicado inicialmente a acceso, administración y consultas.

`VITE_API_BASE_URL` es configuración pública del navegador, nunca un secreto; su valor predeterminado es `http://localhost:8080`. En el servidor de desarrollo se lee al arrancar; para un bundle construido debe definirse durante la construcción.

Todas las solicitudes incluyen `credentials: 'include'`. El cliente obtiene `{token, headerName}` de `GET /api/v1/auth/csrf` antes de mutar y lo renueva tras login, cambio de contraseña y logout. La cookie de sesión `HttpOnly` la administra el navegador: no se lee ni se persiste desde JavaScript. No se usan `localStorage` ni `sessionStorage` para credenciales, tokens o identificadores de sesión.

Las rutas se concretan en L4 de la tarea: login JSON `{code,password}`, contexto en `/auth/me`, cambio JSON `{currentPassword,newPassword}`, logout y `/bootstrap`. El DTO de prueba exige `externalId` UUIDv4, `occurredAt` UTC y `amount` decimal como cadena; preserva los demás campos recibidos. Los errores HTTP conservan su estado incluso si un intermediario devuelve contenido no JSON.
