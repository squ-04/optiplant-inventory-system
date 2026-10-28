# Probar las ventas y sus comprobantes

Guía de la implementación de RF-0027 (registrar ventas), RF-0028 (validar stock antes de vender), RF-0029 (aplicar descuentos de venta) y RF-0031 (consultar comprobante o registro de venta), conforme a INC-014/D01–D06 y al modelo de datos (`sales_sale`, `sales_line`, `inventory_operation.sale_id`). No sustituye los requisitos aprobados; registra decisiones de implementación, rutas, recorrido manual, límites y verificaciones.

## Decisiones aplicadas

- **Solo la venta confirmada existe en el servidor (INC-014/D03).** El modelo no tiene estado de borrador: el borrador vive únicamente en el formulario del operador, se conserva al cerrar el formulario o cambiar de vista y nunca llega al servidor. Un borrador o un intento rechazado no produce venta, comprobante ni movimiento.
- **Retiro mediante el servicio único de movimientos.** La confirmación no modifica existencias por su cuenta: publica una operación `SALE` en `InventoryLedger`, vinculada a la venta, con una salida por línea al costo promedio vigente (RF-0026). El costo aplicado se guarda en `sales_line.historical_base_cost` y nunca se devuelve en la API (INC-003/D04).
- **Validación indivisible de existencias (RF-0028, INC-014/D04).** En una sola transacción se resuelven todas las líneas, se bloquean los saldos de los productos vendidos en orden estable y se compara el disponible físico (sin reservas) con la suma de todas las líneas de cada producto. Si alguna no alcanza, se rechaza la venta completa (`409 INSUFFICIENT_STOCK`) sin escritura alguna. Los bloqueos serializan ventas simultáneas del mismo producto: dos confirmaciones de 2 unidades sobre 3 disponibles confirman solo una.
- **Precio de lista y descuento por línea (RF-0029, INC-014/D01–D02).** El operador elige una lista activa al confirmar y la venta la aplica a todas sus líneas; el precio bruto de cada unidad se toma de esa lista y no se envía desde el cliente (un campo `unitPrice` se rechaza). El descuento es un porcentaje por línea entre 0 y 100 con hasta 6 decimales; no existe descuento de cabecera ni acumulación. Neto unitario = precio × (1 − descuento/100): 100 con 10 % da 90. El neto de línea se guarda exacto (`NUMERIC(51,17)`) y se muestra a 2 decimales, mitad hacia arriba.
- **Doble confirmación (RF-0027-CA-02).** `POST /sales` exige `Idempotency-Key`: repetir la misma clave devuelve el comprobante original sin otro retiro; la misma clave con otra venta responde `409 IDEMPOTENCY_CONFLICT`. La interfaz conserva la clave mientras el resultado sea desconocido, también si el operador edita o descarta el borrador, para que un reintento nunca venda dos veces; si el servidor responde `IDEMPOTENCY_CONFLICT`, informa que la confirmación anterior sí quedó registrada, recarga los comprobantes y usa una clave nueva desde entonces.
- **Comprobante interno inmutable (RF-0031, INC-014/D06).** Cada venta conserva identificador público, código `VT-000001`, fecha, sucursal, responsable y líneas con SKU, producto, unidad, factor, cantidad, lista, precio de lista, descuento, neto y total como instantáneas. La migración V10 retira `UPDATE` al rol de ejecución sobre `sales_sale` y `sales_line` (nunca tuvo `DELETE`) y asigna el código desde una secuencia al insertar. Cambios posteriores de catálogo o precios no alteran el comprobante. No es factura fiscal.
- **Devoluciones.** Cada línea del comprobante informa la salida de inventario de la venta (`movementExternalId`). La devolución existente (`SALE_RETURN` en Movimientos) la referencia, ingresa al costo histórico y deja la venta original sin cambios ni reembolso (INC-014/D05).
- **Perfiles (§5.2).** Solo el operador de una sucursal activa confirma ventas; el gerente y el administrador reciben `403 SALE_FORBIDDEN`. El operador y el gerente consultan los comprobantes de su sucursal; el administrador, los de una sucursal o de toda la red.

## Contrato REST

| Método y ruta | Perfil | Resultado |
|---|---|---|
| `POST /api/v1/sales` | Operador | `{priceList, lines: [{product, unit, quantity, discountPercent}]}` con decimales como texto y cabecera `Idempotency-Key`; `201` con el comprobante. |
| `GET /api/v1/sales?branch=&limit=` | Todos | Comprobantes más recientes primero; `total` cuenta todas las ventas del ámbito. Sin `branch`, el administrador consulta la red y los perfiles locales su sucursal. |
| `GET /api/v1/sales/{id}` | Todos, dentro de su ámbito | Comprobante con líneas y totales (`grossTotal`, `discountTotal`, `totalNet`, `exactTotalNet`). |

Errores: `400 VALIDATION_FAILED` (campos ajenos, `unitPrice`, descuento fuera de 0–100, cantidad no positiva o fraccionaria en unidad indivisible), `400 IDEMPOTENCY_KEY_REQUIRED`, `404 PRODUCT_NOT_FOUND` / `PRODUCT_UNIT_NOT_FOUND` / `PRICE_LIST_NOT_FOUND` / `SALE_NOT_FOUND`, `409 PRODUCT_NOT_OPERABLE`, `PRICE_LIST_INACTIVE`, `PRICE_NOT_DEFINED`, `INSUFFICIENT_STOCK`, `BRANCH_INACTIVE`. Los errores de una línea indican su número. No hay rutas de edición ni `DELETE` (`405`).

## Recorrido manual

1. Como administrador, cree la lista `DETAL` en **Precios** con un precio para la unidad base y otro para `caja` de un producto habilitado en la sucursal.
2. Como operador, registre en **Movimientos** un ingreso ordinario de 10 unidades.
3. Abra **Ventas** y pulse **Nueva venta**. Elija `DETAL`, el producto, 3 unidades y descuento `10`: la línea muestra el precio de lista, el neto unitario y el neto estimado, y el disponible de la sucursal.
4. Agregue una línea de 1 caja del mismo producto: aparece el aviso de existencias insuficientes. Quítela.
5. Pulse **Confirmar venta** y acepte el diálogo. Se informa la venta confirmada y se abre su comprobante; el disponible baja a 7.
6. Intente vender 8 unidades: el servidor rechaza la venta completa, el borrador se conserva y no aparece otro comprobante.
7. Como gerente de la sucursal (o administrador), abra **Ventas** y pulse **Ver comprobante**: sin acción de vender.
8. Opcional: en **Movimientos**, registre una devolución que referencie la salida de la venta; el comprobante no cambia.

## Límites

- Una lista por venta, aplicada a todas sus líneas; el esquema admite lista por línea, sin uso en esta entrega.
- Sin cobro, impuestos, clientes ni factura fiscal. La exportación de ventas corresponde a RF-0053.
- El aviso de existencias del formulario usa el catálogo local consultado y puede estar desactualizado; el servidor decide al confirmar.

## Verificaciones

- Backend: `SaleHttpTest` (9 pruebas) cubre RF-0027-CA-01/02, RF-0028-CA-01/02/03 (confirmaciones simultáneas), RF-0029-CA-02, RF-0031-CA-01/02, totales mostrados coherentes (subtotal − descuentos = neto aunque los importes exactos tengan más de 2 decimales), permisos, lista inactiva o sin precio, producto no habilitado, sucursal inactiva, inmutabilidad en base de datos y ausencia de `DELETE`.
- Frontend: `test/sales.test.ts` cubre el neto unitario, el descuento mostrado, la conservación de la clave ante resultados desconocidos, la suma de líneas por producto frente al disponible, las rutas con `Idempotency-Key` y el rechazo de respuestas mal formadas.
- Navegador: `python3 scripts/check_sales_browser.py` levanta una pila Compose desechable, que retira al terminar con su volumen y sus imágenes construidas, y recorre los pasos 1–7 con comprobación en móvil (390 px).
