# Probar las listas de precios

Guía de la implementación de RF-0030 (gestionar listas de precios), conforme a INC-003/D06, INC-014/D01 y al modelo de datos §6.2 (`catalog_price_list`, `catalog_price_entry`). No sustituye los requisitos aprobados; registra decisiones de implementación, rutas, recorrido manual, límites y verificaciones.

## Decisiones aplicadas

- **Listas globales mantenidas por el administrador (INC-003/D06, INC-014/D01).** Solo el administrador crea listas (código único y nombre), las modifica y las activa o desactiva. No se asignan a sucursales ni a clientes. El gerente y el operador reciben `403 ROLE_FORBIDDEN` en cualquier escritura.
- **Un precio por producto y unidad comercial.** Cada precio pertenece a una unidad de un producto, en COP, como decimal exacto mayor o igual que cero con hasta 18 enteros y 6 decimales (`NUMERIC(24,6)`), sin redondeo. Definir el precio de una unidad que ya lo tiene en la lista lo reemplaza; las demás entradas no cambian. Solo se asignan precios a unidades activas de productos activos.
- **Sin borrado.** El rol de ejecución no tiene `DELETE` sobre las listas ni sobre sus precios (V7). Una lista que no debe usarse se desactiva: conserva sus precios, deja de mostrarse al gerente y al operador y no puede usarse en ventas nuevas (`409 PRICE_LIST_INACTIVE`).
- **Consulta por perfil.** El administrador ve todas las listas; el gerente y el operador solo las activas, que son las que podrán elegir al confirmar una venta. El detalle de una lista inactiva responde `404` a los perfiles locales. Cada precio informa si su producto o su unidad ya no están activos.
- **Precio que toma la venta (RF-0030-CA-01).** `PriceListService.price(lista, unidad)` devuelve el nombre de la lista y el precio bruto vigente de la unidad para que la venta los copie como instantánea (`list_name_snapshot`, `gross_unit_price`). Una unidad sin precio en la lista elegida no se vende con ella (`409 PRICE_NOT_DEFINED`): no hay precio libre fuera del descuento de RF-0029. Cambiar un precio no reexpresa ventas ya confirmadas.
- **Sin efectos de inventario.** Las listas de precios no modifican existencias, costos ni movimientos; el descuento de stock de las ventas corresponderá al servicio único de movimientos (`InventoryLedger`) cuando se implemente RF-0027.

## Contrato REST

| Método y ruta | Perfil | Resultado |
|---|---|---|
| `GET /api/v1/catalog/price-lists` | Todos | Listas por código; perfiles locales, solo activas. `prices` llega `null`. |
| `GET /api/v1/catalog/price-lists/{id}` | Todos | Lista con sus precios por SKU y unidad. |
| `POST /api/v1/catalog/price-lists` | Administrador | `{code, name}`; `201`. Código repetido: `409 PRICE_LIST_CODE_EXISTS`. |
| `POST /api/v1/catalog/price-lists/{id}/update` | Administrador | `{code, name, active}`. |
| `POST /api/v1/catalog/price-lists/{id}/prices` | Administrador | `{product, unit, price}` con `price` como texto (`"99999.5"`); define o reemplaza el precio y devuelve la lista. |

Errores de validación: `400 VALIDATION_FAILED` (campos ajenos, precio negativo, con exponente, coma o más de 6 decimales, referencia que no es UUIDv4). Producto inexistente: `404 PRODUCT_NOT_FOUND`; unidad inexistente o inactiva: `404 PRODUCT_UNIT_NOT_FOUND`; producto inactivo: `409 PRODUCT_INACTIVE`.

## Recorrido manual

1. Inicie sesión como administrador y abra **Precios**. Cree las listas `DETAL` y `MAYOR`.
2. Con `MAYOR` seleccionada, elija un producto, la unidad `caja` y el precio `99999,5`. Guarde: se confirma «Precio definido» y aparece la fila.
3. Pulse **Ver precios** en `DETAL` y defina para la misma caja `120000`. Las dos listas conservan precios distintos.
4. Pulse **Cambiar precio** en la fila: el formulario precarga el valor; guarde `125000`. Se confirma «Precio actualizado» y la fila no se duplica.
5. Edite `MAYOR` y desmarque **Lista activa**. La lista conserva sus precios y se marca «Inactiva».
6. Inicie sesión como operador o gerente y abra **Precios**: solo aparece `DETAL`, con sus precios y sin acciones de mantenimiento.

## Verificaciones

- Backend: `PriceListHttpTest` (6 pruebas) cubre RF-0030-CA-01 con dos listas, reemplazo y precio cero, códigos duplicados, ocultación y bloqueo de listas inactivas, permisos por rol, validaciones y ausencia de `DELETE`.
- Frontend: `test/prices.test.ts` cubre las rutas, el precio exacto como texto y el rechazo de respuestas mal formadas.
- Navegador: `python3 scripts/check_prices_browser.py` levanta una pila Compose desechable, que retira al terminar con su volumen y sus imágenes construidas, y recorre los pasos 1–6 con comprobación en móvil (390 px).

## Límites

- La elección de lista al confirmar una venta, el descuento por línea y el comprobante pertenecen a RF-0027, RF-0029 y RF-0031, aún no implementados; esta entrega deja disponible la resolución del precio para esa venta.
- No se eliminan precios individuales: el modelo no concede borrado al rol de ejecución. Para retirar una lista de las ventas se desactiva.
