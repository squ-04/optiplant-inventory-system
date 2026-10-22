# Historias de usuario — OptiPlant

**Estado:** elaborado a partir del catálogo funcional aprobado v1.0 y las decisiones ratificadas; no acredita implementación ni pruebas.  
**Alcance:** necesidades funcionales de los actores internos de OptiPlant. No define arquitectura, API, persistencia, pantallas ni mecanismos técnicos.  
**Versión:** 1.3 — 22 de octubre de 2026. Conserva H v1.1 y H v1.2/01; integra H v1.3/01–02 en HU-005 y HU-009: baja lógica de productos y nombre histórico del proveedor.

## 1. Información general

### 1.1 Propósito

Este documento comunica el valor de las capacidades aprobadas desde la perspectiva de los actores de OptiPlant. Agrupa requisitos relacionados en objetivos verificables, sin transformar validaciones internas, detalles técnicos o decisiones de diseño en historias artificiales.

### 1.2 Alcance y jerarquía documental

| Precedencia | Fuente | Uso en este documento |
|---|---|---|
| 1 | `docs/00-source/prueba-tecnica.pdf` | Fuente externa definitiva de obligaciones. |
| 2 | `docs/00-source/prueba-tecnica.md` | Transcripción operativa para localizar la fuente. |
| 3 | `docs/01-requerimientos/requisitos-funcionales.md` v1.0 | Fuente principal de los 55 RF, criterios y precisiones humanas integradas. |
| 4 | `docs/01-requerimientos/decisiones-aprobadas-optiplant-v1.0.md` | Decisiones funcionales ratificadas y exclusiones. |
| 5 | `docs/01-requerimientos/restricciones-tecnicas-y-de-negocio.md` y `supuestos-y-dependencias-del-sistema.md` | Restricciones y supuestos que condicionan las historias. |
| 6 | `docs/02-casos-de-uso/especificacion-casos-de-uso.md` | Referencia complementaria de actores, objetivos y casos de uso. |
| 7 | `AGENTS.md` | Instrucciones del proyecto, alcance aprobado y fuentes. |

Ante una diferencia entre un antecedente y una corrección humana posterior incorporada en el catálogo funcional, prevalece el catálogo vigente. En especial, cada despacho admite una única confirmación de llegada; una recepción parcial queda **«Recibida con discrepancia»** y una nueva entrega exige otra transferencia y despacho.

### 1.3 Convenciones

- **HU-NNN:** identificador estable de historia de usuario.
- **RF-NNNN:** requisito funcional aprobado al que se traza la historia.
- **UC-NNN:** caso de uso documentado que aporta una referencia complementaria, no una fuente de alcance independiente.
- Los criterios de aceptación describen resultados observables, no pruebas ejecutadas ni decisiones técnicas.
- Las dependencias expresan relaciones funcionales justificadas; no son una secuencia obligatoria de implementación.

### 1.4 Actores y organización por módulos

| Actor | Ámbito funcional aprobado |
|---|---|
| **Administrador general** | Mantiene configuración, usuarios, sucursales, catálogo, proveedores mínimos, listas de precios y rutas; consulta toda la red; compara sucursales y exporta dentro de su visibilidad. No ejecuta por herencia operaciones locales. |
| **Gerente de sucursal** | Supervisa su sucursal; registra ajustes y correcciones propios; aprueba transferencias de origen o las rechaza con motivo exclusivamente desde «en preparación», antes de aprobar; gestiona discrepancias; consulta valoración, dashboard, reportes y exportaciones de su sucursal. |
| **Operador de inventario** | Ejecuta operaciones locales autorizadas: compras, recepciones, ventas, movimientos ordinarios y transferencias. No exporta ni registra ajustes manuales. |
| **Usuario interno** | Generalización conceptual de los tres roles. No es un rol adicional. |

Todo usuario tiene exactamente un rol; gerente y operador operan una sola sucursal activa. Consultar otra sucursal no concede permiso de escritura sobre ella. Las historias se organizan por Administración, Catálogo e inventario, Compras y valoración, Ventas, Transferencias, Logística, Dashboard y Reportes exportables.

## 2. Catálogo de historias de usuario

### 2.1 Administración, identidad y acceso

#### HU-001 — Configurar mínimos de inventario
- **Módulo:** Administración e inventario.
- **Actor beneficiario:** Administrador general.
- **Historia:** **Como** administrador general, **quiero** consultar y definir el stock mínimo de cada producto habilitado en cada sucursal, **para** que las alertas e indicadores de agotamiento usen un umbral operativo común.
- **Valor funcional:** Mantiene una referencia única y explícita para anticipar necesidades de reabastecimiento sin alterar existencias ni generar compras.
- **Requisitos asociados:** RF-0001, RF-0019, RF-0020, RF-0048.
- **Caso de uso relacionado:** UC-001.
- **Criterios de aceptación:**
  - **Dado** un producto habilitado en una sucursal, **cuando** el administrador registra o modifica su mínimo, incluido cero, **entonces** la consulta posterior muestra el nuevo valor explícito para esa combinación producto-sucursal.
  - **Dado** un mínimo vigente, **cuando** el disponible es menor o igual al mínimo, **entonces** alertas e indicador de próximos a agotarse usan esa misma condición; si el disponible lo supera, no se mantiene la condición activa.
  - **Dado** un cambio exclusivo del mínimo, **cuando** se confirma, **entonces** existencias, costos, transacciones y órdenes de compra no se modifican.
- **Reglas y restricciones:** No se interpreta la ausencia de mínimo como cero; el mínimo es único por producto-sucursal y no bloquea operaciones ni crea compras automáticamente.
- **Dependencias:** Habilitación local del producto y permisos de administrador.

#### HU-002 — Administrar usuarios y su acceso
- **Módulo:** Administración e identidad.
- **Actor beneficiario:** Administrador general.
- **Historia:** **Como** administrador general, **quiero** crear, consultar, actualizar y desactivar cuentas con un único rol y ámbito válido, **para** que las personas autorizadas accedan a las funciones que les corresponden conservando la trazabilidad de sus operaciones.
- **Valor funcional:** Permite administrar responsables del sistema sin perder referencias históricas ni habilitar accesos fuera de rol o sucursal.
- **Requisitos asociados:** RF-0002, RF-0006, RF-0007.
- **Caso de uso relacionado:** UC-002.
- **Criterios de aceptación:**
  - **Dado** nombre, código único y un único rol, **cuando** el administrador crea una cuenta, **entonces** el sistema genera una identidad estable; para gerente u operador exige una sucursal activa única y para administrador no exige sucursal.
  - **Dado** un código ya usado, cero o más de un rol, o una asignación de sucursal inválida, **cuando** se intenta crear o actualizar una cuenta, **entonces** se rechaza sin alterar cuentas existentes.
  - **Dado** un usuario con operaciones históricas, **cuando** se desactiva o se reasigna válidamente, **entonces** sus accesos anteriores se revocan y las operaciones conservan autoría y sucursal históricas; no existe eliminación física.
- **Reglas y restricciones:** No hay autorregistro, invitaciones por correo ni recuperación automática. Los pendientes continúan en su sucursal y pueden ser atendidos por perfiles actuales autorizados.
- **Dependencias:** Gestión de sucursales activas; autenticación y autorización.

#### HU-003 — Administrar sucursales operables
- **Módulo:** Administración.
- **Actor beneficiario:** Administrador general.
- **Historia:** **Como** administrador general, **quiero** crear, consultar, actualizar y desactivar sucursales bajo condiciones operativas seguras, **para** mantener la red organizada sin perder historial ni dejar operaciones inconclusas.
- **Valor funcional:** Permite controlar el ciclo de vida de las sucursales sin cancelar documentos, ajustar inventario o borrar referencias automáticamente.
- **Requisitos asociados:** RF-0003, RF-0005.
- **Caso de uso relacionado:** UC-003.
- **Criterios de aceptación:**
  - **Dado** un código único y un nombre, **cuando** el administrador crea una sucursal, **entonces** queda registrada con identidad estable, estado y datos consultables.
  - **Dado** una sucursal con existencias distintas de cero, compras pendientes o borradores operables, transferencias abiertas o faltantes operativos pendientes, **cuando** se intenta desactivarla, **entonces** se rechaza indicando el impedimento y sin cancelar ni ajustar nada automáticamente.
  - **Dado** una sucursal sin impedimentos, **cuando** se desactiva, **entonces** conserva historial consultable pero no admite nuevas operaciones ni puede elegirse como origen o destino de una transferencia.
- **Reglas y restricciones:** No existe eliminación física, dirección ni contacto obligatorio; la desactivación no inactiva automáticamente a sus usuarios.
- **Dependencias:** Inventario, compras y transferencias deben resolver los impedimentos antes de la baja.

#### HU-004 — Acceder solo a información y operaciones autorizadas
- **Módulo:** Identidad y acceso.
- **Actor beneficiario:** Usuario interno.
- **Historia:** **Como** usuario interno, **quiero** iniciar sesión y acceder únicamente a consultas y operaciones autorizadas para mi rol y sucursal, **para** operar de forma segura dentro de mis responsabilidades.
- **Valor funcional:** Protege información y operaciones de la red respetando rol, estado de cuenta y ámbito de sucursal.
- **Requisitos asociados:** RF-0004, RF-0005, RF-0006, RF-0007, RF-0014.
- **Caso de uso relacionado:** UC-004, UC-005.
- **Criterios de aceptación:**
  - **Dado** un usuario activo con código y contraseña personal válidos, **cuando** inicia sesión, **entonces** obtiene una sesión autenticada; si es su primer ingreso con clave temporal, debe cambiarla antes de operar.
  - **Dado** una cuenta inactiva, credenciales inválidas o una sesión vencida, cerrada o revocada, **cuando** intenta acceder, **entonces** no obtiene una sesión ni ejecuta operaciones protegidas.
  - **Dado** un usuario que intenta una escritura fuera de su ámbito, **cuando** solicita la operación, **entonces** se rechaza sin exponer datos protegidos ni modificar registros; la consulta autorizada de otra sucursal no concede escritura cruzada.
- **Reglas y restricciones:** Contraseñas entre 15 y 128 caracteres, con espacios permitidos. El administrador ve la red pero no hereda compras, ventas, movimientos, solicitudes, aprobaciones ni cancelaciones locales; no se exponen contraseñas.
- **Dependencias:** Cuentas, sucursales activas y matriz funcional de permisos.

### 2.2 Catálogo e inventario

#### HU-005 — Mantener catálogo, habilitación local y unidades
- **Módulo:** Catálogo.
- **Actor beneficiario:** Administrador general.
- **Historia:** **Como** administrador general, **quiero** administrar productos, sus unidades y su habilitación por sucursal, **para** que las operaciones usen un catálogo común con condiciones locales consistentes.
- **Valor funcional:** Asegura identidad comercial común, conversiones válidas y disponibilidad local sin crear inventario ficticio.
- **Requisitos asociados:** RF-0008, RF-0009, RF-0010, RF-0011, RF-0012, RF-0013.
- **Caso de uso relacionado:** UC-006.
- **Criterios de aceptación:**
  - **Dado** un SKU no registrado y los datos permitidos, **cuando** el administrador crea un producto, **entonces** queda disponible para consulta con identidad estable; habilitarlo en otra sucursal no incrementa existencias.
  - **Dado** un producto con unidad base y conversiones propias, **cuando** se configura o usa una cantidad, **entonces** se respeta la conversión del producto y se rechazan fracciones no admitidas o precisiones mayores a tres decimales en la unidad base.
  - **Dado** cualquier producto, tenga o no referencias históricas, movimientos o dependencias, **cuando** se solicita su eliminación, **entonces** solo puede quedar registrado con `active=FALSE` si tiene stock cero en todas las sucursales y no tiene operaciones pendientes; nunca se elimina físicamente, conserva sus documentos y no admite nuevas operaciones (H v1.3/01, RF-0011).
  - **Dado** un producto inactivo, **cuando** el administrador lo reactiva, **entonces** vuelve al catálogo general sin habilitaciones locales ni mínimos restaurados; cada sucursal debe habilitarlo y configurar un nuevo mínimo antes de operar.
- **Reglas y restricciones:** SKU único en toda la red; la unidad base no cambia después de usarse. Cambiar factores no reexpresa saldos ni documentos previos. Solo el administrador puede reactivar productos inactivos, sin restauración local automática.
- **Dependencias:** Autorización de administrador; configuración de mínimo explícito para operar un producto habilitado localmente.

#### HU-006 — Consultar catálogo e inventario de sucursales
- **Módulo:** Inventario.
- **Actor beneficiario:** Usuario interno.
- **Historia:** **Como** usuario interno autorizado, **quiero** consultar el catálogo local y el inventario propio o de otra sucursal, **para** conocer la disponibilidad operativa sin modificar existencias ajenas.
- **Valor funcional:** Ofrece visibilidad de red para apoyar decisiones operativas preservando la autonomía local.
- **Requisitos asociados:** RF-0012, RF-0014, RF-0015.
- **Caso de uso relacionado:** UC-007.
- **Criterios de aceptación:**
  - **Dado** productos habilitados y no habilitados en una sucursal, **cuando** el usuario consulta su catálogo local, **entonces** ve los habilitados, incluso si su stock es cero, y no ve como operable un producto no habilitado.
  - **Dado** dos sucursales con existencias distintas, **cuando** el usuario consulta una distinta de la propia, **entonces** visualiza su físico, disponible y tránsito diferenciados, sin adquirir permiso de escritura sobre ella.
  - **Dado** un movimiento confirmado y una plataforma conectada, **cuando** otra sucursal consulta el inventario, **entonces** obtiene información actualizada según la condición aprobada; ante pérdida de comunicación se advierte posible desactualización y no se confirma escritura no aceptada.
- **Reglas y restricciones:** No hay operación offline, sincronización diferida ni reservas. El operador no consulta valoración o costos agregados restringidos.
- **Dependencias:** Catálogo habilitado, movimientos confirmados y autorización por rol/sucursal.

#### HU-007 — Registrar movimientos trazables de inventario
- **Módulo:** Inventario.
- **Actor beneficiario:** Operador de inventario; gerente de sucursal para ajustes y correcciones propios.
- **Historia:** **Como** responsable autorizado de una sucursal, **quiero** registrar ingresos, retiros y correcciones trazables de inventario, **para** mantener existencias coherentes y explicar cada variación histórica.
- **Valor funcional:** Mantiene un historial auditable y evita saldos negativos o alteraciones directas del inventario.
- **Requisitos asociados:** RF-0016, RF-0017, RF-0018.
- **Caso de uso relacionado:** UC-008.
- **Criterios de aceptación:**
  - **Dado** una sucursal activa, producto habilitado, unidad válida y actor autorizado, **cuando** registra un ingreso o retiro válido, **entonces** se ajusta el saldo correspondiente y el movimiento conserva fecha/hora, responsable, motivo, cantidad, producto, unidad y referencia aplicable.
  - **Dado** un retiro que excedería el físico disponible, **cuando** se intenta confirmar, **entonces** se rechaza íntegramente sin cambiar stock, valor, historial ni crear un documento parcial.
  - **Dado** un movimiento confirmado erróneo, **cuando** un gerente autorizado realiza una corrección válida, **entonces** se registra una compensación vinculada y el hecho original permanece inmutable.
- **Reglas y restricciones:** Operador registra ingresos, retiros ordinarios, devoluciones y mermas; gerente registra únicamente ajustes/correcciones de su sucursal. Compras, ventas y transferencias generan sus movimientos propios y no deben duplicarse manualmente. No se edita el saldo directamente.
- **Dependencias:** Unidades por producto, permisos, valoración y operaciones originadoras cuando corresponda.

#### HU-008 — Consultar alertas de reabastecimiento
- **Módulo:** Inventario.
- **Actor beneficiario:** Gerente de sucursal; administrador general; operador como consultante operacional.
- **Historia:** **Como** gerente de sucursal, **quiero** identificar productos que alcanzan su mínimo y consultar la evolución de sus alertas, **para** reconocer oportunamente necesidades de reabastecimiento.
- **Valor funcional:** Hace visible una condición persistente de inventario sin automatizar compras ni confundirla con faltantes de transferencias.
- **Requisitos asociados:** RF-0019, RF-0020, RF-0048.
- **Caso de uso relacionado:** UC-009.
- **Criterios de aceptación:**
  - **Dado** un producto-sucursal con mínimo explícito, **cuando** el disponible es menor o igual al mínimo, **entonces** existe una única alerta activa identificable para esa combinación.
  - **Dado** una alerta activa, **cuando** el disponible pasa a ser estrictamente mayor que el mínimo, **entonces** la alerta se resuelve y conserva su historial de activación y resolución.
  - **Dado** evaluaciones repetidas de la misma condición, **cuando** se actualiza el inventario, **entonces** no se duplican alertas ni se generan órdenes de compra.
- **Reglas y restricciones:** Las alertas se presentan en interfaz; no se envían por correo o SMS. Son independientes de las alertas de faltantes de transferencia.
- **Dependencias:** HU-001 y movimientos de inventario.

### 2.3 Compras y valoración

#### HU-009 — Gestionar órdenes y condiciones de compra
- **Módulo:** Compras.
- **Actor beneficiario:** Operador de inventario; administrador para proveedores mínimos; gerente y administrador como consultantes autorizados.
- **Historia:** **Como** operador de inventario, **quiero** crear y gestionar órdenes de compra con proveedor, productos y condiciones comerciales, **para** registrar adquisiciones de mi sucursal antes de recibir mercancía.
- **Valor funcional:** Formaliza el ciclo de compra y conserva su historial sin incorporar pagos ni gestión financiera.
- **Requisitos asociados:** RF-0021, RF-0022, RF-0023, RF-0025.
- **Caso de uso relacionado:** UC-010, UC-012.
- **Criterios de aceptación:**
  - **Dado** un proveedor y productos válidos, **cuando** el operador crea una orden, **entonces** queda en borrador con proveedor, nombre vigente del proveedor capturado y no nulo, líneas, precio unitario, descuento por línea y plazo registrados, sin modificar inventario.
  - **Dado** que el proveedor cambia de nombre después de crear el borrador, **cuando** la orden pasa a pendiente de recepción, **entonces** su nombre histórico se actualiza al vigente en esa transición y queda congelado; otro cambio del proveedor no lo modifica, tampoco después de recibir o cancelar la orden (H v1.3/02, RF-0021-CA-03).
  - **Dado** una orden de compra, **cuando** se gestiona su ciclo, **entonces** solo se edita en borrador; una pendiente que requiere cambios se cancela y se reemplaza, y una recibida no se borra.
  - **Dado** compras de distintos proveedores, productos y estados, **cuando** un perfil autorizado consulta el histórico, **entonces** puede distinguirlas por proveedor o producto y reconoce borrador, pendiente, cancelada o recibida.
- **Reglas y restricciones:** Descuento por línea de 0 % a 100 %; plazo entero no negativo desde recepción; COP como moneda de demostración; sin impuestos, fletes, divisas, pagos, tesorería ni compras parciales.
- **Dependencias:** Proveedor mínimo administrado por administrador, catálogo/unidades y sucursal activa.

#### HU-010 — Recibir una compra y actualizar su valoración
- **Módulo:** Compras y valoración.
- **Actor beneficiario:** Operador de inventario.
- **Historia:** **Como** operador de inventario, **quiero** confirmar una recepción completa de compra una sola vez, **para** aumentar el inventario y actualizar el costo promedio ponderado de forma trazable.
- **Valor funcional:** Vincula la llegada de mercancía con el ingreso físico y la valoración por producto-sucursal, evitando duplicaciones.
- **Requisitos asociados:** RF-0024, RF-0026.
- **Caso de uso relacionado:** UC-011.
- **Criterios de aceptación:**
  - **Dado** una orden pendiente de recepción, **cuando** el operador confirma la recepción completa de sus líneas vigentes, **entonces** se registran ingresos auditables, aumenta el inventario una sola vez, se actualiza la valoración y la orden queda recibida.
  - **Dado** una recepción ya confirmada, **cuando** se repite técnicamente la confirmación, **entonces** no se agregan existencias, valor ni movimientos adicionales.
  - **Dado** stock previo y una entrada valorada, **cuando** se calcula el promedio móvil por producto-sucursal, **entonces** se usa el costo neto por unidad base; las salidas posteriores consumen el promedio vigente sin recalcular la historia.
- **Reglas y restricciones:** No hay recepción parcial de compras. Con stock previo cero se adopta el costo de recepción; las correcciones vinculadas preservan historia y no pueden generar stock negativo. No se habilita contabilidad financiera.
- **Dependencias:** HU-009, movimientos de inventario y unidades de producto.

#### HU-011 — Consultar compras y valoración autorizada
- **Módulo:** Compras y valoración.
- **Actor beneficiario:** Gerente de sucursal y administrador general.
- **Historia:** **Como** gerente de sucursal, **quiero** consultar el histórico de compras y la valoración de mi ámbito, **para** supervisar adquisiciones e inventario sin modificar sus registros.
- **Valor funcional:** Permite seguimiento de compras y costo promedio dentro de los permisos aprobados.
- **Requisitos asociados:** RF-0025, RF-0026.
- **Caso de uso relacionado:** UC-012.
- **Criterios de aceptación:**
  - **Dado** un criterio por proveedor o producto, **cuando** el actor autorizado consulta compras, **entonces** visualiza órdenes y recepciones distinguiendo sus estados.
  - **Dado** una consulta de valoración de la sucursal propia o de la red autorizada, **cuando** se solicita, **entonces** se presenta el promedio por producto-sucursal sin modificar compras, movimientos ni valoración.
  - **Dado** un operador de inventario, **cuando** intenta consultar costos o valoración agregada restringida, **entonces** no obtiene esa información.
- **Reglas y restricciones:** Una orden cancelada no se presenta como recibida; una salida no cambia por sí sola el promedio vigente.
- **Dependencias:** HU-009 y HU-010; autorización por rol y ámbito.

### 2.4 Ventas

#### HU-012 — Mantener listas de precios para ventas
- **Módulo:** Ventas.
- **Actor beneficiario:** Administrador general.
- **Historia:** **Como** administrador general, **quiero** mantener listas globales de precios por producto y unidad, **para** que los operadores dispongan de precios definidos al confirmar ventas.
- **Valor funcional:** Proporciona una fuente de precio común y trazable, sin convertir la venta en un mecanismo de precio libre.
- **Requisitos asociados:** RF-0030.
- **Caso de uso relacionado:** UC-013.
- **Criterios de aceptación:**
  - **Dado** productos y unidades existentes, **cuando** el administrador registra o actualiza listas globales, **entonces** sus precios quedan disponibles para consulta y uso en ventas autorizadas.
  - **Dado** dos listas con precios distintos, **cuando** el operador selecciona una al confirmar una venta, **entonces** la venta usa el precio de la lista seleccionada.
  - **Dado** una venta confirmada, **cuando** una lista cambia posteriormente, **entonces** el comprobante conserva la lista y el precio históricos.
- **Reglas y restricciones:** No hay asignación automática por cliente o sucursal, gestión de clientes ni cambios libres de precio fuera del descuento permitido.
- **Dependencias:** Catálogo, unidades y registro de venta.

#### HU-013 — Registrar y consultar una venta confirmada
- **Módulo:** Ventas.
- **Actor beneficiario:** Operador de inventario; gerente y administrador como consultantes autorizados.
- **Historia:** **Como** operador de inventario, **quiero** confirmar una venta con productos, cantidades, precios y descuentos válidos, **para** disminuir el inventario disponible y dejar un comprobante interno consultable.
- **Valor funcional:** Registra una venta completa y consistente, evitando ventas parciales o duplicadas cuando no hay disponibilidad suficiente.
- **Requisitos asociados:** RF-0027, RF-0028, RF-0029, RF-0031.
- **Caso de uso relacionado:** UC-014.
- **Criterios de aceptación:**
  - **Dado** una sucursal activa, productos habilitados, lista y unidades válidas, **cuando** el operador confirma una venta con stock suficiente en todas las líneas, **entonces** se registra la venta, el retiro auditable y el comprobante interno una sola vez.
  - **Dado** stock insuficiente, un descuento fuera de 0 % a 100 % o una confirmación competidora que agota el saldo, **cuando** se intenta confirmar, **entonces** se rechaza toda la venta sin comprobante, retiro ni efecto parcial.
  - **Dado** una venta confirmada, **cuando** un perfil autorizado consulta su comprobante, **entonces** recupera fecha, sucursal, responsable, líneas, cantidades, precio, descuento y total históricos.
- **Reglas y restricciones:** El descuento es por línea, sin promociones ni descuento de cabecera. Una devolución física se registra como movimiento vinculado, sin reembolso ni anulación financiera automática. No existe factura fiscal ni cobro electrónico.
- **Dependencias:** HU-012, catálogo/unidades, movimientos y autorización local.

### 2.5 Transferencias

#### HU-014 — Solicitar y preparar una transferencia
- **Módulo:** Transferencias.
- **Actor beneficiario:** Operador de inventario de destino; operador de origen para preparación.
- **Historia:** **Como** operador de inventario de destino, **quiero** solicitar productos a una sucursal origen y permitir que el origen prepare cantidades viables, **para** coordinar el reabastecimiento entre sucursales sin reservar inventario prematuramente.
- **Valor funcional:** Formaliza la necesidad del destino y distingue cantidades solicitadas y preparadas antes de cualquier salida física.
- **Requisitos asociados:** RF-0032, RF-0033.
- **Caso de uso relacionado:** UC-015.
- **Criterios de aceptación:**
  - **Dado** origen y destino activos y distintos, productos habilitados y unidades compatibles, **cuando** el operador del destino crea una solicitud válida, **entonces** queda registrada con producto, cantidades, origen y destino sin modificar stock ni reservarlo.
  - **Dado** una solicitud, **cuando** el operador de origen revisa disponibilidad y prepara cantidades, **entonces** puede proponer una cantidad igual o menor que la solicitada y el destino distingue ambas cantidades.
  - **Dado** una solicitud inválida por sucursal inactiva/igual, producto no habilitado, unidad incompatible o actor no autorizado, **cuando** se intenta registrar, **entonces** se rechaza sin crear transferencia ni cambiar inventario.
- **Reglas y restricciones:** Solo el operador del destino solicita transferencias; el administrador no sustituye esa operación. Preparar no descuenta ni reserva, no se envía más de lo solicitado y no se despacha una transferencia con todas las cantidades en cero.
- **Dependencias:** Catálogo, inventario consultable, sucursales activas y autorización.

#### HU-015 — Aprobar y despachar una transferencia
- **Módulo:** Transferencias.
- **Actor beneficiario:** Gerente de sucursal de origen; operador de inventario de origen.
- **Historia:** **Como** gerente de sucursal de origen, **quiero** aprobar una transferencia preparada y permitir su despacho trazable o rechazarla con motivo antes de aprobarla, **para** autorizar o denegar el traslado sin alterar inventario por un rechazo.
- **Valor funcional:** Controla la salida física de inventario y establece datos necesarios para la recepción y logística.
- **Requisitos asociados:** RF-0034, RF-0035.
- **Caso de uso relacionado:** UC-015.
- **Criterios de aceptación:**
  - **Dado** una transferencia preparada de la sucursal origen, **cuando** el gerente de origen la aprueba, **entonces** la aprobación queda vinculada y consultable antes del despacho.
  - **Dado** una transferencia «en preparación» de su origen, **cuando** el gerente de origen la rechaza con motivo, **entonces** queda «rechazada» con motivo y responsable consultables, sin aprobación, despacho ni cambios de cantidades, stock o tránsito (RF-0034-CA-03).
  - **Dado** un intento de rechazo sin motivo, por operador, administrador o gerente de otra sucursal, o desde cualquier estado distinto de «en preparación», **cuando** se intente confirmar, **entonces** se deniega sin modificar transferencia ni inventario (RF-0034-CA-04).
  - **Dado** una transferencia aprobada con todas las líneas válidas y stock suficiente, **cuando** el operador de origen registra salida real, llegada estimada y transportista, **entonces** se descuenta físico del origen, se crea tránsito al costo congelado y no se incrementa físico del destino.
  - **Dado** una línea sin stock al momento del despacho o un reintento técnico, **cuando** se confirma, **entonces** se rechaza el despacho completo o se conserva el resultado existente, sin salidas ni tránsito duplicados.
- **Reglas y restricciones:** El gerente de origen aprueba o rechaza con motivo obligatorio exclusivamente desde «en preparación», antes de aprobar (H v1.2/01); el operador de origen conserva preparación/despacho. El administrador no recibe aprobación ni rechazo por herencia. El destino puede cancelar antes de aprobar; después y antes de despachar solo el gerente de origen puede cancelar con motivo. No hay cancelación automática en tránsito, múltiples despachos por pedido, CRUD de transportistas ni seguimiento externo.
- **Dependencias:** HU-014; ruta y datos logísticos; validación de inventario.

#### HU-016 — Confirmar la recepción completa de una transferencia
- **Módulo:** Transferencias.
- **Actor beneficiario:** Operador de inventario de destino.
- **Historia:** **Como** operador de inventario de destino, **quiero** confirmar la llegada completa de una transferencia en tránsito, **para** incorporar al inventario local las cantidades despachadas una única vez.
- **Valor funcional:** Finaliza la recepción física completa preservando el tránsito, el costo congelado y la trazabilidad del responsable.
- **Requisitos asociados:** RF-0036.
- **Caso de uso relacionado:** UC-016.
- **Criterios de aceptación:**
  - **Dado** un despacho en tránsito cuyas cantidades recibidas coinciden con todas las despachadas, **cuando** el operador de destino confirma la llegada, **entonces** aumenta el físico destino, disminuye el tránsito y se registra un ingreso auditable al costo congelado.
  - **Dado** una recepción completa confirmada, **cuando** se repite técnicamente su confirmación, **entonces** no se crean ingresos, valor ni cambios de estado adicionales.
  - **Dado** una entrega física posterior, **cuando** se intenta anexar al despacho ya recibido, **entonces** se rechaza y se requiere una nueva transferencia y despacho.
- **Reglas y restricciones:** Existe una única confirmación de llegada por despacho. El gerente de destino supervisa, pero el operador registra la recepción.
- **Dependencias:** HU-015, movimientos de inventario y permisos de destino.

#### HU-017 — Registrar una recepción parcial y el tratamiento del faltante
- **Módulo:** Transferencias.
- **Actor beneficiario:** Operador de inventario de destino; gerente de origen o destino.
- **Historia:** **Como** operador de inventario de destino, **quiero** registrar una llegada parcial y, como gerente autorizado, definir el tratamiento de su faltante, **para** conservar la diferencia visible y trazable sin falsear inventario ni tránsito.
- **Valor funcional:** Separa la recepción física, la alerta y el tratamiento declarado para evitar pérdidas, reposiciones o cierres automáticos no autorizados.
- **Requisitos asociados:** RF-0037, RF-0038, RF-0039.
- **Caso de uso relacionado:** UC-017.
- **Criterios de aceptación:**
  - **Dado** un despacho de 10 unidades y una llegada de 7, **cuando** el operador confirma la recepción parcial, **entonces** el destino recibe 7, quedan 3 identificadas como faltante/tránsito al costo congelado y la transferencia queda **«Recibida con discrepancia»**.
  - **Dado** una recepción parcial confirmada, **cuando** se intenta una segunda recepción del mismo despacho, incluso sin superar lo despachado, **entonces** se rechaza sin modificar cantidades, stock, valor ni estado.
  - **Dado** una alerta de faltante, **cuando** un gerente de origen o destino selecciona reenvío, ajuste o reclamación y marca **«Resuelto»**, **entonces** se guardan tratamiento, responsable y fecha, sin cambiar cantidades, tránsito, valor ni estado de transferencia.
  - **Dado** una llegada confirmada de cero unidades, **cuando** el operador la registra, **entonces** no hay ingreso físico; la transferencia queda «Recibida con discrepancia» con faltante total, tránsito/valor pendientes y alerta vinculada.
- **Reglas y restricciones:** La resolución de alerta no ejecuta automáticamente el tratamiento. Un reenvío exige nueva transferencia; una pérdida reconocida se registra aparte y reduce tránsito/valor una vez con autorización y evidencia. La llegada cero es una recepción parcial sin movimiento físico y no admite una segunda recepción del mismo despacho.
- **Dependencias:** HU-015; movimientos separados y autorización de gerentes para gestionar discrepancia.

### 2.6 Logística

#### HU-018 — Gestionar rutas y consultar cumplimiento logístico
- **Módulo:** Logística.
- **Actor beneficiario:** Administrador general para rutas; gerente de sucursal para reportes; usuario interno autorizado para consulta.
- **Historia:** **Como** gerente de sucursal, **quiero** consultar estados, tiempos y cumplimiento de transferencias por sucursal o ruta, **para** evaluar el desempeño logístico con información de llegada verificable.
- **Valor funcional:** Permite distinguir transferencia en curso, llegada completa y llegada con discrepancia, evitando métricas basadas en resoluciones administrativas.
- **Requisitos asociados:** RF-0040, RF-0041, RF-0042, RF-0043.
- **Caso de uso relacionado:** UC-018.
- **Criterios de aceptación:**
  - **Dado** una ruta origen-destino, **cuando** el administrador la clasifica, **entonces** conserva una prioridad manual alta, media o baja para consulta y uso autorizado.
  - **Dado** una transferencia despachada con llegada estimada y única llegada confirmada, **cuando** se consulta, **entonces** muestra estado, tiempo estimado y tiempo real hasta esa llegada, identificando si fue completa o con discrepancia.
  - **Dado** en un período una llegada completa puntual, una con discrepancia y una vencida aún en tránsito, **cuando** se genera el reporte, **entonces** el KPI calcula completas puntuales sobre llegadas confirmadas, cuenta la discrepancia como no cumplida y muestra la vencida aparte.
- **Reglas y restricciones:** La zona de negocio es America/Bogota. Una alerta de faltante resuelta no cambia la duración ni el KPI. No se incorporan GPS, mapas, optimización, tarifas, escalas o seguimiento externo.
- **Dependencias:** HU-015, HU-016 y HU-017.

### 2.7 Dashboard

#### HU-019 — Consultar indicadores operativos de sucursal
- **Módulo:** Dashboard.
- **Actor beneficiario:** Gerente de sucursal; operador para panel local operacional; administrador según su visibilidad.
- **Historia:** **Como** gerente de sucursal, **quiero** consultar ventas, rotación, demanda, transferencias y productos próximos a agotarse de mi sucursal, **para** supervisar el estado operativo con indicadores interpretables.
- **Valor funcional:** Reúne indicadores aprobados sin inventar datos, mezclar unidades incompatibles ni modificar información operativa.
- **Requisitos asociados:** RF-0044, RF-0045, RF-0046, RF-0047, RF-0048.
- **Caso de uso relacionado:** UC-019.
- **Criterios de aceptación:**
  - **Dado** ventas confirmadas, **cuando** se abre el dashboard, **entonces** muestra el mes actual y los tres anteriores, con importes netos de descuento, diferenciando mes en curso, cero real y ausencia de datos.
  - **Dado** historia suficiente y promedio físico diario positivo, **cuando** se consulta la rotación, **entonces** usa unidades vendidas del período sobre ese promedio; con historia insuficiente o denominador cero muestra **«No calculable»**.
  - **Dado** productos con ventas comparables en el mes, **cuando** se calculan alta y baja demanda, **entonces** se muestran grupos sin solapamiento, desempate por SKU y productos sin ventas aparte; no se agregan unidades heterogéneas.
  - **Dado** transferencias activas y mínimos configurados, **cuando** se consulta el panel, **entonces** distingue solicitud/preparación, tránsito, recepción y faltante, e identifica productos con disponible menor o igual al mínimo común.
- **Reglas y restricciones:** Las devoluciones se muestran separadas de las ventas. No hay predicción de demanda. El operador no accede a valoración ni costos agregados.
- **Dependencias:** Ventas, inventario, transferencias, logística y HU-001.

#### HU-020 — Comparar el rendimiento de la red
- **Módulo:** Dashboard.
- **Actor beneficiario:** Administrador general.
- **Historia:** **Como** administrador general, **quiero** comparar el rendimiento de las sucursales con las mismas métricas y período, **para** tener visibilidad de la red sin atribuir indicadores financieros no aprobados.
- **Valor funcional:** Permite contrastar desempeño de sucursales con ventas netas, número de ventas y cumplimiento logístico.
- **Requisitos asociados:** RF-0049.
- **Caso de uso relacionado:** UC-019.
- **Criterios de aceptación:**
  - **Dado** datos de dos o más sucursales, **cuando** el administrador consulta la comparativa, **entonces** distingue cada sucursal por ventas confirmadas netas de descuento, número de ventas y cumplimiento logístico del mismo período.
  - **Dado** un gerente u operador, **cuando** intenta consultar la comparativa de red, **entonces** no obtiene esa información y conserva únicamente sus consultas autorizadas.
  - **Dado** ausencia de información, valor cero o una métrica no calculable, **cuando** se presenta la comparativa, **entonces** se diferencian esos estados sin fabricar historial ni rentabilidad.
- **Reglas y restricciones:** Esta comparativa es exclusiva del administrador general y no restringe la consulta autorizada de inventario entre sucursales.
- **Dependencias:** HU-018, HU-019 y autorización.

### 2.8 Reportes exportables

#### HU-021 — Exportar reportes XLSX de movimientos, ventas y transferencias
- **Módulo:** Reportes exportables.
- **Actor beneficiario:** Administrador general y gerente de sucursal.
- **Historia:** **Como** administrador general o gerente de sucursal, **quiero** descargar un reporte XLSX de movimientos, ventas o transferencias filtrado por fechas y ámbito autorizado, **para** reutilizar información operativa fuera de las consultas y dashboards del sistema.
- **Valor funcional:** Aporta el adicional aprobado de exportación reutilizable, respetando permisos, fechas, columnas y límites del alcance.
- **Requisitos asociados:** RF-0050, RF-0051, RF-0052, RF-0053, RF-0054, RF-0055.
- **Caso de uso relacionado:** UC-020.
- **Criterios de aceptación:**
  - **Dado** una categoría aprobada, fechas inicial/final válidas y una sucursal autorizada, **cuando** el actor solicita la exportación, **entonces** obtiene una descarga directa de un libro `.xlsx` real con metadatos del filtro y sin modificar registros de origen.
  - **Dado** movimientos, ventas o transferencias dentro del rango, **cuando** se exportan, **entonces** se aplican respectivamente fecha de registro, confirmación o solicitud; se incluyen únicamente las columnas aprobadas y nunca claves internas ni costos estándar en movimientos.
  - **Dado** un rango superior a 12 meses, inicio posterior al final o más de 10.000 filas, **cuando** se solicita el reporte, **entonces** se rechaza claramente sin truncar resultados ni generar cambios operativos.
  - **Dado** una consulta válida sin resultados, **cuando** se exporta, **entonces** se entrega un XLSX válido con encabezados, metadatos y nota explícita de ausencia de filas.
- **Reglas y restricciones:** El administrador exporta una o todas las sucursales; el gerente solo la propia; el operador no exporta. Ventas se exportan una fila por línea sin duplicar total de cabecera; transferencias una fila por línea, sin duplicar reintentos. No hay PDF, exportación asíncrona, repositorio de archivos, ERP/POS ni exportación indiscriminada de usuarios, costos o dashboards.
- **Dependencias:** Autorización, historial de movimientos, ventas y transferencias.

## 3. Matriz de trazabilidad

| Requisito funcional | Historia(s) de usuario | Caso(s) de uso relacionado(s) |
|---|---|---|
| RF-0001 | HU-001 | UC-001 |
| RF-0002 | HU-002 | UC-002 |
| RF-0003 | HU-003 | UC-003 |
| RF-0004 | HU-004 | UC-005 |
| RF-0005 | HU-003, HU-004, HU-006 | UC-003, UC-005, UC-007 |
| RF-0006 | HU-002, HU-004 | UC-002, UC-004 |
| RF-0007 | HU-002, HU-004 | UC-002, UC-005 |
| RF-0008–RF-0011 | HU-005 | UC-006 |
| RF-0012 | HU-005, HU-006 | UC-006, UC-007 |
| RF-0013 | HU-005 | UC-006 |
| RF-0014 | HU-004, HU-006 | UC-005, UC-007 |
| RF-0015 | HU-006 | UC-007 |
| RF-0016–RF-0018 | HU-007 | UC-008 |
| RF-0019–RF-0020 | HU-001, HU-008 | UC-001, UC-009 |
| RF-0021–RF-0023 | HU-009 | UC-010 |
| RF-0024 | HU-010 | UC-011 |
| RF-0025 | HU-009, HU-011 | UC-010, UC-012 |
| RF-0026 | HU-010, HU-011 | UC-011, UC-012 |
| RF-0027–RF-0029 | HU-013 | UC-014 |
| RF-0030 | HU-012, HU-013 | UC-013, UC-014 |
| RF-0031 | HU-013 | UC-014 |
| RF-0032–RF-0033 | HU-014 | UC-015 |
| RF-0034–RF-0035 | HU-015 | UC-015 |
| RF-0036 | HU-016 | UC-016 |
| RF-0037–RF-0039 | HU-017 | UC-017 |
| RF-0040–RF-0043 | HU-018 | UC-018 |
| RF-0044–RF-0048 | HU-019 | UC-019 |
| RF-0049 | HU-020 | UC-019 |
| RF-0050–RF-0055 | HU-021 | UC-020 |

### 3.1 Comprobación de trazabilidad y cobertura

- Los **55 requisitos funcionales aprobados (RF-0001 a RF-0055)** están relacionados con al menos una historia de usuario.
- Las capacidades transversales de ámbito operativo y autorización (RF-0005 y RF-0007) se expresan en HU-003, HU-004 y las historias afectadas, sin crear historias técnicas aisladas.
- Las decisiones ratificadas de permisos, recepción única, discrepancias, indicadores y exportación se reflejan en los criterios y reglas de HU-002 a HU-021.
- H v1.1 incorpora reactivación exclusiva del administrador sin restauración local automática y llegada cero como recepción parcial sin movimiento físico, faltante total y alerta vinculada.
- H v1.2/01 integra en HU-015 el rechazo motivado exclusivo del gerente de origen desde «en preparación», antes de aprobar, sin cambiar los permisos operativos de HU-014.
- Se excluyen deliberadamente integraciones externas, operación offline, pagos, facturación fiscal, reservas, predicción, MFA, exportación asíncrona y demás exclusiones ratificadas.

## 4. Limitaciones conservadas

Este documento no resuelve ni convierte en funcionalidad aprobada los mecanismos técnicos de autenticación, revocación, control de concurrencia, sincronización, precisión, generación XLSX, API o persistencia. Tampoco asigna prioridades, estimaciones ni esfuerzo. Las historias reflejan alcance aprobado; no declaran implementación, pruebas ejecutadas ni decisiones arquitectónicas posteriores.
