# Probar la solicitud, preparación y aprobación de transferencias

Guía de la implementación de RF-0032 (solicitar transferencias), RF-0033 (preparar cantidades a enviar) y RF-0034 (aprobar transferencias), conforme a INC-015/D01–D06 y D09 y al modelo de datos (`transfers_transfer`, `transfers_line`, `transfers_event`). El despacho, la recepción y el tránsito (RF-0035 y siguientes) no forman parte de esta entrega.

## Decisiones aplicadas

- **Ningún paso mueve existencias (INC-015/D05).** Solicitar, preparar, aprobar, rechazar y cancelar no reservan ni descuentan stock ni crean tránsito: solo el despacho (RF-0035) lo hará. La disponibilidad que ve el origen es su físico actual, sin reservas.
- **Actores (H v0.16, H v1.2/01).** Solicita el operador de la sucursal destino; prepara el operador de origen; aprueba o rechaza el gerente de origen; antes de la aprobación cancela el operador de destino y, aprobada y antes del despacho, solo el gerente de origen con motivo. El administrador consulta toda la red y no ejecuta ningún paso; el gerente de destino supervisa sin obtener el rechazo.
- **Solicitud multilínea (INC-015/D02, D09).** Origen activo y distinto del destino; cada producto activo y habilitado en ambas sucursales, con una unidad activa compatible. Una sola línea por producto y unidad: una repetición se rechaza indicando la línea original (opción elegida entre las dos que admite el RF). Todas las líneas se validan antes de escribir; un rechazo no crea la transferencia.
- **Preparación (INC-015/D03).** El origen indica la cantidad de todas las líneas a la vez, en la unidad solicitada, entre 0 y lo solicitado; nunca más. Puede ajustarla mientras está en preparación; cada revisión es un evento con su instantánea. Solicitado y preparado se conservan por separado.
- **Transiciones atómicas y sin duplicados.** Cada escritura bloquea la fila de la transferencia, valida estado y actor bajo ese bloqueo y registra un único evento. Las escrituras exigen `Idempotency-Key`: repetir la clave devuelve el resultado original sin otro evento. La historia de estados es de solo inserción para el rol de ejecución (migración V11).

## Contrato REST

| Método y ruta | Perfil | Resultado |
|---|---|---|
| `POST /api/v1/transfers` | Operador de destino | `{origin, lines: [{product, unit, quantity}]}`; `201` con la transferencia `REQUESTED`. |
| `POST /api/v1/transfers/{id}/prepare` | Operador de origen | `{lines: [{lineNo, quantity}]}` con todas las líneas; `PREPARING`. |
| `POST /api/v1/transfers/{id}/approve` | Gerente de origen | Sin datos; solo desde `PREPARING`. |
| `POST /api/v1/transfers/{id}/reject` | Gerente de origen | `{reason}` obligatorio; solo desde `PREPARING`. |
| `POST /api/v1/transfers/{id}/cancel` | Según estado | `{reason?}`; obligatorio si está `APPROVED`. |
| `GET /api/v1/transfers?branch=&direction=INCOMING\|OUTGOING&status=&limit=` | Todos | Transferencias del ámbito, más recientes primero. |
| `GET /api/v1/transfers/{id}` | Origen, destino y administrador | Detalle con líneas, aprobación e historia; la disponibilidad del origen solo para origen y administrador. |
| `GET /api/v1/transfers/origins` | Operador | Sucursales activas distintas de la propia. |

Errores: `400 VALIDATION_FAILED`, `SAME_BRANCH`, `REASON_REQUIRED`; `403 TRANSFER_FORBIDDEN` (perfil o lado incorrecto); `404 TRANSFER_NOT_FOUND` (transferencia ajena a la sucursal); `409 BRANCH_INACTIVE`, `PRODUCT_NOT_OPERABLE`, `PREPARED_EXCEEDS_REQUESTED`, `TRANSFER_STATUS_CONFLICT`, `TRANSFER_IN_TRANSIT`, `IDEMPOTENCY_CONFLICT`. Todos terminan indicando que no se modificó la transferencia.

## Recorrido manual

1. Como administrador, cree dos sucursales (origen y destino), un operador en cada una y un gerente en el origen; habilite un producto con unidad `caja` de 12 en ambas.
2. Como operador de origen, registre en **Movimientos** un ingreso de 30 unidades.
3. Como operador de destino, abra **Transferencias**, pulse **Solicitar transferencia**, elija el origen, el producto y 2 cajas. La transferencia queda «Solicitada», sin preparar; no se ve el stock del origen.
4. Como operador de origen, abra el detalle: ve 30 unidades disponibles. Pulse **Preparar envío**, escriba 3 (aviso de que supera lo solicitado; no se puede guardar) y luego 1. Se marca «Menos de lo solicitado».
5. Como gerente de origen, pulse **Aprobar transferencia** y confirme. La aprobación muestra responsable y fecha. Las existencias del origen siguen en 30.
6. Como operador de destino, el detalle distingue 2 cajas solicitadas y 1 a enviar, sin opción de cancelar.
7. Opcional: con otra solicitud preparada, el gerente de origen pulsa **Rechazar** con motivo; o el operador de destino cancela antes de la aprobación.

## Límites

- Sin despacho, transportista, tránsito ni recepción (RF-0035 y siguientes): el paso «Despacho» se muestra pendiente.
- Una transferencia aprobada con todas las cantidades en 0 no se bloquea al aprobar; RF-0033 impide despacharla y esa validación corresponde a RF-0035.
- La disponibilidad mostrada es una lectura del momento; el despacho deberá revalidar todas las líneas (INC-015/D05).

## Verificaciones

- Backend: `TransferHttpTest` (8 pruebas) cubre RF-0032-CA-01/02, RF-0033-CA-01/02, RF-0034-CA-01/02/03/04, saldos y movimientos sin cambios en cada paso, intentos rechazados sin efectos, idempotencia, aprobación y rechazo simultáneos (gana una sola transición), cancelación y preparación simultáneas, y la historia de solo inserción.
- Frontend: `test/transfers.test.ts` cubre los permisos por perfil y lado, la comparación exacta de cantidades, la validación de la preparación y el contrato del cliente.
- Navegador: `python3 scripts/check_transfers_browser.py` levanta una pila Compose desechable, que retira al terminar con su volumen y sus imágenes construidas, y recorre los pasos 3–6 con comprobación en móvil (390 px).
