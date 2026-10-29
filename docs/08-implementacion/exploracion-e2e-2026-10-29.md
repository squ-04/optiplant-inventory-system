# Informe de exploración funcional E2E — Transferencias, logística y cumplimiento

> Estado: evidencia de exploración generada con asistencia de IA (Claude Code), pendiente de revisión humana. Los hallazgos no son decisiones aprobadas ni alteran el alcance; las correcciones requieren autorización expresa.

- Fecha: 2026-10-29. Herramienta: `playwright-cli` (Chromium headless, cinco sesiones simultáneas: administrador, operador y gerente de origen, operador y gerente de destino). Commit: `45fcad9` (main).
- Alcance: RF-0032 a RF-0043 (solicitud, preparación, aprobación, despacho, recepción, faltantes, tratamiento, tiempos, rutas, estados y cumplimiento logístico), más regresión de movimientos, consultas, valoración, alertas y aislamiento por sucursal.
- Entorno (opción B): pila Compose desechable `optiplant-e2e-45fcad9`, `.env` generado en el directorio temporal de la sesión, datos demo V100 y cuentas de prueba propias. Sin datos de producción ni el `.env` del responsable.
- Servicios: frontend `http://localhost:62482` (Vite dev, `200`), backend `http://localhost:62481` (`/actuator/health` = UP), PostgreSQL 17.11 sin publicar al host (accedido con `docker compose exec`). Los tres `healthy`.
- Datos creados: sucursales `E2E-NORTE` (origen), `E2E-SUR` (destino) y `e2e-norte` (prueba de códigos en minúsculas); usuarios `E2E-OP-N`, `E2E-GER-N`, `E2E-OP-S`, `E2E-GER-S` y `E2E-OP-MED` (operador de `DEMO-MED`); productos `E2E-HELECHO` (unidad indivisible, caja de 12) y `E2E-MUSGO` (kg divisible); rutas `E2E-N-S`, `e2e-s-n` y `e2e-n-s`; transferencias `TR-000001`–`TR-000011`; ingresos manuales iniciales. Configuración de datos por API y flujos principales por la interfaz. No se eliminó ni modificó información preexistente.
- Sin cambios de código.

## Hallazgos priorizados

### Media

**H1. La «Salida real» del despacho se precarga con una hora obsoleta.**
- Pasos: como operador de origen, abrir el detalle de una transferencia en preparación; esperar su aprobación; con el mismo detalle abierto (o volviendo a pulsar **Ver detalle** de la misma transferencia), pulsar **Registrar despacho**.
- Esperado: la salida real parte de la hora actual de Bogotá al abrir el formulario.
- Obtenido: el campo muestra la hora en que se abrió el detalle por primera vez (14:43 a las 14:52), aunque se cierre y reabra el formulario con **Volver sin despachar**. Como era anterior a la aprobación (14:44), confirmar mostró «La salida real no puede ser anterior a la aprobación de la transferencia.». Solo al cerrar y reabrir el detalle se renovó (14:52).
- Impacto: si la hora obsoleta es posterior a la aprobación, el despacho se registra sin aviso con una salida anterior a la real, lo que altera las duraciones de RF-0040 y el KPI de RF-0043. El servidor solo puede rechazar el caso anterior a la aprobación.

**H2. El panel de faltante afirma que un reenvío recibido libera el tránsito; no lo hace.**
- Pasos: recepción en 0 de `TR-000008` (1 kg en tránsito); reenvío `TR-000011` desde el detalle, despachado y recibido completo.
- Esperado (guía, RF-0039): el reenvío no liquida el tránsito original; la interfaz lo indica.
- Obtenido: el panel dice «Lo pendiente sigue en tránsito hasta que el gerente de origen registre su pérdida o se reciba un reenvío.», pero tras recibir el reenvío el tránsito de `TR-000008` sigue en 1,000 kg (API y base de datos). El texto se mantiene también después de registrar la pérdida, con tránsito 0.
- Impacto: un gerente puede dar el faltante por cerrado con el reenvío y no registrar la pérdida, dejando valor en tránsito indefinidamente (2.000 COP en la prueba).

### Baja

- **H3. Despacho y rechazo sin diálogo de confirmación.** Aprobar, recibir y registrar la pérdida piden confirmación; despachar (mueve existencias) y rechazar («no se puede deshacer») se ejecutan con un solo clic.
- **H4. Avisos de éxito persistentes en Transferencias.** «TR-000002 quedó rechazada…» seguía visible minutos después al registrar la pérdida de `TR-000001`; «TR-000001 quedó aprobada…» al trabajar en `TR-000002` (equivalente a H9 del 2026-10-28, corregido allí en Ventas, Compras y Movimientos).
- **H5. Etiquetas ambiguas en el panel de faltante.** Con la alerta resuelta, la fecha de apertura se rotula «Alerta abierta» junto a «Alerta resuelta». Una recepción en 0 se describe como «menos de 1 min hasta la llegada parcial».
- **H6. Pasos de estado engañosos en transferencias terminales.** Rechazada o cancelada, el detalle sigue mostrando «Aprobada (pendiente)», «Despacho (pendiente)» y «Recepción (pendiente)».
- **H7. Texto desactualizado en Consultas.** «Compras, ventas y transferencias se incorporan en sus entregas funcionales; no se simulan desde esta pantalla.», aunque ya están implementadas.
- **H8. Errores tipográficos.** «p. m..» (doble punto en rechazo, cancelación y lecturas del servidor); «Operador Sur E2E(E2E-OP-S)» y «TR-000001para» sin espacio; «Enviado 2,500, recibido 2,000 kg» sin unidad en lo enviado; «se despacharon 1 caja».
- **H9. Mensajes genéricos.** Un `500` del listado muestra solo «Error HTTP 500.» (conserva las filas anteriores sin indicarlo); `branch=abc` en el reporte responde «La solicitud no es válida.» en lugar de indicar que la sucursal debe ser un UUID.
- **H10. Códigos que difieren solo en mayúsculas.** Se aceptan `E2E-N-S` y `e2e-n-s` como rutas distintas, igual que las sucursales `E2E-NORTE` y `e2e-norte`. Es coherente en todo el sistema; se registra por la ambigüedad que produce en listados y reportes.
- **H11. Redondeo visible de tiempos.** `TR-000010`: estimado 0 s, real 43 s, desviación 44 s (la desviación se redondea hacia arriba por diseño, la duración no). La resta no coincide al inspeccionar la API.
- **Observación.** En la solicitud, el selector ofrece productos del destino que el origen elegido no tiene habilitados (`DEMO-BOG`); el servidor rechaza con un mensaje claro. El formulario ya lo advierte.

## Escenarios ejecutados

| # | Escenario | Esperado | Obtenido |
|---|---|---|---|
| 1 | Disponibilidad de servicios | 3 servicios sanos | OK |
| 2 | Primer acceso ADMIN con cambio obligatorio; menú por perfil | Bloquea hasta cambiar; operador sin Cumplimiento ni Alertas | OK |
| 3 | Solicitud vacía, cantidades `0`, `-1`, `1,5` (caja), `abc` (RF-0032) | Rechazo sin crear | OK |
| 4 | Origen sin el producto habilitado | `409` con línea indicada | OK |
| 5 | Línea repetida producto+unidad | Rechazo indicando la línea original | OK |
| 6 | Solicitud multilínea 2 caja + 2,5 kg | `TR-000001` Solicitada; destino sin stock del origen | OK |
| 7 | Preparación: 3 caja, `1,5` caja, `2,5555`, `abc`, vacío (RF-0033) | Guardado bloqueado con mensaje | OK |
| 8 | Preparar 1 caja y 2,5 kg | En preparación, «Menos de lo solicitado», historia | OK |
| 9 | Aprobar con diálogo; Escape no aprueba (RF-0034) | Aprobada con responsable; stock 30 | OK |
| 10 | Rechazar sin motivo, con espacios, 520 caracteres, válido | Rechazo / mensaje / 500 máx. / Rechazada | OK (H3, H8) |
| 11 | Cancelación por destino antes de aprobar | Cancelada; sin botón en aprobadas | OK |
| 12 | API: mismo origen, gerente/admin solicitan, campo extra, unidad inexistente, 4 decimales | `400`/`403`/`400`/`404`/`400` | OK |
| 13 | API: preparar/aprobar/rechazar/cancelar por perfil o estado incorrecto | `403`/`409`/`400 REASON_REQUIRED` | OK |
| 14 | API: otra sucursal consulta, UUID inexistente, filtros y límites inválidos | `404`/`404`/`400` | OK |
| 15 | Despacho sin ruta activa (RF-0035) | Aviso y confirmación deshabilitada | OK |
| 16 | Rutas: vacío, código con espacios, válida, trayecto y código duplicados, inversa (RF-0041) | Mensajes claros; destino excluye el origen | OK (H10) |
| 17 | Editar ruta: código de otra, prioridad Media→Alta | `409` / grupo «Prioridad alta» | OK |
| 18 | Despacho: sin transportista, sin llegada, llegada anterior, salida futura, anterior a la aprobación | Bloqueos con mensaje | OK |
| 19 | Salida real precargada | Hora actual | **FALLA (H1)** |
| 20 | Despacho válido de `TR-000001` | En tránsito; origen 30→18 u y 10→7,5 kg | OK (H3) |
| 21 | API despacho: stock insuficiente, todo en 0, perfiles, sin zona, +10 min, 300 caracteres, repetido, cancelar en tránsito | `409`/`409`/`403`/`400`/`400`/`400`/`409`/`409` | OK |
| 22 | Ruta inactiva: despacho rechazado y oculta al operador; reactivar | `ROUTE_REQUIRED` | OK |
| 23 | Prioridad congelada en el despacho | `TR-000001` HIGH, `TR-000004` MEDIUM | OK |
| 24 | Idempotencia de despacho y despachos concurrentes | Mismo resultado / `409`; uno solo | OK |
| 25 | Recepción: sobrante, `0,5` caja, `2,6` kg, `-1`, 4 decimales, vacío (RF-0036/0037) | Bloqueo con mensaje | OK |
| 26 | Recepción parcial 1 caja + 2 kg con diálogo | Con discrepancia, alerta, tránsito 0,5 kg | OK (H8) |
| 27 | API recepción: perfiles, sobrante, líneas vacía/inexistente/duplicada, estado, idempotencia, segunda recepción | `403`/`409`/`400`/`409`/`200`/`409` | OK |
| 28 | Recepción en 0 (RF-0037-CA-04) | Operación `ARRIVAL` sin movimientos | OK |
| 29 | Alerta: resolver sin tratamiento y con reclamación (RF-0038) | Mensaje / resuelta con responsable; estado igual | OK |
| 30 | API alerta: sin faltante, repetida, sin/otro tratamiento, operador, admin | `409`/`409`/`400`/`400`/`403`/`403` | OK |
| 31 | Pérdida en tránsito: vacía, espacios, válida con diálogo (RF-0039) | Mensajes / tránsito 0, autor | OK (H4) |
| 32 | API pérdida: gerente destino, sin evidencia, repetida | `403`/`400`/`409` | OK |
| 33 | Reenvío desde detalle: origen fijo, cantidad propuesta, vínculo | `TR-000007` y `TR-000011` vinculadas | OK |
| 34 | Reenvío con faltante ajeno u otro origen | `404`/`409` | OK |
| 35 | Tránsito original tras recibir el reenvío | Sin cambios y texto coherente | **FALLA (H2)** |
| 36 | Tiempos (RF-0040): en tránsito, discrepancia, a tiempo, tardía, vencida | Valores y textos coherentes | OK (H11) |
| 37 | Filtros por estado y faltante (RF-0042) | Cada estado separado; alerta no cambia el estado | OK (H6) |
| 38 | Cumplimiento gerente destino (RF-0043) | 25,00 % (1 de 4), 1 vencida aparte | OK |
| 39 | Período sin llegadas, fin < inicio, fecha vacía, año 0001, solo hoy | «No calculable» / mensajes / 25,00 % | OK |
| 40 | Cumplimiento admin: red y por sucursal; operador y otra sucursal | Datos / `403` | OK (H9) |
| 41 | Conservación de valor en PostgreSQL | 35.000 = 31.000 + 3.000 tránsito + 1.000 pérdida | OK |
| 42 | Regresión: Movimientos, Consultas (tránsito), Valoración, Catálogo, Compras, Ventas, Precios | Cargan sin errores | OK (H7) |
| 43 | Admin en Transferencias: sin acciones | Solo consulta | OK |
| 44 | Operador de `DEMO-MED` | Sin transferencias de E2E | OK |
| 45 | Vista móvil 390 px en Transferencias, Rutas, Cumplimiento y Alertas | Sin desplazamiento horizontal | OK |
| 46 | `500` y retraso simulados en el listado | Alerta; botón «Actualizando…», `aria-busy` | OK (H9) |
| 47 | Respuesta perdida del despacho y reintento | Aviso de resultado desconocido; misma clave; un despacho | OK |

## Consola y red

- Todos los errores de consola corresponden a rechazos provocados por las pruebas (`400`, `409`), al `500` y al fallo de red simulados, y al `401` inicial de `/auth/me`.
- Sin advertencias de consola ni errores de JavaScript.

## Evidencia de uso de IA

- Herramientas: Claude Code (Opus 5.5) con `playwright-cli`; API desde clientes de prueba con sesión y CSRF para preparar datos y probar casos que la interfaz no permite; interceptación de red para errores, retrasos y respuesta perdida; consultas de solo lectura a PostgreSQL.
- Solicitud del responsable (resumen): pruebas exploratorias E2E con Playwright centradas en RF-0032–RF-0043 y regresión de lo anterior, solo con cuentas y datos de prueba (opción B), sin modificar código ni eliminar información preexistente, con informe priorizado por severidad.
- Validación: H1 se reprodujo cerrando y reabriendo el formulario y el detalle con la hora real; H2 contrastando el texto con la API y la base de datos. Los demás hallazgos se basan en una observación.

## Limitaciones

- Exploración manual asistida por IA; no sustituye pruebas automatizadas.
- No se ejercitó el tiempo inconsistente (`consistent: false`): exige alterar datos fuera de la aplicación; lo cubre `LogisticsComplianceHttpTest`.
- RF-0026-CA-02 (una compra nueva en origen no cambia el tránsito) no se ejercitó: un ingreso manual con existencias no admite costo (`UNIT_COST_NOT_ALLOWED`, por diseño) y no se registró una compra.
- La tolerancia de 5 minutos del reloj solo se probó fuera de ella (+10 min).
- Errores `500`, retrasos y pérdida de respuesta fueron simulados.
- La pila desechable sigue disponible para reproducir los hallazgos hasta que se retire.

## Seguimiento

Correcciones registradas en `odd/tasks/e2e-2026-10-29-fixes.md` (rama `fix/e2e-2026-10-29-findings`) y re-probadas en la misma pila desechable reconstruida:

| Hallazgo | Corrección | Re-prueba |
|---|---|---|
| H1 | Al abrir «Registrar despacho» se propone la hora actual de Bogotá, salvo que el operador haya editado la salida; transportista y llegada estimada se conservan. | Detalle abierto a las 15:18, despacho abierto a las 15:19: salida propuesta 15:19; el transportista se conserva al volver. |
| H2 | El panel indica que solo la pérdida liquida el tránsito y que un reenvío no lo libera; sin tránsito pendiente, «Ya no queda nada en tránsito de este faltante». | `TR-000008` (1 kg pendiente con reenvío recibido) y `TR-000001` (pérdida registrada) muestran cada texto. |
| H3 | Despacho, rechazo y cancelación piden confirmación en un diálogo; Escape no envía y conserva lo escrito. | Diálogos vistos en `TR-000005`, `TR-000012` y `TR-000006`; el despacho confirmado de `TR-000005` muestra el rechazo del servidor por existencias. |
| H4 | El aviso de éxito se retira al abrir otro detalle o consultar con filtros. | Sin aviso tras abrir `TR-000006` y tras **Consultar**. |
| H5 | «Abierta el»; la recepción en 0 se describe «sin mercancía recibida» y «no llegó mercancía, no cumple». | `TR-000008`. |
| H6 | Los pasos no alcanzados de una transferencia rechazada o cancelada se marcan «No realizado» (trazo discontinuo). | `TR-000012`. |
| H7 | Texto de Consultas actualizado. | OK. |
| H8 | Sin doble punto tras «p. m.», espacios entre nombre y código y en «TR-000001 para», unidad en lo enviado; el servidor responde «Línea N: no se puede recibir más de lo enviado: 4 unidad.». | `TR-000012`, `TR-000001`, Alertas «Lectura del servidor: … p. m.»; `TransferReceptionHttpTest`. |
| H9 | Un error del servidor sin detalle se explica en español en todas las vistas; el listado de transferencias añade que lo mostrado puede estar desactualizado. | `500` simulado: «El servidor no pudo completar la solicitud (HTTP 500)… El listado mostrado puede estar desactualizado.». |
| H10 | Decisión del responsable: códigos de datos maestros únicos sin distinguir mayúsculas, conservando lo escrito (migración V12). Si una base ya tiene choques, la migración se detiene sin cambiar nada y los nombra; el inicio de sesión sigue siendo exacto. | Pila desechable con choques reales: el backend no arrancó y los datos quedaron idénticos; tras renombrar los dos registros de prueba, V12 se aplicó y `e2e-sur`, `e2e-helecho`, `e2e-n-s` y `e2e-op-n` reciben `409`. |
| H11 | Decisión del responsable: la duración real también se redondea hacia arriba, de modo que desviación = real − estimado. (La regla de redondeo no estaba en los requisitos aprobados, sino en la guía de transferencias.) | `TR-000010`: 0 / 44 / 44; las demás cuadran. |
