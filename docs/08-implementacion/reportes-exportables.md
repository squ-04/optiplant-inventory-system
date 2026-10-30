# Reportes exportables: prueba manual

> Estado: implementado para RF-0050–RF-0055 (UC-020). No sustituye los requisitos aprobados ni INC-020/D01–D10.

Funcionalidad adicional seleccionada: descarga directa de libros Excel `.xlsx` reales. El enunciado (§4) y la decisión INC-020/D02 fijan exactamente tres categorías: movimientos de inventario, ventas y transferencias. No hay PDF, tareas asíncronas, repositorio de archivos ni tablas propias.

## Permisos y filtros

| Perfil | Alcance |
|---|---|
| Administrador general | Una sucursal o toda la red. |
| Gerente | Solo su sucursal; otra responde `403 BRANCH_FORBIDDEN`. |
| Operador | No exporta (`403 ROLE_FORBIDDEN`) y no ve la vista. |

| Reporte | Endpoint `GET /api/v1/reports/…` | Fecha del filtro | Filas | Filtros adicionales |
|---|---|---|---|---|
| Movimientos | `movements` | Registro | Una por movimiento | `branch` |
| Ventas | `sales` | Confirmación | Una por línea | `branch` |
| Transferencias | `transfers` | Solicitud | Una por línea | `branch`, `status` |

`from` y `to` (`AAAA-MM-DD`) son obligatorias e inclusivas en `America/Bogota`. El fin exclusivo puede llegar hasta el inicio más 12 meses calendario: `2026-01-01` a `2026-12-31` se admite; `2026-01-01` a `2027-01-01` no.

## Contrato de la descarga

- Éxito: `200`, MIME `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition: attachment`, `Cache-Control: no-store` y `X-Report-Rows` con el número de registros.
- Período inválido o superior a 12 meses: `400 VALIDATION_FAILED`.
- Más de 10.000 filas: `422 REPORT_TOO_LARGE`, sin archivo truncado.
- Sin resultados: `200` con libro válido, encabezados, `Filas: 0` y la nota «Sin resultados: no hay registros para los filtros indicados.».

## Contenido del libro

Una hoja con título, metadatos (sucursal, período, fecha del filtro, zona horaria, estado en transferencias, generación y filas), encabezado inmovilizado con filtros y una fila por registro.

| Reporte | Columnas |
|---|---|
| Movimientos | ID público del movimiento, Fecha de registro, Sucursal, SKU, Producto, Clase, Motivo, Cantidad (con signo, como el historial), Unidad, Usuario responsable, Documento origen. Sin costos. |
| Ventas | Número de venta, Fecha de confirmación, Sucursal, Responsable, SKU, Producto, Unidad, Cantidad, Precio unitario (COP), Descuento (%), Importe neto de línea (COP). Sin total de cabecera. |
| Transferencias | Número de transferencia, Origen, Destino, Fecha de solicitud, Fecha de despacho, Fecha de recepción, Estado, SKU, Unidad, Cantidad solicitada, Cantidad despachada, Cantidad recibida, Diferencia pendiente, Tratamiento. |

- Identificadores: UUID público en movimientos, que no tienen número comercial; número comercial en ventas y transferencias (DEC-TEC-ID-001 §9.5). Nunca claves primarias.
- **Diferencia pendiente:** despachado menos recibido desde la llegada; `0` cuando el tránsito ya se dio de baja. **Tratamiento:** solo en líneas con diferencia; indica la decisión registrada, nunca «Resuelto», que no prueba una recepción completa.
- Texto de usuario siempre en celdas de texto: un motivo que empiece por `=` no se evalúa como fórmula.
- Cantidades y precios en celdas numéricas cuando caben con exactitud en 15 dígitos significativos; si no, en texto exacto. Fechas como fechas reales de Excel en hora de Bogotá.
- El importe neto de línea usa la precisión de presentación de 2 decimales (INC-013/D03). Por el redondeo, sumar muchas líneas puede diferir en centavos del total exacto de la venta.

## Reutilización

`reports` no consulta tablas: cada módulo operativo publica su lectura (`MovementHistoryService.exportRows`, `SaleQueryService.exportLines`, `TransferQueryService.exportLines`). `ReportService` centraliza permisos, límites y metadatos; una categoría solo declara columnas y lectura. `XlsxReportWriter` escribe cualquier `ReportTable`.

Dependencia: `org.apache.poi:poi-ooxml` 5.5.1 (ARQ-010). Se usa `XSSFWorkbook`: la prueba exporta 10.000 filas; no se midió la memoria con volumen sostenido, por lo que `SXSSFWorkbook` sigue sin justificarse.

## Recorrido manual

1. Con `docker compose up --build --wait`, entre como gerente y abra **Reportes**. Debe ver las tres categorías y ningún selector de sucursal.
2. Elija **Ventas** y pulse **Descargar Excel**. Abra el archivo: una fila por línea, sin total de cabecera.
3. Elija **Transferencias** y filtre por **Recibidas con discrepancia**. Compruebe cantidades, diferencia y tratamiento.
4. Use fechas de 2020 sin datos: se descarga un libro vacío con su nota y la vista lo avisa.
5. Use un rango de más de 12 meses: la vista lo rechaza y no se descarga nada.
6. Entre como administrador: **Sucursal** ofrece **Toda la red** y cada sucursal; descargue **Movimientos** de la red.
7. Entre como operador: **Reportes** no aparece, y `GET /api/v1/reports/sales` responde `403`.

## Verificación automatizada

`ReportExportHttpTest`, `ReportWorkbookTest`, `frontend/test/reports.test.ts` y `scripts/check_reports_browser.py`, este último contra una pila Compose desechable.
