> Estado: realizada. Generada con IA (Claude Code) el 29/10/2026; el responsable completó los 26 pasos en su entorno local, a continuación del recorrido de compras y ventas (confirmado el 29/10/2026). Ese mismo día se contrastó en solo lectura la base local con «Datos que deberían quedar en PostgreSQL»: transferencias `TR-000001`–`TR-000006` con sus estados, rutas `RUT-MED-BOG` y `RUT-BOG-MED`, existencias de MED-CEN y BOG-NOR y 35 movimientos acumulados (23 previos y 12 nuevos) coinciden. La guía conserva su texto original; los mensajes marcados «aproximadamente» se redactaron antes de la ejecución.

Esta es la guía de recorrido manual en un solo flujo para transferencias entre sucursales, rutas, faltantes, tiempos de entrega y cumplimiento logístico (RF-0032–RF-0043). Continúa el [recorrido manual de compras y ventas](recorrido-manual-compras-ventas.md) y parte exactamente de los datos que deja, contrastados en solo lectura con la base local el 29/10/2026. Se basa en la guía de [transferencias](transferencias.md), en el [informe E2E del 2026-10-29](exploracion-e2e-2026-10-29.md) y en las etiquetas reales de la interfaz (`App.tsx`, `Transfers.tsx`, `TransferDispatchForm.tsx`, `TransferReceptionForm.tsx`, `TransferShortagePanel.tsx`, `TransferTiming.tsx`, `Routes.tsx`, `ShortageAlerts.tsx`, `LogisticsCompliance.tsx`) y del servidor. No recorrí la aplicación en el navegador, así que los mensajes que no pude confirmar en el código aparecen como «aproximadamente».

## Preparación

- Levanta el entorno con `docker compose up --build --wait` y abre `http://localhost:5173`. No borres el volumen: esta guía usa los datos de los recorridos anteriores.
- **Ventana A** (navegador normal): siempre el administrador (`OptiPlant-Admin-2026!`).
- **Ventana B** (ventana privada): los usuarios de sucursal, de uno en uno. Cierra sesión antes de cambiar de usuario.
- **Contraseñas vigentes:** `ope.med` `OptiPlant-OpeMed-2026!`, `ger.med` `OptiPlant-GerMed-2026!`, `ope.bog` `OptiPlant-OpeBog-2026!`, `ger.bog` `OptiPlant-GerBog-2026!`.
- **Quién hace qué:** solicita el operador de la sucursal **destino**; prepara y despacha el operador de **origen**; aprueba o rechaza el gerente de **origen**; recibe el operador de **destino**. El administrador solo consulta y mantiene las rutas.
- **Decimales:** se escriben con coma (`2,5`). Las cantidades pueden mostrarse con tres decimales (`20,000`).
- **Fechas y horas:** el despacho y los tiempos se capturan y muestran en hora de Bogotá (America/Bogota, UTC−5).
- **Códigos:** las transferencias se numeran `TR-000001`, `TR-000002`… La base no tiene ninguna todavía; si creas alguna fuera de la guía, sustituye los códigos siguientes.
- **Datos demo:** las sucursales `DEMO-BOG` y `DEMO-MED` y el producto `DEMO-PLANT` siguen en la base. Aparecen como origen posible en los selectores, pero la guía no los usa.
- **Punto de partida** (fin del recorrido de compras y ventas):

  | Sucursal | SUST-001 (bulto) | FERT-NPK (kg) | MAC-14 (unidad) |
  |---|---|---|---|
  | MED-CEN | 97 a 18.192,307692 | 209,5 a 4.257 | 148 a 1.384,090909 |
  | BOG-NOR | 4 a 18.900 (mínimo 8, alerta activa) | 50 a 4.600 (mínimo 10) | 0 (mínimo 12, alerta activa) |

  Unidades comerciales: `estiba` = 40 bulto en SUST-001 y `saco` = 50 kg en FERT-NPK. No hay rutas ni transferencias. CAL-SUR solo tiene habilitados SUST-001 y SEM-TOM, y no tiene operador activo.

---

## Fase 1 — Revisión inicial (Ventanas A y B)

**Paso 1 — Lo que ve cada perfil antes de empezar**
- **Dónde:** Ventana A en **Transferencias** y **Rutas**. Ventana B como `ope.bog` en el menú lateral.
- **Resultado esperado:**
  - El administrador ve «Transferencias de la red» sin **Solicitar transferencia** y, en **Rutas**, «Todavía no hay rutas. Registre la ruta de cada origen y destino antes de despachar transferencias.» con **Nueva ruta**.
  - `ope.bog` ve **Transferencias** y **Rutas**, pero no **Cumplimiento** ni **Alertas**. En **Rutas**: «Su sucursal no tiene rutas activas…».
- **Estado de los datos:** sin cambios. No crees la ruta todavía: el paso 8 prueba el despacho sin ruta.

---

## Fase 2 — Operador de Bogotá: solicitud (Ventana B)

**Paso 2 — Validaciones de la solicitud**
- **Dónde:** Ventana B como `ope.bog` → **Transferencias → Solicitar transferencia**.
- **Qué hacer:**
  1. Abre **Sucursal origen**: comprueba qué sucursales aparecen.
  2. Elige `CAL-SUR`, producto FERT-NPK, unidad `kg`, cantidad `5`. Pulsa **Solicitar transferencia**.
  3. Cambia el origen a `MED-CEN`. Deja la línea de FERT-NPK y pulsa **Agregar producto**: FERT-NPK, `kg`, `3`. Pulsa **Solicitar transferencia**.
  4. Pulsa **Quitar** en la segunda línea.
- **Resultado esperado:**
  - El selector ofrece `CAL-SUR`, `DEMO-BOG`, `DEMO-MED` y `MED-CEN`, nunca `BOG-NOR`. El formulario no muestra existencias del origen.
  - Con CAL-SUR: «El producto no está activo y habilitado en el catálogo de la sucursal origen.» (aproximadamente; indica la línea). No se crea nada.
  - Con la línea repetida: «Línea 2: repite el producto y la unidad de la línea 1. Indique la cantidad total en una sola línea.». No se crea nada.
- **Estado de los datos:** sin cambios. Deja el formulario abierto.

**Paso 3 — Crear una solicitud multilínea**
- **Dónde:** el mismo formulario, con origen `MED-CEN`.
- **Qué hacer:** deja estas tres líneas y pulsa **Solicitar transferencia**:

  | Producto | Unidad | Cantidad |
  |---|---|---|
  | SUST-001 | bulto | `20` |
  | FERT-NPK | saco (50 kg) | `1` |
  | MAC-14 | unidad | `30` |

  Después abre **Catálogo → Mi sucursal**.
- **Resultado esperado:**
  - «Transferencia TR-000001 solicitada a Sede Medellín Centro con 3 productos. Las existencias no cambian: el origen confirmará o ajustará las cantidades.».
  - El detalle muestra «Solicitada», «Sin preparar» en **A enviar**, el paso «Solicitada» como actual y «Esperando que Sede Medellín Centro revise su disponibilidad y prepare el envío.». FERT-NPK muestra también `50 kg` bajo `1 saco`.
  - El catálogo de BOG-NOR sigue en SUST 4, FERT 50 y MAC 0.
- **Estado de los datos:** TR-000001 solicitada. **Cerrar sesión**.

---

## Fase 3 — Medellín como origen: preparación y aprobación (Ventana B)

**Paso 4 — Preparar con validaciones**
- **Dónde:** Ventana B como `ope.med` → **Transferencias → Ver detalle** de TR-000001 → **Preparar envío**.
- **Qué hacer:**
  1. Observa la columna **Disponible en origen**.
  2. En MAC-14 escribe `31`; luego `2,5`.
  3. Escribe `25` en MAC-14 y deja SUST-001 en `20` y FERT-NPK en `1`. Pulsa **Guardar preparación**.
- **Resultado esperado:**
  - **Disponible en origen**: SUST-001 `97 bulto`, FERT-NPK `209,5 kg`, MAC-14 `148 unidad`. Es el físico actual, sin reservas.
  - `31`: «No puede superar lo solicitado: 30 unidad.» y **Guardar preparación** queda deshabilitado.
  - `2,5`: el navegador lo bloquea con «Número entero.».
  - «Preparación de TR-000001 guardada. El destino ya ve las cantidades a enviar; el gerente de origen debe aprobarla. No se reservaron existencias.». El estado pasa a «En preparación» y MAC-14 se marca «Menos de lo solicitado».
- **Estado de los datos:** TR-000001 en preparación (20 bulto, 1 saco, 25 unidad). MED-CEN sin cambios.

**Paso 5 — Ajustar la preparación**
- **Dónde:** el mismo detalle → **Ajustar preparación**.
- **Qué hacer:** sin cambiar nada, pulsa **Guardar preparación**. Revisa la **Historia de estados**.
- **Resultado esperado:** la historia muestra «En preparación» y después «Preparación ajustada», cada uno con su instantánea «A enviar: 20 bulto de SUST-001; 1 saco de FERT-NPK; 25 unidad de MAC-14» y su responsable Carlos Ruiz (ope.med). El operador no ve **Aprobar transferencia**.
- **Estado de los datos:** sin cambios en cantidades. **Cerrar sesión**.

**Paso 6 — Lo que ve el destino y lo que no puede hacer su gerente**
- **Dónde:** Ventana B como `ger.bog` → **Transferencias → Ver detalle** de TR-000001.
- **Resultado esperado:** ve **Solicitado** 30 y **A enviar** 25 de MAC-14, pero no la columna **Disponible en origen** ni los botones **Aprobar transferencia** o **Rechazar**: la aprobación corresponde al gerente de origen.
- **Estado de los datos:** sin cambios. **Cerrar sesión**.

**Paso 7 — Aprobar sin mover existencias**
- **Dónde:** Ventana B como `ger.med` → **Transferencias → Ver detalle** de TR-000001.
- **Qué hacer:** pulsa **Aprobar transferencia**; en el diálogo pulsa primero Escape, luego vuelve a pulsarlo y confirma con **Aprobar transferencia**. Revisa **Catálogo → Mi sucursal**.
- **Resultado esperado:**
  - Escape no envía nada.
  - «TR-000001 quedó aprobada. Queda pendiente el despacho del origen.». **Aprobada por** Laura Gómez (ger.med) con fecha y hora.
  - El catálogo de MED-CEN sigue en SUST 97, FERT 209,5 y MAC 148.
- **Estado de los datos:** TR-000001 aprobada. **Cerrar sesión**.

---

## Fase 4 — Rutas y despacho (Ventanas A y B)

**Paso 8 — Despacho bloqueado sin ruta**
- **Dónde:** Ventana B como `ope.med` → detalle de TR-000001 → **Registrar despacho**.
- **Resultado esperado:** «No hay una ruta activa de Sede Medellín Centro a Sede Bogotá Norte. El administrador debe registrarla o reactivarla antes del despacho.» y **Confirmar despacho** deshabilitado. Pulsa **Volver sin despachar**.
- **Estado de los datos:** sin cambios. Mantén la sesión abierta.

**Paso 9 — Registrar y clasificar rutas**
- **Dónde:** Ventana A en **Rutas → Nueva ruta**.
- **Qué hacer:**
  1. Registra `RUT-MED-BOG`, origen `MED-CEN`, destino `BOG-NOR`, prioridad «Media», con **Registrar ruta**.
  2. Registra `RUT-BOG-MED`, origen `BOG-NOR`, destino `MED-CEN`, prioridad «Baja».
  3. Intenta registrar `RUT-DUP`, origen `MED-CEN`, destino `BOG-NOR`, prioridad «Alta».
  4. Intenta registrar `rut-med-bog`, origen `BOG-NOR`, destino `CAL-SUR`.
  5. Pulsa **Editar** en `RUT-MED-BOG` y cambia la prioridad a «Alta». Pulsa **Guardar cambios**.
- **Resultado esperado:**
  - «Ruta RUT-MED-BOG registrada con prioridad media.» y «Ruta RUT-BOG-MED registrada con prioridad baja.».
  - El trayecto repetido se rechaza: «Ya existe la ruta RUT-MED-BOG con ese origen y destino: cambie su prioridad o reactívela en lugar de registrarla de nuevo.».
  - El código en minúsculas se rechaza: «Ya existe una ruta con ese código.» (los códigos no distinguen mayúsculas).
  - El formulario de edición no permite cambiar origen ni destino. «Ruta RUT-MED-BOG actualizada. Los despachos ya registrados conservan la prioridad que tenían.». Aparece en el grupo «Prioridad alta» y `RUT-BOG-MED` en «Prioridad baja».
- **Estado de los datos:** 2 rutas activas.

**Paso 10 — Validaciones del despacho**
- **Dónde:** Ventana B (`ope.med`): pulsa **Cerrar detalle**, vuelve a abrir TR-000001 y pulsa **Registrar despacho**.
- **Qué hacer:** observa que se muestra «Ruta RUT-MED-BOG» con prioridad Alta y que **Salida real** propone la hora actual. Prueba, uno a la vez, con **Confirmar despacho**:
  1. Transportista vacío.
  2. Transportista `Transportes La Montaña`, llegada estimada una hora **antes** de la salida.
  3. Salida real mañana a la misma hora.
- **Resultado esperado:** cada intento muestra su mensaje y no abre el diálogo:
  1. «Indique el transportista: nombre o identificador.».
  2. «La llegada estimada no puede ser anterior a la salida real.».
  3. «La salida real no puede ser posterior al momento actual: registre el despacho cuando la mercancía haya salido.».
- **Estado de los datos:** sin cambios. Deja el formulario abierto.

**Paso 11 — Despachar**
- **Dónde:** el mismo formulario.
- **Qué hacer:** vuelve a poner la **Salida real** en la hora actual (o pulsa **Volver sin despachar** y abre el formulario de nuevo para que la proponga). Transportista `Transportes La Montaña`, **Llegada estimada** hoy, 2 horas después de la salida. Pulsa **Confirmar despacho** y acepta el diálogo. Revisa **Movimientos** y **Catálogo → Mi sucursal**.
- **Resultado esperado:**
  - El diálogo indica la salida real en hora de Bogotá y que el despacho no se puede deshacer.
  - «TR-000001 despachada: las cantidades salieron de Sede Medellín Centro y quedan en tránsito hacia Sede Bogotá Norte hasta su recepción.».
  - El detalle queda «En tránsito», con **Despachada por**, **Transportista**, **Salida real**, **Llegada estimada**, **Ruta** RUT-MED-BOG Alta («Prioridad al momento del despacho.») y la columna **Enviado**. Desaparece **Registrar despacho**.
  - **Tiempo de entrega**: duración estimada `2 h`, real «Sin llegada confirmada», «En tránsito, sin llegada confirmada».
  - **Movimientos** muestra tres «Despacho de transferencia»: −20 bulto, −1 saco y −25 unidad.
  - Catálogo MED-CEN: SUST `77`, FERT `159,5`, MAC `123`.
- **Estado de los datos:** TR-000001 en tránsito. MED-CEN: SUST 77, FERT 159,5, MAC 123.

**Paso 12 — La prioridad queda congelada en el despacho**
- **Dónde:** Ventana A en **Rutas → Editar** `RUT-MED-BOG`, y después **Transferencias → Ver detalle** de TR-000001.
- **Qué hacer:** cambia la prioridad a «Media», guarda y abre el detalle.
- **Resultado esperado:** la ruta pasa al grupo «Prioridad media», pero el detalle de TR-000001 sigue mostrando «Alta». El administrador no ve ningún botón de acción, solo **Cerrar detalle**.
- **Estado de los datos:** RUT-MED-BOG con prioridad media. **Cerrar sesión** en la Ventana B.

---

## Fase 5 — Bogotá como destino: recepción completa (Ventana B)

**Paso 13 — Consultar en tránsito y recibir completo**
- **Dónde:** Ventana B como `ope.bog` → **Rutas**, después **Transferencias → Ver detalle** de TR-000001.
- **Qué hacer:**
  1. En **Rutas**, comprueba que ve `RUT-MED-BOG` y `RUT-BOG-MED` sin **Editar** ni **Nueva ruta**.
  2. En el detalle, lee el aviso y pulsa **Confirmar recepción**.
  3. En SUST-001 escribe `21`; luego vuelve a `20`.
  4. Pulsa **Confirmar recepción** y acepta el diálogo.
  5. Revisa **Catálogo → Mi sucursal**.
- **Resultado esperado:**
  - Antes de recibir: «En tránsito: su inventario no cambia hasta que se confirme la recepción.».
  - Las cantidades parten de lo enviado. `21`: «No puede superar lo enviado: 20 bulto. Un sobrante no se recibe en esta transferencia.».
  - Con todo igual: «Llegó todo lo enviado: la transferencia quedará «Recibida completa».».
  - «TR-000001 recibida completa: las cantidades entraron al inventario de Sede Bogotá Norte.». Aparecen **Recepción confirmada por** Julián Mora (ope.bog) y las columnas **Recibido** y **Faltante** («Sin faltante»).
  - **Tiempo de entrega**: real de pocos minutos, «Recibida completa a tiempo: cumple» y la desviación «… antes de lo estimado».
  - Catálogo BOG-NOR: SUST `24`, FERT `100`, MAC `25`.
- **Estado de los datos:** TR-000001 recibida completa. BOG-NOR: SUST 24, FERT 100, MAC 25. **Cerrar sesión**.

**Paso 14 — Alertas de stock resueltas por la recepción**
- **Dónde:** Ventana A en **Alertas**, filtrado por BOG-NOR.
- **Resultado esperado:** las alertas de SUST-001 (24 > 8) y MAC-14 (25 > 12) en BOG-NOR desaparecen sin pulsar nada; en **Historial completo** figuran como «Resueltas». Siguen activas las de CAL-SUR.
- **Estado de los datos:** sin cambios.

---

## Fase 6 — Recepción con faltante y su tratamiento (Ventana B)

**Paso 15 — Segunda transferencia hasta el despacho**
- **Dónde:** Ventana B, cambiando de usuario en cada punto.
- **Qué hacer:**
  1. `ope.bog`: solicita a `MED-CEN` MAC-14 unidad `10` y FERT-NPK kg `5`.
  2. `ope.med`: **Preparar envío** con 10 y 5, sin cambios.
  3. `ger.med`: **Aprobar transferencia**.
  4. `ope.med`: **Registrar despacho** con transportista `Transportes La Montaña` y llegada estimada 1 hora después de la salida.
- **Resultado esperado:** TR-000002 pasa por «Solicitada», «En preparación», «Aprobada» y «En tránsito». MED-CEN queda en SUST 77, FERT 154,5 y MAC 113.
- **Estado de los datos:** TR-000002 en tránsito. **Cerrar sesión**.

**Paso 16 — Recibir con faltante**
- **Dónde:** Ventana B como `ope.bog` → detalle de TR-000002 → **Confirmar recepción**.
- **Qué hacer:** MAC-14 `7` y FERT-NPK `5`. Pulsa **Confirmar recepción** y acepta el diálogo. Luego pulsa otra vez **Actualizar listado** y comprueba que el detalle ya no ofrece **Confirmar recepción**.
- **Resultado esperado:**
  - Antes de confirmar: «Faltan productos en 1 línea: la transferencia quedará «Recibida con discrepancia» y se abrirá una alerta para los gerentes.». El diálogo avisa que no habrá una segunda recepción.
  - «TR-000002 recibida con discrepancia: entró lo recibido y el faltante quedó en tránsito con una alerta para los gerentes.».
  - **Faltante** de MAC-14: `3 unidad`. Panel «Faltante de la recepción» con «Alerta abierta»: «Faltan 3 unidad de MAC-14…», «Enviado 10 unidad, recibido 7 unidad. En tránsito: 3 unidad.».
  - El panel indica: «… Lo pendiente sigue en tránsito hasta que el gerente de origen registre su pérdida; un reenvío es una transferencia nueva y no lo libera.».
  - **Tiempo de entrega**: «Recibida con discrepancia: entrega parcial, no cumple».
  - BOG-NOR: MAC `32`, FERT `105`.
- **Estado de los datos:** TR-000002 recibida con discrepancia; 3 MAC-14 en tránsito. **Cerrar sesión**.

**Paso 17 — Resolver la alerta del faltante**
- **Dónde:** Ventana B como `ger.bog` → **Alertas**, sección «Faltantes de transferencias».
- **Qué hacer:**
  1. Pulsa **Resolver** en TR-000002 y luego **Resuelto** sin elegir tratamiento.
  2. Elige «Reclamación» y pulsa **Resuelto**.
  3. En **Mostrar**, elige «Resueltas». Abre TR-000002 en **Transferencias**.
- **Resultado esperado:**
  - Sin tratamiento: «Seleccione el tratamiento para marcar la alerta como resuelta.».
  - «Alerta de faltante de TR-000002 resuelta con tratamiento reclamación. El inventario, el tránsito y el estado de la transferencia no cambiaron.».
  - En «Resueltas» aparece con tratamiento, responsable Andrea Torres (ger.bog) y fecha.
  - El detalle sigue «Recibida con discrepancia», con «Alerta resuelta» y 3 unidad en tránsito. El gerente de destino no ve **Registrar pérdida en tránsito**.
- **Estado de los datos:** alerta resuelta; tránsito sin cambios. **Cerrar sesión**.

**Paso 18 — Registrar la pérdida en tránsito**
- **Dónde:** Ventana B como `ger.med` → detalle de TR-000002 → **Registrar pérdida en tránsito**.
- **Qué hacer:**
  1. Pulsa **Registrar pérdida** sin motivo; luego con motivo y sin evidencia.
  2. Motivo `Tres macetas extraviadas por el transportista`, evidencia `Acta de entrega 2026-10-29 firmada por el conductor`. Pulsa **Registrar pérdida** y confirma en el diálogo.
- **Resultado esperado:**
  - «Indique el motivo de la pérdida.» y «Indique la evidencia de la pérdida.».
  - «Pérdida en tránsito de TR-000002 registrada. La transferencia sigue «Recibida con discrepancia».».
  - El panel muestra **Pérdida en tránsito** autorizada por Laura Gómez con motivo y evidencia, «En tránsito: 0 unidad» y «… Ya no queda nada en tránsito de este faltante.». El botón desaparece.
  - **Tiempo de entrega** no cambia. MED-CEN y BOG-NOR no cambian en el catálogo.
- **Estado de los datos:** tránsito de TR-000002 en 0. **Cerrar sesión**.

**Paso 19 — Reenvío del faltante y rechazo**
- **Dónde:** Ventana B, primero `ope.bog`, luego `ope.med` y `ger.med`.
- **Qué hacer:**
  1. `ope.bog`: en **Transferencias**, filtra **Faltantes** «Alerta de faltante resuelta» y pulsa **Consultar**. Abre TR-000002 y pulsa **Solicitar reenvío del faltante**. Pulsa **Solicitar transferencia**.
  2. `ope.med`: prepara TR-000003 con `3`.
  3. `ger.med`: pulsa **Rechazar**, luego **Confirmar rechazo** sin motivo. Escribe `La reposición se gestiona con la reclamación al transportista`, confirma y acepta el diálogo.
  4. `ger.med`: abre TR-000002.
- **Resultado esperado:**
  - El filtro solo lista TR-000002. El formulario dice «Reenvío del faltante de TR-000002…», con el origen MED-CEN fijo y MAC-14 unidad `3` propuesto.
  - «Transferencia TR-000003 solicitada a Sede Medellín Centro con 1 producto, como reenvío del faltante de TR-000002…». Su detalle muestra **Reenvío de** «Faltante de TR-000002».
  - Sin motivo, el navegador exige el campo. Con motivo: «TR-000003 quedó rechazada con su motivo. Las cantidades y existencias no cambiaron.». El aviso del detalle indica quién la rechazó y el motivo; «Aprobada», «Despacho» y «Recepción» aparecen como «No realizado».
  - El faltante de TR-000002 lista **Reenvíos**: TR-000003 Rechazada. Su tránsito sigue en 0.
- **Estado de los datos:** TR-000003 rechazada. Existencias sin cambios. **Cerrar sesión**.

**Paso 20 — Cancelación por el destino antes de la aprobación**
- **Dónde:** Ventana B como `ope.bog`.
- **Qué hacer:** solicita a `MED-CEN` SUST-001 `estiba` `1`. En su detalle pulsa **Cancelar transferencia**, deja el motivo vacío (es opcional), pulsa **Confirmar cancelación** y acepta el diálogo.
- **Resultado esperado:** «TR-000004 quedó cancelada. Las existencias no cambiaron.». El detalle muestra «Cancelada» y los pasos siguientes como «No realizado».
- **Estado de los datos:** TR-000004 cancelada. **Cerrar sesión**.

---

## Fase 7 — Bogotá como origen: stock insuficiente y entrega tardía (Ventana B)

**Paso 21 — Despacho rechazado por existencias y cancelación aprobada**
- **Dónde:** Ventana B, cambiando de usuario.
- **Qué hacer:**
  1. `ope.med`: solicita a `BOG-NOR` MAC-14 unidad `40`.
  2. `ope.bog`: **Preparar envío** con `40` y guarda.
  3. `ger.bog`: **Aprobar transferencia**.
  4. `ope.bog`: **Registrar despacho** con transportista `Logística Sabana` y llegada estimada 3 horas después; confirma.
  5. `ger.bog`: **Cancelar transferencia**, primero sin motivo y luego con `Sin existencias para despachar`.
- **Resultado esperado:**
  - En la preparación: «Supera la disponibilidad actual del origen (32 unidad); el despacho no podrá confirmarse sin existencias suficientes.». Es un aviso: se puede guardar.
  - El despacho se rechaza completo: «Línea 1: existencias insuficientes en el origen: se requieren 40 y hay 32 (unidad). No se despachó la transferencia TR-000005: no hubo salida ni tránsito.» (aproximadamente). Sigue «Aprobada» y BOG-NOR conserva MAC `32`.
  - Sin motivo, la cancelación no se envía (motivo obligatorio porque está aprobada). Con motivo: «TR-000005 quedó cancelada. Las existencias no cambiaron.».
- **Estado de los datos:** TR-000005 cancelada. **Cerrar sesión**.

**Paso 22 — Despacho con llegada estimada inmediata**
- **Dónde:** Ventana B, cambiando de usuario.
- **Qué hacer:**
  1. `ope.med`: solicita a `BOG-NOR` FERT-NPK kg `10`.
  2. `ope.bog`: prepara `10`.
  3. `ger.bog`: aprueba.
  4. `ope.bog`: **Registrar despacho** con transportista `Logística Sabana` y **Llegada estimada** igual a la salida real (mismo minuto). Confirma.
  5. Espera al menos 2 minutos y pulsa **Actualizar listado**; abre el detalle.
- **Resultado esperado:** TR-000006 «En tránsito» por la ruta `RUT-BOG-MED` (Baja). Duración estimada «0 min». Pasados los minutos, el detalle indica «En tránsito, llegada estimada vencida». BOG-NOR queda en FERT `95`.
- **Estado de los datos:** TR-000006 en tránsito y vencida. **No la recibas todavía. Cerrar sesión**.

---

## Fase 8 — Cumplimiento logístico (Ventanas B y A)

**Paso 23 — Reporte del gerente con una transferencia vencida**
- **Dónde:** Ventana B como `ger.med` → **Cumplimiento**.
- **Qué hacer:**
  1. Pulsa **Generar reporte** con el período propuesto (del 1 de octubre a hoy).
  2. Pon **Desde** posterior a **Hasta** y genera.
  3. Pon `01/09/2026` a `30/09/2026` y genera.
  4. Vuelve al mes en curso.
- **Resultado esperado:**
  - Cumplimiento `50,00 %` («1 de 2 llegadas completas a tiempo»): TR-000001 cumple y TR-000002 no, aunque su alerta esté resuelta. **Completas con retraso** 0, **Recibidas con discrepancia** 1, **Vencidas en tránsito** 1 («No cuentan en el porcentaje»).
  - «Por sucursal» solo muestra MED-CEN: como origen 50,00 % y como destino «No calculable».
  - «Por ruta» muestra las dos rutas de su sucursal: RUT-MED-BOG 50,00 % y RUT-BOG-MED «No calculable».
  - «Vencidas aún en tránsito»: TR-000006 con salida, llegada estimada y transportista.
  - Fechas invertidas: «La fecha final del período no puede ser anterior a la inicial.».
  - Septiembre: «No calculable» y «Sin llegadas confirmadas en el período», nunca 0 %.
- **Estado de los datos:** sin cambios (el reporte es de solo lectura). **Cerrar sesión**.

**Paso 24 — Recepción tardía**
- **Dónde:** Ventana B como `ope.med` → detalle de TR-000006 → **Confirmar recepción**.
- **Qué hacer:** deja `10`, confirma y acepta el diálogo. Revisa **Catálogo → Mi sucursal**.
- **Resultado esperado:** «TR-000006 recibida completa…». **Tiempo de entrega**: «Recibida completa con retraso: no cumple» y «… después de lo estimado». MED-CEN: FERT `164,5`. El operador no tiene **Cumplimiento** en el menú.
- **Estado de los datos:** TR-000006 recibida completa con retraso. MED-CEN: SUST 77, FERT 164,5, MAC 113. **Cerrar sesión**.

**Paso 25 — Reporte final del gerente y del administrador**
- **Dónde:** Ventana B como `ger.bog` → **Cumplimiento**. Ventana A en **Cumplimiento**.
- **Qué hacer:**
  1. `ger.bog`: genera el mes en curso.
  2. Administrador: genera con «Toda la red»; luego elige `MED-CEN` en **Sucursal** y genera.
- **Resultado esperado:**
  - En los tres reportes: `33,33 %` («1 de 3 llegadas completas a tiempo»), **Completas con retraso** 1, **Recibidas con discrepancia** 1, **Vencidas en tránsito** 0. Las tres llegadas tocan ambas sucursales.
  - Por sucursal: `ger.bog` solo ve BOG-NOR (como origen 0,00 %, como destino 50,00 %). En la red, el administrador ve todas las sucursales: MED-CEN como origen 50,00 % y como destino 0,00 %, BOG-NOR como arriba y las demás (CAL-SUR y las demo) «No calculable». Con `MED-CEN` elegido, solo esa fila.
  - Por ruta: RUT-MED-BOG 50,00 % y RUT-BOG-MED 0,00 %.
  - Solo el administrador tiene el selector **Sucursal**. Las transferencias rechazadas y canceladas no cuentan.
- **Estado de los datos:** sin cambios.

**Paso 26 — Vista de red, filtros de estado y valoración**
- **Dónde:** Ventana A en **Transferencias**, **Movimientos** y **Valoración**.
- **Qué hacer:**
  1. En **Transferencias**, con «Toda la red», filtra **Estado** por «Recibidas completas», luego «Recibidas con discrepancia», «Canceladas» y «Rechazadas».
  2. Elige **Sucursal** `BOG-NOR`, **Sentido** «Desde la sucursal» y consulta.
  3. En **Valoración**, consulta `BOG-NOR` y luego `MED-CEN`.
- **Resultado esperado:**
  - Recibidas completas: TR-000001 y TR-000006. Con discrepancia: TR-000002 («Faltante resuelto: reclamación»). Canceladas: TR-000004 y TR-000005. Rechazadas: TR-000003.
  - Desde BOG-NOR: TR-000005 y TR-000006.
  - Valoración (las transferencias entran al costo congelado del despacho; puede haber ±0,01 por redondeo):

    | Sucursal | Producto | Existencias | Costo promedio | Valor |
    |---|---|---|---|---|
    | BOG-NOR | SUST-001 | 24 bulto | 18.310,256410 | 439.446,15 |
    | BOG-NOR | FERT-NPK | 95 kg | 4.420,333333 | 419.931,67 |
    | BOG-NOR | MAC-14 | 32 unidad | 1.384,090909 | 44.290,91 |
    | MED-CEN | SUST-001 | 77 bulto | 18.192,307692 | 1.400.807,69 |
    | MED-CEN | FERT-NPK | 164,5 kg | 4.266,93 (aprox.) | 701.909,83 |
    | MED-CEN | MAC-14 | 113 unidad | 1.384,090909 | 156.402,27 |

    Totales aproximados: BOG-NOR `903.668,73` y MED-CEN `2.259.119,79`. Las 3 macetas perdidas (≈ 4.152,27) salieron de MED-CEN y no entraron a BOG-NOR.
- **Estado de los datos:** sin cambios.

---

## Resumen de cobertura

| Funcionalidad | Pasos |
|---|---|
| Solicitud multilínea, origen válido, producto operable y línea única (RF-0032) | 2, 3, 15, 20 |
| Preparación hasta lo solicitado, ajuste e instantáneas (RF-0033) | 4, 5, 21 |
| Aprobación, rechazo y cancelación por perfil y estado (RF-0034) | 6, 7, 19, 20, 21 |
| Despacho con transportista, fechas, ruta y salida indivisible (RF-0035) | 8, 10, 11, 21, 22 |
| Recepción completa única (RF-0036) | 13, 24 |
| Recepción parcial, faltante en tránsito, sin segunda recepción (RF-0037) | 16 |
| Alerta de faltante y su resolución con tratamiento (RF-0038) | 16, 17 |
| Pérdida en tránsito y reenvío vinculado (RF-0039) | 18, 19 |
| Tiempos estimados y reales (RF-0040) | 11, 13, 16, 18, 22, 24 |
| Rutas dirigidas, prioridad y su congelación en el despacho (RF-0041) | 1, 9, 12, 13 |
| Estados y filtros (RF-0042) | 3–7, 11, 13, 16, 19, 20, 26 |
| Cumplimiento logístico por período, sucursal y ruta (RF-0043) | 23, 25 |
| Permisos por perfil y lado (§5.2) | 1, 5, 6, 12, 13, 17, 24, 25 |
| Integración con alertas de stock (RF-0020) y valoración (RF-0026-CA-02) | 14, 26 |

## Datos que deberían quedar en PostgreSQL

- **Rutas:** `RUT-MED-BOG` (MED-CEN → BOG-NOR, media, activa) y `RUT-BOG-MED` (BOG-NOR → MED-CEN, baja, activa).
- **Transferencias:**

  | Transferencia | Origen → destino | Estado | Notas |
  |---|---|---|---|
  | TR-000001 | MED-CEN → BOG-NOR | Recibida completa, a tiempo | 20 bulto, 1 saco, 25 de 30 unidad; ruta congelada en Alta |
  | TR-000002 | MED-CEN → BOG-NOR | Recibida con discrepancia | Faltan 3 MAC-14; alerta resuelta (reclamación) y pérdida en tránsito registrada |
  | TR-000003 | MED-CEN → BOG-NOR | Rechazada, con motivo | Reenvío del faltante de TR-000002 |
  | TR-000004 | MED-CEN → BOG-NOR | Cancelada sin motivo | Antes de la preparación |
  | TR-000005 | BOG-NOR → MED-CEN | Cancelada, con motivo | Despacho rechazado por existencias |
  | TR-000006 | BOG-NOR → MED-CEN | Recibida completa, con retraso | Ruta RUT-BOG-MED baja |

- **Existencias:**

  | Sucursal | SUST-001 | FERT-NPK | MAC-14 |
  |---|---|---|---|
  | MED-CEN | 77 | 164,5 | 113 |
  | BOG-NOR | 24 | 95 | 32 |

- **Tránsito:** nada pendiente. TR-000002 se liquidó con la pérdida; las demás se recibieron completas.
- **Movimientos nuevos:** 12. Son 6 «Despacho de transferencia» (3 de TR-000001, 2 de TR-000002 y 1 de TR-000006) y 6 «Recepción de transferencia» (3 de TR-000001, 2 de TR-000002 y 1 de TR-000006). La pérdida en tránsito no genera movimiento físico.
- **Alertas de stock activas:** SUST-001 y SEM-TOM en CAL-SUR (y DEMO-PLANT en DEMO-MED, de los datos demo). Las de SUST-001 y MAC-14 en BOG-NOR quedan resueltas.
- **Alertas de faltante:** una, la de TR-000002, resuelta.

## No incluidas en el recorrido y cómo verificarlas

- **Repetir una operación (idempotencia):** desde la interfaz no se puede reenviar con otra clave. En la API, repetir `POST /api/v1/transfers/{id}/receive` con otra `Idempotency-Key` sobre una transferencia recibida responde `409 ARRIVAL_ALREADY_CONFIRMED`; con la misma clave devuelve el resultado original. Para la interfaz, desactiva la red en las herramientas del navegador justo antes de confirmar, reconecta y repite sin cambios: debe quedar una sola operación.
- **Todo preparado en 0:** una transferencia aprobada con todas sus cantidades en 0 se rechaza al despachar con `NOTHING_TO_DISPATCH` («Todas las cantidades preparadas de … son 0: no se despacha…»).
- **Recepción en 0:** confirmar todas las líneas en 0 deja «Recibida con discrepancia: no llegó mercancía, no cumple», con operación de llegada y sin movimientos. Agotaría las existencias de prueba, por eso queda fuera.
- **Ruta desactivada:** desmarcar **Ruta activa: disponible para nuevos despachos** bloquea los despachos nuevos de ese trayecto; las rutas inactivas solo las ve el administrador y se reactivan, no se registran de nuevo.
- **Sucursal o producto desactivados entre la aprobación y el despacho:** el despacho revalida catálogo y sucursales y se rechaza sin cambios (`BRANCH_INACTIVE`, `PRODUCT_NOT_OPERABLE`).
- **Llegada anterior a la salida:** solo ocurre si se corrigen datos fuera de la aplicación; está cubierta por `DeliveryTimingTest` y `LogisticsComplianceHttpTest`.
- **Perfiles que intentan escribir por la API:** el administrador y el gerente de destino reciben `403 TRANSFER_FORBIDDEN`; el operador recibe `403 ROLE_FORBIDDEN` en `/api/v1/logistics/compliance` y en el alta de rutas; `ger.bog` con `branch` de MED-CEN recibe `403 BRANCH_FORBIDDEN`.
- **CAL-SUR:** no puede solicitar porque no tiene operador activo; solo se usa en el paso 2 como origen sin el producto habilitado.
