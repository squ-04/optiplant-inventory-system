# Probar el stock mínimo y las alertas de reabastecimiento

Guía de la implementación de RF-0019 (controlar stock mínimo) y RF-0020 (alertar reabastecimiento), conforme a INC-010/D01–D05 y al modelo de datos §6.7 (`inventory_reorder_alert`). No sustituye los requisitos aprobados; registra decisiones de implementación, rutas, recorrido manual, límites y verificaciones.

## Decisiones aplicadas

- **Condición única (INC-010/D03).** Una combinación producto-sucursal requiere reabastecimiento cuando su disponible (igual al físico, glosario §5.3) es menor o igual que su mínimo explícito; con mínimo cero, a saldo cero. `ReorderAlertService.requiresRestock` es la única implementación y la reutilizará el indicador de próximos a agotarse (RF-0048). El mínimo explícito ya se exige al habilitar (RF-0001, INC-010/D02); sin habilitación no hay operación ni alerta.
- **Un episodio activo con historia (INC-010/D05).** El índice único parcial de V4 garantiza una sola alerta activa por combinación. Al abrirse guarda el mínimo y el disponible del momento; se resuelve sola cuando el disponible supera estrictamente el mínimo y la fila se conserva. Volver a cumplir la condición abre un episodio nuevo. No hay botón «Resuelto», correo, SMS ni compra automática.
- **Reevaluación tras cambios relevantes.** En la misma transacción del cambio: cada operación de `InventoryLedger` (movimientos manuales hoy; compras, ventas y transferencias cuando publiquen por él), el cambio de mínimo y la habilitación. La desactivación del producto o de la sucursal cierra los episodios porque la combinación deja de ser operable, no porque se haya repuesto. La evaluación bloquea la fila `catalog_branch_product`, de modo que cambios concurrentes de existencias y mínimo se evalúan en serie.
- **Reconciliación al arrancar.** Al iniciar, el backend evalúa todas las combinaciones para alinear datos cargados antes de esta entrega, como la semilla de demostración. Es idempotente: una base consistente no cambia. No se añadió migración.
- **Destinatarios (INC-010/D04).** El gerente ve las alertas de su sucursal y el administrador las de cualquier sucursal o de toda la red, con un aviso persistente mientras haya alertas activas. El operador no consulta alertas: ve el indicador «Requiere reabastecimiento» en el catálogo de su sucursal.
- **Actualización.** La vista y el aviso consultan por polling REST con el mismo intervalo y tiempo máximo que el inventario (ARQ PEN-003, RF-0015); la consulta por defecto se comparte entre aviso y vista, de modo que se lee una sola vez.

## Contrato REST

| Ruta `/api/v1` | Método y finalidad |
|---|---|
| `/inventory/reorder-alerts` | GET con `branch` (UUID), `status` (`ACTIVE` por defecto o `ALL` para el historial) y `limit` (1–500, por defecto 200) opcionales. Responde `readAt`, `branch` (nulo si el administrador consulta toda la red), `activeCount` (todas las activas del ámbito, sin aplicar `limit`) e `items`: activas primero y más recientes primero, con `status`, sucursal, producto, `openedAt`, `resolvedAt`, `minimumAtOpening`, `quantityAtOpening`, `currentMinimum` (nulo si ya no está habilitado) y `currentQuantity`, como cadenas decimales exactas en unidad base. |
| `/products/local` | Cada producto añade `restockAlert`: existe una alerta activa en la sucursal de la sesión. |

Errores: `400 VALIDATION_FAILED` (estado o límite inválidos, referencia no UUIDv4); `401` sin sesión; `403 ROLE_FORBIDDEN` (operador), `BRANCH_FORBIDDEN` (gerente que consulta otra sucursal); `404 BRANCH_NOT_FOUND`.

## Recorrido manual

1. Levantar el entorno según [acceso y administración](acceso-y-administracion.md) con datos de demostración. Como administrador, crear la sucursal `SUR`, un operador y un gerente asignados a ella (ambos cambian su contraseña temporal al entrar).
2. Como administrador, en **Catálogo → Nuevo producto**, crear un producto habilitado en `SUR` con stock mínimo `5`. Con existencias cero ya cumple la condición (RF-0019-CA-03).
3. Como operador de `SUR`, abrir **Catálogo → Mi sucursal**: la columna **Reabastecimiento** muestra «Requiere reabastecimiento». No aparece la sección **Alertas** ni el aviso de gerencia.
4. Como gerente de `SUR`: el aviso «1 producto en su sucursal está en su stock mínimo o por debajo.» permanece al cambiar de sección y **Alertas** muestra el conteo. En **Ver alertas de reabastecimiento** la fila indica disponible `0,000`, mínimo vigente `5,000`, «Al activarse» y estado «Activa» (RF-0020-CA-01).
5. Con la vista del gerente abierta, el operador registra en **Movimientos** un ingreso de `5`: la alerta sigue siendo una sola (igualdad, RF-0020-CA-02). Un ingreso de `1` más (disponible `6`) la resuelve sin pulsar nada y el aviso desaparece; en **Historial completo** figura como «Resuelta» con su fecha.
6. Como administrador, cambiar el mínimo de `SUR` a `6`: se abre un episodio nuevo con mínimo `6,000` (RF-0019-CA-02). **Alertas** con «Todas las sucursales» lo muestra con la columna **Sucursal**; elegir `SUR` filtra la consulta.
7. Un retiro del operador que deje el disponible en `6` o menos mantiene o abre la alerta; desactivar el producto (sin existencias) o la sucursal la cierra y conserva su historia.

`scripts/check_restock_browser.py` automatiza los pasos 2–6 y dos regresiones de la exploración E2E (seis escenarios): la confirmación de baja con el diálogo compartido (Escape y «Cancelar» no envían la baja y devuelven el foco; la acción desactiva una sola vez) y la actualización de «Mi sucursal» tras registrar un movimiento, sin recargar el navegador. Usa `playwright-cli` en una pila Compose desechable con nombre único y datos de demostración, que elimina al terminar con su volumen y sus imágenes construidas. No imprime credenciales; los diagnósticos privados quedan en `result.log` del directorio temporal indicado, que quien ejecuta debe retirar.

## Alcance y límites

- El indicador del catálogo local se lee al cargar el catálogo; no se actualiza por polling.
- La alerta logística de faltante de transferencia (INC-010/D06, RF-0038) es independiente y no forma parte de esta entrega. El indicador de próximos a agotarse del dashboard (RF-0048) reutilizará la misma condición.
- El historial no tiene paginación ni filtros de fecha: muestra hasta 200 episodios (500 con `limit`).

## Verificaciones

Backend: `ReorderAlertHttpTest` (4 pruebas: umbral inclusivo y resolución estricta con historia, mínimo modificado y mínimo cero, cierre por desactivación de producto y sucursal, permisos y validaciones), más `ProductConfigurationHttpTest` y `ProductHttpTest` actualizadas, dentro del runner Maven Docker de `backend/README.md`. Frontend: `frontend/test/alerts.test.ts` y `catalog.test.ts` con `pnpm test` y `pnpm build`. Resultados y recorrido en navegador registrados en `odd/tasks/rf-0019-0020.md`.
