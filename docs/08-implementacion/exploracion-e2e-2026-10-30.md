# Informe de exploración funcional E2E — Dashboard y reportes exportables

> Estado: evidencia de exploración generada con asistencia de IA (Claude Code), pendiente de revisión humana. Los hallazgos no son decisiones aprobadas ni alteran el alcance; las correcciones requieren autorización expresa.

- Fecha: 2026-10-30. Herramienta: `playwright-cli` (Chromium headless, cuatro sesiones: administrador, gerente y operador de origen, operador de destino). Commit: `f24cbb0` (main).
- Alcance: RF-0044 a RF-0049 (dashboard) y RF-0050 a RF-0055 (reportes `.xlsx`), más regresión de ventas, compras, devoluciones, transferencias, faltantes, stock mínimo, alertas, sesiones y navegación por todos los módulos.
- Entorno (opción B): pila Compose desechable `optiplant-e2e-f24cbb0`, `.env` generado en el directorio temporal de la sesión, datos demo V100 y cuentas de prueba propias. Sin datos de producción ni el `.env` del responsable.
- Servicios: frontend `http://localhost:63482` (Vite dev, `200`), backend `http://localhost:63481` (`/actuator/health` = UP), PostgreSQL 17.11 sin publicar al host (accedido con `docker compose exec`). Los tres `healthy`. Puertos del proyecto por defecto, `5173` y `8080`; se usaron otros para aislar la pila.
- Datos creados: sucursales `E2E-NORTE`, `E2E-SUR`, `E2E-INACT` y `E2E-INA2` (estas dos desactivadas); usuarios `E2E-OP-N`, `E2E-GER-N`, `E2E-OP-S`, `E2E-GER-S`, `E2E-GER-X` y `E2E-GER-I`; ruta `E2E-N-S`; lista `E2E-DETAL`; proveedor `E2E-PROV`; productos `E2E-DEM-01`–`12`, `E2E-PESO-01`–`03`, `E2E-BOLSA`, `E2E-SIN`, `E2E-MIN-1`–`4`, `E2E-ROT-A`–`C`, `E2E-MOV`, `E2E-TRF-U`, `E2E-TRF-K` y `E2E-BORDE`; ventas, `OC-000001` y transferencias `TR-000001`–`TR-000019`. No se eliminó ni modificó información preexistente.
- Datos alterados por SQL, solo en esta base desechable: fechas retroactivas de ventas, movimientos, transferencias, historia de saldos y registro de `E2E-NORTE` (la API no permite crear hechos pasados), y 10.001 movimientos en junio de 2025 para el tope real de filas.
- Sin cambios de código.

## Resultado general

No se encontraron fallos críticos ni altos, ni errores de cálculo, de permisos o de integración frontend-backend. Todos los criterios de aceptación ejercitados de RF-0044 a RF-0055 se cumplieron. Queda un punto de alcance por decidir (H1) y defectos menores de presentación y robustez.

## Hallazgos priorizados

### Media

**H1. El operador ve importes de venta agregados en el Dashboard: requiere decisión del responsable.**
- Pasos: entrar como `E2E-OP-N`, abrir **Dashboard**, pestaña **Ventas y rotación**. `GET /dashboard/sales-comparison` responde `200`.
- Obtenido: 18 ventas por 231.785,42 COP en octubre, 40.500,00 COP en septiembre y demás meses; la misma vista del gerente.
- Esperado: RF-0007 y la matriz §5.2 conceden al operador un «panel operacional local, sin valoración/costos agregados». UC-019 lo declara actor secundario «para panel local operacional» y solo le excluye la comparativa de red.
- Análisis: el importe de ventas no es literalmente valoración ni costo de inventario, por lo que no es un incumplimiento demostrado; tampoco hay texto que le conceda agregados monetarios. Ningún documento revisado resuelve el caso. Se registra para decidir si el operador conserva los importes o solo ve cantidades. Comparativa y exportación sí están bloqueadas (`403`).

### Baja

- **H2. Comparativa ilegible en parte a 1280 px.** Los códigos del gráfico se recortan por la izquierda («)EMO-BOG», «2E-NORTE»: eje de 72 px). En la tabla el importe se parte a mitad de número («231.785,4 / 2 COP») y el encabezado en «Venta / s». Evidencia: `adm-comparativa.png`.
- **H3. Una respuesta `200` que no es un libro se guarda como `.xlsx` y se anuncia como éxito.** Con una respuesta HTML simulada (como la de un proxy) se descargó `transfers.xlsx` y se mostró «Se descargó transfers.xlsx.». La vista no comprueba el tipo de contenido. El backend real no produce este caso.
- **H4. Demanda fallida conserva las cifras sin avisar.** Con un `500` simulado al reconsultar el mismo mes, el mensaje no indica que lo mostrado pueda estar desactualizado, aviso que sí tiene el listado de transferencias desde la corrección H9 del 2026-10-29. Al cambiar de sucursal o mes las cifras sí se descartan.
- **H5. Textos imprecisos en impacto de transferencias.** `TR-000010` (recepción en 0) dice «Lo recibido ya está en el físico de destino» con 0,000 recibido. Las transferencias ya recibidas conservan «Llegada estimada».
- **H6. Los filtros de Reportes se pierden tras una sesión vencida.** Con la sesión borrada, la vista pide iniciar sesión (mensaje correcto) y al volver regresa a Movimientos y a las fechas por defecto. Solo repite una consulta y el aviso general lo advierte.

### Observaciones (conformes con lo aprobado)

- **Pérdida en tránsito fuera del reporte de movimientos.** `TRANSIT_LOSS` no genera `inventory_movement` (no toca el físico, RF-0039), así que tampoco está en el historial: el reporte coincide con RF-0052-CA-01 y la pérdida se ve en el reporte de transferencias. La etiqueta «Pérdida en tránsito» de `ReportService.MOVEMENT_KINDS` nunca se usa.
- **Mínimo cero con saldo cero.** La regla aprobada los incluye: `E2E-SUR` lista como «próximos a agotarse» todos los productos habilitados que nunca tuvo. Con muchos productos el indicador pierde utilidad.
- **Rotación del mes en curso.** Los productos con historia iniciada dentro del mes aparecen «No calculable»; es la regla aprobada, y en una demostración con datos recientes toda la tabla puede verse así.
- **Fechas futuras en reportes.** Se aceptan (`2026-10-01` a `2027-06-01`); la rotación las rechaza. RF-0055 no lo prohíbe.
- `branch=abc` sigue respondiendo «La solicitud no es válida.»; la interfaz no puede producirlo.

### Descartados tras comprobar

- «0 barras» en el gráfico con movimiento reducido: no se reproduce (3 con y sin la preferencia).
- `TR-000019` «aún activa» tras recibirla: el texto estaba en vistas ocultas; el Dashboard no la muestra.
- «Saltar al contenido» visible: artefacto de la captura de página completa; está fuera de pantalla sin foco.
- `400`/`409` al cambiar el mínimo: error del guion de prueba, que usó el endpoint de habilitación.

## Escenarios ejecutados

| # | Escenario | Esperado | Obtenido |
|---|---|---|---|
| 1 | Disponibilidad de servicios | 3 servicios sanos | OK |
| 2 | RF-0044: julio, agosto, septiembre y octubre de `E2E-NORTE` | Sin datos / incompleto / completo / en curso | OK |
| 3 | RF-0044: sucursal registrada hoy | 3 meses «Sin datos»; octubre «Sin ventas», 0,00 COP | OK |
| 4 | RF-0044: gráfico, leyenda, unidades por producto, caja a unidad base | 2 caja = 24 bolsa; sin sumar unidades | OK |
| 5 | Devolución ligada a venta | Aparte: 1 devolución, no resta importe ni demanda | OK |
| 6 | RF-0045-CA-02: 20 vendidas y promedio 10 | Rotación 2,00 | OK |
| 7 | RF-0045: cero ventas / promedio cero / historia corta | 0,00 / No calculable / No calculable | OK |
| 8 | Rotación: fechas invertidas, vacías, futuras, año 0001 | Mensaje en la vista y `400` | OK |
| 9 | RF-0046: 13 productos con empates | 5 alta, 5 baja, sin repetir; desempate por SKU | OK |
| 10 | RF-0046: 3 kg, 1 producto, mes sin ventas, mes previo al registro | Reparto 2/1; baja vacía con nota; sin datos | OK |
| 11 | Demanda: mes futuro, vacío, `2026-13`, `2026-1`, `abc`, `0001-01` | `400` / mensaje | OK |
| 12 | RF-0047: 8 situaciones de transferencia, origen y destino | Previsión, tránsito, recibido, faltante y pérdida separados | OK (H5) |
| 13 | Excluidas: completa, cerrada con pérdida, rechazada, cancelada | No aparecen | OK |
| 14 | RF-0048: disponible = mínimo, 0 = 0, por encima, agotado | Identifica los que cumplen | OK |
| 15 | RF-0048-CA-02: mínimo de 5 a 6 con stock 6 | Entra al indicador y a alertas (4→5) | OK |
| 16 | RF-0049: mismos datos por sucursal, inactiva y sin datos | Métricas por sucursal; 14,29 % coincide con 1 de 7 | OK (H2) |
| 17 | RF-0049-CA-02: gerente y operador | `403`; sin pestaña | OK |
| 18 | Otra sucursal por parámetro (gerente/operador) en 5 lecturas | `403 BRANCH_FORBIDDEN` | OK |
| 19 | Administrador sin sucursal elegida | Mensaje por pestaña; API `400` | OK |
| 20 | Cambio de sucursal con respuesta lenta | Respuesta tardía descartada | OK |
| 21 | Estados de carga y `500` simulado en lecturas | `aria-busy`, botón, alerta, recuperación | OK (H4) |
| 22 | Estado al cambiar de pestaña y vista | Mes y filtros conservados | OK |
| 23 | RF-0051: los 3 reportes son ZIP OOXML reales | Abren con `openpyxl`; 1 hoja, encabezado inmovilizado, autofiltro | OK |
| 24 | RF-0052: 57 filas = 57 en PostgreSQL; columnas | Sin costos ni claves internas | OK |
| 25 | Clases, signos, documento origen, responsable | Venta, compra, despacho, recepción, retiro, devolución | OK |
| 26 | Texto `=1+1` y producto `=HYPERLINK(...)` | Celdas de texto, 0 fórmulas | OK |
| 27 | RF-0053-CA-01: venta de 3 líneas con descuentos | Una fila por línea; sin total de cabecera | OK |
| 28 | Suma de líneas frente al Dashboard | 231.785,42 = 231.785,42 | OK |
| 29 | RF-0054: 19 líneas, estados, cantidades, tratamiento | Reclamación, Ajuste, Reenvío, alerta abierta, pérdida | OK |
| 30 | Filtro por estado y metadato | Solo ese estado | OK |
| 31 | RF-0055-CA-01: 23:59:59,999 del 29, 00:00 y 23:59:59,999 del 30, 00:00 del 1.º | Solo los 2 interiores, en las 3 categorías | OK |
| 32 | RF-0055-CA-02: inicio > final, faltantes, 12 meses, bisiesto, año 0001 | Rechazo; `2026-01-01`–`2026-12-31` admitido; vista = servidor | OK |
| 33 | RF-0050-CA-03: sin resultados | Libro válido con encabezados y nota; aviso en la vista | OK |
| 34 | Tope real: 10.000 y 10.001 filas | `200` en 0,5 s (662 kB) / `422`, mensaje claro en la vista | OK |
| 35 | RF-0050-CA-04: 11 rutas × 4 identidades | `401` anónimo; `403` operador y otra sucursal | OK |
| 36 | Exportar no cambia datos | Hash de 9 tablas idéntico | OK |
| 37 | Descarga desde Chromium: nombre, filas, cabeceras CORS | `Content-Disposition`, `X-Report-Rows`, `no-store` | OK |
| 38 | Filtros bloqueados al generar; estado no se filtra a Ventas | Bloqueados; URL limpia | OK |
| 39 | `401`, `403`, `422`, `500` vacío y fallo de red simulados | Mensaje y sin archivo | OK |
| 40 | `200` no XLSX simulado | Rechazo | **FALLA (H3)** |
| 41 | Sesión perdida real | Pide iniciar sesión | OK (H6) |
| 42 | Contraseña temporal sin cambiar | `403` | OK |
| 43 | Cambio de rol de un gerente | Sesión revocada: `401` | OK |
| 44 | Gerente de sucursal desactivada | Conserva consulta histórica | OK |
| 45 | Zonas horarias del navegador UTC+14 y UTC−11 | Fechas por defecto de Bogotá | OK |
| 46 | Móvil 375 px: 5 pestañas y Reportes | Sin desplazamiento de página; tablas desplazables | OK |
| 47 | Teclado en pestañas (flechas, Inicio, Fin) | Foco y selección | OK |
| 48 | Venta por interfaz | Dashboard y Demanda se actualizan sin recargar | OK |
| 49 | Transferencia completa por interfaz (`TR-000019`) | Solicitud a recepción; impacto en Dashboard | OK |
| 50 | Compra por API: enviar y recibir; gerente no recibe | Recepción en movimientos; `403` | OK |
| 51 | Reclamación, ajuste, pérdida y reenvío | Reflejados en Dashboard y reporte | OK |
| 52 | Navegación por los 16 destinos del administrador | Sin errores | OK |
| 53 | Menús por perfil | Operador sin Reportes, Cumplimiento, Valoración ni Alertas | OK |
| 54 | Saldos negativos en PostgreSQL | 0 | OK |

## Consola y red

- Todos los errores de consola corresponden a rechazos provocados por las pruebas (`403`, `422`), al `500` simulado y al `401` inicial de `/auth/me`.
- Sin advertencias de consola ni errores de JavaScript. CORS expone `Content-Disposition` y `X-Report-Rows` al origen del frontend.

## Evidencia de uso de IA

- Herramientas: Claude Code (Opus 5.5) con `playwright-cli`; clientes de prueba con sesión y CSRF; `openpyxl` en un entorno virtual temporal para leer los libros; interceptación de red; SQL sobre la base desechable.
- Solicitud del responsable (resumen): pruebas exploratorias E2E con Playwright centradas en RF-0044–RF-0055 y regresión de lo anterior, solo con cuentas y datos de prueba (opción B), sin modificar código ni eliminar información preexistente, con informe priorizado por severidad.
- Validación: los resultados de RF-0045, RF-0046 y RF-0055 se fijaron de antemano; los libros se contrastaron con PostgreSQL. H2 se basa en la captura y no en una medición del DOM. H3 y H4 usan respuestas simuladas. Las capturas y descargas quedaron en el directorio temporal de la sesión, fuera del repositorio.

## Limitaciones

- Exploración asistida por IA; no sustituye pruebas automatizadas.
- **El cambio de mes no se ejercitó:** el reloj del servidor no se alteró. Los meses cerrados se simularon con fechas retroactivas, no con el paso real al 1 de noviembre.
- Las fechas retroactivas no recorren el flujo real; RF-0045 se comprobó con historia preparada así.
- Solo Chromium; los libros no se abrieron en Excel en esta sesión (sí el 2026-10-30, antes, junto con Safari).
- No se probaron descargas concurrentes de 10.000 filas ni la memoria sostenida.
- `E2E-GER-N` usó reportes con filas masivas de `E2E-MOV`; ese producto no sirve para rotación anterior a junio de 2025 en esta pila.
- La pila desechable sigue disponible para reproducir los hallazgos hasta que se retire.

## Seguimiento

Decisión del responsable del 2026-10-30: el operador no ve importes agregados (H1) y se corrigen H2 a H5. H6 y las observaciones no se modifican. La re-prueba se hizo en la misma pila desechable reconstruida.

| Hallazgo | Corrección |
|---|---|
| H1 | `GET /dashboard/sales-comparison` añade `amountsIncluded` y devuelve `netAmount: null` al operador. Su vista no muestra gráfico ni columna de importes, y conserva ventas, unidades y devoluciones. Solo administrador y gerente reciben importes. |
| H2 | El eje del gráfico se ajusta al código más largo (máximo 160 px); importes y número de ventas no se parten. |
| H3 | Una respuesta `200` sin el tipo de un libro de Excel es un error y no se guarda ningún archivo. |
| H4 | Un fallo de lectura con cifras en pantalla avisa que pueden estar desactualizadas en todas las lecturas del Dashboard. |
| H5 | Una recepción en 0 dice «No llegó mercancía»; la llegada estimada solo se muestra en tránsito. |
