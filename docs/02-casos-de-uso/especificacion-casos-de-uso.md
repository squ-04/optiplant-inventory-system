# Especificación de casos de uso — OptiPlant

**Estado:** definitivo para modelado de casos de uso; no acredita implementación ni pruebas.  
**Versión:** 1.3 — 22 de octubre de 2026. Conserva H v1.1 y H v1.2/01; integra H v1.3/01–02 en UC-006 y UC-010: baja lógica de productos y nombre histórico del proveedor.  
**Alcance:** interacciones funcionales aprobadas del Sistema de Inventario Multi-Sucursal OptiPlant.

## 1. Información general

### 1.1 Propósito y alcance

Este documento especifica los objetivos que los actores alcanzan mediante OptiPlant y los resultados observables de esas interacciones. Es la base para los diagramas UML de casos de uso y no define API, persistencia, arquitectura, mecanismos de seguridad, ni pantallas.

El alcance cubre administración y acceso, catálogo, inventario, compras, ventas, transferencias, logística, dashboard y el módulo adicional aprobado de exportación XLSX. Las integraciones ERP, POS y cualquier otro sistema externo están excluidas: **Sistema externo** no se modela como actor operativo.

### 1.2 Jerarquía y documentación de referencia

| Precedencia | Fuente | Uso en esta especificación |
|---|---|---|
| 1 | `docs/00-source/prueba-tecnica.pdf` | Fuente definitiva de obligaciones externas. |
| 2 | `docs/00-source/prueba-tecnica.md` | Transcripción operativa para localización de requisitos. |
| 3 | `docs/01-requerimientos/requisitos-funcionales.md` v1.2 | Catálogo funcional aprobado, 55 RF y sus precisiones humanas posteriores integradas. |
| 4 | `docs/01-requerimientos/decisiones-aprobadas-optiplant-v1.0.md` | Decisiones ratificadas aplicables a permisos, reglas y adicional. |
| 5 | `docs/01-requerimientos/restricciones-tecnicas-y-de-negocio.md` y `supuestos-y-dependencias-del-sistema.md` | Límites, restricciones y supuestos que condicionan los flujos. |
| 6 | `AGENTS.md` | Convenciones del proyecto, decisiones aprobadas y exclusiones. |

Cuando un antecedente difiere de una decisión humana posterior integrada en el catálogo funcional, prevalece esta última. En particular, cada despacho admite una sola confirmación de llegada; una recepción parcial queda como **Recibida con discrepancia** y no admite recepciones complementarias contra ese despacho.

### 1.3 Actores y responsabilidades

| Actor | Responsabilidad y ámbito |
|---|---|
| **Administrador general** | Mantiene configuración aprobada, usuarios, sucursales, catálogo, proveedores mínimos, listas de precios y rutas. Consulta toda la red, su dashboard y comparativas; exporta una o todas las sucursales. No ejecuta por herencia compras, ventas, movimientos locales, solicitudes, aprobaciones ni cancelaciones de transferencias. |
| **Gerente de sucursal** | Supervisa su sucursal; realiza ajustes y correcciones de existencias propios con trazabilidad; aprueba transferencias de origen y puede rechazarlas con motivo exclusivamente desde «en preparación», antes de aprobar; gestiona discrepancias de origen o destino; consulta dashboard, valoración, reportes y exportaciones de su sucursal. |
| **Operador de inventario** | Ejecuta las operaciones locales autorizadas: compras, recepciones de compra, ventas, ingresos, retiros ordinarios, solicitudes de transferencia desde su destino, preparación y despacho de origen y recepción de destino. Consulta información operacional propia; no exporta ni realiza ajustes manuales. |
| **Usuario interno** | Generalización conceptual de los tres roles anteriores. Accede a funciones protegidas conforme a su rol, sucursal activa y permiso. No es un rol adicional. |

Todos los usuarios tienen exactamente un rol. El administrador es global; gerente y operador están vinculados a una única sucursal. La consulta de otra sucursal no autoriza escritura sobre ella.

### 1.4 Organización del catálogo

| Módulo | Casos de uso |
|---|---|
| Administración, identidad y acceso | UC-001 a UC-005 |
| Catálogo e inventario | UC-006 a UC-009 |
| Compras y valoración | UC-010 a UC-012 |
| Ventas | UC-013 a UC-014 |
| Transferencias | UC-015 a UC-017 |
| Logística | UC-018 |
| Dashboard | UC-019 |
| Reportes exportables | UC-020 |

## 2. Catálogo de casos de uso

### 2.1 Administración, identidad y acceso

#### UC-001 — Gestionar configuración de stock mínimo
- **Objetivo:** consultar o modificar el stock mínimo por producto y sucursal para reutilizarlo en alertas e indicadores de agotamiento.
- **Actor principal:** Administrador general. **RF:** RF-0001, RF-0019, RF-0020, RF-0048.
- **Precondiciones:** usuario autenticado y autorizado; producto habilitado en la sucursal.
- **Disparador:** el administrador necesita revisar o cambiar el mínimo.
- **Flujo principal:** 1. Consulta el mínimo de producto-sucursal. 2. Indica un valor explícito, incluido cero. 3. El sistema valida y guarda el valor. 4. Reevalúa la condición `disponible <= mínimo` para alertas e indicadores.
- **Alternativos y excepciones:** si no existe un mínimo explícito, el producto no queda operable en esa sucursal; no se asume cero. Si el dato no es válido, se rechaza sin cambios.
- **Postcondiciones:** el mismo umbral queda disponible para reabastecimiento y productos próximos a agotarse; no cambian existencias, costos ni transacciones.
- **Reglas:** una alerta activa por producto-sucursal; no genera compras ni bloquea operaciones automáticamente.

#### UC-002 — Gestionar usuarios
- **Objetivo:** crear, consultar, actualizar, listar y desactivar cuentas de usuarios sin eliminar su historia.
- **Actor principal:** Administrador general. **RF:** RF-0002, RF-0006, RF-0007.
- **Precondiciones:** administrador autenticado; para gerente u operador, sucursal activa disponible.
- **Disparador:** se requiere administrar una cuenta.
- **Flujo principal:** 1. Selecciona crear, consultar, actualizar o desactivar. 2. Para alta, registra nombre, código único y exactamente un rol; asigna una sucursal activa si el rol es gerente u operador. 3. El sistema genera identidad estable y registra el estado. 4. Para una actualización permitida, conserva identidad e historial. 5. Para baja, deja la cuenta inactiva y revoca accesos previos.
- **Alternativos y excepciones:** código duplicado, cero/múltiples roles o asignación de sucursal inválida se rechazan sin cambios. Una reasignación permitida conserva la autoría histórica y los pendientes de la sucursal; no se bloquea por su creador original.
- **Postcondiciones:** la cuenta refleja el estado y asignación vigentes; no hay eliminación física ni exposición de credenciales.
- **Reglas:** las cuentas inactivas no pueden autenticarse; no hay autorregistro, invitación por correo ni recuperación automática.

#### UC-003 — Gestionar sucursales
- **Objetivo:** crear, consultar, actualizar y desactivar sucursales de la organización conservando su trazabilidad.
- **Actor principal:** Administrador general. **RF:** RF-0003, RF-0005.
- **Precondiciones:** administrador autenticado.
- **Disparador:** se necesita administrar una sucursal.
- **Flujo principal:** 1. Selecciona alta, consulta, actualización o desactivación. 2. En un alta registra código único y nombre. 3. El sistema genera la identidad, conserva el estado y permite la consulta. 4. En una baja valida los impedimentos operativos. 5. Si no existen, marca la sucursal inactiva y conserva su historial.
- **Alternativos y excepciones:** se rechaza código duplicado. Se rechaza la baja con existencias no cero, borradores operables, compras pendientes de recepción, transferencias abiertas o faltantes/valor de tránsito pendientes. No se cancelan documentos ni se ajusta inventario automáticamente.
- **Postcondiciones:** una sucursal inactiva sigue siendo consultable por perfiles autorizados, pero no admite nuevas operaciones ni puede elegirse como origen o destino.
- **Reglas:** no hay eliminación física, dirección, contacto ni desactivación automática de usuarios asociados.

#### UC-004 — Autenticar usuario
- **Objetivo:** iniciar una sesión para una cuenta activa mediante código único y contraseña personal.
- **Actor principal:** Usuario interno. **RF:** RF-0006.
- **Precondiciones:** cuenta registrada y activa.
- **Disparador:** el usuario solicita iniciar sesión.
- **Flujo principal:** 1. Ingresa código y contraseña. 2. El sistema verifica credenciales y estado. 3. Si son válidos, establece sesión autenticada. 4. Si es el primer ingreso con contraseña temporal, exige cambiarla antes de operar.
- **Alternativos y excepciones:** código inexistente, contraseña incorrecta, cuenta inactiva o contraseña nueva fuera de 15–128 caracteres se rechazan sin sesión ni cambio de credenciales. Al cerrar, vencer o revocar la sesión, se exige autenticación nueva.
- **Postcondiciones:** existe una sesión válida o el acceso queda rechazado; hechos previamente confirmados no se deshacen.
- **Reglas:** la contraseña no se consulta ni registra en texto visible; no hay MFA ni recuperación automática por correo.

#### UC-005 — Consultar información y ejecutar operación autorizada
- **Objetivo:** acceder a datos u operaciones protegidas dentro del permiso y ámbito de sucursal aplicables.
- **Actor principal:** Usuario interno. **RF:** RF-0004, RF-0005, RF-0007, RF-0014.
- **Precondiciones:** sesión activa; recurso y sucursal existentes.
- **Disparador:** el usuario intenta consultar o ejecutar una capacidad del sistema.
- **Flujo principal:** 1. Solicita la función. 2. El sistema determina rol, estado de cuenta y ámbito. 3. Permite la consulta u operación cuando la matriz de permisos lo autoriza. 4. Restringe los datos y efectos al ámbito permitido.
- **Alternativos y excepciones:** una cuenta desactivada, sesión revocada, referencia de otra sucursal para escritura o permiso inexistente se rechazan sin revelar datos protegidos ni modificar registros.
- **Postcondiciones:** solo se entrega información y se ejecutan efectos autorizados.
- **Reglas:** visibilidad global del administrador no hereda la ejecución local; el gerente no escribe fuera de su sucursal; el operador no consulta valoración ni costos agregados ajenos.

### 2.2 Catálogo e inventario

#### UC-006 — Administrar catálogo, habilitación local y unidades
- **Objetivo:** mantener productos, sus unidades y su disponibilidad operativa por sucursal.
- **Actor principal:** Administrador general. **Actores secundarios:** Gerente de sucursal y Operador de inventario como consultantes/usuarios de unidades. **RF:** RF-0008 a RF-0013.
- **Precondiciones:** administrador autenticado; para habilitar, sucursal activa.
- **Disparador:** se necesita crear, consultar, actualizar o retirar un producto, o definir sus unidades.
- **Flujo principal:** 1. Registra o selecciona un producto. 2. Define SKU único, datos permitidos, unidad base y conversiones propias. 3. Habilita explícitamente el producto en las sucursales requeridas y define su mínimo. 4. El sistema permite consulta del catálogo general y local. 5. Las operaciones posteriores usan las unidades y factores vigentes.
- **Alternativos y excepciones:** SKU duplicado, fracciones no admitidas o precisión superior a tres decimales en unidad base se rechazan. La eliminación de cualquier producto, con o sin referencias, movimientos o dependencias, es exclusivamente lógica mediante `active=FALSE`, solo con stock cero en todas las sucursales y sin pendientes; no se permite borrado físico (H v1.3/01, RF-0011). Un producto inactivo no admite nuevas operaciones.
- **Postcondiciones:** catálogo, habilitaciones y factores quedan disponibles sin crear existencias.
- **Reglas:** unidad base inmutable tras uso; cambios de conversión no reexpresan documentos ni saldos históricos. Solo el administrador reactiva un producto inactivo; la reactivación no restaura habilitaciones locales ni mínimos previos, por lo que cada sucursal debe habilitarlo y configurar un nuevo mínimo antes de operar.

#### UC-007 — Consultar inventario y catálogo local
- **Objetivo:** consultar catálogo y existencias propias o de otra sucursal autorizada.
- **Actor principal:** Usuario interno. **RF:** RF-0012, RF-0014, RF-0015.
- **Precondiciones:** sesión autorizada; sucursal consultada existente.
- **Disparador:** el usuario solicita inventario o catálogo de una sucursal.
- **Flujo principal:** 1. Selecciona la sucursal autorizada. 2. El sistema muestra productos habilitados, físico, disponible y tránsito diferenciados. 3. Si se consulta otra sucursal, muestra su inventario sin habilitar escritura cruzada. 4. Tras un movimiento confirmado, la información compartida se actualiza según la plataforma conectada.
- **Alternativos y excepciones:** ante pérdida de comunicación, se advierte posible desactualización; una operación no aceptada no se presenta como confirmada ni se guarda para sincronización offline.
- **Postcondiciones:** no se alteran inventario ni catálogo.
- **Reglas:** catálogo local significa producto habilitado, incluso con stock cero; no hay reservas ni operación offline.

#### UC-008 — Registrar movimiento de inventario y consultar historial
- **Objetivo:** registrar ingresos, retiros, devoluciones, mermas o ajustes autorizados y conservar el historial auditable.
- **Actor principal:** Operador de inventario. **Actor secundario:** Gerente de sucursal para ajustes/correcciones propios. **RF:** RF-0016 a RF-0018.
- **Precondiciones:** sesión autorizada; sucursal activa; producto habilitado y unidad válida.
- **Disparador:** se necesita registrar un movimiento local.
- **Flujo principal:** 1. Selecciona tipo y motivo. 2. Indica producto, unidad, cantidad positiva y referencia cuando exista. 3. El sistema valida autorización, disponibilidad y reglas de unidad. 4. Confirma el movimiento, actualiza el saldo aplicable y guarda fecha/hora, responsable, motivo, cantidad y referencia. 5. El usuario autorizado puede consultar el historial.
- **Alternativos y excepciones:** un retiro que produciría físico negativo se rechaza íntegramente. Una devolución referenciada no supera lo vendido menos devoluciones previas. Un hecho confirmado no se edita: una corrección se registra como compensación vinculada; la corrección de una entrada identificada usa costo histórico y el ajuste por conteo usa promedio vigente.
- **Postcondiciones:** el movimiento queda inmutable y auditable; no hay edición directa de saldo.
- **Reglas:** compras, ventas y transferencias crean sus movimientos propios; no deben duplicarse mediante registro manual equivalente.

#### UC-009 — Consultar y atender alertas de inventario
- **Objetivo:** identificar productos en condición de reabastecimiento y conservar el ciclo de la alerta.
- **Actor principal:** Gerente de sucursal. **Actor secundario:** Administrador general; Operador de inventario como consultante operacional. **RF:** RF-0019, RF-0020, RF-0048.
- **Precondiciones:** mínimo explícito configurado para producto-sucursal.
- **Disparador:** el disponible alcanza o cae por debajo del mínimo, o un actor consulta los indicadores.
- **Flujo principal:** 1. El sistema evalúa el disponible contra el mínimo. 2. Crea o actualiza una única alerta activa por producto-sucursal. 3. Presenta la alerta e indicador al actor autorizado. 4. Cuando el disponible supera el mínimo, resuelve la alerta y conserva su historia.
- **Alternativos y excepciones:** evaluaciones repetidas no duplican la alerta. La ausencia de mínimo explícito impide operar el producto en la sucursal.
- **Postcondiciones:** la condición y su historial son consultables.
- **Reglas:** esta alerta es independiente de la alerta de faltante de transferencias; no envía correo/SMS ni crea órdenes automáticamente.

### 2.3 Compras y valoración

#### UC-010 — Gestionar orden de compra y condiciones
- **Objetivo:** crear y administrar órdenes de compra con proveedor, productos, precio, descuento y plazo.
- **Actor principal:** Operador de inventario. **Actor secundario:** Administrador general para proveedores; Gerente de sucursal y Administrador como supervisores/consultantes. **RF:** RF-0021 a RF-0023, RF-0025.
- **Precondiciones:** sucursal activa; proveedor y productos válidos/habilitados; operador autorizado.
- **Disparador:** se requiere adquirir productos o consultar el histórico de compras.
- **Flujo principal:** 1. Selecciona proveedor y líneas. 2. Registra precio bruto por unidad comercial, descuento de línea y plazo. 3. El sistema guarda la orden como borrador con el nombre vigente del proveedor capturado y no nulo. 4. El operador la gestiona hacia pendiente de recepción, cancelación o consulta; al pasar a pendiente, el sistema actualiza el nombre histórico con el vigente y lo congela desde esa transición, sin que cambios posteriores del proveedor lo modifiquen (H v1.3/02, RF-0021-CA-03). 5. Los perfiles autorizados consultan el histórico por proveedor o producto.
- **Alternativos y excepciones:** descuento fuera de 0–100 %, plazo negativo/no entero o proveedor con código duplicado se rechazan. Solo se editan borradores; una orden pendiente que requiere cambios se cancela y se reemplaza. No se borra una orden recibida.
- **Postcondiciones:** se conserva orden, condiciones, estado e histórico; no se cambia inventario ni se generan pagos.
- **Reglas:** moneda COP, sin impuestos, fletes, divisas, tesorería ni compras parciales.

#### UC-011 — Confirmar recepción de compra y valorar inventario
- **Objetivo:** recibir una orden pendiente completa una única vez, aumentar inventario y recalcular su costo promedio ponderado.
- **Actor principal:** Operador de inventario. **RF:** RF-0024, RF-0026.
- **Precondiciones:** orden pendiente de recepción; sucursal activa; operador autorizado.
- **Disparador:** llega la mercancía correspondiente a la orden.
- **Flujo principal:** 1. Selecciona la orden pendiente. 2. Confirma recepción completa de sus líneas vigentes. 3. El sistema registra los ingresos auditables. 4. Incrementa inventario una sola vez. 5. Calcula el promedio móvil por producto-sucursal a partir del costo neto y valor existente. 6. Marca la orden como recibida.
- **Alternativos y excepciones:** recepción parcial o repetición de una recepción confirmada no modifica inventario, valor ni movimientos. Una orden cancelada no altera costo promedio.
- **Postcondiciones:** orden recibida, ingresos trazables e inventario/valor actualizados una vez.
- **Reglas:** al agotar existencias por una salida ordinaria se consume el valor residual; correcciones históricas que agoten cantidad registran una diferencia de valoración separada. No se recalcula historia, no se altera precio de venta ni se habilita contabilidad financiera.

#### UC-012 — Consultar compras y valoración autorizada
- **Objetivo:** consultar histórico de compras y valoración dentro del ámbito permitido.
- **Actor principal:** Administrador general o Gerente de sucursal. **RF:** RF-0025, RF-0026.
- **Precondiciones:** sesión autorizada.
- **Disparador:** se solicita el histórico por proveedor/producto o el valor promedio de inventario.
- **Flujo principal:** 1. El actor define el ámbito y criterio de consulta. 2. El sistema recupera órdenes con sus estados y recepciones. 3. Presenta las compras y, cuando está autorizado, la valoración por producto-sucursal.
- **Alternativos y excepciones:** el operador no obtiene valoración/costos agregados. Una orden cancelada no se presenta como recibida.
- **Postcondiciones:** no se modifican órdenes, movimientos ni valoración.
- **Reglas:** el historial distingue borrador, pendiente, cancelada y recibida; una salida no cambia por sí sola el promedio vigente.

### 2.4 Ventas

#### UC-013 — Gestionar listas de precios
- **Objetivo:** mantener listas de precios globales y permitir su consulta/uso en ventas autorizadas.
- **Actor principal:** Administrador general. **Actor secundario:** Operador de inventario. **RF:** RF-0030.
- **Precondiciones:** productos y unidades existentes; administrador autenticado.
- **Disparador:** se necesita crear, actualizar o consultar una lista.
- **Flujo principal:** 1. El administrador define listas globales y precios por producto-unidad. 2. El sistema conserva sus valores. 3. El operador consulta y selecciona una lista al registrar una venta.
- **Alternativos y excepciones:** no se asignan automáticamente listas por cliente o sucursal; un operador no administra listas.
- **Postcondiciones:** las listas quedan disponibles para ventas posteriores.
- **Reglas:** una venta confirmada conserva lista y precio históricos; no hay gestión de clientes ni precio libre fuera del descuento aprobado.

#### UC-014 — Registrar venta y consultar comprobante
- **Objetivo:** confirmar una venta válida, descontar inventario y disponer de su registro posterior.
- **Actor principal:** Operador de inventario. **Actor secundario:** Gerente de sucursal y Administrador general como consultantes autorizados. **RF:** RF-0027 a RF-0031.
- **Precondiciones:** sucursal activa; producto habilitado; lista/precio y unidad válidos; operador autorizado.
- **Disparador:** el operador registra una venta.
- **Flujo principal:** 1. Indica líneas, cantidades, unidades, lista de precios y descuento por línea. 2. El sistema valida el stock disponible de todas las líneas. 3. Si es suficiente, confirma la venta indivisiblemente. 4. Registra retiro auditable, comprobante interno y datos comerciales históricos. 5. Los perfiles autorizados consultan el comprobante.
- **Alternativos y excepciones:** disponibilidad insuficiente, descuento fuera de 0–100 % o confirmación competidora que agota stock rechazan toda la venta sin retiro, comprobante ni efecto parcial. La repetición técnica no duplica venta ni retiro.
- **Postcondiciones:** venta y comprobante inmutables; inventario disminuido una sola vez.
- **Reglas:** devolución física se registra como movimiento vinculado, sin reembolso ni anulación financiera automática; no hay factura fiscal ni cobro electrónico.

### 2.5 Transferencias

#### UC-015 — Solicitar, preparar, aprobar y despachar transferencia
- **Objetivo:** trasladar productos entre sucursales mediante solicitud, preparación, aprobación de origen y despacho trazable.
- **Actor principal:** Operador de inventario. **Actores secundarios:** Gerente de sucursal de origen; Operador de origen/destino. **RF:** RF-0032 a RF-0035.
- **Precondiciones:** origen y destino activos y distintos; productos habilitados; sesión y permisos válidos.
- **Disparador:** el operador del destino requiere productos de una sucursal origen.
- **Flujo principal:** 1. El operador del destino crea solicitud con origen, destino y líneas. 2. El operador de origen revisa disponibilidad y prepara cantidades iguales o menores que las solicitadas. 3. El gerente de origen aprueba la transferencia. 4. El operador de origen registra salida real, llegada estimada y transportista. 5. El sistema revalida todas las líneas, descuenta físico en origen y registra tránsito al costo congelado.
- **Alternativos y excepciones:** no se permite solicitud por administrador, origen/destino iguales o inactivos, producto no habilitado o unidad incompatible. Preparar no reserva ni descuenta. H v1.2/01: el gerente de origen puede rechazar con motivo obligatorio exclusivamente desde «en preparación», antes de aprobar, pasando a «rechazada» sin cambios de cantidades, stock o tránsito. Un intento sin motivo, por otro actor o en otro estado se deniega sin cambios; el gerente de destino no recibe ese permiso por supervisar recepción. Si una línea no tiene stock al despacho, se rechaza todo el intento sin convertir por ello la transferencia en «rechazada». El destino puede cancelar antes de aprobación; después y antes de despacho solo el gerente origen puede cancelar justificadamente; no se cancela en tránsito.
- **Postcondiciones:** en el flujo principal, transferencia en tránsito con cantidades solicitadas, preparadas y despachadas diferenciadas; destino aún no aumenta físico. En rechazo de negocio válido, transferencia «rechazada» con motivo y responsable, sin aprobación, despacho ni cambios de inventario; las cancelaciones conservan su resultado propio.
- **Reglas:** no hay urgencia, varios despachos por pedido, CRUD de transportistas ni seguimiento externo. Reintentos técnicos no duplican salida ni tránsito.

#### UC-016 — Confirmar recepción de transferencia
- **Objetivo:** registrar una única llegada de transferencia; su flujo principal cubre la recepción completa y UC-017 cubre la recepción parcial.
- **Actor principal:** Operador de inventario de destino. **Actor secundario:** Gerente de sucursal de destino. **RF:** RF-0036.
- **Precondiciones:** transferencia en tránsito; responsable autorizado en destino.
- **Disparador:** llega la totalidad de lo despachado.
- **Flujo principal:** 1. Selecciona la transferencia enviada. 2. Confirma cantidades coincidentes con todas las líneas despachadas. 3. El sistema registra ingreso auditado al destino, reduce tránsito y conserva costo congelado. 4. Cambia el estado a recibida completa.
- **Alternativos y excepciones:** una repetición técnica no genera nuevos ingresos, valor, cambios de estado ni movimientos. Una nueva entrega física no puede anexarse: requiere otra transferencia y despacho.
- **Postcondiciones:** el destino tiene físico recibido, tránsito pendiente cero y una única llegada confirmada.
- **Reglas:** el gerente supervisa/valida, pero el operador registra la recepción.

#### UC-017 — Registrar recepción parcial y gestionar faltante
- **Objetivo:** documentar una llegada parcial, alertar su diferencia y registrar el tratamiento sin alterar automáticamente inventario o estado.
- **Actor principal:** Operador de inventario de destino para recepción. **Actor secundario:** Gerente de origen o destino para gestión del faltante. **RF:** RF-0037 a RF-0039.
- **Precondiciones:** transferencia en tránsito; llegada con cantidad recibida menor a despachada; actores autorizados.
- **Disparador:** el responsable constata una diferencia al recibir.
- **Flujo principal:** 1. Registra una única recepción con cantidades recibidas. 2. El sistema ingresa solo lo recibido al destino, mantiene el faltante/tránsito correspondiente al costo congelado y deja la transferencia **Recibida con discrepancia**. 3. Genera alerta vinculada. 4. Un gerente autorizado selecciona reenvío, ajuste o reclamación y marca la alerta como **Resuelto**. 5. El sistema guarda tratamiento, responsable y fecha.
- **Alternativos y excepciones:** sobrantes y segunda recepción del mismo despacho se rechazan. Resolver sin tratamiento o por actor no autorizado se rechaza. Una llegada confirmada de cero unidades se admite como recepción parcial: no crea movimiento físico ni incrementa destino; registra faltante total, mantiene tránsito/valor al costo congelado, deja «Recibida con discrepancia» y genera la alerta vinculada.
- **Postcondiciones:** la recepción y discrepancia son históricas; la alerta puede quedar resuelta, sin cambiar cantidades, valor, tránsito ni estado de la transferencia.
- **Reglas:** `Resuelto` no ejecuta reenvío, ajuste ni reclamación. Una pérdida reconocida se registra aparte, autorizada y trazable, reduciendo tránsito/valor una sola vez; un reenvío requiere una nueva transferencia y despacho.

### 2.6 Logística

#### UC-018 — Administrar y consultar logística de transferencias
- **Objetivo:** clasificar rutas, consultar estados y tiempos de transferencia, y generar cumplimiento por sucursal o ruta.
- **Actor principal:** Administrador general para rutas; Gerente de sucursal para reportes. **Actor secundario:** Usuario interno autorizado para consulta de estados. **RF:** RF-0040 a RF-0043.
- **Precondiciones:** transferencia y/o ruta existentes; sesión autorizada.
- **Disparador:** se necesita mantener ruta, revisar un traslado o evaluar cumplimiento.
- **Flujo principal:** 1. El administrador clasifica la ruta origen-destino con prioridad alta, media o baja. 2. El usuario consulta el estado de una transferencia. 3. El sistema muestra solicitada, preparación, aprobada, tránsito, recibida completa, **Recibida con discrepancia**, cancelada o rechazada según eventos. 4. Al existir llegada confirmada, compara salida real con llegada estimada y real. 5. El gerente genera el reporte por sucursal o ruta.
- **Alternativos y excepciones:** sin llegada confirmada no se inventa duración ni se incluye el traslado en el denominador del KPI; se muestra como tránsito vencido si aplica. Sin llegadas confirmadas, el resultado es **No calculable**.
- **Postcondiciones:** rutas, estados y reportes son consultables; no se alteran transferencias por una consulta.
- **Reglas:** el tiempo real termina en la única llegada, completa o con discrepancia. El KPI es llegadas completas puntuales / llegadas confirmadas del período; discrepancias cuentan como no cumplidas, aunque su alerta esté resuelta.

### 2.7 Dashboard

#### UC-019 — Consultar dashboard e indicadores
- **Objetivo:** consultar indicadores operativos y comparativas permitidas para apoyar la operación.
- **Actor principal:** Gerente de sucursal. **Actor secundario:** Administrador general; Operador de inventario para panel local operacional. **RF:** RF-0044 a RF-0049.
- **Precondiciones:** sesión autorizada; datos operativos dentro del ámbito consultado.
- **Disparador:** el actor abre el dashboard.
- **Flujo principal:** 1. El sistema determina ámbito y período. 2. Muestra ventas del mes y tres meses anteriores, rotación, demanda alta/baja, transferencias/impacto y próximos a agotarse. 3. Para administrador, permite comparar sucursales con las mismas métricas y período. 4. Distingue cero, falta de datos y **No calculable** según aplique.
- **Alternativos y excepciones:** gerente y operador no acceden a comparativa de rendimiento de red. Con historia insuficiente para promedio diario de rotación o denominador cero, muestra **No calculable**. Un ranking no mezcla unidades heterogéneas ni solapa grupos de alta y baja demanda.
- **Postcondiciones:** no se modifican datos operativos.
- **Reglas:** ventas son netas de descuentos; rotación es ventas del período / promedio temporal diario de físico; el indicador de agotamiento reutiliza el mínimo de UC-001.

### 2.8 Reportes exportables

#### UC-020 — Exportar reporte XLSX
- **Objetivo:** obtener un archivo XLSX reutilizable de movimientos, ventas o transferencias dentro del ámbito autorizado.
- **Actor principal:** Administrador general o Gerente de sucursal. **RF:** RF-0050 a RF-0055.
- **Precondiciones:** sesión autorizada; fechas inicial y final válidas; categoría de reporte seleccionada.
- **Disparador:** el actor solicita una exportación.
- **Flujo principal:** 1. Selecciona categoría, sucursal permitida y rango de fechas. 2. El sistema aplica la semántica temporal: movimientos por registro, ventas por confirmación y transferencias por solicitud. 3. Valida permisos, rango inclusivo America/Bogota y límites. 4. Genera descarga directa de un libro XLSX real. 5. Incluye columnas aprobadas por categoría y metadatos del filtro.
- **Alternativos y excepciones:** inicio posterior al final, rango mayor a 12 meses o más de 10.000 filas se rechazan sin truncar ni modificar operaciones. Sin resultados se entrega XLSX válido con encabezados y nota explícita. Operador o gerente fuera de su sucursal no obtienen archivo.
- **Postcondiciones:** archivo externo coherente con los datos autorizados; los registros de origen no cambian.
- **Reglas:** movimientos no exponen costos ni claves internas; ventas tienen una fila por línea sin duplicar total de cabecera; transferencias tienen una fila por línea y no se duplican por reintentos. No hay PDF, repositorio de archivos ni exportación asíncrona.

## 3. Relaciones UML entre casos de uso

Las relaciones siguientes son las únicas recomendadas para el diagrama. Las validaciones internas, cálculos de costo, actualizaciones de inventario y verificaciones de permiso se describen dentro de los flujos; no se crean casos de uso artificiales para ellas.

| Relación | Justificación UML |
|---|---|
| **Generalización:** Administrador general, Gerente de sucursal y Operador de inventario especializan a **Usuario interno**. | Comparten la necesidad de sesión y autorización, pero tienen responsabilidades distintas. |
| **Asociación:** Administrador ↔ UC-001, UC-002, UC-003, UC-006, UC-013, UC-018, UC-019, UC-020. | Son objetivos directos de su rol, bajo límites de la matriz de permisos. |
| **Asociación:** Gerente ↔ UC-008, UC-009, UC-012, UC-015, UC-016, UC-017, UC-018, UC-019, UC-020. | Refleja supervisión, ajustes propios, aprobación/rechazo de origen según fase y gestión de discrepancias, sin atribuir preparación o despacho al gerente. |
| **Asociación:** Operador ↔ UC-007, UC-008, UC-010, UC-011, UC-014, UC-015, UC-016, UC-017, UC-019. | Refleja operaciones locales autorizadas y excluye exportación, ajustes manuales y comparativas de red. |
| **`<<extend>>`: UC-017 extiende UC-016 — Confirmar recepción de transferencia.** | Solo ocurre cuando la llegada confirmada tiene faltantes; la condición produce la alternativa de recepción parcial y discrepancia. |
| **Generalización:** Exportar movimientos, Exportar ventas y Exportar transferencias especializan UC-020 — Exportar reporte XLSX. | Son variantes del mismo objetivo de exportación, con formato, fechas, límites y permisos comunes, pero columnas y semántica temporal propias. |

No se modela una relación `<<include>>` con UC-004 para cada caso protegido: la autenticación es precondición transversal, no un objetivo invocado funcionalmente por el actor durante cada caso. Tampoco se modela `Sistema externo` ni relaciones para reintentos técnicos, validación de stock o cálculo de costo, porque no son objetivos autónomos del actor.

## 4. Matriz de trazabilidad requisito–caso de uso

| Requisito funcional | Caso(s) de uso que lo cubren |
|---|---|
| RF-0001 | UC-001 |
| RF-0002 | UC-002 |
| RF-0003 | UC-003 |
| RF-0004 | UC-005 |
| RF-0005 | UC-003, UC-005, UC-007 a UC-018 |
| RF-0006 | UC-004 |
| RF-0007 | UC-005; precondición transversal de UC-001 a UC-020 |
| RF-0008 | UC-006 |
| RF-0009 | UC-006 |
| RF-0010 | UC-006 |
| RF-0011 | UC-006 |
| RF-0012 | UC-007 |
| RF-0013 | UC-006, UC-008, UC-010, UC-014, UC-015 |
| RF-0014 | UC-005, UC-007 |
| RF-0015 | UC-007 |
| RF-0016 | UC-008 |
| RF-0017 | UC-008 |
| RF-0018 | UC-008 |
| RF-0019 | UC-001, UC-009 |
| RF-0020 | UC-001, UC-009 |
| RF-0021 | UC-010 |
| RF-0022 | UC-010 |
| RF-0023 | UC-010 |
| RF-0024 | UC-011 |
| RF-0025 | UC-010, UC-012 |
| RF-0026 | UC-011, UC-012 |
| RF-0027 | UC-014 |
| RF-0028 | UC-014 |
| RF-0029 | UC-014 |
| RF-0030 | UC-013, UC-014 |
| RF-0031 | UC-014 |
| RF-0032 | UC-015 |
| RF-0033 | UC-015 |
| RF-0034 | UC-015 |
| RF-0035 | UC-015 |
| RF-0036 | UC-016 |
| RF-0037 | UC-017 |
| RF-0038 | UC-017 |
| RF-0039 | UC-017 |
| RF-0040 | UC-018 |
| RF-0041 | UC-018 |
| RF-0042 | UC-018 |
| RF-0043 | UC-018 |
| RF-0044 | UC-019 |
| RF-0045 | UC-019 |
| RF-0046 | UC-019 |
| RF-0047 | UC-019 |
| RF-0048 | UC-001, UC-009, UC-019 |
| RF-0049 | UC-019 |
| RF-0050 | UC-020 |
| RF-0051 | UC-020 |
| RF-0052 | UC-020 |
| RF-0053 | UC-020 |
| RF-0054 | UC-020 |
| RF-0055 | UC-020 |

### 4.1 Comprobación de cobertura

- Los 55 requisitos funcionales aprobados (RF-0001 a RF-0055) tienen al menos un caso de uso asociado.
- Las capacidades transversales RF-0005 y RF-0007 se aplican como regla y precondición, sin crear casos técnicos artificiales.
- Las decisiones posteriores sobre recepción única, discrepancias, permisos de solicitud, indicadores y exportación están reflejadas en UC-015 a UC-020.
- H v1.1 incorpora reactivación exclusiva del administrador sin restauración local automática y llegada cero como recepción parcial sin movimiento físico; ambas se trazan a UC-006 y UC-017.
- H v1.2/01 completa UC-015: rechazo motivado exclusivo del gerente de origen desde «en preparación», antes de aprobar; no añade casos de uso, actores ni relaciones UML.

## 5. Limitaciones conservadas

Este documento no sustituye la especificación de diagramas UML, diseño de API, arquitectura, modelo de datos, requisitos no funcionales ni pruebas. Los mecanismos técnicos de autorización, revocación, actualización de inventario, control de concurrencia, precisión y generación XLSX permanecen en sus artefactos de diseño/RNF. Tales límites no reducen los resultados funcionales ya aprobados ni autorizan inferir funciones adicionales.
