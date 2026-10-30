# OptiPlant — Registro de decisiones aprobadas y cierre de incertidumbres funcionales

**Versión:** 1.3 · **Fecha de última integración:** 22 de octubre de 2026  
**Estado:** DECISIONES RATIFICADAS POR EL RESPONSABLE DEL PROYECTO — APROBADAS PARA INTEGRACIÓN DOCUMENTAL  
**Procedencia:** ratificación explícita del responsable en esta conversación de la propuesta v0.2, incluyendo `DEC-TEC-ID-001`.  
**Alcance:** 115 decisiones de `INC-003` a `INC-020`, más 1 decisión técnica transversal de identificación dual.  
**Advertencia:** aprobar las alternativas no significa que todos los escenarios de implementación y casos límite estén especificados. Consultar §5 y §8.

> **INSTRUCCIÓN VINCULANTE PARA EL AGENTE:** Esta versión sustituye **solo en condición de aprobación** a `propuesta-cierre-incertidumbres-optiplant-v0.2.md`. El responsable aprueba expresamente las 115 resoluciones de la matriz (§3), sus parámetros (§4), exclusiones (§6) y `DEC-TEC-ID-001` (§9). Integra las reglas funcionales aprobadas en `requisitos-funcionales.md` y su trazabilidad, después de contrastar la versión vigente del catálogo y el enunciado original. No vuelvas a solicitar aprobación general de estas decisiones; únicamente consulta discrepancias reales o nuevos detalles de negocio que sigan abiertos. No modificar automáticamente código, DDL, `AGENTS.md`, fuentes originales, diagramas ni hacer commits.

## 1. Fuentes y reglas de interpretación

- Fuente externa definitiva: `docs/00-source/prueba-tecnica.pdf`; transcripción `docs/00-source/prueba-tecnica.md` como ayuda de lectura.
- Contexto interno: `AGENTS.md`, catálogo funcional `requisitos-funcionales.md` (v0.9 citada por la guía) y `incertidumbres-pendientes-temporal.md`. Si el catálogo cambió desde la guía, comparar antes de integrar.
- `EXTERNO`: lo exige el enunciado, sin dar necesariamente el detalle propuesto. `APROBADO PREVIAMENTE`: decisión ya consignada por la guía original. `APROBADO POR EL RESPONSABLE`: regla de proyecto ratificada el 20 de octubre de 2026; no se debe atribuir como exigencia externa de OptiPlant.
- Los identificadores `INC-NNN/DNN` conservan su significado y son **referencias a decisiones**, no requisitos funcionales nuevos. Mantener intactos IDs de RF y criterios de aceptación existentes.
- Decidir comportamiento observable en RF; mover cifras de desempeño, controles de seguridad y mecanismos concretos a RNF/restricciones/arquitectura cuando corresponda, con trazabilidad.

## 2. Decisiones previamente aprobadas (no reabrir sin orden)

- El administrador gestiona usuarios y sucursales con crear/consultar/listar/actualizar/desactivar, sin eliminación física; administra stock mínimo, que es el mismo parámetro para alertas de reposición y productos próximos a agotarse.
- Sucursal inactiva conserva historial y bloquea operación, y solo puede desactivarse cuando no hay existencias ni compras pendientes ni transferencias abiertas o faltantes sin resolver. No desactiva automáticamente sus usuarios.
- Administrador administra productos; gerente puede registrar ajustes de stock en su sucursal mediante movimientos trazables. No se edita el saldo directamente.
- Un rol por usuario: administrador global; gerente y operador ligados a una sucursal activa. Consulta de otros inventarios permitida sin editar. Comparativas entre sucursales exclusivas de administrador.
- Credenciales por código único y contraseña; la política de contraseña, provisión y sesiones ha sido ratificada en `INC-003/D09–D14` de este documento.
- Funcionalidad adicional seleccionada: reportes exportables. Integraciones ERP/POS y externas excluidas.
- Stack elegido por el proyecto: Spring Boot/Java, JPA/Hibernate, PostgreSQL, React/TypeScript/Vite, API REST, Recharts como alternativa inicial y Docker Compose, con monolito modular en el backend. No atribuir elección a OptiPlant.

## 3. Matriz de decisiones aprobadas (115 de 115)

**Lectura:** A/B remite a la alternativa de la guía temporal. El texto de cada fila es la **resolución aprobada** por el responsable, con sus matices y límites explícitos. Los aspectos formulados como opcionales, condicionales o por diseñar **no** se convierten en requisitos cerrados sin precisión adicional.
### INC-003 — Permisos, credenciales y sesiones

- **INC-003/D01 — Operaciones del administrador | A [APROBADO POR RESPONSABLE].** Administrador mantiene configuración, catálogo, usuarios/sucursales y consultas de toda la red, sin ejecutar por herencia ventas, compras ni movimientos locales; conserva su facultad expresa de solicitar transferencias.
- **INC-003/D02 — Compras y ventas locales del gerente | A [APROBADO POR RESPONSABLE].** Gerente consulta/supervisa compras y ventas locales y ajusta stock y aprueba transferencias conforme a permisos ya fijados. Operador confirma compras, recepciones y ventas de su sucursal.
- **INC-003/D03 — Preparación, despacho y recepción de transferencias | A [APROBADO POR RESPONSABLE].** Operador prepara, despacha o recepciona transferencias correspondientes a su sucursal; gerente de origen aprueba y supervisa, gerente destino supervisa recepción.
- **INC-003/D04 — Catálogo, consultas e indicadores locales | A [APROBADO POR RESPONSABLE].** Los tres roles consultan catálogo e inventario autorizado; operador puede consultar panel local operacional, pero no costos agregados/valoración; gerente consulta dashboard y reportes propios; administrador observa la red. Precisión del responsable, 2026-10-30: el panel del operador tampoco incluye importes agregados de venta; conserva número de ventas, unidades e indicadores operativos, y sigue viendo cada comprobante que registra.
- **INC-003/D05 — Reportes logísticos y exportaciones | A [APROBADO POR RESPONSABLE].** Administrador consulta/descarga reportes de toda la red o una sucursal; gerente consulta/descarga solo los de su sucursal; operador no exporta, pero sí consulta registros operativos.
- **INC-003/D06 — Gestión de rutas y listas de precios | A [APROBADO POR RESPONSABLE].** Solo administrador mantiene listas de precios y rutas compartidas; gerente y operador las usan en operaciones permitidas.
- **INC-003/D07 — Cambiar rol o reasignar sucursal de usuario | A [APROBADO POR RESPONSABLE].** Antes de cambiar sucursal o rol, resolver las tareas personales pendientes si las hubiera; no cambiar autor, sucursal ni permisos registrados en hechos anteriores.
- **INC-003/D08 — Destino inactivo y usuario asociado | A [APROBADO POR RESPONSABLE].** En sucursal inactiva: permitir únicamente consultas históricas autorizadas, no operaciones. Nunca desactivar automáticamente a los usuarios.
- **INC-003/D09 — Política funcional de contraseña | A [APROBADO POR RESPONSABLE].** Como política aprobada, permitir entre 15 y 128 caracteres y espacios; verificar credenciales débiles o comprometidas si el mecanismo adoptado lo permite. Sin rotación periódica arbitraria.
- **INC-003/D10 — Entrega inicial de contraseña | A [APROBADO POR RESPONSABLE].** La contraseña inicial será temporal, individual, no derivada del código y obligará cambio en primer ingreso. Proveer primer administrador mediante inicialización segura en Compose sin credenciales públicas.
- **INC-003/D11 — Cambio voluntario de contraseña | A [APROBADO POR RESPONSABLE].** Requiere acreditar la contraseña vigente. Excluir recuperación automática por correo y autorregistro.
- **INC-003/D12 — Sesiones, salida y cuentas desactivadas | A [APROBADO POR RESPONSABLE].** El cierre de sesión debe invalidar el acceso utilizado. Cambio de rol/sucursal o desactivación de cuenta debe revocar accesos previos; resolver mecanismo técnico posteriormente.
- **INC-003/D13 — Duración de sesión | A [APROBADO POR RESPONSABLE].** Parámetros aprobados: 30 minutos sin actividad y 8 horas máximo absoluto. No deshacer operaciones ya confirmadas; los formularios no enviados deben dar aviso razonable.
- **INC-003/D14 — Intentos fallidos de acceso | A [APROBADO POR RESPONSABLE].** Acordar regla concreta de protección en documento de seguridad: punto de partida 5 intentos fallidos dentro de 15 minutos y espera progresiva (5, 15 y hasta 30 minutos), sin bloqueo permanente automático; revisar frente a riesgos de abuso.

### INC-004 — Autonomía y actualización de inventario

- **INC-004/D01 — Significado de autonomía | A [APROBADO POR RESPONSABLE].** Autonomía significa registrar operaciones locales sobre una plataforma común conectada: sin almacenamiento offline ni sincronización diferida de operaciones.
- **INC-004/D02 — Frescura observable | A [APROBADO POR RESPONSABLE].** Objetivo aprobado de visibilidad en pantalla conectada: máximo 5 segundos tras confirmación bajo condiciones normales de demostración. Medición/carga pertenece a RNF.
- **INC-004/D03 — Desconexión o datos desactualizados | A [APROBADO POR RESPONSABLE].** Ante pérdida de comunicación, identificar explícitamente que los valores pueden estar desactualizados y no confirmar operaciones locales que no hayan sido aceptadas por el backend.
- **INC-004/D04 — Mecanismo | A [APROBADO POR RESPONSABLE].** Definir el mecanismo para la actualización (consultas periódicas o eventos) en diseño posterior; el requisito funcional exige dato actualizado y el stack REST inicial no cambia.

### INC-005 — Catálogo y producto

- **INC-005/D01 — Identidad en la red | A [APROBADO POR RESPONSABLE].** Catálogo común y código de producto único en la red, con existencias independientes por sucursal; transferir no duplica identidad del producto.
- **INC-005/D02 — Atributos mínimos | A [APROBADO POR RESPONSABLE].** Producto: identificador estable, SKU único a nivel organización, nombre, descripción opcional y unidades. No exigir marcas, imágenes, código de barras ni categorías por defecto. Dado que `DEC-TEC-ID-001` está aprobada, el identificador público (`external_id`) será distinto tanto de la clave primaria de persistencia como del SKU; esto no crea un atributo comercial adicional obligatorio.
- **INC-005/D03 — Disponibilidad en catálogo local | A [APROBADO POR RESPONSABLE].** Habilitación local explícita por administrador; al crear producto puede seleccionar varias sucursales o todas las existentes. Habilitar no significa agregar stock.
- **INC-005/D04 — Cambios e identidad histórica | A [APROBADO POR RESPONSABLE].** Conservar identificador estable y SKU tras usarse. Guardar instantánea de descripción comercial/precios en el documento confirmado, además de la referencia estable del producto. De conformidad con `DEC-TEC-ID-001`, preservar además la inmutabilidad de `external_id`, sin migrar ni reescribir referencias históricas.

### INC-006 — Eliminación, inactivación e historia

- **INC-006/D01 — Conservación de todo producto | B [APROBADO POR RESPONSABLE, H v1.3/01].** Todos los productos permanecen registrados; eliminación exclusivamente lógica mediante `active=FALSE`, incluso sin referencias históricas, movimientos o dependencias. Sustituye la alternativa A inicialmente ratificada, que deja de ser aplicable. No se permite eliminación física ni en cascada.
- **INC-006/D02 — Condiciones de baja lógica | A [APROBADO POR RESPONSABLE; aplicación general H v1.3/01].** Para todo producto: conservarlo y permitir desactivación exclusivamente si stock es cero en TODAS las sucursales y no hay operaciones pendientes. Se mantiene la reactivación exclusiva del administrador ratificada en H v1.1, sin restaurar habilitaciones ni mínimos locales.
- **INC-006/D03 — Efecto de inactividad | A [APROBADO POR RESPONSABLE].** Una baja global impide nuevas operaciones sobre el producto, pero no elimina el acceso al historial; coordinar con condición de cierre en D02.
- **INC-006/D04 — Corregir historia | A [APROBADO POR RESPONSABLE].** Nunca editar ni eliminar movimientos históricos confirmados; registrar compensación nueva con vínculo y motivo, preservando auditoría del hecho original.

### INC-007 — Unidades de medida y conversión

- **INC-007/D01 — Modelo de cantidades | A [APROBADO POR RESPONSABLE].** Cada producto tiene su propia unidad base y conversiones, no una equivalencia universal. Ejemplo ilustrativo: una caja del producto P = 12 unidades base.
- **INC-007/D02 — Fraccionamiento | A [APROBADO POR RESPONSABLE].** Permitir decimales únicamente en unidades compatibles con el carácter divisible del producto; los bienes unitarios enteros no aceptan fracciones.
- **INC-007/D03 — Precisión de cantidad | A [APROBADO POR RESPONSABLE].** Precisión de cantidad aprobada: hasta 3 decimales en unidad base cuando se permite fraccionar; rechazar factores/cantidades que exijan mayor precisión, sin redondear silenciosamente.
- **INC-007/D04 — Precios y registro | A [APROBADO POR RESPONSABLE].** Registrar unidad comercial, cantidad y factor usado, precio por dicha unidad y equivalente exacto en unidad base para saldo y valoración.
- **INC-007/D05 — Cambio de equivalencias | A [APROBADO POR RESPONSABLE].** Solo administrador configura factores. Los cambios rigen desde ese momento; no se reexpresan saldos históricos ni documentos pasados. Unidad base inmutable tras uso.

### INC-008 — Stock, disponibilidad y tránsito

- **INC-008/D01 — Reservas | A [APROBADO POR RESPONSABLE].** Sin reservas de stock; la preparación de transferencia no retiene disponibilidad. El despacho vuelve a comprobar existencias.
- **INC-008/D02 — Stock negativo | A [APROBADO POR RESPONSABLE].** No permitir saldo físico negativo después de ventas, transferencias, mermas, ajustes o correcciones.
- **INC-008/D03 — Momento de validar | A [APROBADO POR RESPONSABLE].** Validar y registrar la salida de manera indivisible con la modificación de stock; una validación previa de pantalla no basta.
- **INC-008/D04 — Tránsito | A [APROBADO POR RESPONSABLE].** Lo despachado deja de estar disponible en origen; solo ingresa al destino al confirmar recepción. Mientras tanto se refleja por separado como tránsito.

### INC-009 — Devoluciones, mermas, ajustes y corrección

- **INC-009/D01 — Registro mínimo | A [APROBADO POR RESPONSABLE].** Todo movimiento lleva identificador, clase, sucursal, producto, unidad, cantidad positiva, dirección, motivo, fecha/hora, responsable y referencia relacionada si existe.
- **INC-009/D02 — Devoluciones de ingreso | A [APROBADO POR RESPONSABLE].** Si la devolución refiere a venta, la suma devuelta del producto no puede superar la cantidad realmente vendida menos devoluciones anteriores. Sin referencia, exigir motivo y costo justificado cuando corresponda.
- **INC-009/D03 — Mermas y ajustes | A [APROBADO POR RESPONSABLE].** Operador registra ingresos/retiros ordinarios de inventario, incluidas mermas y devoluciones según permisos. Solo gerente hace ajustes manuales y correcciones de existencias en su sucursal. Sin segundo aprobador automático.
- **INC-009/D04 — Corrección de un movimiento | A [APROBADO POR RESPONSABLE].** Correcciones con movimiento compensatorio vinculado, autor, fecha y motivo. No reinterpretar esta corrección como anulación de compra, venta ni documento fiscal.
- **INC-009/D05 — Fecha de efectos | A [APROBADO POR RESPONSABLE].** El stock y valoración se afectan en el instante de confirmación; no reabrir saldos contables de días anteriores por registrar tarde una incidencia.

### INC-010 — Stock mínimo y alertas

- **INC-010/D01 — Granularidad | A [APROBADO POR RESPONSABLE].** Mínimo configurable por combinación producto-sucursal, mantenido exclusivamente por administrador.
- **INC-010/D02 — Valores y ausencia de configuración | A [APROBADO POR RESPONSABLE].** Exigir valor explícito (puede ser cero) antes de habilitar el producto para operar en una sucursal. No usar nulo como cero implícito.
- **INC-010/D03 — Condición única | A [APROBADO POR RESPONSABLE].** Condición de alerta e indicador próximo a agotarse: disponible <= mínimo. Cuando mínimo es cero, alertar a saldo cero.
- **INC-010/D04 — Canal y destinatarios | A [APROBADO POR RESPONSABLE].** Alertas persistentes en interfaz de gerente afectado y administrador; operador ve indicadores operativos de su sucursal. No correo ni SMS.
- **INC-010/D05 — Disparo y repetición | A [APROBADO POR RESPONSABLE].** Una alerta activa por producto-sucursal; se actualiza después de cambios relevantes y se resuelve al pasar estrictamente por encima del mínimo. Conservar historia de activación/resolución.
- **INC-010/D06 — Faltantes | A [APROBADO POR RESPONSABLE].** Cada faltante de transferencia tiene alerta propia enlazada a la transferencia, no depende del mínimo de stock.

### INC-011 — Compras y recepción

- **INC-011/D01 — Estados y operaciones | A [APROBADO POR RESPONSABLE].** Flujo: borrador → pendiente de recepción → recibida; cancelada antes de recibir. Sin aprobación de compra, tesorería ni pagos.
- **INC-011/D02 — Recepción parcial de compras | A [APROBADO POR RESPONSABLE].** La recepción de compra se confirma completa para las líneas vigentes de una orden; no se implementan compras parcialmente recibidas, a diferencia de transferencias.
- **INC-011/D03 — Cambios y cancelación | A [APROBADO POR RESPONSABLE].** Solo editar borradores; orden pendiente no se reescribe, se cancela y se crea otra, dejando ambos registros. Orden recibida no se borra.
- **INC-011/D04 — Confirmación repetida | A [APROBADO POR RESPONSABLE].** Una recepción confirmada no incrementa inventario ni valor otra vez si llega una petición repetida. La API explicará si retorna el resultado existente o conflicto identificable.
- **INC-011/D05 — Compra pendiente y cierre de sucursal | A [APROBADO POR RESPONSABLE].** Antes de desactivar sucursal deben desaparecer compras pendientes y borradores operables: las pendientes se reciben/cancelan y los borradores se cancelan.
- **INC-011/D06 — Histórico | A [APROBADO POR RESPONSABLE].** Conservar e identificar borradores, canceladas, pendientes y recibidas; consultas por proveedor/producto. Las estadísticas de compra realizada distinguen órdenes de recepciones.

### INC-012 — Proveedores y condiciones comerciales

- **INC-012/D01 — Identificar proveedor | A [APROBADO POR RESPONSABLE].** Proveedor como catálogo de referencia reutilizable mínimo: identificador, código único y nombre. Administrador lo crea y modifica; NO agregar módulo ampliado de evaluación de proveedores.
- **INC-012/D02 — Datos extra | A [APROBADO POR RESPONSABLE].** No exigir NIT, domicilio, correo, cuenta bancaria ni datos fiscales para registrar proveedor y comprar; identidad mínima debe estar disponible.
- **INC-012/D03 — Descuentos de compra | A [APROBADO POR RESPONSABLE].** Porcentaje por línea de compra entre 0 y 100%, con 0 explícito. Precio neto no negativo.
- **INC-012/D04 — Plazo de pago | A [APROBADO POR RESPONSABLE].** Plazo en días enteros no negativos contado desde recepción confirmada; vencimiento calculado y conservado. No implica pagos o saldos por pagar.
- **INC-012/D05 — Moneda, impuestos y fletes | A [APROBADO POR RESPONSABLE].** Para la demostración se aprueba moneda única COP, sin impuestos, fletes ni conversión de divisas. COP es decisión ratificada del proyecto, no obligación explícita de OptiPlant.

### INC-013 — Costo promedio ponderado

- **INC-013/D01 — Método y ámbito | A [APROBADO POR RESPONSABLE].** Promedio ponderado móvil POR producto y sucursal, actualizado al registrar entradas valoradas confirmadas.
- **INC-013/D02 — Costo de entrada | A [APROBADO POR RESPONSABLE].** Costo de entrada por unidad base = precio unitario bruto en unidad de compra × (1 − descuento porcentual) ÷ factor de conversión (si factor expresa unidades base por unidad comercial).
- **INC-013/D03 — Precisión y saldo cero | A [APROBADO POR RESPONSABLE].** Precisión aprobada: cálculos decimales exactos, costo promedio unitario a 6 decimales, resultados monetarios mostrados a 2, redondeo comercial mitad hacia arriba; cuando stock previo = 0, adoptar costo de recepción. Definir política residual cuando se agota.
- **INC-013/D04 — Salidas | A [APROBADO POR RESPONSABLE].** Ventas, mermas, ajustes negativos y despachos consumen costo promedio vigente; no actualizar el costo unitario promedio por la mera salida.
- **INC-013/D05 — Transferencias | A [APROBADO POR RESPONSABLE].** El costo origen al despachar se congela; la recepción parcial/completa ingresa al destino por costo origen despachado. Compras posteriores en origen no cambian valor de ese tránsito.
- **INC-013/D06 — Devoluciones y ajustes positivos | A [APROBADO POR RESPONSABLE].** Devolución asociada a venta: valor al costo histórico de la salida devuelta. Entradas sin referencia usan promedio actual o un costo unitario explícitamente justificado si no existe base.
- **INC-013/D07 — Errores y retroactividad | A [APROBADO POR RESPONSABLE].** Correcciones en fecha actual, vinculadas al original, conservando trazabilidad del valor y sin recalcular todos los movimientos posteriores. Si no hay stock para la compensación, impedir saldo negativo.

### INC-014 — Ventas, descuentos y comprobantes

- **INC-014/D01 — Listas de precios | A [APROBADO POR RESPONSABLE].** Múltiples listas globales mantenidas por administrador; precio por producto y unidad, operador elige lista al confirmar venta. No cambios libres de precio fuera de descuento registrado.
- **INC-014/D02 — Descuento | A [APROBADO POR RESPONSABLE].** Descuento de venta por línea, porcentaje 0–100%, capturado por operador. No combinar con descuentos por cabecera ni promociones.
- **INC-014/D03 — Estados y confirmación | A [APROBADO POR RESPONSABLE].** Solo la venta confirmada descuenta stock y produce comprobante interno; ningún borrador o intento fallido produce movimiento. Evitar doble confirmación.
- **INC-014/D04 — Concurrencia | A [APROBADO POR RESPONSABLE].** Cuando dos ventas disputan unidades, confirmar únicamente las cubiertas; una venta no parcial que falle debe quedar totalmente sin cambios.
- **INC-014/D05 — Anular o devolver venta | A [APROBADO POR RESPONSABLE].** Devolución de inventario referencia venta si existe, pero no reembolsa ni anula financieramente la venta. Dashboard: ventas y devoluciones se muestran separadas.
- **INC-014/D06 — Comprobante | A [APROBADO POR RESPONSABLE].** Registro de venta interno consultable e inmutable: ID, fecha, sucursal, responsable, líneas con SKU/unidad/cantidad, lista/precio, descuentos y total. Sin factura fiscal.

### INC-015 — Preparación, aprobación y despacho de transferencias

- **INC-015/D01 — Quién aprueba y cuándo | A [APROBADO POR RESPONSABLE].** El gerente de sucursal origen aprueba después de preparar/revisar, antes de despachar. Un operador del destino (o el administrador) puede solicitar, y gerente puede supervisar. No atribuir aprobación al admin automáticamente.
- **INC-015/D02 — Una o varias líneas | A [APROBADO POR RESPONSABLE].** Cada solicitud puede incluir varias líneas de productos con mismo origen/destino. Una sola línea por producto/unidad elegida o normalizar repeticiones, conservando cantidades de cada etapa.
- **INC-015/D03 — Cantidad ajustada en origen | A [APROBADO POR RESPONSABLE].** Origen puede proponer menos de lo pedido y el destino lo ve; no enviar más sin nueva solicitud. No despachar una transferencia cuyas cantidades resultan todas cero.
- **INC-015/D04 — Estados | A [APROBADO POR RESPONSABLE].** Estados visibles: solicitada, en preparación, aprobada, en tránsito, recibida completa, con faltantes, cerrada con diferencia tratada, cancelada/rechazada. Identificar estados aún abiertos para bloquear desactivación.
- **INC-015/D05 — Reservar o descontar | A [APROBADO POR RESPONSABLE].** No reservar. Solo despachar reduce stock de origen; revalidar existencias de TODAS las líneas antes de confirmar. En fallo, no hay despacho parcial implícito.
- **INC-015/D06 — Cancelación y rechazo | A [APROBADO POR RESPONSABLE].** Destino cancela solicitud antes de aprobación; después de aprobada y antes de despachar solo origen gerente cancela justificadamente. Origen rechaza con motivo. Prohibido cancelar automáticamente en tránsito.
- **INC-015/D07 — Datos de despacho y transporte | A [APROBADO POR RESPONSABLE].** Registrar nombre/identificador textual de transportista, salida real y llegada estimada obligatorios al despacho. Sin CRUD de transportista ni seguimiento externo.
- **INC-015/D08 — Consistencia y reintentos | A [APROBADO POR RESPONSABLE].** El despacho es una confirmación indivisible para todas sus líneas; reintentos no duplican salidas ni tránsito.
- **INC-015/D09 — Validez de origen y destino | A [APROBADO POR RESPONSABLE].** Origen y destino activos y diferentes. Productos existentes y habilitados donde deban operarse, unidades compatibles. Validación repetida inmediatamente antes del despacho.

### INC-016 — Recepción parcial y faltantes

- **INC-016/D01 — Tránsito y propiedad de cantidades | A [APROBADO POR RESPONSABLE].** Despacho: disminuye físico origen y aumenta tránsito; no cuenta como físico destino hasta recepción. No duplicar saldo de la red.
- **INC-016/D02 — Efecto de recepción parcial | A [APROBADO POR RESPONSABLE].** Al recibir parte, registrar entrada de esa parte y discrepancia independiente que permanece visible y abierta.
- **INC-016/D03 — Recepciones sucesivas | A [APROBADO POR RESPONSABLE].** Permitir varias recepciones del MISMO despacho, siempre que acumulado <= despachado. Una incidencia de llegada sin unidades puede registrarse sin movimiento físico de cantidad cero, si se valida ese escenario.
- **INC-016/D04 — Qué significa faltante | A [APROBADO POR RESPONSABLE].** Diferencia entre enviado y recibido NO es automáticamente merma: puede seguir en tránsito hasta nueva recepción o resolución formal.
- **INC-016/D05 — Reenvío | A [APROBADO POR RESPONSABLE].** Si se decide reponer faltante, crear nueva transferencia vinculada. Antes de reenviar, resolver/documentar destino del faltante original para evitar duplicaciones de stock.
- **INC-016/D06 — Ajuste o reclamación | A [APROBADO POR RESPONSABLE].** Ajuste/pérdida reconocida exige motivo/evidencia y elimina la cantidad pendiente de tránsito justificadamente. Una reclamación sin resolver sigue abierta; nunca incrementa stock por sí sola.
- **INC-016/D07 — Autoridad y estados finales | A [APROBADO POR RESPONSABLE].** Operador registra recepción; gerentes de origen y destino gestionan discrepancia y administrador supervisa. Gerente de origen autoriza baja de valor en tránsito, gerente de destino valida lo recibido; no modificar stock de sucursal ajena.
- **INC-016/D08 — Costo y reversión | A [APROBADO POR RESPONSABLE].** Usar costo unitario de despacho para recepción, tránsito y pérdida reconocida, y contabilizar pérdida una sola vez.
- **INC-016/D09 — Errores y sobrantes | A [APROBADO POR RESPONSABLE].** Si acumulado excede envío, rechazar antes de confirmar. Corrección de recepción confirmada mediante compensación vinculada y permisos, no alterando el registro original.

### INC-017 — Logística y rutas

- **INC-017/D01 — Ruta | A [APROBADO POR RESPONSABLE].** Ruta dirigida origen–destino, con identificador y clasificación. No rutas con escalas, mapas, tracking ni optimización.
- **INC-017/D02 — Clasificación | A [APROBADO POR RESPONSABLE].** Clasificar rutas por prioridad manual alta/media/baja. No gestionar tarifas logísticas ni motores de optimización; el tiempo se mide separadamente.
- **INC-017/D03 — Medición de duración | A [APROBADO POR RESPONSABLE].** Distinguir duración de entrega física (despacho→recepción completa) de tiempo hasta cierre administrativo (despacho→cierre con diferencia tratada). La primera NO equivale a la segunda en entregas con pérdida.
- **INC-017/D04 — Estimado y cumplimiento | A [APROBADO POR RESPONSABLE].** Puntual si recepción COMPLETA <= fecha/hora estimada al despacho. KPI de entregas completas puntuales / transferencias cerradas del período; cierres con diferencia cuentan como no cumplidas, y abiertas vencidas se muestran aparte.
- **INC-017/D05 — Tiempo estimado de ruta | B [APROBADO POR RESPONSABLE].** La llegada estimada se ingresa al despachar. No configurar tiempos típicos automáticos por ruta como requisito.
- **INC-017/D06 — Calendario y zona horaria | A [APROBADO POR RESPONSABLE].** Zona de negocio única aprobada: America/Bogota. Mostrarla explícitamente y utilizarla de manera uniforme para calendario, indicadores y filtros de exportación.
- **INC-017/D07 — Reporte por sucursal | A [APROBADO POR RESPONSABLE].** Reportar desempeño de cada sucursal como origen y como destino en apartados diferenciados y por ruta dirigida.

### INC-018 — Indicadores del dashboard

- **INC-018/D01 — Volumen de ventas | A [APROBADO POR RESPONSABLE].** Monetario: importe neto de descuentos de ventas confirmadas. Físico: cantidades vendidas en unidad base por producto; mostrar devoluciones separadas, no como ventas negativas implícitas.
- **INC-018/D02 — Período comparado | A [APROBADO POR RESPONSABLE].** Mes actual y tres meses anteriores; diferenciar meses completos de mes en curso. Opcionalmente mostrar comparativa acumulada hasta igual día del mes para no inducir error.
- **INC-018/D03 — Rotación | A [APROBADO POR RESPONSABLE].** Rotación por producto/sucursal = unidades vendidas período ÷ inventario físico promedio temporal diario del período. Denominador cero => no calculable.
- **INC-018/D04 — Alta y baja demanda | A [APROBADO POR RESPONSABLE].** Ranking de 5 de mayor venta y 5 de menor venta entre productos con ventas, sin solapar grupos; productos sin ventas se muestran aparte. Unidades no comparables deben identificarse, sin agregados físicos heterogéneos.
- **INC-018/D05 — Transferencias activas e impacto | A [APROBADO POR RESPONSABLE].** Solicitado/preparado es previsión, despachado es tránsito, recibido incrementa físico, faltante pendiente aparte; ninguna solicitud modifica disponible.
- **INC-018/D06 — Rendimiento entre sucursales | A [APROBADO POR RESPONSABLE].** Comparación red solo para administrador: valor de ventas confirmado neto de descuentos, número de ventas y cumplimiento logístico del mismo período; no indicar rentabilidad.
- **INC-018/D07 — Ausencia de datos e historia inicial | A [APROBADO POR RESPONSABLE].** Diferenciar cero real de ausencia de datos/no calculable; no fabricar meses ni historial ni presentar seeds de prueba como datos productivos.

### INC-019 — Historias de usuario ilustrativas

- **INC-019/D01 — Órdenes de pago | A [APROBADO POR RESPONSABLE].** El ejemplo de historia de usuario de órdenes de pago NO vuelve obligatoria una funcionalidad financiera; conservar condiciones de compra y plazo exigidos.
- **INC-019/D02 — Tres meses anteriores | A [APROBADO POR RESPONSABLE].** Mismo acuerdo temporal que INC-018/D02: tres meses anteriores. Esta no es una segunda decisión independiente.
- **INC-019/D03 — Urgencia en transferencia | A [APROBADO POR RESPONSABLE].** Urgencia de solicitud (de historia recomendada) no se incorpora en la primera versión; no se confunde con prioridad de ruta obligatoria.

### INC-020 — Reportes exportables

- **INC-020/D01 — Formato | A [APROBADO POR RESPONSABLE].** Generar Excel XLSX real, no CSV renombrado. No agregar PDF en la funcionalidad adicional.
- **INC-020/D02 — Categorías | A [APROBADO POR RESPONSABLE].** Tres categorías: movimientos de inventario, ventas y transferencias. No exportar usuarios, costos internos indiscriminadamente ni todo el dashboard.
- **INC-020/D03 — Filtros | A [APROBADO POR RESPONSABLE].** Filtros: fechas obligatorias, sucursal según visibilidad y estado opcional si corresponde. Admin puede consultar una/todas; gerente solo suya; operador no exporta.
- **INC-020/D04 — Fecha de cada categoría | A [APROBADO POR RESPONSABLE].** Fecha filtro movimientos: registro; ventas: confirmación; transferencias: solicitud. Incluir columnas de despacho/recepción si existen y declarar la fecha del filtro.
- **INC-020/D05 — Límites del rango | A [APROBADO POR RESPONSABLE].** Fechas inicial y final inclusivas según America/Bogota, sin solapamiento al día siguiente. Rechazar inicio > final.
- **INC-020/D06 — Columnas de movimientos | A [APROBADO POR RESPONSABLE].** Movimientos: id, fecha, sucursal, SKU/nombre producto, clase/motivo, cantidad/unidad, usuario responsable, documento origen. Omitir costos en exportación estándar.
- **INC-020/D07 — Columnas de ventas | A [APROBADO POR RESPONSABLE].** Ventas: una fila por línea, id, fecha, sucursal, responsable, producto/unidad, cantidad, precio, descuento, importe neto de línea. No repetir total de cabecera como importe sumable.
- **INC-020/D08 — Columnas de transferencias | A [APROBADO POR RESPONSABLE].** Transferencias: una fila por línea con id, origen, destino, fechas, estado, SKU/unidad, cantidad solicitada/despachada/recibida acumulada, diferencia pendiente y tratamiento. No duplicar fila por recepción.
- **INC-020/D09 — Coherencia y archivo vacío | A [APROBADO POR RESPONSABLE].** Exportar resultado coherente con encabezados/metadatos; sin filas, generar XLSX válido vacío con nota explícita. Error no modifica operaciones.
- **INC-020/D10 — Generación y entrega | A [APROBADO POR RESPONSABLE].** Descarga directa, sin tareas de generación asíncrona ni repositorio de archivos. Tope operativo aprobado: máximo 10.000 filas y 12 meses por reporte; exceder => error claro sin truncamiento.

## 4. Parámetros aprobados y ejemplos de aceptación

| Parámetro | Valor aprobado | Estado/Ubicación documental |
|---|---|---|
| Visibilidad near-real-time | ≤ 5 segundos tras confirmación en entorno de demostración definido | APROBADO; trasladar a RNF de rendimiento y RF de actualización visible |
| Contraseña | 15–128 caracteres; se permiten espacios; no cambio periódico obligatorio | APROBADO; documentar en RNF de seguridad / RF cambio inicial/voluntario |
| Sesión | 30 minutos de inactividad; 8 horas de duración máxima | APROBADO; documentar en RNF seguridad / RF acceso |
| Fallos de acceso | 5 fallos/15 minutos, espera progresiva 5, 15, 30 minutos | APROBADO como política inicial; desarrollar diseño antiabuso en RNF/arquitectura |
| Cantidad física | hasta 3 decimales en unidad base si producto divisible | APROBADO; RF unidades / reglas de negocio |
| Costo unitario | hasta 6 decimales; cálculos decimales exactos | APROBADO; RF valoración / RNF precisión |
| Totales monetarios | 2 decimales; mitad hacia arriba | APROBADO; RF importes / RNF precisión |
| Moneda | COP, como única moneda de demostración | APROBADO como supuesto de negocio del proyecto, no exigencia externa |
| Zona calendario | America/Bogota | APROBADO; supuesto del proyecto / comportamiento de filtros |
| Descuento por línea | 0–100 %, sin acumular descuentos múltiples | APROBADO; RF compras/ventas |
| Rotación | Unidades vendidas / stock físico promedio temporal diario del período | APROBADO; RF dashboard |
| Comparación ventas | Mes actual frente a 3 meses anteriores; señalar mes incompleto | APROBADO; RF dashboard |
| Ranking alta/baja demanda | Hasta 5 productos con ventas en cada extremo, sin solapamientos; sin ventas por separado | APROBADO; RF dashboard |
| Exportación | XLSX, rango inclusivo, 3 categorías, ≤ 10.000 filas y ≤ 12 meses | APROBADO; RF exportación / RNF capacidad |

### Ejemplos de aceptación numérica

1. **Promedio móvil:** producto P con 10 unidades a costo 100 + entrada de 5 unidades a costo neto 130 ⇒ cantidad 15 y promedio 110. Salida de 3 unidades ⇒ saldo 12 unidades con promedio 110. No incorporar una compra cancelada a este promedio.
2. **Unidades:** si 1 caja = 12 unidades base y se compra 1 caja por 120 con descuento de 10%, ingreso = 12 unidades a costo 9 por unidad base. La conversión debe ser del producto concreto.
3. **Transferencia parcial:** origen despacha 10 unidades a costo congelado 9; se reciben 7 ⇒ origen redujo 10; destino aumenta 7; 3 permanecen identificadas en tránsito/faltante (27 de valor), con «Recibida con discrepancia». No hay recepción complementaria del mismo despacho: la regularización se registra aparte y un reenvío exige nueva transferencia. Una llegada de cero unidades sigue el mismo flujo sin ingreso físico.
4. **Venta concurrente:** quedan 3 unidades; dos solicitudes simultáneas de venta de 2 no se confirman ambas. La solicitud rechazada no produce comprobante ni movimiento parcial.
5. **Alertas:** producto con disponible 5 y mínimo 5 activa aviso; si sube a 6 se resuelve. Una diferencia de transferencia genera un aviso logístico separado, independientemente del mínimo.

## 5. Refinamientos pendientes que NO revierten las decisiones aprobadas

La elección de las 115 alternativas, los parámetros de §4 y la identificación dual están **ratificados**. Los siguientes puntos son **trabajos de especificación, verificación o diseño**, no excusa para reabrir todas las decisiones. Si alguno exige una nueva elección funcional sustancial, elevar **solo ese caso** al responsable sin inventar el resultado:

1. **INC-003:** redactar una matriz completa de roles/acciones/sucursal, incluyendo el manejo de tareas personales abiertas antes de reasignar a un usuario. Ya están aprobados los permisos descritos en §3; no conceder privilegios por defecto.
2. **INC-003 (seguridad):** detallar implementación de revocación de sesiones, provisión inicial y mitigación del abuso del control de intentos fallidos. Los límites de contraseña y sesión de §4 ya están aprobados. No elegir una biblioteca o estrategia de token implícitamente.
3. **INC-005/010 y 006:** la habilitación local exige configurar mínimo explícito, incluido cero; aclarar en el diseño transaccional cómo se realiza. H v1.1 ya aprueba la reactivación exclusiva por administrador, sin restauración automática de habilitación local ni mínimo; diseñar la secuencia técnica sin alterar esa regla.
4. **INC-009/013:** diseñar y validar con ejemplos las compensaciones que afectan al **valor** del inventario después de movimientos posteriores (costo histórico vs. vigente, remanentes, saldo cero); no reescribir eventos históricos y no generar stock negativo. Si no se puede establecer una política consistente con las reglas ratificadas, registrar la brecha antes de implementar.
5. **INC-015/016:** documentar claramente estados y transiciones de la única llegada por despacho y reclamaciones abiertas. H v1.1 aprueba una llegada de cero unidades como recepción parcial sin movimiento físico, con faltante total, alerta y tratamiento separado. Mantener cerradas las reglas de faltantes, responsables y costos ya decididas.
6. **INC-017:** diferenciar métricas de entrega completa y cierre administrativo en fichas/criterios de aceptación; nunca computar una pérdida tratada como entrega completa puntual.
7. **INC-018:** definir cómo calcular el promedio diario cuando no existe historia suficiente y cómo presentar de forma honesta rankings de productos de unidades heterogéneas, sin sumar piezas y kilos como unidad única. No fabricar historial.
8. **INC-020:** documentar manejo de límites de 10.000 filas y 12 meses ya **aprobados**, error explícito y generación de archivo vacío; no truncar resultados.
9. **DEC-TEC-ID-001:** concretar alcance exacto por entidad, estrategia de generación del UUIDv4, nombres coherentes de propiedades en DTO, restricciones `UNIQUE`, mapeos y pruebas de autorización. La identificación dual ya está aprobada; los detalles de implementación no autorizan cambiar stack ni exponer claves internas.

**No quedan pendientes de ratificación**: COP como moneda única de demostración, `America/Bogota` como zona común, los períodos de tres meses anteriores, el formato XLSX, las tres categorías, los topes de exportación ni el método de clave dual. Son **decisiones del proyecto**, no exigencias expresas de OptiPlant.

## 6. Exclusiones explícitas

No introducir por inferencia: operación offline; sincronización de escrituras diferidas; sistemas externos; emails/SMS; recuperación automática por email; autogestión de registro; doble factor; procesamiento de pagos; órdenes de pago; facturación fiscal; impuestos/fletes; contabilidad financiera; rutas con GPS/optimización; transacciones transportistas externas; pedidos divididos en varios despachos; compra recibida parcialmente; reservación de stock; predicción de demanda; módulo avanzado de proveedores; promociones; múltiples monedas; generación asíncrona de exportaciones.

## 7. Instrucciones para integrar las decisiones aprobadas

El responsable **autoriza la integración documental** de las resoluciones ratificadas en el catálogo de requisitos funcionales y su matriz de trazabilidad. Esta autorización no es un permiso para desarrollar funcionalidades ni modificar código/arquitectura.

1. Comparar la versión vigente de `requisitos-funcionales.md`, `AGENTS.md` y el enunciado PDF con la base de esta aprobación (catálogo v0.9 mencionado por la guía). Si hay modificaciones posteriores contradictorias, comunicar la discrepancia antes de sobrescribirla.
2. Incorporar las reglas de **cada uno de los 115 códigos** `INC-NNN/DNN` de §3 como aprobadas, ajustando solo los RF/CA afectados, sin renumerar IDs ni duplicar requisitos. Preservar las diferencias entre exigencias externas, reglas derivadas y elecciones propias de proyecto.
3. Consolidar matriz de permisos; estados y transiciones de compras/ventas/transferencias; glosario físico/disponible/tránsito/faltante; valoración; reglas de unidades; indicadores y reportes. Vincular módulos con reglas transversales sin copiarlas de forma contradictoria.
4. Trasladar los aspectos cuantitativos de rendimiento, controles de seguridad, límites de volumen y precisión técnica a RNF cuando corresponda; reflejar el resultado observable imprescindible también en RF/CA. Registrar moneda, zona y exclusiones donde corresponda en supuestos y restricciones.
5. Registrar `DEC-TEC-ID-001` como **decisión arquitectónica aprobada**, una única vez en el registro de decisiones técnicas o en documentación de arquitectura/modelo E-R/contrato API cuando se produzcan esos artefactos. En los RF incluir únicamente los efectos funcionales observables, sin multiplicar requisitos por columna de base de datos.
6. Verificar casos: ventas concurrentes, promedio móvil, conversiones decimales, movimientos compensatorios, recepción duplicada de compra, transferencias con faltantes, reclamaciones, exportación XLSX, límites y acceso por rol/sucursal. Para los bordes de §5, registrar una tarea pendiente específica.
7. Emitir informe de integración `INC → RF → CA` con resultado (`incorporado`, `incorporado con detalle de diseño pendiente`, `conflicto detectado`). No declarar implementado ni probado lo que solo está aprobado/documentado.
8. No ejecutar commits, merges, modificaciones de fuentes originales, cambios en `AGENTS.md`, código de aplicación, DDL ni diagramas sin una orden independiente.

## 8. Acta de ratificación y alcance de la aprobación

- **Quién decide:** responsable del proyecto, mediante autorización explícita en esta conversación.
- **Fecha:** 20 de octubre de 2026.
- **Documento ratificado:** `propuesta-cierre-incertidumbres-optiplant-v0.2.md`.
- **Decisiones funcionales ratificadas:** totalidad de `INC-003/D01–D14` hasta `INC-020/D01–D10` (115 de 115, según la matriz de §3); 18 bloques de incertidumbre.
- **Decisión técnica adicional ratificada:** `DEC-TEC-ID-001`, identificación dual interna/pública (§9), independiente del conteo funcional.
- **Valores y precisiones:** los especificados en §3 y §4, incluidos COP, America/Bogota, UUIDv4 público, `BIGINT` interno, 10.000 filas y 12 meses por exportación.
- **Excepciones expresas del responsable:** ninguna indicada en la ratificación; permanecen solo condiciones y aspectos no definidos por el propio artefacto (§5).
- **Vigencia:** este documento consolida la aprobación, **no sustituye** al PDF original ni acredita por sí solo la actualización material de RF, RNF, diseño, código o pruebas.

**Regla para el agente:** la autorización de estas decisiones es real y no debe quedar etiquetada como mera recomendación IA. Las cuestiones pendientes mencionadas en §5 continúan sin selección adicional; está prohibido adjudicarlas por inferencia.

## 9. Decisión técnica transversal aprobada: identificador interno y externo

**Código:** `DEC-TEC-ID-001` · **Estado:** `APROBADO POR RESPONSABLE — 20/10/2026` · **Naturaleza:** decisión de persistencia y contrato API, **no** requisito funcional nuevo ni exigencia de OptiPlant.

### 9.1. Regla aprobada

Adoptar **dos identificadores técnicos para entidades persistentes expuestas individualmente por la API REST**:

- `id`: clave primaria técnica interna, `BIGINT`, usada en PostgreSQL para las relaciones entre entidades (`FK`) y en el funcionamiento interno del backend.
- `external_id`: identificador público **UUID versión 4**, único, no nulo e inmutable, generado desde un componente de confianza del backend o base de datos para identificar la entidad en solicitudes, rutas y respuestas de API. La implementación exacta de generación se definirá en diseño; el contrato público utilizará UUIDv4.
- Los códigos de negocio (`SKU` de producto, código único de acceso de usuario, y eventuales números legibles de orden) se conservan **por separado**. No se sustituyen por UUID ni sirven automáticamente de clave primaria.

La necesidad funcional es identificar sin ambigüedad los recursos y mantener sus referencias a lo largo del tiempo. **La duplicidad técnica `id`/`external_id` y los tipos de columna pertenecen al diseño de datos/arquitectura**; no deben convertirse en campos editables del formulario ni duplicarse como RF por entidad.

### 9.2. Alcance aprobado por criterio de exposición

Aplicar, cuando sean recursos consultados, creados, modificados o referenciados individualmente desde la API, a: usuarios, sucursales, productos, proveedores, órdenes de compra, ventas, transferencias, movimientos de inventario consultables, y, si se exponen como recursos individuales, rutas y listas de precios. Evaluar la necesidad en unidades, comprobantes, recepciones y otros recursos que efectivamente se gestionen por API.

**No** exigir un `external_id` para cada tabla de asociación, línea de detalle, fila de apoyo o tabla interna sin identidad independiente desde la API. No duplicar claves por reflejo. Una entidad de detalle obtiene ID externo únicamente si se consulta/manipula por separado y ello se justifica.

### 9.3. Regla contractual y ejemplo ilustrativo

Para un producto ficticio:

| Propiedad | Persistencia (ejemplo) | API (ejemplo) |
|---|---|---|
| `id` interno | `42` | Nunca se devuelve como identificador del recurso |
| `external_id` | `a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11` | En `/api/productos/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11` |
| `sku` | `PROD-001` | Se devuelve como atributo comercial propio |

Un ejemplo de representación JSON es `{"id":"a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11","sku":"PROD-001","nombre":"Arroz 500 g"}`. **En el ejemplo de DTO REST, `id` representa el UUID público**, mientras en la entidad de persistencia `id` representa el `BIGINT`. Esta convención requiere documentación y pruebas para evitar confusión; también sería válido llamarlo `externalId` en los DTO si se adopta **una sola convención** consistentemente. No presentar este ejemplo como endpoint ya aprobado.

En cada operación: el backend recibe `external_id`, identifica la entidad mediante un índice único, **comprueba los permisos de rol y sucursal sobre esa entidad**, y utiliza el `id` interno para sus relaciones. Jamás confiar en el identificador externo como prueba de autorización. En particular, conocer el UUID de una venta, transferencia o sucursal **no** autoriza a modificarla ni a consultar información fuera del ámbito permitido.

### 9.4. Reglas de integridad y ciclo de vida

1. Al crear una entidad su identificador externo se asigna por el backend o por la base de datos, no como dato arbitrario editable por el frontend; debe existir unicidad en base de datos.
2. `id` y `external_id` no se modifican después de crear el registro. La inactivación conserva ambos y todas las referencias históricas.
3. Asociaciones entre entidades en el contrato REST utilizan referencias públicas; el backend las resuelve a claves internas y aplica validación de existencia, ámbito y permisos.
4. Claves foráneas internas siguen haciendo referencia al `id` técnico según el modelo de datos. La exclusión de integraciones externas permanece vigente.
5. Errores y respuestas no deben revelar claves internas ni detalles de persistencia. El método de resolución y tipos finales se confirman con el modelo E-R, JPA y los contratos REST.
6. Los límites de seguridad se prueban sobre solicitudes que alteran el UUID para intentar consultar u operar sobre otras sucursales, además de verificar que el `id` numérico no sea aceptado en su lugar.
7. UUID no es secreto, sesión ni token de autenticación; no previene por sí mismo vulnerabilidades de referencia insegura a objetos (IDOR).

### 9.5. Trazabilidad con las incertidumbres

| Consideración | Relación con la decisión aprobada |
|---|---|
| `INC-003/D07–D08` | Cambiar rol o sucursal de un usuario no cambia su identidad técnica ni su historial; cada acceso se revalida según permisos actuales. |
| `INC-005/D01–D04` | Producto común a la red con identificador técnico estable y SKU comercial independiente; no confundir catálogo global con existencias locales. |
| `INC-006/D01–D04` | Baja exclusivamente lógica de productos según H v1.3/01; conservar claves foráneas y referencias a documentos históricos; no modificar `external_id` por una reactivación. |
| `INC-009/D04`, `INC-011`, `INC-014`, `INC-015` y `INC-016` | Movimientos, compras, ventas y transferencias se referencian por identificadores públicos en la API, con vínculo histórico interno e integridad de operaciones. |
| `INC-012/D01`, `INC-017/D01` | Código de proveedor o identificador de ruta no equivale necesariamente al UUID público ni a la PK. |
| `INC-020/D06–D08` | Cuando los archivos exportan `id` de movimiento/venta/transferencia, se utiliza el identificador público o el número comercial autorizado, **nunca la clave primaria técnica**, y se rotulan inequívocamente las columnas. |

### 9.6. Ventajas y límites

**Ventajas esperadas:** desacoplar el contrato público de la estrategia de claves relacionales; permitir claves internas compactas y foráneas sencillas; conservar referencias API estables; reducir la exposición de identificadores secuenciales y la enumeración trivial; aportar una convención uniforme en módulos.

**Costos:** columna e índice `UNIQUE` adicionales por entidad alcanzada, generación de UUID y búsquedas por identificador público; más trabajo en mapeos entidad/DTO. No afirmar mejoras universales de rendimiento ni seguridad automática.

**Alternativa válida no escogida por esta decisión:** un único UUID como clave primaria y pública, que reduce duplicación de identificadores, pero implica otro diseño de índices/foráneas. La separación de identificadores es una **decisión arquitectónica aprobada por el proyecto**, no una imposición técnica externa.

### 9.7. Acción solicitada al agente tras la aprobación

1. Registrar `DEC-TEC-ID-001` como **APROBADA** por decisión expresa del responsable, separada de las 115 decisiones funcionales.
2. Reflejar el principio en el registro de arquitectura, en el modelo de datos y en el contrato de API cuando estos artefactos se elaboren; **no** modificar SQL/entidades/DTO/endpoints/código ahora, porque aprobar una decisión no autoriza construirla.
3. Confirmar en diseño únicamente detalles no cerrados: qué recursos tienen UUID público, mecanismo de generación UUIDv4, nombres consistentes del DTO (`id` público versus `externalId`), índices y pruebas de autorización. No volver a preguntar si se adopta o no la identificación dual: **esa decisión está tomada**.
4. No crear nuevos RF artificiales para la presencia de columnas técnicas. Al actualizar RF/CA, preservar las referencias funcionales, la seguridad de acceso y el uso de identificadores públicos en reportes y operaciones donde proceda.
5. El `id` relacional interno nunca se expone como identificador público; la autorización por recurso sigue siendo obligatoria aunque el UUID sea difícil de adivinar.

**Constancia de ratificación:** “Queda aprobada `DEC-TEC-ID-001` como convención técnica transversal de identificación dual para las entidades expuestas individualmente por API, con `BIGINT` interno y UUIDv4 público inmutable, preservando códigos de negocio independientes y comprobando autorización en cada operación”.

### Referencias técnicas para el diseño posterior

- OWASP, *Insecure Direct Object Reference Prevention Cheat Sheet*: https://cheatsheetseries.owasp.org/cheatsheets/Insecure_Direct_Object_Reference_Prevention_Cheat_Sheet.html
- PostgreSQL, *UUID Type*: https://www.postgresql.org/docs/current/datatype-uuid.html
- Hibernate ORM, *Generated Identifiers*: https://docs.hibernate.org/stable/orm/introduction/html_single/


## 10. Resoluciones funcionales posteriores — H v1.1

**Estado:** APROBADAS POR EL RESPONSABLE DEL PROYECTO — 21 de octubre de 2026. Estas reglas complementan el registro sin modificar sus antecedentes ni atribuirlas al enunciado externo.

- **H v1.1/01 — Reactivación de producto inactivo.** Solo el administrador general puede reactivar un producto inactivo. La reactivación lo restituye al catálogo general y conserva su identidad e historial, pero no lo habilita automáticamente en ninguna sucursal ni restaura habilitaciones o mínimos anteriores. Para operar en una sucursal, debe habilitarse explícitamente y configurarse un nuevo stock mínimo.
- **H v1.1/02 — Llegada de transferencia con cero unidades.** Una llegada confirmada de cero unidades se procesa como recepción parcial: no crea un movimiento físico de cantidad cero ni aumenta inventario destino; deja el total despachado como faltante/tránsito al costo congelado, establece **«Recibida con discrepancia»** y genera la alerta vinculada. La recepción física queda finalizada; no se permite una segunda recepción del mismo despacho. El tratamiento posterior conserva las reglas de reenvío, ajuste o reclamación y no modifica saldos automáticamente.

**Trazabilidad:** H v1.1/01 → RF-0011, UC-006, HU-005. H v1.1/02 → RF-0037–RF-0039, UC-017, HU-017.


## 11. Resolución funcional posterior — H v1.2/01

**Estado:** APROBADA POR EL RESPONSABLE DEL PROYECTO — 21 de octubre de 2026. Precisa INC-015/D06 sin reescribir su antecedente.

- **Rechazo motivado de transferencia:** exclusivamente el gerente de la sucursal de origen, únicamente cuando la transferencia esté **«en preparación»** y antes de aprobarla. El motivo es obligatorio. La transferencia pasa a **«rechazada»**, conservando motivo y responsable, sin modificar cantidades, stock ni tránsito. Desde otros estados o por otros actores se deniega sin cambios.
- **Responsabilidades conservadas:** el operador de origen prepara y despacha; el gerente de origen revisa, aprueba o rechaza y cancela según la fase autorizada. No se ratificó trasladar todas las acciones de origen al gerente ni al administrador.
- **Evidencia humana:** «Dejemos que el rechazo lo haga el gerente de origen tambien»; a la propuesta «¿Confirmamos que el gerente de origen podrá rechazar con motivo únicamente cuando la transferencia esté «en preparación», antes de aprobarla?» respondió «Si confirmamos».
- **Trazabilidad:** INC-015/D06 → H v1.2/01 → RF-0034-CA-03–RF-0034-CA-04, matriz §5.2, UC-015, HU-015 y actividad de transferencia. No cambia las reglas de cancelación, recepción o resolución de faltantes.

**Control de aprobación:** 18 bloques / **115 decisiones funcionales iniciales aprobadas** / **2 resoluciones funcionales posteriores H v1.1 aprobadas** / **1 resolución funcional posterior H v1.2/01 aprobada** / **1 decisión técnica transversal aprobada** / **0 propuestas de la matriz sin ratificar**. Los escenarios de refinamiento descritos en §5 aún requieren especificación y pruebas.

## 12. Resoluciones posteriores — H v1.3/01–02

**Estado:** APROBADAS POR EL RESPONSABLE DEL PROYECTO — 22 de octubre de 2026. Actualización limitada a las dos decisiones ratificadas; no autoriza implementación.

- **H v1.3/01 — Productos conservados permanentemente.** «Todos los productos deberán conservarse en la base de datos. No se permitirá su eliminación física, independientemente de que tengan o no referencias históricas, movimientos o dependencias». La eliminación consiste exclusivamente en desactivar mediante `active`, respetando stock cero en toda la red y ausencia de pendientes. Sustituye la alternativa A anterior de INC-006/D01; no modifica las reglas vigentes de reactivación. Trazabilidad: RF-0011, UC-006, HU-005, modelo §6.2/§7.4/DAT-011.
- **H v1.3/02 — Nombre histórico del proveedor.** «Al crear una orden de compra en estado `DRAFT`, `supplier_name_snapshot` almacenará el nombre vigente del proveedor»; «Al pasar la orden a `PENDING`, el campo se actualizará con el nombre vigente del proveedor en ese momento»; «A partir de `PENDING`, el nombre quedará congelado y no se modificará por cambios posteriores en el catálogo de proveedores»; «Se mantiene `supplier_name_snapshot` como `NOT NULL`». Precisa INC-012/D01 sin ampliar el catálogo ni alterar estados/recepción. Trazabilidad: RF-0021-CA-03, UC-010, HU-009, modelo §6.3/DAT-009.

**Control de actualización:** las aprobaciones históricas anteriores se conservan, con dos resoluciones H v1.3/01–02 añadidas y sustitución expresamente identificada de INC-006/D01. La guía temporal mantiene alternativas como antecedente no vigente, no como permiso operativo.
