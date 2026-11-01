# Validación desde un clon limpio — 2026-11-01

> Estado: evidencia de verificación generada con asistencia de IA (Claude Code), pendiente de revisión humana. No sustituye los requisitos ni las decisiones aprobadas.

Comprueba, antes de la sustentación del 4 de noviembre de 2026, que el proyecto se levanta como indica el enunciado y que sus módulos funcionan sobre esa instalación.

- **Fuente:** clon nuevo de `https://github.com/squ-04/optiplant-inventory-system` en el commit `2f2fbba`, más el renombrado a `docker-compose.yml`, aplicado como parche y publicado después en `70d44c3`.
- **Entorno:** macOS, Docker 28.3.2, Compose 2.38.2. Carpeta temporal y proyecto `clone-test`, sin `.env` y con volúmenes propios.
- **Construcción:** `docker compose build --no-cache --pull`, seguida del comando literal del enunciado, `docker compose up`.

## Resultado general

| Comprobación | Resultado |
|---|---|
| Clon sin `.env`, `node_modules` ni artefactos de compilación | Correcto |
| Construcción sin caché de capas | 1 min 36 s |
| `docker compose up` hasta servicios sanos | 17 s; `credentials` termina con código 0 |
| Recorrido funcional por API | 66 de 66 comprobaciones |
| Recorrido de pantallas en navegador | Sin errores de consola ni respuestas 4xx/5xx |
| `scripts/smoke_stack.py` | Correcto |
| `scripts/check_transfers_browser.py` | Correcto: 16 escenarios |
| `scripts/check_dashboard_browser.py` | Correcto: 2 escenarios |
| Comprobaciones completas del hook `pre-push` sobre `70d44c3` | Correctas |

## Recorrido funcional por API

Se ejecutó con usuarios y datos creados por la propia prueba, sobre la API pública. Además de los códigos HTTP, comprueba los efectos: existencias, costo, trazabilidad y permisos.

| Sección del enunciado | Comprobaciones | Cobertura principal |
|---|---|---|
| §2 Perfiles y acceso | 8 | Acceso anónimo y credenciales inválidas rechazados, cambio obligatorio de contraseña, gestión de sucursales y usuarios, restricciones del operador |
| §3.1 Inventario | 15 | Alta, edición y baja lógica de productos, unidades (1 caja = 12), catálogo propio, inventario de otra sucursal, ingresos y retiros, idempotencia, trazabilidad, alerta por stock mínimo |
| §3.2 Compras | 8 | Orden con precio, descuento y plazo, edición y cancelación, ingreso automático al recibir, histórico por proveedor y producto, costo promedio ponderado |
| §3.3 Ventas | 10 | Validación de existencias, descuento, dos listas de precios, sucursal, fecha y responsable, comprobante, histórico, idempotencia |
| §3.4 Transferencias | 7 | Ajuste de cantidad en origen, despacho, recepción completa, recepción parcial con faltante y tratamiento |
| §3.5 Logística | 6 | Prioridad de ruta, estado en tránsito, tiempos estimado y real, cumplimiento por sucursal y ruta |
| §3.6 Dashboard | 8 | Ventas por mes, rotación, demanda, transferencias, reabastecimiento, comparativa exclusiva del administrador |
| §4 Reportes exportables | 4 | Tres archivos `.xlsx` válidos y fechas obligatorias |

Valor calculado de forma independiente: 9 unidades a 100 más 12 unidades a 225 (una caja de 3.000 con 10 % de descuento) dan un costo promedio de 171,43, el mismo que devuelve la valoración.

Las dos primeras ejecuciones informaron 14 y 9 fallos, y la tercera 1. Todos procedían de supuestos incorrectos del script sobre el contrato de la API o los permisos, salvo el hallazgo siguiente. Cada caso se contrastó con el código antes de corregir el script. El resultado de 66 de 66 corresponde a una ejecución completa sobre una base recién creada.

## Recorrido en navegador

Con `playwright-cli` se accedió como administrador, gerente y operador y se abrieron todas las entradas del menú: 16, 15 y 11, respectivamente. El dashboard se mostró según el perfil: el administrador elige sucursal, el operador ve cantidades sin importes y el gerente ve la gráfica de importes, cuyo total del mes coincide con los 2.600 vendidos en la prueba.

## Hallazgo y corrección

**Un parámetro obligatorio ausente respondía 500 en lugar de 400.** Afectaba a `GET /api/v1/products/{id}/units/convert`, el único endpoint con parámetros de consulta obligatorios. Se corrigió después de esta validación: `ApiErrors` responde ahora `400 VALIDATION_FAILED`, y `ProductConfigurationHttpTest` cubre la ausencia de cada parámetro.

## Límites

- El recorrido en navegador abrió cada pantalla sin completar sus formularios. Los flujos por interfaz de esta sesión son los de los scripts de transferencias y dashboard; las reglas del resto de módulos se ejercitaron por API.
- `--no-cache --pull` evita la caché de capas, pero Docker conservó las imágenes base ya descargadas. En un equipo nuevo, la primera ejecución tardará más.
- El script del recorrido por API fue una herramienta puntual y no se versionó.
- Esta validación cubre el arranque y el comportamiento funcional. No evalúa rendimiento ni carga.
