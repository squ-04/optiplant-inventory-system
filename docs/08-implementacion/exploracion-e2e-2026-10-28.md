# Informe de exploración funcional E2E — Compras, valoración, precios y ventas

> Estado: evidencia de exploración generada con asistencia de IA (Claude Code), pendiente de revisión humana. Los hallazgos no son decisiones aprobadas ni alteran el alcance; las correcciones requieren autorización expresa.

- Fecha: 2026-10-28. Herramienta: `playwright-cli` (Chromium headless, tres sesiones simultáneas: operador, administrador y gerente/operador de otra sucursal). Commit: `e24036a` (main).
- Alcance: RF-0021 a RF-0031 (compras, valoración, listas de precios y ventas), más regresión de identidad, catálogo, movimientos, alertas y aislamiento por sucursal.
- Entorno (opción B): pila Compose desechable `optiplant-e2e-8e50c09b`, `.env` generado en el directorio temporal de la sesión, datos demo V100 y cuentas de prueba propias. Sin datos de producción ni el `.env` del responsable.
- Servicios: frontend `http://localhost:62382` (Vite dev), backend `http://localhost:62381` (`/actuator/health` = UP), PostgreSQL 17.11 sin publicar al host (accedido con `docker compose exec`). Los tres `healthy`.
- Datos creados: sucursal `E2E-SUR`; usuarios `E2E-OP-SUR` (operador), `E2E-GER-SUR` (gerente) y `E2E-OP-MED` (operador de `DEMO-MED`); producto `E2E-ROSA` con unidad `Caja` (12); listas `DETAL-E2E` y `MAYOR-E2E` (luego inactiva); proveedores `PRV-E2E` y `PRV-E2E2`; órdenes `OC-000001`–`OC-000004`; ventas `VT-000001`–`VT-000006`; un ingreso manual y una devolución. No se eliminó ni modificó información preexistente (los datos demo solo se consultaron).
- Sin cambios de código.

## Hallazgos priorizados

### Media

**H1. El diálogo de confirmación de venta muestra un total con un precio obsoleto; el servidor cobra otro.**
- Pasos: con el operador, confirmar una venta con la lista `DETAL-E2E` (precio unidad 1.500). Como administrador, cambiar el precio de la unidad a 1.600. Con el operador, pulsar **Nueva venta** (la lista llega preseleccionada), elegir el producto y 1 unidad, **Confirmar venta**.
- Esperado: el formulario y el diálogo reflejan el precio vigente (1.600) o advierten que el servidor fijará el precio al confirmar.
- Obtenido: la línea muestra precio de lista 1.500, el diálogo dice «total neto estimado de 1.500,00 COP» y, tras aceptar, el servidor confirma `VT-000005` «por 1.600,00 COP». No se advierte la diferencia.
- Evidencia: ninguna petición `GET /catalog/price-lists/{id}` al abrir la venta nueva; al volver a elegir la lista sí se recarga (en una prueba anterior así se mostró 1.500 correctamente). Base de datos: `VT-000005` neto 1600.
- Impacto: el operador acepta un importe distinto del registrado (RF-0027, RF-0030: el precio es el de la lista al confirmar). El servidor actúa correctamente; el defecto es de presentación y consentimiento.

### Baja

- **H2. Lista de precios desactivada sigue ofrecida al operador y el mensaje no indica la causa.** Tras desactivar `MAYOR-E2E`, el formulario de venta abierto la sigue listando. Al elegirla, `GET /catalog/price-lists/{id}` responde `404` y el formulario muestra «Línea 1: la lista elegida no tiene precio para esa unidad. Elija otra unidad u otra lista.», aunque la lista sí tiene precio: está inactiva. No se envía la venta (sin efectos).
- **H3. Descuentos fuera de rango pasan la validación del cliente.** En ventas, `100,5` cumple el patrón `\d{1,3}(,\d{1,6})?`: la línea muestra «Neto —» y el diálogo «total neto estimado de —», y el servidor rechaza con `400` y mensaje claro. En compras, `101` también pasa el patrón; el servidor responde «el descuento debe ser un porcentaje entre 0 y 100…». Sin efectos en stock.
- **H4. Mensajes nativos genéricos.** Precio, plazo y descuento inválidos (`-5`, `abc`, `1.5`, `-1`) muestran «Haz coincidir el formato solicitado.», sin indicar el formato esperado (coma decimal, días enteros, 0–100).
- **H5. Validación del cuerpo antes que el perfil.** Gerente o administrador con cuerpo incompleto en `POST /purchases/orders` o `POST /sales` reciben `400 VALIDATION_FAILED` («Seleccione el proveedor…», «Seleccione la lista…»); con cuerpo válido reciben `403`. Sin efectos; solo orden de comprobaciones.
- **H6. Concordancia gramatical.** Recepción de una orden de una línea: «Se registrará el ingreso de las 1 líneas completas…» y «aumentó por las 1 líneas».
- **H7. Referencia de devolución difícil de identificar.** El selector «Venta de referencia» lista fecha, cantidad y SKU sin el código de venta (`VT-…`); las dos líneas de `VT-000001` aparecen con la misma hora.
- **H8. Formatos heterogéneos.** Fechas `28/10/26, 2:22:06 p. m.` junto a vencimiento `28/10/2026`; precio de lista `11.000,5` junto a neto `11.000,50`; mensajes del servidor con y sin «No se confirmó la venta.» y «Línea 1: La unidad…» con mayúscula intermedia.
- **H9. Estado de éxito persistente.** El aviso «Venta VT-000005 confirmada…» seguía visible en Ventas minutos después, tras recorrer otras vistas.
- **H10. (Descartado en la re-prueba; ver Seguimiento.) Cambiar la unidad de una línea de compra borra el precio introducido** (el campo pasa a «Precio unitario por Caja» vacío). Puede ser intencional, porque el precio es por unidad comercial; se registra como observación.

### Corrección durante la sesión

- Una primera medición indicó que «Actualizar» (Ventas, Compras, Precios) no mostraba estado de carga. Era un error del localizador de la prueba, que buscaba el botón por su texto y dejaba de encontrarlo al cambiar a «Actualizando…». Repetida sobre el elemento, la medición muestra el botón deshabilitado, «Actualizando…» y `aria-busy`. Se descarta.

## Escenarios ejecutados

| # | Escenario | Esperado | Obtenido |
|---|---|---|---|
| 1 | Disponibilidad de servicios | 3 servicios sanos | OK |
| 2 | Primer acceso ADMIN, operador y gerente con cambio obligatorio | Bloquea hasta cambiar | OK |
| 3 | Crear sucursal, 3 usuarios, producto con mínimos y unidad Caja (12) | 201 y mensajes | OK |
| 4 | Navegación por perfil | Operador sin Administración, Valoración ni Alertas | OK |
| 5 | Listas de precios: crear, código duplicado | 201 / 409 con mensaje, sin cambios | OK |
| 6 | Precio `-5`, `abc` / `0` / `11000,5` | Rechazo cliente / aceptado (≥ 0) / exacto | OK (ver H4) |
| 7 | Proveedor: crear, duplicado, campo `nit`, código vacío | 201 / 409 / 400 / 400 | OK |
| 8 | Orden: sin proveedor, plazo `-1` y `1.5`, descuento `101` | Rechazo sin orden | OK (ver H3, H4) |
| 9 | Borrador 2 Caja × 120 con 10 % (RF-0023-CA-02) | Neto 216,00, stock sin cambios | OK |
| 10 | Nombre histórico A → B al enviar → C tras enviar (RF-0021-CA-03) | Orden conserva B al recibir | OK |
| 11 | API: editar pendiente, recepción parcial, sin `Idempotency-Key`, admin recibe | 409 / 400 / 400 / 403 | OK |
| 12 | Recepción: Escape no envía; confirmar | Recibida, vence hoy, +24 u, movimiento «Recepción de compra» | OK (ver H6) |
| 13 | Repetir recepción con otra clave; cancelar recibida | 409 sin ingresos | OK |
| 14 | Operador no ve costo por unidad base | Columna ausente, `unitBaseCost: null` | OK |
| 15 | Enviar y cancelar con motivo; recibir cancelada | Motivo en historia, stock sin cambios, 409 | OK |
| 16 | Reemplazo prellenado, edición de borrador (6 × 12), segundo reemplazo | Vinculadas, 72,00, 409 | OK |
| 17 | Idempotencia de creación: misma clave igual / distinta | Mismo resultado / 409 | OK |
| 18 | Cantidad fraccionaria en unidad indivisible; producto no habilitado | 400 / 409 | OK |
| 19 | Valoración tras 2.ª compra 3 × 130 (RF-0026) | 27 u, promedio 22,444444, valor 606 | OK |
| 20 | Valoración: operador / gerente otra sucursal / admin por sucursal | 403 / 403 / datos | OK |
| 21 | Venta: aviso de stock insuficiente en formulario | Aviso visible | OK |
| 22 | Confirmar venta de 39 u con 27 disponibles (RF-0028-CA-02) | 409 total, borrador conservado, sin comprobante | OK |
| 23 | Venta 3 u (10 %) + 1 Caja (RF-0029-CA-02, RF-0031-CA-01) | 13.700,50; comprobante completo | OK |
| 24 | Descuento 100 % / `100,5` / `-1` / 7 decimales | Neto 0 / rechazo / rechazo / rechazo | OK (ver H3) |
| 25 | Dos ventas simultáneas de 2 u con 3 disponibles (RF-0028-CA-03) | Una 201, otra 409, queda 1 | OK |
| 26 | API ventas: repetir clave, clave con otra venta, `unitPrice`, sin clave, cantidad 0 y 0,5, lista sin precio, admin vende | 201 mismo / 409 / 400 / 400 / 400 / 400 / 409 / 403 | OK (ver H5, H8) |
| 27 | Cambiar precio, renombrar producto; consultar `VT-000001` (RF-0031-CA-02) | Comprobante sin cambios | OK |
| 28 | Venta con lista preseleccionada tras cambio de precio | Precio vigente o aviso | **FALLA (H1)** |
| 29 | Venta con lista desactivada | Lista no ofrecida o mensaje de inactiva | **FALLA (H2)** |
| 30 | Respuesta de venta perdida tras confirmarse en el servidor; reintento | Aviso de resultado desconocido; reintento sin duplicar | OK (6 ventas, stock 19) |
| 31 | Borrador de venta al cambiar de vista | Conservado | OK |
| 32 | Ingreso manual con costo justificado (stock 0) | Movimiento y valor 200 | OK |
| 33 | Devolución vinculada: excedida / válida de 2 u | 409 / ingreso a costo histórico 22,444444, comprobante intacto | OK (ver H7) |
| 34 | Desactivar sucursal con existencias | Rechazo, sigue activa | OK |
| 35 | Gerente: compras y ventas solo consulta, filtros por proveedor, producto y estado, costo visible | Sin acciones; resumen por estado | OK |
| 36 | Operador de `DEMO-MED` sobre datos de `E2E-SUR` | 403 consultas, 404 escrituras, sin cambios | OK |
| 37 | Admin: ventas de la red, comprobante demo, valoración por sucursal | Datos correctos | OK |
| 38 | Estados de carga y 500 simulado en Compras y Ventas | Botón deshabilitado, `aria-busy`, alerta | OK |
| 39 | Vista móvil 390 px en Compras, Ventas, Precios y Movimientos | Sin desplazamiento horizontal de página | OK |
| 40 | Regresión H1 del 2026-10-27 (catálogo obsoleto) | Catálogo montado refleja ventas | OK (19,000) |
| 41 | Persistencia en PostgreSQL | Órdenes, ventas, saldo 19 / 212,518518 / 11,185185, operaciones sin duplicar | OK |

## Consola y red

- Todos los errores de consola corresponden a rechazos provocados por las pruebas (400, 403, 404, 409) y al `401` inicial de `/auth/me`, salvo el `404` de `GET /catalog/price-lists/{id}` de H2.
- `DELETE /purchases/orders/{id}` no se pudo observar desde el navegador: la verificación previa CORS no admite `DELETE` y el navegador bloquea la petición. La ausencia de borrado queda cubierta por `PurchaseHttpTest` según la guía de compras.
- Sin advertencias de consola.

## Evidencia de uso de IA

- Herramientas: Claude Code (Opus 5.5) con `playwright-cli`; peticiones a la API desde la sesión del navegador (con cookie y token CSRF) para casos que la interfaz no permite enviar; interceptación de red para retrasos, 500 y respuesta perdida; consultas de solo lectura a PostgreSQL.
- Solicitud del responsable (resumen): pruebas exploratorias E2E con Playwright centradas en RF-0021–RF-0031 y regresión de lo anterior, solo con cuentas y datos de prueba (opción B), sin modificar código ni eliminar información preexistente, con informe priorizado por severidad.
- Validación: H1 se reprodujo contrastando el diálogo, el mensaje de éxito y la base de datos; H2 con la respuesta `404` y el mensaje mostrado. Los demás hallazgos se basan en una observación.
- Corrección durante la sesión: se descartó un falso hallazgo sobre estados de carga (ver arriba).

## Limitaciones

- Exploración manual asistida por IA; no sustituye pruebas automatizadas.
- No se aisló el bloqueo de desactivación por órdenes en borrador (RF-0003-CA-06): la sucursal tenía existencias y el rechazo se produjo por esa regla.
- Errores 500, retrasos y pérdida de respuesta fueron simulados con interceptación de red.
- Transferencias, dashboards y reportes no están implementados; RF-0026-CA-02 no se pudo ejercitar.
- La pila desechable sigue disponible para reproducir los hallazgos hasta que se retire.

## Seguimiento

Correcciones registradas en `odd/tasks/e2e-2026-10-28-fixes.md` (rama `fix/e2e-2026-10-28-findings`) y re-probadas en la misma pila desechable reconstruida:

| Hallazgo | Corrección | Re-prueba |
|---|---|---|
| H1 | Al abrir el formulario se recargan las listas; antes del diálogo se relee la lista elegida y, si un precio cambió, se muestran los precios actuales y se pide revisar el total. Si el servidor confirma un total distinto del estimado, el aviso de éxito lo indica. | Precio cambiado con el formulario abierto: confirmación detenida con «Los precios de la lista Detal E2E cambiaron… (línea 1)» y total 1.700,00; `VT-000007` confirmada por 1.700,00. Al reabrir con la lista preseleccionada: 1.750,00. |
| H2 | Una lista desactivada se informa como «ya no está disponible», sale del selector y el borrador se conserva. | OK. |
| H3 | El descuento admite en el cliente solo 0–100 con hasta 6 decimales; no se abre el diálogo con un total «—». | `100,5`, `150`, `101`, `-1` y 7 decimales bloqueados en ventas y compras. |
| H4 | El navegador muestra la ayuda de cada campo en lugar del mensaje genérico. | «Porcentaje entre 0 y 100…», «Número entero.», «Número entero de días…». |
| H5 | El perfil se comprueba antes de validar el cuerpo en ventas y escrituras de compras. | Administrador y gerente con cuerpo vacío o inválido: `403`. |
| H6 | Concordancia de número en la recepción. | «…de la línea completa…, valorada…»; «aumentó con la línea recibida». |
| H7 | La venta de referencia se identifica por su código. | «Venta VT-000007, 28/10/2026, 2:53:35 p. m.: 1,000 unidad de E2E-ROSA…». |
| H8 | Fechas `dd/mm/aaaa, h:mm:ss a. m.`; precios unitarios con al menos 2 decimales y hasta 6; mensajes de línea «Línea N: <minúscula>…», y en ventas terminados en «No se confirmó la venta.». | `28/10/2026, 2:56:09 p. m.`, `1.750,00`, `11.000,50`, `12,00 por unidad`; mensajes del servidor verificados por API. |
| H9 | Los avisos de éxito de Ventas, Compras y Movimientos se retiran al salir de la vista. | OK. |
| H10 | Sin cambios: no se reproduce. El precio escrito se conserva al cambiar la unidad; la observación inicial fue un artefacto de la prueba. | OK. |
