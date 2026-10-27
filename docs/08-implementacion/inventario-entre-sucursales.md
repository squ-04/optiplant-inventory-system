# Probar el inventario entre sucursales

Guía de la implementación de RF-0014 (consultar inventario de otra sucursal) y RF-0015 (compartir información de inventario). No sustituye los requisitos aprobados ni ARQ-PEN-003; registra rutas, recorrido manual, límites y verificaciones.

## Decisiones aplicadas

- RF-0014 y matriz §5.2: administrador, gerente y operador consultan las existencias de cualquier sucursal, incluidas las inactivas, sin escritura cruzada ni valoración/costos. La respuesta identifica la sucursal y separa físico, disponible y tránsito (glosario §5.3). Disponible coincide con físico porque no existen reservas.
- Filas mostradas: productos con saldo en la sucursal, productos habilitados y activos en su catálogo (aunque tengan cero) y productos con tránsito pendiente hacia ella. El tránsito no se suma al físico ni al disponible.
- RF-0015 y ARQ-PEN-003: polling REST con TanStack Query 5.102.6 cada dos segundos solo mientras la vista **Consultas** está visible; reconsulta al recuperar foco, conexión o visibilidad. El polling usa GET, que nunca cuenta como actividad interactiva de la sesión.
- RF-0015-CA-02: si falla la comunicación o el servidor responde con un error 5xx (por ejemplo, base de datos no disponible), la tabla conserva la última lectura, sigue reintentando y muestra una advertencia de posible desactualización; no se afirma ninguna operación confirmada. Un rechazo del cliente (sesión, permiso, sucursal inexistente: 4xx) detiene el polling, sustituye el texto de actualización automática por un aviso de detención y se informa en el contenedor de la aplicación.
- Cada lectura se cancela a los 5 segundos sin respuesta (meta de RNF-REN-01) y se trata como pérdida de comunicación; así, un servidor o una base de datos que no responden muestran la advertencia en lugar de un estado «en línea».
- El historial operativo de compras, ventas, transferencias y movimientos (RF-0004/RF-0005) sigue siendo una consulta explícita, sin polling.

## Contrato REST

| Ruta `/api/v1` | Método y finalidad |
|---|---|
| `/visibility/branches/{uuid}/inventory` | GET: `branch` (`externalId`, `code`, `name`, `active`), `readAt` (hora de la instantánea de base de datos) e `items` con `productExternalId`, `sku`, `name`, `baseUnitName`, `physicalQuantity`, `availableQuantity`, `inTransitQuantity` como decimales exactos de tres cifras |

Errores: `400 VALIDATION_FAILED` si la referencia no es UUIDv4; `401` sin sesión; `404 BRANCH_NOT_FOUND` si la sucursal no existe. La ruta previa `/visibility/inventory/{uuid}` se conserva sin cambios.

## Recorrido manual

1. Levantar el entorno según [acceso y administración](acceso-y-administracion.md) con datos de demostración. Como administrador, crear dos sucursales activas (por ejemplo, NORTE y SUR), un operador asignado a SUR y un producto habilitado en ambas desde **Catálogo**.
2. Iniciar sesión como el operador de SUR, abrir **Consultas**, elegir `DEMO-BOG` y pulsar **Consultar sucursal**. El título identifica «Demostración Bogotá (DEMO-BOG)», la tabla muestra `DEMO-PLANT` con físico y disponible `8.000` y una columna separada de tránsito, sin costos. Un aviso indica que es una consulta de solo lectura de otra sucursal (RF-0014-CA-01).
3. Elegir NORTE: aparece el producto habilitado con `0.000`, no las cifras de DEMO-BOG ni las propias.
4. Con la vista abierta, el estado indica la hora de la última lectura y avanza cada dos segundos. Como todavía no existen comandos de ingresos o retiros (RF-0016/RF-0017), simular un movimiento confirmado **solo en una pila desechable**: `docker compose exec postgres psql -U <usuario> -d <base> -c "UPDATE inventory_balance SET physical_qty=5,inventory_value=50 WHERE product_id=(SELECT id FROM catalog_product WHERE sku='DEMO-PLANT') AND branch_id=(SELECT id FROM identity_branch WHERE code='DEMO-BOG')"`. En menos de cinco segundos la tabla muestra `5.000` sin pulsar ningún botón (RF-0015-CA-01).
5. Detener el backend con `docker compose stop backend`. En el siguiente intento aparece la advertencia ámbar «Sin comunicación con el servidor… pueden estar desactualizados»; la tabla conserva la última lectura. Reanudar con `docker compose start backend`: la advertencia desaparece en la siguiente lectura correcta (RF-0015-CA-02).
6. Cambiar a otra sección (por ejemplo **Catálogo**): en la pestaña de red del navegador cesan las solicitudes a `/inventory`; al volver a **Consultas** se reconsulta de inmediato.
7. Como administrador, consultar una sucursal inactiva: se muestra con la etiqueta «Sucursal inactiva» y sus existencias siguen consultables.

## Alcance y límites

- La meta de cinco segundos no se declara medida: dos segundos es el parámetro inicial aprobado y la medición bajo carga queda pendiente en RNF (§5.4, ARQ-PEN-003).
- Los navegadores pueden ralentizar temporizadores en pestañas ocultas; el polling se suspende en segundo plano y se reanuda al volver.
- La comprobación de RF-0015-CA-01 con movimientos reales depende de RF-0016, RF-0017 y RF-0036; hasta entonces las pruebas modifican el saldo directamente en una base efímera.

## Verificaciones

Backend: `VisibilityHttpTest` (tres pruebas nuevas: consulta con tránsito separado y sin valoración para los tres roles, cambio visible en la lectura siguiente, errores 400/401/404 y sucursal inactiva) dentro del runner Maven Docker de `backend/README.md`. Frontend: `frontend/test/inventory.test.ts` con `pnpm test` y `pnpm build`. Los resultados observados se registran en `odd/tasks/rf-0014-0015.md`.
