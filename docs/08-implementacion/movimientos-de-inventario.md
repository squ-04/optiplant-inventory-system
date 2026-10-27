# Probar los movimientos de inventario

Guía de la implementación de RF-0016 (registrar ingresos), RF-0017 (registrar retiros) y RF-0018 (conservar historial auditable), con la valoración de RF-0026 que esos movimientos aplican. No sustituye los requisitos aprobados ni las decisiones INC-009/INC-013; registra rutas, recorrido manual, límites y verificaciones.

## Decisiones aplicadas

- **Servicio único de movimientos.** `InventoryLedger` (`backend/.../inventory/service`) es el único componente que escribe existencias, movimientos y valoración (ARQ PEN-005). Los movimientos manuales lo usan hoy; compras, ventas y transferencias deberán publicar sus operaciones (`PURCHASE_RECEIPT`, `SALE`, `DISPATCH`, `ARRIVAL`) a través de él, sin actualizar saldos por su cuenta. Una venta que necesita guardar el costo aplicado en su línea llama primero a `lock`, que bloquea el saldo y devuelve el promedio vigente hasta el final de la transacción.
- **Permisos (INC-009/D03, INC-003/D01).** El operador registra ingresos ordinarios, devoluciones y retiros o mermas; el gerente, ajustes por conteo y correcciones de entradas. Ambos solo en su sucursal asignada y activa; la sucursal sale siempre de la sesión. El administrador no registra movimientos locales y consulta el historial de cualquier sucursal.
- **Valoración (RF-0026, INC-013).** Promedio ponderado móvil por producto y sucursal, a 6 decimales con redondeo mitad hacia arriba. Un ingreso sin referencia se valora al promedio vigente si hay existencias; sin existencias exige un costo unitario justificado (y lo rechaza si las hay). Retiros y ajustes por conteo consumen el promedio vigente sin cambiarlo. La salida que agota el producto consume el valor restante y registra aparte la diferencia de redondeo (`FINAL_ROUNDING`).
- **Compensaciones vinculadas (H v0.13).** Una devolución con referencia apunta a la salida de una venta, entra a su costo histórico y no supera lo vendido menos lo ya devuelto en neto: una devolución corregida después libera sus unidades (decisión L14). Una corrección apunta a una entrada identificada (recepción de compra, ingreso ordinario o devolución) y solo descuenta unidades registradas de más (decisión L14): las unidades que faltaron entran como un nuevo ingreso, una recepción complementaria de la compra o un ajuste por conteo. Usa la unidad y el costo histórico de la entrada, recalcula el promedio desde el valor corregido y, si agota la cantidad, registra la diferencia de valoración aparte (`HISTORICAL_CORRECTION`). Por decisión del responsable (L12 del documento ODD), si quedan unidades pero el valor resultaría negativo, el valor se lleva a cero, el exceso se registra como la misma diferencia vinculada y el promedio queda en cero hasta la siguiente entrada valorada. Una corrección a la baja no supera lo pendiente de esa entrada.
- **Sin rastro de lo rechazado (RF-0017-CA-03).** Todo se valida antes de escribir; una salida que excede el físico no deja operación, movimiento, cambio de saldo ni registro de idempotencia.
- **Inmutabilidad (RF-0018, INC-006/D04).** La migración V8 retira `UPDATE` sobre operaciones, movimientos y componentes de valoración al rol de ejecución (`DELETE` nunca se concedió).
- **Concurrencia e idempotencia (ARQ-007).** `READ COMMITTED` con bloqueo pesimista del saldo, en orden de producto; las compensaciones del mismo producto se serializan por ese bloqueo. El registro exige la cabecera `Idempotency-Key`: repetir la misma clave con la misma solicitud devuelve el resultado original sin nuevos efectos; con otra solicitud responde `409 IDEMPOTENCY_CONFLICT`. `IdempotencyService` (`common/idempotency`) es reutilizable por otros comandos críticos. CORS admite esa cabecera solo desde el origen configurado del frontend.

## Contrato REST

| Ruta `/api/v1` | Método y finalidad |
|---|---|
| `/inventory/movements` | POST con `Idempotency-Key`: `kind` (`MANUAL_ENTRY`, `SALE_RETURN`, `MANUAL_EXIT`, `COUNT_ADJUSTMENT`, `ENTRY_CORRECTION`), `direction` (`IN`/`OUT`, obligatoria en ajustes por conteo; una corrección solo admite `OUT`), `product` y `unit`, o bien `reference` (UUID del movimiento compensado), `quantity` y `unitCost` como cadenas decimales exactas, y `reason` (hasta 500 caracteres). Responde `201` con el movimiento, el responsable y las existencias resultantes, sin costos. |
| `/inventory/branches/{uuid}/movements` | GET con `product` y `limit` (1–500, por defecto 200) opcionales: movimientos más recientes primero, con fecha, clase, dirección, producto, unidad, cantidades, motivo, responsable, vínculo y cantidad pendiente de compensar. `valuation` solo para gerente y administrador. |

Errores principales: `400 VALIDATION_FAILED`, `UNIT_COST_REQUIRED`, `UNIT_COST_NOT_ALLOWED`, `IDEMPOTENCY_KEY_REQUIRED`; `403 MOVEMENT_FORBIDDEN`, `BRANCH_FORBIDDEN`; `404 PRODUCT_NOT_FOUND`, `PRODUCT_UNIT_NOT_FOUND`, `MOVEMENT_NOT_FOUND`, `BRANCH_NOT_FOUND`; `409 INSUFFICIENT_STOCK`, `COMPENSATION_EXCEEDS_PENDING`, `RETURN_REFERENCE_INVALID`, `CORRECTION_REFERENCE_INVALID`, `PRODUCT_NOT_OPERABLE`, `BRANCH_INACTIVE`, `IDEMPOTENCY_CONFLICT`.

## Recorrido manual

1. Levantar el entorno según [acceso y administración](acceso-y-administracion.md) con datos de demostración. Como administrador, crear un operador y un gerente asignados a `DEMO-BOG` (ambos cambian su contraseña temporal al entrar).
2. Como operador, abrir **Movimientos**. El historial de `DEMO-BOG` muestra la apertura sintética (+10) y la venta sintética (−2) con fecha, responsable, motivo y cantidad, sin columnas de costo (RF-0018-CA-01).
3. Pulsar **Nuevo movimiento**, elegir **Retiro o merma**, `DEMO-PLANT`, cantidad `100` y un motivo. Se rechaza con «excede las existencias físicas disponibles»; el formulario conserva los datos y el historial no cambia (RF-0017-CA-03). Con cantidad `1` se registra y el aviso indica las existencias resultantes (RF-0017-CA-01).
4. Elegir **Devolución de cliente** y, en **Venta de referencia**, la venta sintética (pendiente `2,000`). Devolver `1`: se registra vinculada. Intentar devolver `2` de esa misma venta: se rechaza porque supera lo vendido menos lo devuelto (RF-0016-CA-03).
5. Como gerente, abrir **Movimientos**: aparecen **Costo unitario** y **Efecto en valor**. Elegir **Corrección de una entrada**, la apertura de demostración y cantidad `2`: el historial muestra la corrección vinculada al «Ingreso ordinario del …», y la apertura conserva sus datos originales (RF-0018-CA-03).
6. Elegir **Ajuste por conteo**, **Incrementa** o **Disminuye**, `DEMO-PLANT` y cantidad `1`: cambian solo las existencias de `DEMO-BOG`; **Consultas** de otra sucursal muestra sus cifras sin cambios (RF-0016-CA-02, RF-0017-CA-02).
7. Como administrador, en **Movimientos** elegir una sucursal y **Consultar historial**; no aparece **Nuevo movimiento**.
8. Para comprobar la idempotencia, desconectar la red del navegador antes de **Registrar movimiento**: aparece «No se sabe si el movimiento quedó registrado: envíelo de nuevo sin cambios y no se duplicará». Al reconectar y reenviar sin cambios se registra una sola vez.

## Alcance y límites

- Compras (RF-0024), ventas (RF-0027/RF-0028) y transferencias aún no existen: los motivos «compra» y «venta» de RF-0016-CA-01/RF-0017-CA-01 se ejercitan en las pruebas publicando operaciones directamente en `InventoryLedger`, como lo harán esos módulos. No hay ingreso manual por compra ni venta manual, porque esos documentos generan sus propios movimientos.
- Solo se corrigen entradas identificadas y solo a la baja; un error en un retiro o unidades faltantes se regularizan con un nuevo ingreso o un ajuste por conteo. No hay correcciones exclusivas de precio (RF-0026, límites).
- Cada operación de `InventoryLedger` reevalúa la alerta de stock mínimo de las combinaciones afectadas; ver [alertas de reabastecimiento](alertas-de-reabastecimiento.md).
- El historial no tiene paginación ni filtros de fecha; muestra los 200 movimientos más recientes (hasta 500 con `limit`). La exportación pertenece a reportes (RF-0050–RF-0055).

## Verificaciones

Backend: `InventoryMovementHttpTest` (16 pruebas, incluidas las decisiones L12 y L14: costo justificado y promedio, RF-0026-CA-01/CA-03/CA-04/CA-05, rechazo sin rastro, devolución vinculada con tope, permisos por rol, sucursal inactiva y catálogo, idempotencia, salidas concurrentes, historial y visibilidad de valoración, privilegios de inmutabilidad, preflight CORS) dentro del runner Maven Docker de `backend/README.md`. Frontend: `frontend/test/movements.test.ts` con `pnpm test` y `pnpm build`. Resultados y recorrido en navegador registrados en `odd/tasks/rf-0016-0018.md`.
