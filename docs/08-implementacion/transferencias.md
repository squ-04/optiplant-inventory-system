# Probar la solicitud, preparación, aprobación y despacho de transferencias y las rutas

Guía de la implementación de RF-0032 (solicitar transferencias), RF-0033 (preparar cantidades a enviar), RF-0034 (aprobar transferencias), RF-0035 (registrar despacho) y RF-0041 (clasificar rutas), conforme a INC-015/D01–D09, INC-016/D01, INC-017/D01–D02, INC-003/D06 y al modelo de datos (`transfers_*`, `logistics_route`, `logistics_dispatch`, `logistics_dispatch_line`, `inventory_transit`, `inventory_transit_entry`). La recepción, los faltantes y la comparación de tiempos (RF-0036 y siguientes) no forman parte de esta entrega.

## Decisiones aplicadas

- **Solo el despacho mueve existencias (INC-015/D05).** Solicitar, preparar, aprobar, rechazar y cancelar no reservan ni descuentan stock ni crean tránsito. La disponibilidad que ve el origen es su físico actual, sin reservas.
- **Actores (H v0.16, H v1.2/01).** Solicita el operador de la sucursal destino; prepara el operador de origen; aprueba o rechaza el gerente de origen; antes de la aprobación cancela el operador de destino y, aprobada y antes del despacho, solo el gerente de origen con motivo. El administrador consulta toda la red y no ejecuta ningún paso; el gerente de destino supervisa sin obtener el rechazo.
- **Solicitud multilínea (INC-015/D02, D09).** Origen activo y distinto del destino; cada producto activo y habilitado en ambas sucursales, con una unidad activa compatible. Una sola línea por producto y unidad: una repetición se rechaza indicando la línea original (opción elegida entre las dos que admite el RF). Todas las líneas se validan antes de escribir; un rechazo no crea la transferencia.
- **Preparación (INC-015/D03).** El origen indica la cantidad de todas las líneas a la vez, en la unidad solicitada, entre 0 y lo solicitado; nunca más. Puede ajustarla mientras está en preparación; cada revisión es un evento con su instantánea. Solicitado y preparado se conservan por separado.
- **Mismas reglas de cantidad en cliente y servidor.** Cada línea informa `unitDivisible` y `baseDivisible`. El formulario de preparación aplica, en el mismo orden y con los mismos mensajes que `UnitConversion.toBase`, las reglas de hasta 3 decimales, sin fracciones en una unidad indivisible, equivalente en unidad base con hasta 3 decimales y entero si la base no admite fracciones, y nunca más de lo solicitado; cualquiera de ellas bloquea el guardado. Superar la disponibilidad del origen solo advierte. El servidor sigue validando todo.
- **Estados en un solo lugar.** En el frontend, `frontend/src/lib/transfers/statuses.ts` define código, etiqueta, etiqueta de filtro y tono de cada estado; de ahí derivan el tipo, el validador de respuestas, la insignia, el filtro y los pasos. En el backend, el `enum TransferStatus` define códigos y etiquetas de los mensajes. Un código nuevo o renombrado exige además migrar las restricciones CHECK de V3.
- **Rutas (RF-0041, INC-017/D01–D02, INC-003/D06).** Una ruta es dirigida (origen→destino distintos y activos al registrarla), con código único y prioridad manual alta, media o baja: el único criterio de clasificación. El trayecto existe una sola vez: una ruta inactiva se reactiva, no se registra de nuevo. Origen y destino no se modifican; código, prioridad y estado sí. Solo el administrador las registra y edita y ve también las inactivas; gerente y operador consultan las activas de su sucursal. Sin tarifas, escalas, mapas, seguimiento ni optimización.
- **Despacho (RF-0035, INC-015/D07–D09, INC-016/D01).** Lo registra el operador de origen sobre una transferencia aprobada, con transportista textual, salida real y llegada estimada obligatorias (hora de America/Bogota, UTC−5, capturada al minuto). La salida no puede ser futura (se toleran 5 minutos de adelanto del reloj del equipo respecto al servidor) ni anterior al minuto de la aprobación, y la llegada estimada no puede ser anterior a la salida. Usa la ruta activa de su origen y destino y guarda su prioridad de ese momento: reclasificarla después no cambia el despacho. Sin ruta activa en el momento del despacho no se despacha: decisión confirmada por el responsable el 28/10/2026, coherente con `logistics_dispatch.route_id` obligatorio en el modelo aprobado. Una transferencia con todas sus cantidades preparadas en 0 no se despacha.
- **Despacho indivisible y valorado.** Bajo el bloqueo de la transferencia se revalidan sucursales activas, cada producto y unidad en origen y destino, y las existencias de todas las líneas (bloqueadas en orden de producto). Si una línea no alcanza, se rechaza todo sin salida, tránsito ni despacho. Si todo es válido, en la misma transacción se registran el despacho y sus líneas, una operación `DISPATCH` con una salida por línea positiva al costo promedio del origen (congelado en la línea), un saldo de tránsito por línea con todo el valor retirado (la diferencia de redondeo de una salida que agota el saldo se conserva aparte) y el paso a «En tránsito». El destino no recibe nada hasta la recepción. Una línea preparada en 0 se conserva con cantidad y valor 0, sin movimiento ni tránsito. Los costos no se exponen al operador.
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
| `POST /api/v1/transfers/{id}/dispatch` | Operador de origen | `{carrier, departedAt, estimatedArrivalAt}` (ISO-8601 con zona); solo desde `APPROVED`; `IN_TRANSIT` con `dispatch` y cantidades enviadas. |
| `GET /api/v1/transfers/origins` | Operador | Sucursales activas distintas de la propia. |
| `GET /api/v1/logistics/routes?priority=HIGH\|MEDIUM\|LOW` | Todos | Rutas de prioridad alta a baja; los perfiles locales, solo las activas de su sucursal. |
| `POST /api/v1/logistics/routes` | Administrador | `{code, origin, destination, priority}`; `201`. |
| `POST /api/v1/logistics/routes/{id}/update` | Administrador | `{code, priority, active}`; origen y destino no se modifican. |

Errores: `400 VALIDATION_FAILED`, `SAME_BRANCH`, `REASON_REQUIRED`; `403 TRANSFER_FORBIDDEN` (perfil o lado incorrecto); `404 TRANSFER_NOT_FOUND` (transferencia ajena a la sucursal); `409 BRANCH_INACTIVE`, `PRODUCT_NOT_OPERABLE`, `PREPARED_EXCEEDS_REQUESTED`, `TRANSFER_STATUS_CONFLICT`, `TRANSFER_IN_TRANSIT`, `IDEMPOTENCY_CONFLICT`; en el despacho además `ROUTE_REQUIRED`, `NOTHING_TO_DISPATCH` e `INSUFFICIENT_STOCK` (indica la línea). Todos terminan indicando que no se modificó la transferencia ni el inventario. Rutas: `400 VALIDATION_FAILED`, `SAME_BRANCH`; `403 ROLE_FORBIDDEN`; `404 ROUTE_NOT_FOUND`; `409 ROUTE_EXISTS`, `CODE_EXISTS`, `BRANCH_INACTIVE`.

## Recorrido manual

1. Como administrador, cree dos sucursales (origen y destino), un operador en cada una y un gerente en el origen; habilite un producto con unidad `caja` de 12 en ambas.
2. Como operador de origen, registre en **Movimientos** un ingreso de 30 unidades.
3. Como operador de destino, abra **Transferencias**, pulse **Solicitar transferencia**, elija el origen, el producto y 2 cajas. La transferencia queda «Solicitada», sin preparar; no se ve el stock del origen.
4. Como operador de origen, abra el detalle: ve 30 unidades disponibles. Pulse **Preparar envío**, escriba 3 (aviso de que supera lo solicitado; no se puede guardar) y luego 1. Se marca «Menos de lo solicitado».
5. Como gerente de origen, pulse **Aprobar transferencia** y confirme. La aprobación muestra responsable y fecha. Las existencias del origen siguen en 30.
6. Como operador de destino, el detalle distingue 2 cajas solicitadas y 1 a enviar, sin opción de cancelar.
7. Opcional: con otra solicitud preparada, el gerente de origen pulsa **Rechazar** con motivo; o el operador de destino cancela antes de la aprobación.
8. Como operador de origen, en la transferencia aprobada pulse **Registrar despacho**: sin ruta activa se avisa y no se puede confirmar.
9. Como administrador, abra **Rutas**, pulse **Nueva ruta** y registre origen→destino con prioridad «Media»; luego **Editar** y cámbiela a «Alta». La ruta aparece en el grupo «Prioridad alta». Registrar otra con el mismo origen y destino se rechaza.
10. Como operador de origen, vuelva a **Registrar despacho**: se muestra la ruta y su prioridad. Confirme sin transportista (se indica el dato que falta); luego escriba el transportista y la llegada estimada (la salida real viene con la hora actual de Bogotá) y confirme. La transferencia queda «En tránsito», con transportista, salida, llegada estimada, ruta y columna «Enviado»; la disponibilidad del origen baja de 30 a 18. El botón de despacho desaparece.
11. Como operador de destino, el detalle muestra los datos del despacho y que su inventario no cambia hasta la recepción; en **Rutas** consulta la ruta sin poder modificarla.
12. Opcional: con una transferencia aprobada cuya segunda línea supere las existencias (por ejemplo, tras una salida manual), el despacho se rechaza indicando la línea y nada cambia.

## Límites

- Sin recepción, faltantes ni comparación de tiempos (RF-0036 y siguientes): una transferencia en tránsito permanece así.
- Una transferencia aprobada con todas las cantidades en 0 se aprueba, pero su despacho se rechaza (`NOTHING_TO_DISPATCH`); el gerente de origen puede cancelarla.
- La disponibilidad mostrada es una lectura del momento; el despacho la revalida para todas las líneas (INC-015/D05).
- Sin catálogo de transportistas ni seguimiento externo (INC-015/D07); sin tiempos típicos por ruta (INC-017/D05).

## Verificaciones

- Backend: `RouteAndDispatchHttpTest` (7 pruebas) cubre RF-0041-CA-01 (clasificación, orden, filtro, reclasificación, ámbito local, inactivas), rutas inválidas o duplicadas y permisos sin cambios; RF-0035-CA-01 (transportista, fechas, ruta, cantidades enviadas consultables por origen y destino), salidas y tránsito al costo congelado sin ingreso en destino, prioridad congelada, RF-0035-CA-02 (línea sin stock: nada cambia), reintento con la misma clave, con otra clave y concurrente (una sola salida y un solo tránsito), permisos, datos obligatorios y coherentes, revalidación de catálogo y sucursales, todo en cero, y la diferencia de redondeo de una salida que agota el saldo llevada al tránsito.
- Backend: `TransferHttpTest` (8 pruebas) cubre RF-0032-CA-01/02, RF-0033-CA-01/02, RF-0034-CA-01/02/03/04, saldos y movimientos sin cambios en cada paso, intentos rechazados sin efectos, idempotencia, aprobación y rechazo simultáneos (gana una sola transición), cancelación y preparación simultáneas, la historia de solo inserción, la divisibilidad de cada línea y los estados admitidos en el filtro.
- Frontend: `test/routes.test.ts` cubre prioridades, elección de ruta, permiso de despacho, conversión exacta de la hora de Bogotá, las validaciones del despacho idénticas a las del servidor y el contrato del cliente. `test/transfers.test.ts` cubre los permisos por perfil y lado, la comparación exacta de cantidades, las reglas de preparación idénticas a las del servidor con sus mensajes, la definición única de estados y el contrato del cliente.
- Navegador: `python3 scripts/check_transfers_browser.py` levanta una pila Compose desechable, que retira al terminar con su volumen y sus imágenes construidas, y recorre los pasos 3–6 y 8–11 con comprobación en móvil (390 px).
