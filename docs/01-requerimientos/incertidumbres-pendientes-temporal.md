# Incertidumbres pendientes — guía temporal de decisiones

**Estado:** documento temporal de análisis; alternativas y recomendaciones **no aprobadas**.  
**Base:** [catálogo funcional v0.9](requisitos-funcionales.md), [AGENTS.md](../../AGENTS.md) y [enunciado original](../00-source/prueba-tecnica.pdf), con su [transcripción](../00-source/prueba-tecnica.md).  
**Propósito:** facilitar una respuesta conjunta del responsable del proyecto, sin modificar el catálogo ni resolver decisiones en su nombre.

## Cómo revisar y responder

1. Leer los límites ya aprobados antes de elegir alternativas.
2. Revisar cada consideración identificada como `INC-NNN/DNN`. Estos códigos son referencias de este cuestionario, no nuevos requisitos ni decisiones arquitectónicas aprobadas.
3. Responder con la alternativa elegida y sus precisiones. Puede rechazarse una recomendación, combinar opciones coherentes, proponer otra o dejar un punto pendiente.
4. Indicar valores concretos cuando la elección los requiera: fórmulas, plazos, umbrales, columnas, etc. Aprobar una opción no aprueba números que no estén escritos.
5. Tras tu respuesta, contrastaremos consistencia y alcance antes de actualizar las fichas afectadas. Una elección no cierra automáticamente toda una incertidumbre.

Ejemplo de respuesta — **solo formato, no decisión**:

```text
INC-004/D01: A.
INC-004/D02: A, con actualización visible en un máximo de 5 segundos.
INC-013/D01: necesito revisar el ejemplo antes de decidir.
INC-020/D04: otra propuesta: <detalle>.
```

En las tablas, **A/B/C son alternativas locales a cada fila**. La recomendación explica cuál se propone y por qué; no es una selección humana. Todos los valores numéricos propuestos son puntos de partida del proyecto, no exigencias de OptiPlant. No hay respuestas preseleccionadas.

## 1. Situación actual y límites que no se reabren

Hay **18 incertidumbres con pendientes**: INC-003 parcialmente resuelta y las 17 de INC-004 a INC-020 abiertas. INC-001 e INC-002 están resueltas y solo se citan como contexto.

### Decisiones ya aprobadas

- Administrador consulta y modifica stock mínimo; ese mismo parámetro sirve para reabastecimiento y productos próximos a agotarse. No se propone otro umbral independiente.
- Administrador gestiona usuarios y sucursales: crear, listar, consultar, actualizar y desactivar, sin eliminación física.
- Atributos mínimos de ambas entidades aprobados en RF-0002 y RF-0003. No se reabre su selección; las asignaciones se rigen por las decisiones de acceso.
- Desactivar sucursal requiere existencias cero y ausencia de compras por recibir o transferencias abiertas, incluidos faltantes pendientes. Se conserva historia, se impide operar y no se desactivan automáticamente sus usuarios.
- Administrador con CRUD general de productos; gerente puede ajustar stock únicamente en su sucursal activa, mediante movimientos con fecha, responsable, motivo y cantidad. No hay edición directa del saldo sin trazabilidad.
- Un rol por usuario. Administrador global sin sucursal obligatoria; gerente y operador con una única sucursal. Consultar otro inventario no permite modificarlo.
- Acceso por código único de usuario y contraseña personal; política, provisión y sesiones aún pendientes.
- Comparativas de rendimiento entre sucursales exclusivas del administrador. Gerente conserva dashboard y reportes locales. La consulta de inventario de otras sucursales sigue permitida.
- Adicional elegido: reportes exportables. Excel `.xlsx`, las tres categorías y el filtro por fechas siguen como propuestas de detalle.
- Integraciones ERP/POS y otras plataformas externas excluidas. La API REST entre React y Spring Boot permanece.
- Stack y monolito modular por capas aprobados en AGENTS.md; no se propone sustituirlos. Los límites de paquetes y mecanismos técnicos se justifican en §8.

### Distinciones de lectura

- **Exigencia externa:** comportamiento que aparece en el enunciado. Se identifica por su sección.
- **Vacío:** detalle que la fuente no decide; no autoriza a omitir la función.
- **Propuesta de precisión:** alternativa para hacer verificable una obligación existente.
- **Ampliación potencial:** añade un flujo o capacidad no expresamente exigido. Se señala y no se incorpora sin aprobación.
- **Diseño posterior:** mecanismo técnico, modelo físico o parámetro de calidad que debe desarrollarse después, sin dejar ambiguo el resultado funcional.

## 2. INC-003 — Permisos restantes, credenciales y sesiones

**Fuente:** §3.6, §6.2 y §8.2. **Afecta:** RF-0002, RF-0005–RF-0007, consultas y operaciones de todos los módulos.  
**Lo que falta:** completar responsabilidades no detalladas, reasignación de usuarios y ciclo de acceso. No se reabren rol único, sucursal única, CRUD del administrador, ajustes locales del gerente ni exclusividad de comparativas.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-003/D01 — Operaciones del administrador | A: administrar catálogo/configuración/usuarios/sucursales y consultar la red, sin heredar automáticamente operaciones comerciales. B: además, poder ejecutar todas las operaciones locales seleccionando sucursal activa. | **A**, separación clara entre administración global y operación local; conserva las facultades explícitas del administrador para solicitar transferencias. | Definir excepciones expresas, no un permiso genérico de superusuario. B aumenta la matriz y escenarios de prueba. |
| INC-003/D02 — Compras y ventas locales del gerente | A: supervisar/consultar y realizar los ajustes y aprobaciones ya acordados; operador registra compras y ventas. B: gerente también puede registrar compras, recepciones y ventas de su sucursal. | **A**, respeta la distribución descrita en §6.2 sin duplicar atribuciones. B es razonable como suplencia, pero requiere autorización específica. | Definir quién recibe compras; no quitar al operador sus funciones expresas. |
| INC-003/D03 — Preparación, despacho y recepción de transferencias | A: operador ejecuta las etapas de su sucursal; gerente aprueba y supervisa. B: gerente también ejecuta preparación, despacho y recepción. | **A**, responsabilidades simples. Coordinar aprobación y faltantes con INC-015/016. | Debe existir un actor autorizado para cada etapa, tanto en origen como en destino. |
| INC-003/D04 — Catálogo, consultas e indicadores locales | A: los tres roles consultan catálogo/inventario; operador ve también dashboard local, gerente dashboard y reportes locales. B: operador solo consultas operativas; dashboard local para gerente/administrador. | **A**, las consultas ayudan a operar y no autorizan escritura. Mantener comparación de rendimiento solo para administrador. | Precisar si costos de compra/valoración se muestran a operador; recomiendo ocultarlos si no son necesarios para sus tareas, sin impedir registrar precios de compra exigidos. |
| INC-003/D05 — Reportes logísticos y exportaciones | A: administrador consulta red y exporta por ámbito; gerente consulta/exporta su sucursal; operador solo registros operativos. B: también operador exporta datos de su sucursal. | **A**, menor exposición y coherente con gestión local/global. | El detalle de archivos y filtros se decide en INC-020; no equivale a aprobar sus formatos o categorías. |
| INC-003/D06 — Gestión de rutas y listas de precios | A: administrador mantiene rutas y listas; gerente/operador las usan según permisos. B: gerente mantiene también las correspondientes a su sucursal. | **A**, reduce cambios locales que afecten reglas compartidas; no concede automáticamente al gerente cambios distintos de stock. | Alinear alcance global/local de listas en INC-014 y de rutas en INC-017. |
| INC-003/D07 — Cambiar rol o reasignar sucursal de usuario | A: administrador cambia asignación, conserva historia bajo la sucursal original y renueva el acceso con los nuevos permisos. B: bloquear cambios si el usuario tiene historial y crear otra cuenta. | **A**, evita cuentas duplicadas y no reescribe operaciones pasadas. | Nunca migrar el historial a la nueva sucursal; precisar si tareas abiertas requieren reasignar responsable antes del cambio. |
| INC-003/D08 — Destino inactivo y usuario asociado | A: no asignar nuevas cuentas operativas a sucursales inactivas; usuarios ya asociados pueden consultar historia autorizada, sin operar, hasta su reasignación/desactivación por administrador. B: impedir iniciar sesión mientras su sucursal esté inactiva, sin cambiar el estado de la cuenta. | **A**, conserva la distinción aprobada entre desactivar cuenta y sucursal. | B limita más el acceso histórico; ninguna opción permite operar ni desactivar usuarios automáticamente. |
| INC-003/D09 — Política funcional de contraseña | A: frase de contraseña larga, permitiendo espacios, sin reglas arbitrarias de composición ni cambios periódicos obligatorios. B: contraseña con mayúscula/minúscula/número/símbolo y vencimiento periódico. | **A**; como propuesta concreta, mínimo 15 y máximo 128 caracteres, rechazando contraseñas comunes o comprometidas. Son valores propuestos, no un estándar impuesto por la prueba. | Aprobar límites y validaciones. Protección de almacenamiento y algoritmo se justifican en §8; no usar código de usuario como contraseña. |
| INC-003/D10 — Entrega inicial de contraseña | A: administrador genera/asigna una temporal, la entrega por un medio seguro fuera de la aplicación y se exige cambio en primer acceso. B: administrador asigna una inicial personal sin cambio obligatorio. | **A**, evita mantener una contraseña conocida por quien crea la cuenta. No requiere correo. | **Ampliación funcional a aprobar:** pantalla y condición de cambio inicial. No guardar contraseñas en README, logs ni exportaciones. El alta inicial del primer administrador debe automatizarse conforme a Compose, con mecanismo seguro a definir en §8. |
| INC-003/D11 — Cambio voluntario de contraseña | A: usuario cambia la propia acreditando la actual; no cambia la de otros. B: no incluir cambio voluntario y limitarse al flujo inicial. | **A**, permite mantener la credencial personal sin implementar recuperación. | **Interacción adicional a aprobar**. Recuperación por correo y autorregistro siguen excluidos; no añadirlos implícitamente. |
| INC-003/D12 — Sesiones, salida y cuentas desactivadas | A: cierre de sesión explícito; desactivar cuenta o cambiar rol/sucursal invalida el acceso previo. B: permitir acceso anterior hasta vencimiento de la sesión. | **A**, coherente con impedir acceso a cuentas desactivadas y evitar permisos obsoletos. | El método técnico se diseña en §8. El cierre de sesión es una interacción propuesta; B no puede dejar indefinidamente operativa una cuenta desactivada. |
| INC-003/D13 — Duración de sesión | A: vencimiento por inactividad y duración máxima. B: sesión hasta cierre manual, sin límite práctico. | **A**; propuesta inicial: 30 minutos de inactividad y máximo 8 horas, sin perder operaciones ya confirmadas. | Valores de seguridad/calidad por aprobar; tratamiento de formularios no enviados debe explicarse. No agregar estos ajustes al panel de configuración, cuyo alcance ya está limitado. |
| INC-003/D14 — Intentos fallidos de acceso | A: limitación temporal progresiva por cuenta/origen, sin bloqueo administrativo permanente automático. B: bloqueo fijo de cuenta tras varios fallos, desbloqueado por administrador. | **A**, mitiga adivinación sin introducir un flujo permanente de desbloqueo ni facilitar el bloqueo de cuentas ajenas. | Elegir umbrales y tiempo en seguridad/diseño; se propone como comportamiento de protección, no como mecanismo técnico ya seleccionado. |

**Para cerrar:** aprobar una matriz consolidada que recoja D01–D06 y los permisos previamente acordados; resolver D07–D14 o trasladar expresamente el detalle técnico a su artefacto correspondiente. Los resultados funcionales de acceso y revocación no deben quedar indefinidos.

## 3. INC-004 — Autonomía y actualidad de inventario

**Fuente:** §2–2.1 y §8.2. **Afecta:** RF-0005, RF-0015.  
El enunciado permite tiempo real o near-real-time; no exige operación sin conexión ni un servidor por sucursal.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-004/D01 — Significado de autonomía | A: operaciones independientes por sucursal sobre la plataforma común conectada. B: operación offline con sincronización posterior. | **A**, satisface autonomía operativa sin ampliar la arquitectura aprobada. | B añade almacenamiento local, conflictos y reconciliación; requiere ampliación explícita. |
| INC-004/D02 — Frescura observable | A: near-real-time con demora máxima acordada. B: notificación inmediata de cada cambio conectado. | **A**, propongo hasta 5 segundos tras confirmar una operación en condiciones normales de demostración. | Definir carga/entorno y cómo medirlo en requisitos no funcionales; no prometer esa cifra como exigencia externa. |
| INC-004/D03 — Desconexión o datos desactualizados | A: indicar falta de actualización, no confirmar escrituras sin backend y recuperar información al reconectar. B: permitir cola de escrituras offline. | **A**, evita confirmar movimientos que no existen en la fuente de datos. | No confundir mostrar último dato con asegurar que sigue vigente; B depende de elegir D01-B. |
| INC-004/D04 — Mecanismo | A: decidir en §8 entre consultas periódicas y notificaciones, según la demora aprobada. B: imponer ya un canal específico. | **A**, primero acordar resultado medible y después justificar mecanismo. | No reabre monolito, PostgreSQL ni REST como comunicación inicial del proyecto. |

## 4. INC-005 — Catálogo y datos de producto

**Fuente:** §3.1, con relación a §2 y §3.4. **Afecta:** RF-0008–RF-0012 y referencias de todos los módulos.  
Ya se aprobó CRUD general del administrador y ajustes de stock local del gerente, no duplicación del catálogo ni edición general por gerente.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-005/D01 — Identidad en la red | A: catálogo común con existencias por sucursal. B: catálogos locales con correspondencias entre productos. | **A**, evita duplicar identidad y simplifica transferencias e históricos. | B añade conciliación de productos equivalentes; no confundir independencia de stock con independencia de catálogo. |
| INC-005/D02 — Atributos mínimos | A: identificador estable, código/SKU único en la organización, nombre, descripción opcional y unidades de INC-007. B: añadir obligatoriamente marca, categoría, código de barras e imágenes. | **A**, suficiente para identificar productos y operar; campos extra solo si hay uso aprobado. | La fuente no enumera estos campos. Stock, costo promedio y listas de precios no se editan como atributos generales arbitrarios. |
| INC-005/D03 — Disponibilidad en catálogo local | A: producto vinculado/habilitado en sucursal, incluso con saldo cero; stock se muestra separado. B: mostrar solo productos con saldo positivo. | **A**, permite reabastecer y recibir productos agotados. | Se necesita una asociación producto–sucursal, no un CRUD local de producto. Confirmar cómo se habilita: administrador de forma explícita o al primer ingreso válido. Recomiendo habilitación administrativa explícita, con opción de todas las sucursales al alta para simplificar. |
| INC-005/D04 — Cambios e identidad histórica | A: nombre/descripcion editables, identificador y código estables tras uso. B: editar también códigos y reproducir nombres nuevos sobre todos los documentos pasados. | **A**, facilita rastrear el mismo producto. | Precisar si comprobantes conservan nombre/precio de la operación; recomiendo conservar datos comerciales históricos y mantener vínculo al producto. |

## 5. INC-006 — Eliminación de productos y correcciones históricas

> **Antecedente sustituido — H v1.3/01, 22 de octubre de 2026:** las alternativas/recomendaciones de eliminación física de D01 y D02 que aparecen abajo se conservan únicamente como historia de análisis; no son permisos vigentes. La decisión ratificada actual exige conservar todos los productos y aplicar exclusivamente baja lógica mediante `active=FALSE`, con las condiciones vigentes de stock cero y ausencia de pendientes. Consultar el registro de decisiones §12 y RF-0011 v1.3. No se modifican las reglas de compensación histórica de D04.

**Fuente:** §3.1 exige tanto eliminación como historial auditable. **Afecta:** RF-0010–RF-0011, RF-0018.  
No existe todavía una política aprobada de baja de productos. No debe suponerse que la desactivación de usuarios/sucursales se aplica automáticamente aquí.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-006/D01 — Producto sin uso | A: permitir eliminación física solo sin movimientos, documentos, existencias ni transferencias relacionadas. B: siempre baja lógica. | **A**, ofrece eliminación real del CRUD sin destruir historia. | Definir referencias que impiden borrar; si alguna existe, no eliminar en cascada. |
| INC-006/D02 — Producto con historia | A: rechazar borrado y conservar registro; permitir inactivación únicamente sin stock ni operaciones abiertas. B: eliminar físicamente y dejar copias en documentos históricos. | **A**, menor riesgo de perder referencias. La inactivación complementa, no sustituye silenciosamente, la eliminación real de D01. | La aceptación de esta interpretación de «eliminación» debe quedar aprobada; si se exige borrar productos usados, pedir aclaración antes de vulnerar auditoría. |
| INC-006/D03 — Efecto de inactividad | A: impedir nuevas compras, ventas y movimientos de producto inactivo, conservando consultas. B: ocultar de listas pero seguir admitiendo movimientos por referencia. | **A**, estado comprensible y verificable; resolver stock/pendientes antes de inactivar. | No decidir aquí reactivación automática; estado inactivo sería una precisión funcional nueva a aprobar. |
| INC-006/D04 — Corregir historia | A: preservar movimiento original y registrar corrección compensatoria vinculada. B: editar/eliminar movimiento confirmado. | **A**, mantiene quién hizo qué y evita reconstrucciones ambiguas. | Autoridad y valoración de correcciones en INC-009/013; no exigir un sistema ampliado de auditoría de toda acción. |

## 6. INC-007 — Unidades, conversiones y precisión

**Fuente:** §3.1 exige múltiples unidades por producto. **Afecta:** RF-0013, RF-0016–RF-0017, RF-0023–RF-0027, RF-0032–RF-0037.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-007/D01 — Modelo de cantidades | A: unidad base por producto y equivalencias específicas. B: stock independiente por unidad sin conversión. | **A**, permite vender/transferir en distintas presentaciones sin duplicar saldo. | Ejemplo propuesto: caja = 12 unidades. Factores por producto, no una caja universal. |
| INC-007/D02 — Fraccionamiento | A: permitir fracciones según unidad/producto. B: solo enteros en toda unidad. | **A**, soporta masa/volumen y evita fraccionar productos indivisibles. | Aprobar qué unidades permiten fracciones; no tratar 0,5 unidades de cualquier producto como siempre válido. |
| INC-007/D03 — Precisión de cantidad | A: decimales exactos, hasta 3 decimales en unidad base cuando sea fraccionable; rechazar conversiones no representables. B: redondear silenciosamente cada operación. | **A**, precisión propuesta y sin pérdida oculta de stock. | Si 3 decimales son insuficientes se ajustan antes del diseño; dinero/costo tienen política separada en INC-013. |
| INC-007/D04 — Precios y registro | A: precio ligado a la unidad de la línea, registrar unidad/factor utilizado y normalizar para stock/valoración. B: precio sin unidad o conversiones implícitas. | **A**, evita confundir precio por caja con precio por pieza. | Mostrar cantidad original y equivalente cuando aporte claridad; todas las categorías deben usar la misma conversión. |
| INC-007/D05 — Cambio de equivalencias | A: permitir cambios para operaciones futuras conservando factor histórico; unidad base no cambia tras uso. B: recalcular operaciones pasadas con el factor nuevo. | **A**, no altera historia ni cantidades confirmadas. | Definir quién mantiene unidades; propongo administrador por su CRUD general. |

## 7. INC-008 — Stock físico, disponible y reservas

**Fuente:** §3.1, §3.3 y §3.4. **Afecta:** RF-0012, RF-0014, RF-0017, RF-0028, RF-0033.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-008/D01 — Reservas | A: sin reservas; disponible local = físico local, revalidado al confirmar salida. B: reservar al preparar transferencias, disponible = físico − reservado. | **A**, menos estados y liberaciones; la preparación no garantiza stock hasta el despacho. | B ofrece mayor garantía, pero exige liberar reservas en cancelación/ajuste; coordinar con INC-015. |
| INC-008/D02 — Stock negativo | A: impedirlo en toda salida y ajuste de disminución. B: permitirlo en ajustes autorizados fuera de ventas. | **A**, simplifica coherencia y evita existencias físicas imposibles. | Es una regla propuesta más amplia que la validación explícita de venta; aprobarla, no atribuirla al PDF. |
| INC-008/D03 — Momento de validar | A: comprobar y aplicar la salida como resultado indivisible; ante conflicto una operación no se confirma. B: confiar en una consulta previa o en el frontend. | **A**, evita sobreventa entre operaciones simultáneas. | Resultado funcional a fijar ahora; estrategia transaccional/concurrencia en §8. |
| INC-008/D04 — Tránsito | A: separado del stock disponible de origen y destino una vez despachado; mostrarlo aparte. B: contarlo como disponible en destino antes de recepción. | **A**, no vender mercancía aún no recibida. | Coordinar efectos exactos con INC-015/016 y dashboard. |

## 8. INC-009 — Devoluciones, mermas, ajustes y correcciones

**Fuente:** §3.1. **Afecta:** RF-0016–RF-0018.  
Ya está aprobado que el gerente ajuste stock de su sucursal; no se reabre ese permiso ni se permite editar saldo sin movimiento.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-009/D01 — Registro mínimo | A: tipo de movimiento, producto/unidad, cantidad positiva, motivo obligatorio, sucursal, fecha y responsable; tipo determina incremento/retiro. B: cantidad firmada y motivo libre sin tipo. | **A**, distingue ajustes, devoluciones y mermas; fecha/responsable/motivo/cantidad ya son obligación externa. | No crear documentos financieros por estos movimientos. |
| INC-009/D02 — Devoluciones de ingreso | A: registrar devolución con referencia a venta original cuando exista y sea identificable; exigir motivo si no existe referencia. B: flujo completo de devoluciones comerciales con reembolsos. | **A**, cubre el ingreso exigido sin ampliar a pagos. | Definir límite acumulado si referencia una venta; recomiendo no superar lo vendido menos devoluciones previas. Casos sin venta requieren costo justificado según INC-013. |
| INC-009/D03 — Mermas y ajustes | A: operador registra movimientos de su rol y gerente ajustes locales, con motivo y sin aprobación adicional automática. B: toda merma/ajuste requiere un segundo aprobador. | **A**, menor carga y consistente con roles explícitos; no omitir trazabilidad. | Si se necesitan autorizaciones por importe/cantidad, son ampliación a aprobar. |
| INC-009/D04 — Corrección de un movimiento | A: movimiento compensatorio vinculado y motivo, sin editar original. B: editar movimiento confirmado y recalcular historia. | **A**, preserva auditoría; propongo gerente local autorizado para correcciones de inventario, sin alterar precios/documentos originales. | Coordinar con INC-006 e INC-013; una corrección de stock no anula una venta o compra por sí misma. |
| INC-009/D05 — Fecha de efectos | A: fecha/hora de registro actual para efectos de stock, sin retroactividad operativa. B: admitir fecha pasada y recalcular saldos/costos posteriores. | **A**, evita recálculos complejos; puede conservarse una fecha documental informativa si se aprueba. | B afecta valoración e informes y debe presupuestarse como alcance adicional. |

## 9. INC-010 — Mínimos y alertas

**Fuente:** §3.1, §3.4 y §3.6. **Afecta:** RF-0001, RF-0019–RF-0020, RF-0038, RF-0048.  
Administrador y parámetro compartido ya aprobados. La alerta de faltantes es distinta y no depende de stock mínimo.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-010/D01 — Granularidad | A: mínimo por producto y sucursal. B: mínimo global por producto. | **A**, sucursales pueden tener demandas distintas sin cambiar la identidad del producto. | El administrador mantiene el valor por ámbito; no concede ese permiso al gerente. |
| INC-010/D02 — Valores y ausencia de configuración | A: mínimo no negativo en unidad base; exigir configuración al habilitar producto local y permitir 0 explícito. B: mínimo ausente equivale silenciosamente a 0. | **A**, evita interpretar ausencia de decisión como configuración válida. | Respetar fraccionamiento de INC-007. Proponer 0 no implica desactivar todas las alertas. |
| INC-010/D03 — Condición única | A: alertar e identificar próximo a agotarse cuando disponible ≤ mínimo. B: cuando disponible < mínimo. | **A**, incluye el punto de reposición y, con mínimo 0, alerta al agotarse. | Aplicar la misma condición al indicador y alerta; cantidad disponible definida en INC-008. |
| INC-010/D04 — Canal y destinatarios | A: avisos persistentes en interfaz para administrador y gerente de la sucursal; operador ve indicadores operativos locales. B: añadir correo/SMS. | **A**, suficiente sin servicios externos ni nuevos canales. | B amplía alcance; integración ERP/POS sigue excluida y no es equivalente a un canal de avisos. |
| INC-010/D05 — Disparo y repetición | A: reevaluar después de movimientos confirmados y cambios de mínimo; una alerta activa por producto/sucursal y resolverla al superar el umbral. B: crear una nueva alerta cada consulta o movimiento. | **A**, evita duplicados y usa el valor vigente. | Definir conservación de avisos resueltos; recomiendo conservarlos como historial de alertas, sin confundirlo con auditoría integral. |
| INC-010/D06 — Faltantes | A: alerta vinculada a cada recepción parcial, visible para gerentes de origen/destino y administrador. B: aviso genérico sin vínculo. | **A**, permite identificar y tratar la diferencia; no reutiliza el umbral de reabastecimiento. | Su resolución sigue el tratamiento de INC-016, no la reposición de stock local. |

## 10. INC-011 — Ciclo de compras y recepción

**Fuente:** §3.2. **Afecta:** RF-0021–RF-0025 y RF-0003.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-011/D01 — Estados y operaciones | A: borrador → pendiente de recepción → recibida; cancelación antes de recibir. B: añadir aprobación gerencial, pago y conciliación. | **A**, cubre crear/gestionar/recibir sin convertir compras en tesorería. | No se omiten condiciones comerciales ni historia; estado de pago no exigido. |
| INC-011/D02 — Recepción parcial de compras | A: recepción completa en una operación; cantidades deben coincidir con orden vigente. B: varias recepciones parciales con saldo pendiente. | **A**, §3.2 no exige parcialidad, a diferencia de transferencias. | Si el negocio necesita B, aprobarlo expresamente; actualizar estados y pruebas. No extender A a transferencias, donde parcialidad es obligatoria. |
| INC-011/D03 — Cambios y cancelación | A: editar solo borrador; para corregir una orden pendiente aún no recibida, cancelarla y crear otra, conservando ambas. B: permitir volver a borrador antes de recibir para modificar condiciones. C: editar incluso órdenes recibidas. | **A**, ciclo más pequeño y condiciones recibidas preservadas, sin crear stock al cancelar. | La orden recibida no se reescribe; errores se corrigen mediante el procedimiento aprobado en INC-009/013. B añade una transición que debe validarse; C requiere reversión y recálculo. |
| INC-011/D04 — Confirmación repetida | A: una recepción no incrementa stock ni costo dos veces; repetición devuelve resultado ya registrado o rechazo identificable. B: tratar cada clic como un nuevo ingreso. | **A**, evita duplicación sin exigir todavía un mecanismo técnico. | Elegir respuesta de repetición en contrato de API; testear cantidad, historial y costo. |
| INC-011/D05 — Compra pendiente y cierre de sucursal | A: toda orden pendiente de recepción impide desactivar; se recibe o cancela válidamente antes. B: mover automáticamente pendientes a otra sucursal. | **A**, aplica la política ya aprobada sin reasignaciones silenciosas. | Precisar si borradores también bloquean; recomiendo exigir cancelación de borradores antes de desactivar para no dejar órdenes operables. |
| INC-011/D06 — Histórico | A: consultar todas las órdenes, incluido borrador/cancelada, con estado; métricas de compras recibidas distinguen recepción real. B: guardar solo las recibidas. | **A**, permite explicar el ciclo y no confundir orden con ingreso. | Definir filtros por proveedor/producto y separar cantidades pedidas de recibidas. |

## 11. INC-012 — Proveedores y condiciones comerciales

**Fuente:** §3.2; alternativa no seleccionada de gestión ampliada en §4. **Afecta:** RF-0021, RF-0023.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-012/D01 — Identificar proveedor | A: registro mínimo reutilizable con identificador, código único y nombre. B: texto libre en cada compra. | **A**, permite históricos fiables sin confundir homónimos. | Propongo administrador crea/actualiza referencia mínima; no incluye evaluación de desempeño, contratos o un módulo ampliado como adicional. Es soporte derivado de compras a aprobar. |
| INC-012/D02 — Datos extra | A: no exigir documentos fiscales, contacto ni cuenta bancaria. B: exigirlos para toda compra. | **A**, no son necesarios en el alcance descrito. | No eliminar identificación de proveedor ni su histórico. |
| INC-012/D03 — Descuentos de compra | A: porcentaje por línea, explícito incluso si es 0. B: importe fijo y porcentaje, por línea y por orden, acumulables. | **A**, cálculo único y verificable; proponer rango 0–100 %, sin precio neto negativo. | Límites propuestos, no literales del PDF; INC-013 define costo neto. |
| INC-012/D04 — Plazo de pago | A: días no negativos desde recepción confirmada; conservar plazo y fecha de vencimiento calculada. B: fecha fija elegida al ordenar. | **A**, regla clara sin procesar pagos. | Aprobar el evento base: recepción u orden; el PDF no lo fija. No implica cuentas por pagar ni órdenes de pago. |
| INC-012/D05 — Moneda, impuestos y fletes | A: una moneda de proyecto, sin cálculo fiscal ni distribución de fletes. B: multimoneda, impuestos y costos adicionales distribuidos. | **A**, pero elegir explícitamente la moneda con el responsable; no asumir COP por la ubicación de la empresa. | Definir precisión monetaria en INC-013. Registrar descuentos/precios sigue siendo obligatorio. |

## 12. INC-013 — Valoración por promedio ponderado

**Fuente:** §3.2. **Afecta:** RF-0026 y todos los movimientos con valoración.  
Se requiere una política coherente y ejemplos aprobados; la fuente no proporciona fórmula ni componentes.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-013/D01 — Método y ámbito | A: promedio ponderado móvil por producto y sucursal. B: promedio periódico global. | **A**, refleja recepciones confirmadas y existencias locales sin procesos de cierre periódico. | Consistente con autonomía local; no se adopta FIFO como sustituto del promedio exigido. |
| INC-013/D02 — Costo de entrada | A: precio unitario neto del descuento, normalizado a unidad base; sin impuestos/fletes si se aprueba INC-012/D05-A. B: costo total con cargos distribuidos. | **A**, trazable desde la compra. | Si se elige B, definir cada cargo y reparto antes de usar la fórmula. |
| INC-013/D03 — Precisión y saldo cero | A: cálculo decimal exacto, costo unitario con 6 decimales y totales monetarios a 2; cuando saldo previo es cero, costo nuevo = costo de entrada. B: redondear costo a 2 decimales en cada paso. | **A**, reduce acumulación de error; redondeo propuesto a mitad hacia arriba, por aprobar junto con moneda. | Mantener precisión interna y presentación diferenciadas; no dividir entre cero ni inventar costo si falta dato. |
| INC-013/D04 — Salidas | A: ventas/mermas/ajustes de salida usan costo promedio vigente y no recalculan el promedio de las unidades restantes. B: recalcular a partir del precio de venta o de otro costo. | **A**, no confunde precio comercial con valoración. | Precisar valor remanente al agotar stock y consistencia de redondeos. |
| INC-013/D05 — Transferencias | A: congelar costo unitario de origen al despachar; destino recibe a ese costo y recalcula promedio por lo efectivamente recibido. B: recibir al promedio previo del destino. | **A**, conserva el valor transferido sin fabricar ganancias por traslado. | Faltantes y costos en tránsito en INC-016; posteriores compras en origen no cambian el costo del envío. |
| INC-013/D06 — Devoluciones y ajustes positivos | A: devolución vinculada reingresa al costo de salida original; ajuste positivo usa promedio vigente o exige costo justificado si no existe. B: todo ingreso exige precio manual libre. | **A**, menor discrecionalidad. | Puede requerir conservar costo histórico de venta; cuando no hay referencia/costo, no confirmar hasta registrarlo con motivo. |
| INC-013/D07 — Errores y retroactividad | A: correcciones compensatorias en fecha actual, con su efecto valorado y sin reescribir el pasado. B: recalcular toda la cadena histórica. | **A**, proporcional al plazo y compatible con auditoría. | Definir reversión del valor original y efecto en el saldo actual; si faltan unidades para compensar, rechazar y pedir resolución, no permitir negativos ocultos. |

**Ejemplo propuesto para aprobar con D01–D03:** 10 unidades a costo 100 más recepción de 5 a costo neto 130 dan 15 unidades y costo promedio 110: `(10 × 100 + 5 × 130) / 15`. Una venta de 3 deja 12 unidades a promedio 110. Este ejemplo no define por sí solo todos los casos: aprobar también saldo cero, descuento, conversión, devolución y transferencia.

## 13. INC-014 — Ventas, listas, descuentos y comprobantes

**Fuente:** §3.3 y relación con §3.1. **Afecta:** RF-0027–RF-0031.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-014/D01 — Listas de precios | A: varias listas comunes mantenidas por administrador, con precio por producto/unidad; operador selecciona una para la venta. B: listas independientes por sucursal y cliente, con prioridades automáticas. | **A**, cumple pluralidad sin crear gestión de clientes ni reglas de prioridad. | Confirmar precio por unidad de INC-007 y si se permite cambiarlo manualmente; recomiendo no sobrescribirlo libremente, usando descuento explícito. |
| INC-014/D02 — Descuento | A: porcentaje por línea, sin acumulación con otros descuentos no definidos. B: porcentajes/importes a varios niveles. | **A**, proponer 0–100 % y precio neto no negativo, alineado con INC-012 sin obligar a compartir política. | Definir quién puede aplicarlo; propongo operador durante venta local, sin módulo de promociones. |
| INC-014/D03 — Estados y confirmación | A: registrar venta confirmada como operación indivisible con salida; formulario no confirmado no descuenta. B: cotización, reserva, autorización, pago y facturación separados. | **A**, alcance suficiente para §3.3. | Una venta fallida no crea comprobante confirmado ni retiro; comportamiento ante repetición debe impedir doble salida. |
| INC-014/D04 — Concurrencia | A: al competir por últimas unidades, solo se confirma lo cubierto por stock; las otras ventas se rechazan sin cambios. B: admitir sobreventa y corregir después. | **A**, sostiene disponibilidad obligatoria. | Mecanismo en §8; no delegar decisión al frontend. |
| INC-014/D05 — Anular o devolver venta | A: no añadir anulación financiera; registrar devolución de inventario con referencia conforme INC-009 y conservar venta histórica. B: incluir anulación completa y reembolsos. | **A**, no se exige reembolso. | Definir si dashboard mostrará ventas brutas y devoluciones separadas o netas; coordinar INC-018. No llamar anulación a un ajuste de stock. |
| INC-014/D06 — Comprobante | A: registro interno identificable, consultable y con datos de venta; no documento fiscal. B: factura fiscal o documento imprimible con requisitos legales. | **A**, el PDF admite «comprobantes o registros». | Identificador/fecha, sucursal, responsable, líneas con cantidades/unidades, precios, descuentos y totales; conservar valores de la operación aunque cambie la lista. |

## 14. INC-015 — Solicitud, aprobación, preparación y despacho

**Fuente:** §3.4 y aprobación del gerente en §6.2. **Afecta:** RF-0032–RF-0036, RF-0042, RF-0047, RF-0054 y RF-0003.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-015/D01 — Quién aprueba y cuándo | A: gerente de origen aprueba después de revisar/preparar cantidad y antes del despacho. B: gerente destino y origen aprueban cada uno. | **A**, origen controla disponibilidad y responde al flujo descrito sin doble aprobación. | Destino sigue solicitando; administrador también puede solicitar según §3.4. No otorgar aprobación al administrador automáticamente. |
| INC-015/D02 — Una o varias líneas | A: una transferencia admite varias líneas de producto con mismo origen/destino. B: una transferencia por producto. | **A**, evita documentos repetidos y no altera los pasos; B reduce validaciones y es admisible si se prioriza simplicidad absoluta. | Cada línea conserva solicitada/preparada/enviada/recibida; impedir duplicados ambiguos del mismo producto/unidad. |
| INC-015/D03 — Cantidad ajustada en origen | A: enviar hasta lo solicitado, admitiendo menos y dejando constancia; si todas las líneas quedan en cero, no despachar. B: permitir enviar más de lo pedido sin nueva aceptación. | **A**, evita recepción sorpresa y conserva cantidad original. | Elegir si destino debe aceptar reducciones; recomiendo informar sin segundo ciclo de aprobación, porque §3.4 permite ajuste de origen. |
| INC-015/D04 — Estados | A: solicitada → en preparación → aprobada → en tránsito → recibido o con faltantes; rechazada/cancelada antes del envío. B: añadir reservas, etapas de transportista y entregas programadas múltiples. | **A**, detalle propuesto que conserva los cuatro estados visibles exigidos. | Solicitud/preparación/aprobación/tránsito y faltantes no resueltos cuentan como abiertos para RF-0003. Faltantes se cierran conforme INC-016, no por cambiar solo una etiqueta. |
| INC-015/D05 — Reservar o descontar | A: sin reserva, descontar origen al despacho y revalidar entonces. B: reservar al preparar y descontar al despachar. | **A**, consistente con INC-008/D01-A; no se descuenta al solicitar. | Si falta stock al despachar, rechazar sin salida y volver a ajustar/preparar según reglas; no despachar parcialmente de forma implícita. |
| INC-015/D06 — Cancelación y rechazo | A: destino cancela solicitud no despachada; origen rechaza por imposibilidad, con motivo; no cancelar envíos en tránsito. B: cancelar en cualquier estado y devolver stock automáticamente. | **A**, evita borrar mercancía ya enviada. | Precisar actor de cancelación después de aprobación; propongo gerente de origen para no competir con un despacho. B exige reversión física/contable adicional. |
| INC-015/D07 — Datos de despacho y transporte | A: transportista como dato identificable de texto y llegada estimada obligatorios; registrar fecha/hora real de despacho. B: CRUD completo de transportistas e integración de tracking. | **A**, cubre fuente sin módulo externo. | Rutas y duración en INC-017; no confundir transportista con actor externo integrado. |
| INC-015/D08 — Consistencia y reintentos | A: despacho se confirma completo para sus líneas o no se confirma; repetir no descuenta de nuevo. B: confirmar línea por línea sin estado de fallo conjunto. | **A**, simplifica stock y tránsito coherentes. | Resultado funcional; elección de transacción/controles de concurrencia en §8. |
| INC-015/D09 — Validez de origen y destino | A: sucursales distintas y activas, con productos identificados en catálogo y unidades compatibles. B: permitir origen igual a destino como ajuste interno. | **A**, no confunde transferencia entre sucursales con un ajuste de inventario. La exigencia de actividad ya está aprobada en RF-0003. | Prohibir traslado a sí misma es precisión propuesta; validar también antes del despacho y rechazar sin movimientos si falla. B añade otro significado al flujo, innecesario para el alcance descrito. |

## 15. INC-016 — Tránsito, recepción parcial y tratamiento de faltantes

**Fuente:** §3.4 pasos 4–5. **Afecta:** RF-0035–RF-0039, RF-0042, RF-0047, RF-0054 y RF-0003.  
**No es opcional:** recibir parcialmente, registrar diferencia, alertar y definir tratamiento. No reducirlo a recepción completa por ahorrar alcance.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-016/D01 — Tránsito y propiedad de cantidades | A: al despachar, origen disminuye físico y aumenta tránsito; destino aumenta solo al recibir. B: sumar destino al despachar. | **A**, no duplica disponible; tránsito mantiene visibilidad de lo enviado. | La suma explicable es origen + tránsito + destino + pérdidas formalizadas; evitar que un faltante desaparezca sin tratamiento. |
| INC-016/D02 — Efecto de recepción parcial | A: ingresar inmediatamente lo recibido, dejar diferencia identificada en tránsito/pendiente y emitir alerta. B: no ingresar nada hasta recibir todo. | **A**, representa realidad operativa y permite usar las unidades recibidas. | Es precisión del proyecto sobre un vacío, no cita literal de actualización parcial. Debe quedar historia de cada recepción. |
| INC-016/D03 — Recepciones sucesivas | A: permitir varias recepciones acumuladas hasta lo enviado. B: una sola recepción que fija faltante definitivo. | **A**, permite completar un envío demorado sin crear mercancía; no exige varios despachos por transferencia. | Bloquear acumulado superior a enviado; recepción repetida no vuelve a ingresar unidades. Definir recepción con cero: recomiendo no crear movimiento de cantidad cero, pero sí registrar incidencia de recepción sin unidades si se aprueba ese caso. |
| INC-016/D04 — Qué significa faltante | A: diferencia recibida vs. enviada queda pendiente hasta decisión formal, no pérdida automática. B: toda diferencia es merma inmediata. | **A**, permite llegada posterior, reclamación o ajuste real. | Alerta ligada al faltante, y RF-0003 impide desactivar sucursales mientras siga abierto. |
| INC-016/D05 — Reenvío | A: crear transferencia de reposición vinculada; resolver primero la diferencia del envío original mediante ajuste/reclasificación justificado si ya no llegará, sin restaurar origen. B: aumentar cantidades del envío original y volver a despachar sin vínculo nuevo. | **A**, distingue unidades originales de nuevas y conserva costo/stock. | Antes de reponer se debe explicar qué ocurrió con el faltante original; si sigue en camino no duplicar reposición. Definir actores y vínculo, no automatizar un reenvío por solo elegir etiqueta. |
| INC-016/D06 — Ajuste o reclamación | A: registrar tratamiento y evidencia/motivo; ajuste reconocido resuelve cantidad pendiente con movimiento justificado; reclamación permanece abierta hasta cierre documentado sin integración externa. B: gestionar expediente legal, seguros y pagos. | **A**, cubrir tratamiento real sin un módulo jurídico/financiero. | Reclamación no aumenta stock. Cerrar reclamación requiere destino de cantidad/valor: recepción posterior, ajuste/pérdida o reposición vinculada. No cerrar solo marcando «resuelto». |
| INC-016/D07 — Autoridad y estados finales | A: operador registra recepción; gerentes de las sucursales implicadas gestionan tratamiento, administrador supervisa; cerrar con recibido completo o cerrado con diferencia tratada. B: operador resuelve faltantes libremente. | **A**, separa registro de recepción y resolución de discrepancia. | Precisar cuál gerente autoriza ajuste de pérdida; recomiendo origen para valor despachado y destino confirma recibido, sin permitir editar sucursal ajena. El estado con faltantes debe seguir visible en la historia aun tras cerrar. |
| INC-016/D08 — Costo y reversión | A: usar costo congelado al despacho para tránsito, recepción y pérdida reconocida; ninguna recepción reescribe costo de origen. B: usar costo que tenga origen en cada recepción posterior. | **A**, coherente con INC-013. | Comprobar conservación de valor y diferencias justificadas; no registrar la misma pérdida en origen y destino. |
| INC-016/D09 — Errores y sobrantes | A: rechazar recepción acumulada superior a lo enviado y corregir datos antes de confirmar; una recepción confirmada se corrige con movimiento vinculado y permisos. B: aceptar sobrante como inventario sin referencia. | **A**, evita inventar mercancía. | B requiere caso de negocio adicional. Las correcciones no borran la recepción ni permiten saldos negativos; coordinar INC-009. |

## 16. INC-017 — Rutas, tiempos y cumplimiento logístico

**Fuente:** §3.5, con datos de §3.4. **Afecta:** RF-0040–RF-0043, RF-0055.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-017/D01 — Ruta | A: par dirigido origen–destino identificable, con datos de clasificación. B: itinerario con escalas, mapa y optimización. | **A**, suficiente para reporte por ruta; ida y vuelta pueden tener duraciones distintas. | No exige GPS ni integración con transportista. |
| INC-017/D02 — Clasificación | A: prioridad operativa registrada y posibilidad de consultar/ordenar por ella. B: clasificación por costo. C: clasificación por tiempo estimado. | **A**, satisface una alternativa de «prioridad, costo o tiempo» sin añadir costos de transporte. C también es razonable reutilizando duración estimada; no es obligatorio implementar las tres. | Elegir criterio y escala; propongo alta/media/baja para prioridad, sin optimización automática. |
| INC-017/D03 — Medición de duración | A: inicio = despacho real; fin = recepción completa o cierre con diferencia tratada, mostrando además primera recepción. B: medir desde solicitud a primera recepción. | **A**, distingue transporte de espera de preparación. | No mezclar «entrega física completa» con «cierre administrativo por faltante»: mostrar resultado/condición de cierre y no contar una pérdida tratada como entrega completa. |
| INC-017/D04 — Estimado y cumplimiento | A: llegada estimada explícita al despacho; puntual si recepción completa ocurre hasta esa fecha/hora; parcial no cuenta como entrega completa puntual. B: medir solo primera recepción, aunque falte mercancía. | **A**, evita premiar entrega incompleta. | Propuesta: porcentaje de entregas completas a tiempo entre transferencias cerradas del período; cerradas con diferencia cuentan como incumplidas; abiertas vencidas se muestran aparte. Aprobar denominador y caso sin datos. |
| INC-017/D05 — Tiempo estimado de ruta | A: duración típica opcional que sugiere llegada, confirmada al despacho. B: solo llegada manual por transferencia. | **B**, evita otro parámetro mantenible; el PDF exige estimación, no estimador automático. A es mejora opcional si aporta valor. | No introducir una configuración logística genérica como parte de INC-001. |
| INC-017/D06 — Calendario y zona horaria | A: una zona de negocio explícita para sucursales y reportes; conservar instantes inequívocos y mostrar zona. B: zona distinta por sucursal. | **A**, menor complejidad. Elegir la zona con el responsable, sin inferirla solo por el país de la empresa. | Alinea meses y filtros de fechas de INC-018/020; detalle de almacenamiento en §8. |
| INC-017/D07 — Reporte por sucursal | A: vista de envíos propios y recepciones propias diferenciadas, además de vista por ruta. B: mezclar ambos sentidos sin distinguir. | **A**, permite atribuir desempeño y faltantes. | Cumplir ambas perspectivas obligatorias, sucursal y ruta; no exigir exportación en este reporte básico. |

## 17. INC-018 — Definiciones del dashboard

**Fuente:** §3.6. **Afecta:** RF-0044–RF-0049; RF-0048 usa la condición de INC-010.  
Ya se resolvió quién ve comparativas y qué umbral identifica proximidad a agotarse. No se propone otro umbral ni predicción de demanda.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-018/D01 — Volumen de ventas | A: importe de ventas confirmadas neto de descuentos, más cantidades vendidas por producto/unidad base. B: solo contar documentos de venta. | **A**, explica valor y volumen; no sumar kilogramos y piezas como un total homogéneo. | Decidir si devoluciones se muestran separadas o restan al neto; recomiendo ventas y devoluciones separadas inicialmente, con etiquetas claras. |
| INC-018/D02 — Período comparado | A: mes actual y tres anteriores como precisión propuesta, calendario de INC-017. B: período elegido libremente con un número configurable por usuario. | **A**, coherente con ejemplo de §6.3 pero no impuesto por él; evita nueva configuración administrativa. | Mostrar que el mes actual está incompleto; proponer comparar acumulado al mismo día y conservar totales mensuales como contexto. Si se eligen totales completos, advertir diferencia de duración. |
| INC-018/D03 — Rotación | A: por producto/sucursal, unidades vendidas del período ÷ inventario físico promedio del mismo período. B: costo de ventas ÷ inventario promedio valorado. | **A**, operativo y compatible con demanda; B es más financiero. | Aprobar promedio: recomiendo promedio temporal diario reconstruido de movimientos, no solo saldo actual. Si promedio es cero, mostrar «no calculable», no infinito ni 0 falso. Mantener ambas magnitudes en misma unidad. |
| INC-018/D04 — Alta y baja demanda | A: ranking por unidades vendidas por producto durante el período, mostrando productos sin ventas aparte; alta = primeros N, baja = últimos N con ventas. B: umbrales absolutos o percentiles. | **A**, interpretable sin parámetros administrativos nuevos; propuesta N = 5 y sin duplicar productos entre grupos si hay pocos. | Definir desempate estable por código; comparar unidades dentro de cada producto, no interpretar masa y piezas como magnitudes físicamente equivalentes. Aprobar si el ranking mixto es suficiente o separar familias/unidades, lo que ampliaría clasificación. |
| INC-018/D05 — Transferencias activas e impacto | A: cantidades solicitadas/preparadas como previsión sin afectar disponible; en tránsito separado; recibido ya afecta físico; faltantes pendientes separados. B: sumar toda solicitud al stock esperado como si estuviera confirmado. | **A**, distingue promesa de movimiento real. | Coincidir con INC-008/015/016 y definir estados activos; no descontar dos veces un envío ni sumar tránsito a disponible. |
| INC-018/D06 — Rendimiento entre sucursales | A: ventas monetarias netas de descuentos, número de ventas y cumplimiento logístico con el mismo período. B: rentabilidad, cuotas y metas comerciales. | **A**, usa información solicitada sin inventar metas o gastos. | Solo administrador; señalar que ventas no equivalen a utilidad. Moneda común y métricas logísticas deben estar acordadas. |
| INC-018/D07 — Ausencia de datos e historia inicial | A: mostrar cero solo cuando significa cero y «sin datos/no calculable» donde no puede obtenerse indicador; datos de demostración separados e identificados. B: inventar meses o inferir rotación de un saldo actual. | **A**, no fabricar evidencia. | Preparar datos de ejemplo autorizados después, sin mezclar historial sintético con real; no generarlos en esta actividad. |

## 18. INC-019 — Alcance de los ejemplos de historias de usuario

**Fuente:** §6.3, historias recomendadas y opcionales. **Afecta:** RF-0023, RF-0026, RF-0032 y RF-0044 relacionados.  
No interpretar la opcionalidad del formato como permiso para omitir funciones obligatorias de §3. Tampoco elevar todos los detalles de ejemplos a obligaciones nuevas.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-019/D01 — Órdenes de pago | A: no implementar generación de órdenes de pago; conservar condiciones/plazo de compra exigidos en §3.2. B: añadir cuentas por pagar/órdenes de pago. | **A**, ese flujo aparece en un ejemplo y ampliaría el sistema financiero. | Registrar interpretación de alcance; si OptiPlant confirma obligatoriedad adicional, reevaluar sin ocultarlo. |
| INC-019/D02 — Tres meses anteriores | A: adoptar tres meses como precisión del proyecto alineada con INC-018/D02-A. B: mantener otro período elegido explícitamente. | **A**, una concreción pequeña y útil, sin atribuir el número al mínimo de §3.6. | Esta fila y INC-018/D02 son la misma decisión; responder una sola vez y referenciarla para evitar contradicción. |
| INC-019/D03 — Urgencia en transferencia | A: no añadir prioridad a la solicitud; mantener clasificación de rutas de §3.5 como concepto separado. B: campo de urgencia en la solicitud que origen puede consultar, sin algoritmo automático. | **A**, mantiene alcance mínimo; B es una mejora pequeña si se desea, pero no confundir prioridad de ruta con urgencia del pedido. | Si B se aprueba, definir valores y permisos; no introducir SLA ni despacho automático por urgencia. |

## 19. INC-020 — Detalle del adicional de reportes exportables

**Fuente:** §4 y decisiones del proyecto. **Afecta:** RF-0050–RF-0055 y permisos de INC-003.  
El módulo está seleccionado; no vuelve a ofrecerse otra funcionalidad adicional. Las elecciones siguientes concretan el alcance aún propuesto.

| Consideración | Alternativas | Recomendación y razón | Impacto / condición de cierre |
|---|---|---|---|
| INC-020/D01 — Formato | A: solo Excel `.xlsx`. B: PDF y Excel. | **A**, una salida reutilizable cumple el valor incremental sin mantener dos formatos. | Archivo real `.xlsx`, no CSV renombrado. No investigar/seleccionar biblioteca todavía. |
| INC-020/D02 — Categorías | A: movimientos, ventas y transferencias. B: una sola categoría inicial aprobada expresamente. | **A**, reutiliza datos centrales y coincide con la propuesta; el texto externo usa alternativas, no obliga a las tres por sí solo. | Aprobar alcance de RF-0052–RF-0054; no añadir exportación de todo dashboard o de usuarios. |
| INC-020/D03 — Filtros | A: rango de fechas obligatorio y sucursal según permisos; estado opcional donde exista. B: todos los filtros posibles de cada módulo. | **A**, suficiente y verificable. | No permitir seleccionar otra sucursal para eludir permisos; administrador puede elegir una o toda la red, gerente solo propia si se aprueba INC-003/D05-A. |
| INC-020/D04 — Fecha de cada categoría | A: movimientos por fecha de registro, ventas por confirmación y transferencias por solicitud, mostrando además despacho/recepción si existen. B: selector de tipo de fecha para cada exportación. | **A**, significado explícito sin muchas combinaciones. | No sustituir fechas faltantes por otras silenciosamente; indicar fecha de filtrado en encabezado del archivo. |
| INC-020/D05 — Límites del rango | A: ambos días incluidos en zona de negocio; interpretar hasta el fin del día final sin duplicar el siguiente. B: instantes exactos introducidos por usuario. | **A**, más sencillo para reportes operativos. | Rechazar inicio posterior a fin; duración máxima y tamaño se definen como límites de calidad, no descartar filas sin avisar. |
| INC-020/D06 — Columnas de movimientos | A: identificador, fecha, sucursal, producto/código, tipo/motivo, cantidad y unidad, responsable, referencia de operación. B: solo totales agregados. | **A**, permite auditar y aporta valor frente a indicadores. | Si se exporta costo, aplicar permiso específico; nunca exportar contraseñas ni secretos. |
| INC-020/D07 — Columnas de ventas | A: una fila por línea con identificador de venta, fecha, sucursal, responsable, producto/unidad, cantidad, precio, descuento e importe de línea. B: solo un total por venta. | **A**, detalle reutilizable; no repetir el total de venta en cada línea como si fuera sumable. | Definir moneda y redondeo; no exportar información fiscal o de clientes no contemplada. |
| INC-020/D08 — Columnas de transferencias | A: una fila por línea con identificador, origen/destino, fechas, estado, producto/unidad y cantidades solicitada/enviada/recibida/faltante y tratamiento. B: solo cabeceras con totales. | **A**, permite revisar discrepancias. | Indicar si son cantidades acumuladas al exportar; no duplicar líneas por cada recepción sin explicar la granularidad. |
| INC-020/D09 — Coherencia y archivo vacío | A: exportar una vista coherente de los datos consultados; sin coincidencias generar archivo válido con encabezados y nota de ausencia. B: exportar páginas sucesivas que pueden mezclar estados o devolver archivo ambiguo sin datos. | **A**, resultado verificable incluso con actividad concurrente. | El mecanismo de consistencia y límites de memoria se diseña en §8; error de exportación no cambia datos operativos. |
| INC-020/D10 — Generación y entrega | A: descarga directa para volumen acotado de prueba, con error claro si excede límite aprobado. B: trabajos asíncronos, notificación y repositorio de archivos. | **A**, evita infraestructura no solicitada. | Establecer límite de filas/tiempo en requisitos no funcionales; nunca truncar con éxito silenciosamente. B solo si la carga real lo requiere. |

## 20. Dependencias para responder sin contradicciones

| Decisiones relacionadas | Compatibilidad que debe conservarse |
|---|---|
| INC-003 con todos los módulos | Ninguna propuesta asigna operaciones a roles que no existan; después se consolida una única matriz. No quitar al operador compras/ventas/ingresos/retiros/solicitudes explícitos. |
| INC-004 con INC-008 y §8 | Frescura visual no sustituye validación al confirmar. No adoptar offline por elegir un mecanismo de notificación. |
| INC-005, INC-006 e INC-007 | Identidad común, producto inactivo y factores históricos deben conservar referencias, cantidades y documentos previos. |
| INC-007, INC-008, INC-013 | Cantidades convertidas, disponibilidad y costo deben usar unidad coherente; precisión y redondeos se aprueban conjuntamente. |
| INC-008/D01 e INC-015/D05 | Elegir ambos sin reserva o ambos con reserva; no preparar descontando stock en un módulo y reservarlo de nuevo en otro. |
| INC-009, INC-013 e INC-014 | Devolución/corrección de stock no anula automáticamente venta ni pago; conservar costo original cuando corresponda. |
| INC-010 e INC-018 | Mismo mínimo y misma condición para reabastecimiento y proximidad a agotarse; no responder con dos umbrales. |
| INC-011, INC-015, INC-016 y RF-0003 | Estados pendientes impiden desactivar; resolver cantidades y faltantes antes de dar una operación por cerrada. |
| INC-013/D05 e INC-016/D08 | Mismo costo congelado de despacho para tránsito y recepción; ninguna unidad o pérdida se contabiliza dos veces. |
| INC-017, INC-018 e INC-020 | Una definición de zona, límites de período y fecha de cada evento; los reportes deben declarar qué fecha filtran. |
| INC-018/D02 e INC-019/D02 | Una sola elección sobre meses anteriores; no son dos permisos para elegir valores incompatibles. |
| INC-003/D05 e INC-020 | Exportar no amplía la visibilidad del usuario. Gerente local no obtiene comparativa global por descargar un archivo. |

## 21. Qué debe quedar fuera de una aprobación funcional automática

- No elegir todavía bibliotecas de autenticación, exportación o comunicación en tiempo real; tampoco tokens frente a sesiones, bloqueos concretos, tablas, tipos de identificador o paquetes Java. Se justifican en §8 dentro del stack aprobado.
- Los valores de demora, sesión, capacidad y precisión propuestos deben aprobarse o ajustarse; después se documentan en requisitos de calidad/diseño cuando corresponda.
- Recuperación de cuenta por correo, multifactor, integración externa, pagos, facturación fiscal, offline, rutas optimizadas, lotes/caducidad y predicción no forman parte del mínimo aprobado. Una alternativa que los incorpore necesita autorización de ampliación, no solo una decisión técnica.
- No se elimina un requisito explícito por preferir una opción simple: múltiples unidades, distintas listas de precios, clasificación de rutas, reportes logísticos, recepción parcial de transferencias y tratamiento de faltantes permanecen exigidos.
- La fecha de presentación no justifica omitir obligaciones sin una decisión expresa y sin comunicar el incumplimiento resultante.

## 22. Plantilla opcional para tu respuesta conjunta

Puedes responder por códigos, sin copiar las tablas completas. Las consideraciones no respondidas conservarán su estado pendiente.

```text
Decisiones aceptadas:
- INC-NNN/DNN: alternativa A/B/C, con estos detalles: ...

Recomendaciones aceptadas en bloque:
- INC-NNN: acepto las recomendaciones de D01 a DNN, excepto ...

Cambios o alternativas propias:
- INC-NNN/DNN: ...

Valores concretos:
- Moneda y zona horaria: ...
- Umbrales/plazos/precisiones: ...

Pendientes o preguntas:
- INC-NNN/DNN: ...
```

**Importante:** responder «acepto recomendaciones» debe identificar el bloque o los códigos a los que se refiere. Las recomendaciones condicionadas siguen necesitando sus valores o variantes; no se interpretará una respuesta incompleta como aprobación de lo no especificado.

## 23. Alcance y comprobación de esta guía

- Incluye un bloque para cada incertidumbre pendiente del catálogo v0.9: INC-003–INC-020. INC-001/002 se conservan cerradas y no se someten a otra elección.
- Cada bloque indica fuente y requisitos afectados; cada consideración presenta alternativas, recomendación y consecuencias o datos para cerrarla.
- La selección ya aprobada de reportes exportables, los permisos ya aprobados y la exclusión externa no se modifican por este archivo.
- Esta guía es una instantánea de revisión, no la nueva fuente definitiva ni una especificación implementable. El catálogo mantiene su estado y contenido hasta incorporar tus decisiones expresas.
- No se han generado código, diagramas ni pruebas de aplicación como parte de esta actividad. Los ejemplos de este archivo son explicativos, no resultados ejecutados.
- Su carácter temporal no autoriza borrarlo automáticamente: se conservará hasta que indiques cómo archivarlo o retirarlo después de incorporar las respuestas.

**Registro de elaboración:** solicitado por el responsable para analizar todos los pendientes y responder de forma conjunta. Elaborado con asistencia de IA sobre el catálogo v0.9 y el enunciado; ninguna alternativa se marca elegida y no se atribuyen recomendaciones a OptiPlant. Se mantiene la pausa de resolución individual mientras el responsable realiza su revisión.
