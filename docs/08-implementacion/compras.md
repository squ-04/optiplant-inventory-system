# Probar las compras: proveedores, órdenes, recepción e histórico

Guía de la implementación de RF-0021 (crear órdenes de compra), RF-0023 (registrar condiciones de compra), RF-0024 (actualizar inventario al recibir compras) y RF-0025 (consultar histórico de compras), conforme a INC-011/D01–D06, INC-012/D01–D05, H v1.3/02 y al modelo de datos §6.3. No sustituye los requisitos aprobados; registra decisiones de implementación, rutas, recorrido manual, límites y verificaciones.

## Decisiones aplicadas

- **Proveedor mínimo (RF-0021, INC-012/D01–D02).** Código único y nombre, creados y modificados solo por el administrador. La API rechaza cualquier otro campo (NIT, correo, datos fiscales). Un código duplicado se rechaza con `SUPPLIER_CODE_EXISTS` y el catálogo no cambia. Todos los perfiles consultan la lista para usarla.
- **Orden en borrador (RF-0021, RF-0023).** El operador crea la orden en su sucursal activa, con el proveedor y al menos una línea: producto activo y habilitado en la sucursal, unidad comercial activa, cantidad, precio unitario bruto por unidad comercial y descuento porcentual explícito entre 0 y 100. El plazo de pago es un número entero de días ≥ 0. Se valida todo antes de escribir: una línea rechazada no deja ninguna orden. Crear la orden no modifica existencias ni genera pagos. El neto de línea se guarda exacto, `cantidad × precio × (1 − descuento/100)`, sin redondeo (§5.1).
- **Paso a pendiente de recepción (RF-0021-CA-03, H v1.3/02).** El borrador toma el nombre vigente del proveedor; al pasar a pendiente se actualiza con el nombre vigente en ese momento y desde entonces queda congelado, también al recibir. En la misma transición se revalidan las líneas contra el catálogo y se fijan sus instantáneas (SKU, nombre, unidad, factor y equivalente base).
- **Recepción completa por el servicio de inventario (RF-0024, ARQ PEN-005).** La recepción de una orden pendiente publica una sola operación `PURCHASE_RECEIPT` en `InventoryLedger`, vinculada a la orden, con una entrada por línea vinculada a `purchases_line`. No existe otro mecanismo que modifique existencias. Cada entrada se valora a `precio × (1 − descuento/100) / factor`, en una sola división a 6 decimales HALF_UP (§5.1), y ese costo queda guardado en la línea. El promedio ponderado (RF-0026), los saldos y la reevaluación de alertas (RF-0020) los aplica el propio servicio. Si un producto ya no es operable, se rechaza la recepción completa y no se recibe ninguna línea.
- **Vencimiento (INC-012/D04).** Es la fecha de la recepción confirmada en `America/Bogota` más el plazo; con plazo cero vence el mismo día. Como límite técnico, el plazo admite hasta 36500 días, para que la suma de fechas nunca desborde (§6.3).
- **Confirmación repetida (INC-011/D04).** Repetir la petición con la misma `Idempotency-Key` devuelve el resultado original. Una petición nueva sobre una orden ya recibida se rechaza con `409 PURCHASE_ALREADY_RECEIVED`. En ningún caso se registra otro ingreso ni cambia el valor.
- **Sin recepción parcial (INC-011/D02).** La recepción no admite datos en el cuerpo: enviar líneas o cantidades responde `400 PARTIAL_RECEIPT_NOT_SUPPORTED` y no modifica ni la orden ni el inventario.
- **Histórico (RF-0025, INC-011/D06).** Se consulta por proveedor y por producto, y opcionalmente por estado. Incluye borradores, pendientes, recibidas y canceladas, con un resumen de conteos por estado calculado en el servidor. El resumen distingue las órdenes de las compras recibidas. El gerente y el operador consultan su sucursal; el administrador, una sucursal o toda la red.
- **Permisos (§5.2).** Solo el operador de la sucursal registra, envía y recibe órdenes. El gerente y el administrador reciben `403 PURCHASE_FORBIDDEN`. Una orden de otra sucursal responde `404` a las escrituras y `403` a la consulta, sin revelar datos.
- **Regla compartida.** `OperableUnits` (catálogo) resuelve el producto operable y su unidad activa para los movimientos manuales y las compras; no hay dos implementaciones de la misma regla.
- **Sin migración.** Las tablas V2/V4/V6 ya contenían proveedores, órdenes, líneas, eventos de estado y los vínculos con inventario.

## Contrato REST

| Método y ruta | Perfil | Resultado |
|---|---|---|
| `GET /api/v1/purchases/suppliers` | Todos | Proveedores por código. |
| `POST /api/v1/purchases/suppliers` | Administrador | `201`; cuerpo `{code, name}`. |
| `POST /api/v1/purchases/suppliers/{id}/update` | Administrador | `200`; cuerpo `{code, name}`. |
| `POST /api/v1/purchases/orders` | Operador | `201`, orden `DRAFT`. Cuerpo `{supplier, paymentTermDays, lines:[{product, unit, quantity, unitPrice, discountPercent}]}`, decimales como texto. Requiere `Idempotency-Key`. |
| `POST /api/v1/purchases/orders/{id}/submit` | Operador | `200`, orden `PENDING`. Cuerpo vacío o `{}`. Requiere `Idempotency-Key`. |
| `POST /api/v1/purchases/orders/{id}/receive` | Operador | `200`, orden `RECEIVED` con costo por unidad base, vencimiento y operación de inventario. Cuerpo vacío o `{}`. Requiere `Idempotency-Key`. |
| `GET /api/v1/purchases/orders?branch=&supplier=&product=&status=&limit=` | Todos, según ámbito | Histórico, del más reciente al más antiguo, con `summary` por estado. Límite por defecto 100, máximo 500. |
| `GET /api/v1/purchases/orders/{id}` | Todos, según ámbito | Orden con líneas, condiciones e historia de estados. |

Los importes se devuelven a 2 decimales (`netAmount`, `totalNet`) y exactos (`exactNetAmount`, `exactTotalNet`). `supplier.name` es el nombre histórico que conserva la orden.

## Recorrido manual

1. Con el administrador, abra **Compras › Proveedores**, cree `PRV-VALLE` «Viveros del Valle» e intente crear otro con el mismo código: se rechaza y la lista no cambia.
2. En el catálogo, dé de alta un producto con unidad base `unidad` indivisible, habilítelo en una sucursal y agréguele la unidad `caja` con factor 12.
3. Con el operador de esa sucursal, abra **Compras › Nueva orden de compra**: proveedor `PRV-VALLE`, plazo 0, 2 `caja` a 120 con descuento 10. El neto estimado muestra 216,00 COP. Pulse **Crear borrador** y compruebe que las existencias del catálogo no cambian.
4. Como administrador, renombre el proveedor a «Viveros B». Con el operador, abra el detalle de la orden y pulse **Enviar a recepción**: el proveedor aparece como «Viveros B». Renómbrelo de nuevo: la orden conserva «Viveros B».
5. Pulse **Confirmar recepción**. El diálogo explica que entran todas las líneas; Escape o Cancelar no envían nada. Al confirmar: la orden queda «Recibida», con costo 9,000000 por unidad y vencimiento en la fecha de hoy. **Movimientos** muestra «Recepción de compra» +2,000 caja (24 unidades) con el operador como responsable; en el catálogo, el físico sube en 24.
6. Repita la recepción desde la API con otra clave: responde `409 PURCHASE_ALREADY_RECEIVED` y las existencias no cambian.
7. Con el gerente, abra **Compras**: no puede crear ni recibir órdenes; filtre por producto y por proveedor y compruebe los estados y el resumen. Con el administrador, consulte toda la red o una sucursal.

## Alcance y límites

- Editar borradores, cancelar órdenes y crear órdenes de reemplazo pertenecen a RF-0022, que no forma parte de esta entrega. El histórico ya muestra e identifica las órdenes canceladas cuando existan.
- Como no hay cancelación, una sucursal con borradores o pendientes no puede desactivarse hasta recibirlos (RF-0003-CA-06 sigue aplicándose).
- No se implementan pagos, saldos por pagar, impuestos, fletes ni otras monedas (INC-012/D05, INC-019/D01). Tampoco la desactivación de proveedores: la columna `active` existe y se respeta al crear órdenes, pero no tiene operación.
- El código de orden `OC-` más el identificador interno con seis dígitos es una convención técnica de esta entrega.

## Verificaciones

- Backend: `PurchaseHttpTest` (9 escenarios: proveedores, creación y condiciones, validaciones sin efectos, nombre histórico, recepción por el servicio de inventario con valoración y vencimiento, repetición, recepción parcial o bloqueada, permisos e histórico) y suite completa con el runner Docker de `backend/README.md`.
- Frontend: `pnpm test` (`test/purchases.test.ts`: neto exacto, redondeo de presentación, acciones por perfil, plazo y contrato del cliente) y `pnpm build`.
- Navegador: `python3 scripts/check_purchases_browser.py` levanta una pila Compose desechable y recorre con Playwright el alta de proveedores, la orden con recepción, el histórico del gerente y la vista de red en móvil (390 px).
