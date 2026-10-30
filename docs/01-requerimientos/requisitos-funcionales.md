# Requerimientos funcionales — OptiPlant

**Estado:** catálogo funcional **aprobado** en su formulación v1.0; decisiones y cuatro precisiones PR-01–PR-04 integradas. No acredita implementación ni verificación del software.  
**Versión:** 1.3 — 22 de octubre de 2026. Conserva H v1.1 y H v1.2/01; integra H v1.3/01–02: productos con baja exclusivamente lógica y captura/congelación del nombre histórico del proveedor.  
**Ámbito:** sección 6.1 de la prueba técnica, exclusivamente requerimientos funcionales.

## 1. Propósito, alcance y exclusiones aprobadas

Documentar qué debe hacer el Sistema de Inventario Multi-Sucursal de una misma organización, sin resolver de forma implícita las reglas pendientes ni definir su implementación.

El catálogo comprende administración y acceso, catálogo de productos, inventario, compras, ventas, transferencias, logística, dashboard y reportes exportables. La clasificación es documental: no fija los límites de paquetes Java. El backend aprobado es un monolito modular por capas; las tecnologías de `AGENTS.md` son decisiones del proyecto, no requisitos tecnológicos impuestos por OptiPlant.

- **Obligación externa:** proponer e implementar al menos un adicional con valor real (§4).
- **Decisión aprobada del proyecto:** implementar reportes exportables, con valor incremental respecto a las consultas, dashboards y reportes básicos.
- **Detalle aprobado del adicional:** Excel `.xlsx` real para movimientos, ventas y transferencias, con fechas, columnas y límites definidos en RF-0050–RF-0055; ratificación D, INC-020. No se exige PDF.
- **Exclusión aprobada:** integraciones ERP, POS u otras plataformas externas y el actor opcional «Sistema externo» (§6.2). No afecta la API REST entre React y Spring Boot.
- **Fuera de esta actividad:** requisitos no funcionales, especificación de API, diagramas, diseño de datos, código y pruebas ejecutables. Sus obligaciones externas se conservan en la matriz para tratarlas posteriormente.

Las decisiones del proyecto citadas como aprobadas sí están ratificadas. La aprobación de esta versión ratifica la formulación de las 55 fichas y sus criterios; no acredita implementación, pruebas ni las demás partes de §6.1.

## 2. Fuentes y convenciones

### 2.1. Fuentes

| Clave | Fuente y uso |
|---|---|
| E | [PDF oficial](../00-source/prueba-tecnica.pdf), fuente definitiva de requisitos externos. |
| M | [Transcripción operativa](../00-source/prueba-tecnica.md); facilita localizar el texto de E. |
| P | [AGENTS.md](../../AGENTS.md), especialmente §1: decisiones y propuestas del proyecto. |
| H | Instrucciones humanas de esta conversación: aprobación del método, selección del adicional, exclusión externa y autorización de redacción. |
| D | [Decisiones aprobadas v1.0](decisiones-aprobadas-optiplant-v1.0.md), §3–§6 y §9: 115 consideraciones ratificadas y DEC-TEC-ID-001. La autorización humana actual permite su integración documental. |
| G | [Guía temporal de incertidumbres](incertidumbres-pendientes-temporal.md), antecedente de alternativas; D determina la resolución aprobada. Se conserva sin modificar. |

**Precedencia de correcciones humanas:** la decisión H de v0.11 (§8) sustituye la autorización de recepciones sucesivas de D, INC-016/D03. Cada despacho tiene una única confirmación de llegada; parcial se registra como **«Recibida con discrepancia»**, sin completar después ese despacho. Una nueva entrega requiere otra transferencia y otro despacho. La protección contra duplicados impide repetir efectos de la misma confirmación; no supone reintentar físicamente la entrega. D y G se conservan como antecedentes sin reescribirlos. El tratamiento del faltante sigue separado, sin pérdida ni resolución automática.

**Precisiones de valoración aprobadas:** H v0.12 resuelve el residual de redondeo; H v0.13 (§8) distingue corrección de cantidad de entrada identificada a su costo histórico de ajuste por conteo sin origen identificado al promedio vigente. Si la compensación histórica agota cantidad y deja valor, registrar diferencia de valoración separada y vinculada, no llamarla redondeo. PR-02 resuelta en ese alcance; sin modificar precios/importes de venta ni operaciones anteriores, ni autorizar un módulo contable o correcciones exclusivas de precio no especificadas.

**Simplificación aprobada H v0.14:** la transferencia conserva «Recibida con discrepancia»; se retira el estado adicional de cierre con diferencia tratada. El botón «Resuelto» afecta solo la alerta, registra tratamiento (reenvío, ajuste o reclamación), responsable y fecha, sin modificar inventario, valor o estado del traslado. Movimientos manuales autorizados/trazables se registran aparte. La reasignación no se bloquea por operaciones creadas por el usuario: siguen bajo la sucursal y supervisión del gerente, conservando historia y revocando accesos previos. Sustituye los detalles anteriores de D/este catálogo que exigían aquel cierre o tareas personales adicionales; D permanece intacta como antecedente.

**Cierre de indicadores H v0.15:** ranking mensual por unidades comparables, empates por SKU y grupos sin solapamiento; rotación «No calculable» ante historia insuficiente o promedio cero. Cumplimiento logístico sobre llegadas confirmadas del período, no sobre estados de cierre ni alertas resueltas; tiempo real hasta única recepción, distinguiendo completa/discrepancia. Esta aprobación cierra PR-04 y el ciclo de cuatro precisiones; las fuentes y decisiones técnicas se conservan, sin elevar funciones adicionales condicionadas a compromisos aprobados.

**Alineación documental H v0.16:** RF-0004 precisa que la visibilidad global no hereda la **ejecución** de ventas, compras o movimientos locales, sin excluir su consulta conforme a §5.2. La solicitud de transferencia queda reservada al **operador del destino**: se elige esa alternativa de E §3.4 paso 1 —«la sucursal destino (o un administrador)»— y se excluye la variante del administrador, por coherencia con las responsabilidades de §6.2. Es una decisión del proyecto, no texto literal de E. JWT y la autorización por roles corresponden a RNF y al diseño de E §8.2; no se incorporan a este catálogo funcional.

Las páginas indicadas son **páginas físicas del PDF**, no su pie repetido «Pág. 1». El cotejo previo de E y M no identificó diferencias sustantivas; ambos omiten la sección 11 y conservan una lista numerada del 6 al 12 en §12. No se corrigen los originales.

### 2.2. Identificadores y dimensiones independientes

- `RF-NNNN`: identificador global estable; no depende del módulo, origen o estado.
- `RF-NNNN-CA-NN`: criterio de aceptación estable dentro del requisito.
- `INC-NNN`: incertidumbre; no es una regla aprobada.
- `INC-NNN/DNN`: consideración y decisión ratificada en D; no es un RF nuevo. §7.4 vincula cada una con sus RF y criterios.
- No renumerar ni reutilizar identificadores retirados. Conservar relaciones de sustitución o división si cambia la obligación.
- **Origen:** explícito externo, solicitud explícita del proyecto o derivado de ingeniería. Un derivado deberá citar requisito padre, decisión aplicable y razonamiento; no se atribuirá al PDF.
- **Obligatoriedad:** obligatorio externo, compromiso aprobado del proyecto, condicionado a selección, opcional externo o por determinar.
- **Estado documental:** propuesto, en revisión, aprobado o retirado. Es independiente de la obligación externa y del estado de implementación.

Las precisiones de D se incorporan como decisiones aprobadas del proyecto, no como mandatos literales de E. Las condiciones, opciones y detalles todavía abiertos no se transforman en obligaciones cerradas. Los ejemplos de E §6.3 solo se incorporan donde existe ratificación expresa, como el período de comparación mensual.

La resolución de INC-001 incorpora una precisión aprobada del proyecto a RF-0001, RF-0019, RF-0020 y RF-0048: el administrador consulta y modifica el stock mínimo, que se reutiliza para alertas de reabastecimiento y productos próximos a agotarse. Las funciones conservan su fundamento externo; esta delimitación procede de H y se documenta en §8, no se atribuye literalmente al PDF. La aprobación de esta precisión no aprueba íntegramente las fichas ni cierra sus demás incertidumbres.

La resolución de INC-002 aprueba para el administrador crear, listar, consultar, actualizar y desactivar usuarios y sucursales, sin eliminación física, y los atributos mínimos de RF-0002 y RF-0003. No se incluyen autorregistro, invitaciones por correo ni recuperación automática de contraseña. La baja de sucursal exige stock cero y resolver borradores de compra, compras por recibir y transferencias abiertas, incluidos faltantes; §5.1 conserva los efectos. Contacto y ubicación siguen excluidos de la base inicial. D completa los permisos y políticas funcionales de acceso; sus mecanismos técnicos siguen por diseñar.

INC-003, ratificada en D, delimita los permisos en §5.2: administrador con mantenimiento global, gerente con supervisión y ajustes propios, operador con operaciones locales. No se heredan operaciones locales por tener rol administrador o gerente. El catálogo común y su habilitación local se definen en INC-005; ningún ajuste permite editar saldos directamente ni mantener el catálogo sin autorización.

El acceso mediante **código único de usuario y contraseña personal**, el cambio inicial/voluntario y la revocación de sesiones están aprobados en D. No se adopta correo obligatorio, PIN ni código compartido. RF-0006 describe los efectos observables; §5.4 conserva parámetros aprobados para su traslado a RNF y al diseño de E §8, sin elegir bibliotecas ni estrategia de tokens.

Por decisión H, INC-003, registro §8, cada usuario tiene exactamente un rol. El administrador tiene ámbito global sin sucursal obligatoria; gerente y operador están asignados a una única sucursal. Esto delimita su ámbito de operación, no elimina las consultas entre sucursales exigidas por E ni concede permisos de escritura cruzados. No se incorpora selección de varias sucursales o combinación de roles para un mismo usuario.

### 2.3. Plantilla aplicada y valores comunes

Cada ficha contiene módulo (por agrupación), origen, obligatoriedad, estado, fuente y fragmento, actores/alcance, descripción, reglas/datos, dependencias, criterios y límites pendientes.

Para evitar repetir información, las fichas **RF-0001–RF-0049** heredan: **origen explícito externo; obligatoriedad obligatorio externo; estado aprobado; fundamento de derivación no aplica**. RF-0050–RF-0055 declaran sus propios valores. La extracción y la descomposición editorial no constituyen decisiones de ingeniería adicionales.

**Trazabilidad posterior de todas las fichas:** casos de uso, diagramas, implementación y pruebas pendientes de elaboración; no se presume su existencia ni cumplimiento. DEC-TEC-ID-001 ya está aprobada en D §9; su materialización en arquitectura/API/modelo se realizará posteriormente. **Aprobación de formulación:** aprobada por el responsable el 2026-10-21 (v1.0); no confundirla con la ratificación de reglas en D ni con la implementación.

Los criterios expresan resultados observables; no son pruebas ejecutadas. Los ejemplos de datos para verificarlos deberán respetar las reglas aprobadas. Cuando una incertidumbre afecta un criterio, este delimita lo verificable y no cierra la decisión pendiente.

## 3. Índice general de requisitos

Las 55 fichas y sus 138 criterios quedan aprobados en su formulación v1.0; las decisiones citadas de D y los seis compromisos del adicional están aprobados, sin acreditar implementación.

| ID | Título | Módulo / área | Origen | Obligatoriedad |
|---|---|---|---|---|
| RF-0001 | Gestionar configuración | Administración | Externo | Obligatorio externo |
| RF-0002 | Gestionar usuarios | Administración | Externo | Obligatorio externo |
| RF-0003 | Gestionar sucursales | Administración | Externo | Obligatorio externo |
| RF-0004 | Proporcionar visibilidad general | Administración | Externo | Obligatorio externo |
| RF-0005 | Mantener ámbito operativo local | Multi-sucursal | Externo | Obligatorio externo |
| RF-0006 | Autenticar usuarios | Acceso | Externo | Obligatorio externo |
| RF-0007 | Autorizar operaciones | Acceso | Externo | Obligatorio externo |
| RF-0008 | Crear productos | Catálogo | Externo | Obligatorio externo |
| RF-0009 | Consultar productos | Catálogo | Externo | Obligatorio externo |
| RF-0010 | Actualizar productos | Catálogo | Externo | Obligatorio externo |
| RF-0011 | Eliminar productos | Catálogo | Externo | Obligatorio externo |
| RF-0012 | Visualizar catálogo local | Catálogo | Externo | Obligatorio externo |
| RF-0013 | Gestionar unidades por producto | Catálogo | Externo | Obligatorio externo |
| RF-0014 | Consultar inventario de otra sucursal | Inventario | Externo | Obligatorio externo |
| RF-0015 | Compartir información de inventario | Inventario | Externo | Obligatorio externo |
| RF-0016 | Registrar ingresos | Inventario | Externo | Obligatorio externo |
| RF-0017 | Registrar retiros | Inventario | Externo | Obligatorio externo |
| RF-0018 | Conservar historial auditable | Inventario | Externo | Obligatorio externo |
| RF-0019 | Controlar stock mínimo | Inventario | Externo | Obligatorio externo |
| RF-0020 | Alertar reabastecimiento | Inventario | Externo | Obligatorio externo |
| RF-0021 | Crear órdenes de compra | Compras | Externo | Obligatorio externo |
| RF-0022 | Gestionar órdenes de compra | Compras | Externo | Obligatorio externo |
| RF-0023 | Registrar condiciones de compra | Compras | Externo | Obligatorio externo |
| RF-0024 | Actualizar inventario al recibir compras | Compras | Externo | Obligatorio externo |
| RF-0025 | Consultar histórico de compras | Compras | Externo | Obligatorio externo |
| RF-0026 | Calcular costo promedio ponderado | Compras | Externo | Obligatorio externo |
| RF-0027 | Registrar ventas | Ventas | Externo | Obligatorio externo |
| RF-0028 | Validar stock antes de vender | Ventas | Externo | Obligatorio externo |
| RF-0029 | Aplicar descuentos de venta | Ventas | Externo | Obligatorio externo |
| RF-0030 | Gestionar listas de precios | Ventas | Externo | Obligatorio externo |
| RF-0031 | Consultar comprobante o registro de venta | Ventas | Externo | Obligatorio externo |
| RF-0032 | Solicitar transferencias | Transferencias | Externo | Obligatorio externo |
| RF-0033 | Preparar cantidades a enviar | Transferencias | Externo | Obligatorio externo |
| RF-0034 | Aprobar transferencias | Transferencias | Externo | Obligatorio externo |
| RF-0035 | Registrar despacho | Transferencias | Externo | Obligatorio externo |
| RF-0036 | Confirmar recepción completa | Transferencias | Externo | Obligatorio externo |
| RF-0037 | Registrar recepción parcial y faltantes | Transferencias | Externo | Obligatorio externo |
| RF-0038 | Alertar faltantes | Transferencias | Externo | Obligatorio externo |
| RF-0039 | Definir tratamiento del faltante | Transferencias | Externo | Obligatorio externo |
| RF-0040 | Registrar y comparar tiempos | Logística | Externo | Obligatorio externo |
| RF-0041 | Clasificar rutas | Logística | Externo | Obligatorio externo |
| RF-0042 | Consultar estados de transferencias | Logística | Externo | Obligatorio externo |
| RF-0043 | Reportar cumplimiento logístico | Logística | Externo | Obligatorio externo |
| RF-0044 | Comparar ventas mensuales | Dashboard | Externo | Obligatorio externo |
| RF-0045 | Presentar rotación | Dashboard | Externo | Obligatorio externo |
| RF-0046 | Identificar alta y baja demanda | Dashboard | Externo | Obligatorio externo |
| RF-0047 | Mostrar transferencias e impacto | Dashboard | Externo | Obligatorio externo |
| RF-0048 | Mostrar indicadores de reabastecimiento | Dashboard | Externo | Obligatorio externo |
| RF-0049 | Comparar rendimiento entre sucursales | Dashboard | Externo | Obligatorio externo |
| RF-0050 | Exportar reportes con valor incremental | Reportes | Proyecto | Compromiso aprobado del proyecto |
| RF-0051 | Exportar en formato .xlsx | Reportes | Proyecto | Compromiso aprobado del proyecto |
| RF-0052 | Exportar movimientos | Reportes | Proyecto | Compromiso aprobado del proyecto |
| RF-0053 | Exportar ventas | Reportes | Proyecto | Compromiso aprobado del proyecto |
| RF-0054 | Exportar transferencias | Reportes | Proyecto | Compromiso aprobado del proyecto |
| RF-0055 | Filtrar exportaciones por fechas | Reportes | Proyecto | Compromiso aprobado del proyecto |

## 4. Fichas por módulos y áreas funcionales

### 4.1. Administración, identidad y acceso

#### RF-0001 — Gestionar configuración
- **Fuente:** E §6.2, p. 5, administrador: «Gestiona configuración». Precisión del proyecto: H, resolución de INC-001 registrada en §8.
- **Actores / alcance:** administrador general; configuración operativa limitada al stock mínimo.
- **Descripción:** el sistema deberá permitir al administrador consultar y modificar el stock mínimo utilizado para las alertas de reabastecimiento y la identificación de productos próximos a agotarse.
- **Reglas y datos:** reutilizar el mismo parámetro en ambas funciones, sin otro umbral independiente. La modificación no cambia existencias, costos ni transacciones, no bloquea operaciones ni genera compras automáticamente. Esta delimitación es una decisión del proyecto; E no enumera parámetros concretos.
- **Dependencias:** RF-0006, RF-0007, RF-0019; relacionada con RF-0020 y RF-0048.
- **RF-0001-CA-01:** dado un stock mínimo por producto-sucursal, cuando el administrador lo consulte, modifique y vuelva a consultar, entonces recuperará el nuevo valor explícito, incluido cero, para esa combinación.
- **RF-0001-CA-02:** dados los valores de existencias y costos y los registros de transacciones, cuando el administrador cambie únicamente el stock mínimo, entonces aquellos conservarán sus valores y registros; el cambio no bloqueará operaciones ni creará órdenes de compra automáticamente.
- **Límites / pendientes:** INC-001 resuelta; D, INC-010/D01–D03 ratifica granularidad, valor explícito y comparación inclusiva. No se incluye un gestor genérico de configuración.

#### RF-0002 — Gestionar usuarios
- **Fuente:** E §6.2, p. 5, administrador: «Gestiona [...] usuarios». Precisiones del proyecto: H, operaciones y atributos mínimos en INC-002, código y contraseña personal en INC-003, registradas en §8.
- **Actores / alcance:** administrador general; usuarios del sistema.
- **Descripción:** el sistema deberá permitir al administrador crear, listar, consultar, actualizar y desactivar usuarios.
- **Reglas y datos:** atributos mínimos aprobados: identidad única, estable, generada por el sistema y no editable, con referencia pública según §5.3 y sin exponer la clave interna; nombre completo obligatorio, editable y no necesariamente único; código de usuario obligatorio y único incluso entre cuentas inactivas; exactamente un perfil de administrador, gerente u operador; estado activo/inactivo. Gerente y operador requieren asignación a una única sucursal activa para operar; el administrador tiene ámbito global sin sucursal obligatoria, según H/D, INC-003. Código identifica la cuenta, no sustituye la contraseña personal de RF-0006. No se permitirá eliminación física. Desactivar impide acceso, revoca accesos previos y conserva referencias históricas. Cambiar datos/asignaciones permitidos no altera identidad ni hechos anteriores. Sin contacto, autorregistro, invitaciones por correo o recuperación automática de contraseña.
- **Dependencias:** RF-0003, RF-0006, RF-0007.
- **RF-0002-CA-01:** dados nombre completo, código único y un único perfil, con una sucursal activa asignada si es gerente u operador, cuando el administrador cree un usuario, entonces se generará su identidad estable, se registrarán sus datos y estado, y podrá localizarse en el listado y consultarse individualmente. Si el perfil es administrador, no se exigirá sucursal para su ámbito global. La contraseña inicial temporal e individual sigue RF-0006; la clave de persistencia no se expone al usuario.
- **RF-0002-CA-02:** dados usuarios registrados, cuando el administrador liste y consulte uno de ellos, entonces podrá distinguirlo de los demás y recuperar sus datos registrados, sin modificarlos.
- **RF-0002-CA-03:** dado un usuario y un cambio permitido de datos o asignación conforme a INC-003, cuando el administrador lo actualice, entonces la consulta posterior reflejará el cambio sin eliminar las referencias a sus operaciones históricas.
- **RF-0002-CA-04:** dado un usuario activo con operaciones históricas, cuando el administrador lo desactive, entonces el usuario permanecerá registrado como inactivo, no podrá acceder al sistema y sus referencias como responsable de esas operaciones seguirán siendo consultables; no se ofrecerá eliminación física como alternativa de baja.
- **RF-0002-CA-05:** dado un código ya asignado a una cuenta activa o inactiva, cuando se intente crear otra cuenta o actualizar una existente con el mismo código, entonces se rechazará la duplicación sin crear o modificar cuentas.
- **RF-0002-CA-06:** dado un usuario existente, cuando se modifiquen sus datos permitidos o su estado, entonces conservará su identificador interno y las referencias históricas lo seguirán identificando; no se admitirá editar ese identificador.
- **RF-0002-CA-07:** cuando se intente crear o actualizar un usuario con cero roles o con más de uno, entonces se rechazará la asignación sin crear el usuario ni alterar su configuración anterior.
- **RF-0002-CA-08:** cuando se intente crear o actualizar un gerente u operador sin sucursal o con más de una asignada, entonces se rechazará sin crear el usuario ni cambiar su asignación anterior; cada uno debe conservar una única sucursal.
- **RF-0002-CA-09:** dado un cambio de rol o sucursal permitido, cuando se confirme, entonces se revocarán los accesos previos y se conservarán autor, sucursal y permisos registrados en hechos anteriores. No bloquearlo por compras/transferencias pendientes que el usuario creó: siguen perteneciendo a la sucursal bajo supervisión del gerente. Por ejemplo, una compra pendiente creada por operadora de A seguirá en A tras reasignarla a B; otro operador autorizado de A podrá recibirla, conservando la creadora original y el nuevo responsable de recepción. La reasignada no podrá continuar operando en A con accesos anteriores.
- **Límites / pendientes:** INC-002 resuelta; D, INC-003/D07–D12. No desactivar usuarios por baja de sucursal. Formatos y revocación técnica se diseñarán posteriormente. Identidad pública estable, generada y no editable conforme a §5.3; no agregar claves técnicas a formularios.

#### RF-0003 — Gestionar sucursales
- **Fuente:** E §6.2, p. 5, administrador: «Gestiona [...] sucursales». Precisiones del proyecto: H, operaciones, atributos mínimos y desactivación condicionada de sucursales en INC-002, registradas en §8.
- **Actores / alcance:** administrador general; sucursales de una organización.
- **Descripción:** el sistema deberá permitir al administrador crear, listar, consultar, actualizar y desactivar las sucursales de la red.
- **Reglas y datos:** identidad única, estable, generada y no editable, con referencia pública según §5.3 y sin exponer clave interna; código obligatorio, único, no reutilizable e inmutable tras alta; nombre obligatorio editable no necesariamente único; estado activa/inactiva. Sin dirección, ciudad, contacto ni eliminación física. Por H/D, solo desactivar con existencias cero en todos los productos, sin borradores de compra operables, compras pendientes de recepción ni transferencias abiertas como origen/destino, incluidos faltantes pendientes de tratamiento. Si falla alguna condición, rechazar indicando impedimento, sin cancelar documentos ni ajustar stock automáticamente. Inactiva conserva identidad/historial y consultas autorizadas, no nuevas operaciones comerciales/de inventario ni selección como origen/destino. No desactivar automáticamente usuarios ni introducir cierre gradual.
- **Dependencias:** RF-0006, RF-0007, RF-0016–RF-0018, RF-0021–RF-0024, RF-0027, RF-0032–RF-0039, RF-0042; restricción común de §5.1.
- **RF-0003-CA-01:** dados un código único y nombre de sucursal, cuando el administrador la cree, entonces se generará su identificador interno, se conservarán código, nombre y estado, y podrá localizarse en el listado y consultarse individualmente.
- **RF-0003-CA-02:** dadas sucursales registradas, cuando el administrador liste y consulte una de ellas, entonces podrá distinguirla de las demás y recuperar sus datos registrados sin modificarlos.
- **RF-0003-CA-03:** dada una sucursal y un cambio permitido de sus datos, cuando el administrador la actualice, entonces la consulta posterior reflejará el cambio conservando sus referencias históricas.
- **RF-0003-CA-04:** dada una sucursal activa con existencias cero en todos sus productos, sin borradores de compra operables, compras por recibir ni transferencias abiertas o faltantes pendientes de tratamiento, cuando el administrador la desactive, entonces permanecerá registrada como inactiva y su historial seguirá siendo consultable por perfiles autorizados; no habrá eliminación física ni pérdida de referencias históricas.
- **RF-0003-CA-05:** dada una sucursal activa con al menos un producto cuya existencia no sea cero, cuando el administrador intente desactivarla, entonces se rechazará indicando el impedimento y se conservarán su estado, existencias y documentos sin ajustes ni cancelaciones automáticas.
- **RF-0003-CA-06:** dada una sucursal activa con compras pendientes de recepción o borradores de compra operables, cuando se intente desactivarla, entonces se rechazará indicando dichas compras como impedimento y se conservarán su estado, existencias y documentos. Antes de la baja, cancelar borradores y recibir o cancelar órdenes pendientes mediante sus operaciones válidas.
- **RF-0003-CA-07:** dada una sucursal activa con una transferencia abierta, cuando se intente desactivarla como origen o como destino, entonces se rechazará indicando el impedimento y conservando su estado, existencias y documentos. El caso deberá cubrir preparación, tránsito y faltantes pendientes de tratamiento; los demás estados abiertos se alinearán con INC-015 e INC-016.
- **RF-0003-CA-08:** dada una sucursal inactiva, cuando se intente registrar una operación comercial o de inventario, o seleccionarla como origen/destino de una nueva transferencia, entonces se impedirá sin modificar existencias ni crear la operación; las consultas históricas autorizadas seguirán disponibles. Cubrir ingresos, retiros, compras, ventas y transferencias de §5.1.
- **RF-0003-CA-09:** dados usuarios asociados a una sucursal y una desactivación de sucursal permitida, cuando se complete, entonces el estado activo/inactivo de sus cuentas no se modificará automáticamente; esto no les permitirá operar sobre la sucursal inactiva.
- **RF-0003-CA-10:** dado un código perteneciente a una sucursal activa o inactiva, cuando se intente registrar otra con ese código, entonces se rechazará sin crear la sucursal ni modificar la existente.
- **RF-0003-CA-11:** dada una sucursal registrada, cuando se modifique su nombre o estado mediante operaciones permitidas, entonces conservará identificador interno y código; los intentos de cambiar estos identificadores se rechazarán sin alterar las referencias históricas.
- **Límites / pendientes:** INC-002 resuelta; D, INC-011/D05 e INC-015–INC-016, precisadas por H v0.14, completan impedimentos de baja. §5.1 distingue alerta resuelta de inventario regularizado. Identidad pública conforme a §5.3. Sin administración multi-organización ni reactivación automática.

#### RF-0004 — Proporcionar visibilidad general
- **Fuente:** E §6.2, p. 5: el administrador «tiene visibilidad total del sistema».
- **Actores / alcance:** administrador general; todas las sucursales.
- **Descripción:** el sistema deberá permitir al administrador consultar la información operativa de todas las sucursales comprendida en el alcance.
- **Reglas y datos:** visibilidad no equivale a permiso ilimitado de modificación ni a exponer secretos de autenticación.
- **Dependencias:** RF-0007, RF-0014, RF-0025, RF-0031, RF-0042, RF-0043, RF-0049.
- **RF-0004-CA-01:** dados registros de dos sucursales en las consultas del catálogo, cuando el administrador consulte cada ámbito, entonces podrá acceder a ambos sin quedar restringido a una sola sucursal.
- **Límites / pendientes:** D, INC-003/D01 y D04–D05; H v0.16. Visibilidad global de datos operativos, sin secretos y sin heredar la **ejecución** de ventas, compras o movimientos locales; puede consultarlos según §5.2. El administrador no solicita transferencias: esa operación corresponde al operador del destino (RF-0032).

#### RF-0005 — Mantener ámbito operativo local
- **Fuente:** E §2–2.1, p. 1: «transacciones locales» y «autonomía operativa»; §6.2, p. 5: gerente «supervisa operaciones de su sucursal».
- **Actores / alcance:** gerente y operador en su única sucursal asignada; administrador con ámbito global según sus permisos aprobados.
- **Descripción:** el sistema deberá distinguir las operaciones e inventarios por sucursal y permitir su gestión local independiente.
- **Reglas y datos:** la consulta de otra sucursal no constituye autorización para modificarla; no se presupone operación offline. La operación local exige sucursal activa conforme a RF-0003 y §5.1; una sucursal inactiva conserva consultas históricas autorizadas, no nuevas operaciones. Por decisión H, INC-003, registro §8, gerente y operador tienen una sola sucursal operativa asignada; no pueden cambiar su ámbito de escritura seleccionando otra sucursal en una consulta. El gerente puede ajustar stock solo de su propia sucursal mediante RF-0016–RF-0018, sin modificar el saldo de otras.
- **Dependencias:** RF-0003, RF-0007; operaciones RF-0016–RF-0039.
- **RF-0005-CA-01:** dadas existencias locales de dos sucursales, cuando se registre un movimiento local en una, entonces cambiarán sus existencias y no las de la otra, salvo una operación entre sucursales expresamente registrada.
- **RF-0005-CA-02:** dadas operaciones de distintas sucursales, cuando el gerente consulte las de su sucursal, entonces podrá distinguir sus operaciones de las de las demás.
- **RF-0005-CA-03:** ante pérdida de comunicación, cuando una operación no haya sido aceptada por el backend, entonces no se presentará como confirmada ni se guardará para sincronización offline; se advertirá que las consultas pueden estar desactualizadas.
- **Límites / pendientes:** D, INC-004/D01–D04: autonomía sobre plataforma común conectada, sin escrituras offline. §5.4 conserva la meta de frescura; el mecanismo de actualización se decidirá en diseño sin cambiar REST.

#### RF-0006 — Autenticar usuarios
- **Fuente:** E §8.2, p. 7: «Estrategia de autenticación y autorización implementada»; §6.2, p. 5, actores internos. Precisión H, INC-003, registro §8: código único de usuario y contraseña personal.
- **Actores / alcance:** usuarios internos; no incluye actor externo.
- **Descripción:** el sistema deberá permitir iniciar sesión mediante código único de usuario y contraseña personal, verificando que la cuenta esté activa.
- **Reglas y datos:** el código identifica la cuenta y la contraseña verifica su identidad; no son una misma credencial ni un secreto compartido entre usuarios. El PDF no impone este mecanismo: es decisión del proyecto H, registro §8. Los usuarios desactivados mediante RF-0002 no podrán autenticarse. La contraseña no es un atributo consultable del usuario; su protección, provisión y ciclo de sesión deberán concretarse sin exponerla en consultas ni registros de actividad.
- **Dependencias:** RF-0002.
- **RF-0006-CA-01:** dada una cuenta activa y su código y contraseña personal correctos, cuando solicite iniciar sesión, entonces se establecerá una sesión autenticada para esa cuenta. Un código inexistente o una contraseña incorrecta no establecerán una sesión autenticada.
- **RF-0006-CA-02:** dado un usuario desactivado y su código y contraseña correctos, cuando intente autenticarse, entonces se rechazará el acceso sin establecer una sesión autenticada.
- **RF-0006-CA-03:** dada una cuenta registrada, cuando se consulten sus datos o se revise la actividad de autenticación registrada, entonces no se mostrará ni registrará su contraseña.
- **Reglas funcionales aprobadas (D, INC-003/D09–D14):** contraseña inicial temporal, individual y no derivada del código; cambio obligatorio en primer ingreso antes de operar. Cambio voluntario acreditando contraseña vigente. Salida invalida el acceso utilizado; desactivar cuenta o cambiar rol/sucursal revoca accesos previos. Sesión vencida exige autenticarse de nuevo sin deshacer operaciones confirmadas; advertir razonablemente sobre formularios no enviados.
- **RF-0006-CA-04:** dado un primer ingreso con contraseña temporal válida, cuando se autentique el usuario, entonces se exigirá cambiarla antes de operar. Un cambio voluntario con contraseña vigente incorrecta se rechazará sin modificar credenciales; con la correcta y nueva contraseña admisible, se aceptará.
- **RF-0006-CA-05:** dada una sesión abierta, cuando se cierre, venza o se revoque por baja/cambio de rol/sucursal, entonces su acceso previo no permitirá continuar en operaciones protegidas; los hechos ya confirmados permanecerán intactos.
- **RF-0006-CA-06:** al establecer o cambiar contraseña, se admitirán espacios y longitudes de 15 a 128 caracteres; fuera de esos límites se rechazará sin cambiar credenciales. No se impondrá rotación periódica arbitraria. La detección de contraseñas débiles/comprometidas queda condicionada al mecanismo adoptado, no se presume implementada.
- **Límites / pendientes:** §5.4 conserva parámetros de seguridad aprobados y provisión segura del primer administrador; faltan diseño antiabuso y mecanismos de protección, sin texto plano ni credenciales públicas. No se eligen JWT, OAuth ni bibliotecas; sin MFA, autorregistro o recuperación automática por correo.

#### RF-0007 — Autorizar operaciones
- **Fuente:** E §6.2, p. 5, responsabilidades de actores; §3.6, p. 3, perfiles administrativos; §8.2, p. 7, autorización implementada.
- **Actores / alcance:** exactamente un rol por usuario; administrador global sin sucursal obligatoria; gerente y operador en una única sucursal, activa para operar. Matriz funcional aprobada en §5.2.
- **Descripción:** el sistema deberá autorizar operaciones y consultas conforme a las responsabilidades y ámbitos aprobados.
- **Reglas y datos:** aplicar §5.2 a cada recurso y ámbito, incluso si se conoce su identificador público (§5.3). Cuenta desactivada o acceso revocado no permite obtener datos protegidos ni operar. Sucursal inactiva solo conserva consultas históricas autorizadas, sin desactivar automáticamente cuentas. Administrador no hereda operaciones locales; gerente no mantiene catálogo general ni opera en otras sucursales.
- **Regla de comparativas (H/D, INC-003):** comparativas de rendimiento de red exclusivas del administrador. Gerente con dashboard y reportes propios; operador con panel operacional local sin valoración/costos agregados, sin importes agregados de venta (precisión del responsable, 2026-10-30) y sin exportación. Se conserva consulta de inventario de otras sucursales según RF-0014.
- **Dependencias:** RF-0006; afecta todos los módulos.
- **RF-0007-CA-01:** dada la matriz aprobada en INC-003, cuando una identidad solicite una operación, entonces se permitirá si está autorizada y se rechazará sin efectuarla si no lo está.
- **RF-0007-CA-02:** dada una cuenta desactivada y el tratamiento de sesiones aprobado en INC-003, cuando intente acceder a una operación protegida, entonces no podrá ejecutarla ni obtener los datos protegidos.
- **RF-0007-CA-03:** dado un gerente de la sucursal A y existencias en A y B, cuando intente registrar un ajuste de stock sobre B, entonces se rechazará sin cambiar existencias ni registrar el movimiento de ajuste en ninguna sucursal. Su consulta autorizada de B no habilitará ese cambio.
- **RF-0007-CA-04:** dado un gerente u operador asignado a A, cuando intente ejecutar una operación local de su rol sobre B sustituyendo la sucursal de la solicitud o seleccionándola desde una consulta, entonces se rechazará sin modificar datos ni registrar la operación. Esto no impide la consulta de inventario de B ni los flujos entre sucursales autorizados expresamente, como solicitar una transferencia desde A.
- **RF-0007-CA-05:** dados un administrador, un gerente y un operador, cuando intenten una acción de §5.2 fuera de su permiso o sustituyan la referencia pública de un recurso por la de otra sucursal no autorizada para esa acción, entonces se rechazará sin divulgar datos protegidos ni modificar registros. Cubrir mantenimiento global, ventas/compras, ajustes, etapas de transferencia y exportación.
- **Límites / pendientes:** D, INC-003/D01–D08, con precisión H v0.14: pendientes de la sucursal, sin bloqueo por autoría ni módulo de tareas personales. Revocación técnica posterior; no conceder permisos adicionales por inferencia.

### 4.2. Catálogo de productos

#### RF-0008 — Crear productos
- **Fuente:** E §3.1, p. 1: «creación [...] de registros de producto». Precisión H, INC-003, registro §8: CRUD general para el administrador.
- **Actores / alcance:** administrador general; catálogo común de la organización.
- **Descripción:** el sistema deberá permitir crear registros de producto.
- **Reglas y datos:** identidad estable, SKU único en la red, nombre, descripción opcional y unidades. Identidad pública distinta del SKU y de la clave interna (§5.3). Sin marcas, imágenes, códigos de barras ni categorías obligatorios. Crear producto no crea stock; administrador puede habilitarlo explícitamente en varias sucursales o todas las existentes, con mínimo explícito por sucursal.
- **Dependencias:** RF-0007, RF-0009.
- **RF-0008-CA-01:** dados los datos de producto aprobados, cuando el administrador cree el registro, entonces el producto quedará disponible para consulta con los datos registrados.
- **RF-0008-CA-02:** dado un SKU ya existente, cuando se intente crear otro producto con ese SKU, entonces se rechazará sin duplicar el catálogo. Habilitar el producto en otra sucursal conservará su identidad y no incrementará existencias.
- **Límites / pendientes:** D, INC-005/D01–D03. La secuencia transaccional de habilitación/configuración del mínimo se definirá en diseño sin permitir operar antes de completar ambos.

#### RF-0009 — Consultar productos
- **Fuente:** E §3.1, p. 1: «consulta [...] de registros de producto». Precisión H, INC-003, registro §8: CRUD general para el administrador.
- **Actores / alcance:** administrador, gerente y operador; catálogo común y ámbito de consulta autorizado.
- **Descripción:** el sistema deberá permitir consultar los registros de producto existentes.
- **Reglas y datos:** distinguir información de producto de existencias por sucursal.
- **Dependencias:** RF-0007.
- **RF-0009-CA-01:** dado un producto registrado, cuando se consulte, entonces se mostrarán sus datos registrados sin modificarlo.
- **Límites / pendientes:** D, INC-003/D04 e INC-005; filtros avanzados no prescritos. Consulta no concede mantenimiento ni acceso a valoración restringida.

#### RF-0010 — Actualizar productos
- **Fuente:** E §3.1, p. 1: «actualización [...] de registros de producto». Precisión H, INC-003, registro §8: CRUD general para el administrador.
- **Actores / alcance:** administrador general; producto existente del catálogo general.
- **Descripción:** el sistema deberá permitir actualizar registros de producto.
- **Reglas y datos:** conservar identidad estable y SKU tras el uso del producto; identidad pública siempre inmutable (§5.3). Actualizar atributos comerciales permitidos no altera instantáneas de descripción/precios/unidades/factores de documentos confirmados ni sus referencias. No confundir actualización con ajuste de existencias; gerente no mantiene atributos generales.
- **Dependencias:** RF-0007, RF-0009.
- **RF-0010-CA-01:** dado un producto y un atributo modificable aprobado, cuando el administrador lo actualice, entonces la consulta posterior reflejará el valor actualizado.
- **RF-0010-CA-02:** dado un producto usado en documentos confirmados, cuando cambien sus atributos comerciales permitidos, entonces esos documentos conservarán sus datos históricos y referencias; se rechazará cambiar su SKU o identidad protegida.
- **Límites / pendientes:** D, INC-005/D04 e INC-006. La reactivación manual ya está aprobada en RF-0011/H v1.1; actualizar atributos comerciales no reactiva un producto por inferencia ni restaura habilitaciones locales o mínimos.

#### RF-0011 — Eliminar productos
- **Fuente:** E §3.1, p. 1: «eliminación de registros de producto»; §3.1, p. 2, historial auditable. Precisión H, INC-003, registro §8: CRUD general para el administrador.
- **Actores / alcance:** administrador general; producto existente.
- **Descripción:** el sistema deberá permitir la eliminación de productos conforme a una política compatible con la trazabilidad obligatoria.
- **Reglas y datos:** H v1.3/01 sustituye la alternativa anterior de D, INC-006/D01: todos los productos se conservan permanentemente, tengan o no referencias históricas, movimientos o dependencias. La eliminación es exclusivamente lógica mediante desactivación (`active=FALSE`), solo con stock cero en todas las sucursales y sin operaciones pendientes, conforme a INC-006/D02–D03; no se permite borrado físico ni en cascada. Producto globalmente inactivo no admite nuevas operaciones, pero conserva historial. Solo el administrador puede reactivarlo; la reactivación restaura su disponibilidad en el catálogo general, pero no lo habilita para operar en ninguna sucursal ni restaura mínimos previos: cada sucursal debe habilitarlo explícitamente y configurar de nuevo su stock mínimo antes de operar.
- **Dependencias:** RF-0007, RF-0009, RF-0018.
- **RF-0011-CA-01:** dado un producto que cumpla las condiciones de eliminación aprobadas, cuando el administrador solicite eliminarlo, entonces el resultado corresponderá a la política aprobada sin perder el historial exigido de movimientos asociados.
- **RF-0011-CA-02:** dado cualquier producto, con o sin historia, movimientos o dependencias, cuando se solicite su eliminación, entonces no se permitirá el borrado físico. Si tiene stock u operaciones pendientes se rechazará la desactivación sin borrar referencias ni modificar existencias/documentos; con stock cero en todas las sucursales y sin pendientes, permanecerá registrado con `active=FALSE`.
- **RF-0011-CA-03:** dado un producto inactivo, cuando se intente una nueva operación de inventario, compra, venta o transferencia, entonces se impedirá sin modificar saldos; su historial seguirá consultable con autorización.
- **RF-0011-CA-04:** dado un producto inactivo, cuando el administrador lo reactive, entonces volverá a estar disponible en el catálogo general sin habilitarse para operar en ninguna sucursal ni recuperar mínimos anteriores. Cuando una sucursal requiera operarlo, deberá habilitarlo explícitamente y configurar un nuevo mínimo antes de registrar compras, ventas, movimientos o transferencias.
- **Límites / pendientes:** H v1.1 resuelve la reactivación manual sin crear restauración automática de habilitaciones o mínimos; la secuencia técnica de habilitación y configuración corresponde al diseño.

#### RF-0012 — Visualizar catálogo local
- **Fuente:** E §3.1, p. 1: «Visualizar el catálogo de productos disponibles en la sucursal propia».
- **Actores / alcance:** usuarios de sucursal autorizados.
- **Descripción:** el sistema deberá mostrar los productos disponibles en la sucursal propia.
- **Reglas y datos:** catálogo local = productos habilitados explícitamente por administrador, no solo los de stock positivo. Habilitación no agrega stock y exige mínimo explícito, incluido cero. Mostrar existencias físicas y disponibilidad conforme al glosario §5.3, con tránsito separado.
- **Dependencias:** RF-0005, RF-0009, RF-0014.
- **RF-0012-CA-01:** dados productos de distintas sucursales, cuando se consulte el catálogo propio, entonces se mostrarán los correspondientes a la sucursal consultante conforme a la definición de disponibilidad aprobada.
- **RF-0012-CA-02:** dado un producto habilitado con stock cero y otro no habilitado, cuando se consulte el catálogo local, entonces se distinguirá la habilitación sin presentar al segundo como operable; el primero conservará su presencia local aunque no tenga existencias.
- **Límites / pendientes:** D, INC-005/D03, INC-008 e INC-010/D02. Sin reservas.

#### RF-0013 — Gestionar unidades por producto
- **Fuente:** E §3.1, p. 2: «Gestionar múltiples unidades de medida por producto».
- **Actores / alcance:** administrador configura; gerente y operador usan/consultan unidades en operaciones autorizadas.
- **Descripción:** el sistema deberá gestionar más de una unidad de medida para un mismo producto.
- **Reglas y datos:** D, INC-007/D01–D05: unidad base y conversiones propias por producto; no equivalencias universales. Fracciones solo para productos/unidades divisibles, hasta 3 decimales en unidad base; rechazar cantidades/factores que exijan más precisión, sin redondeo silencioso. Registrar unidad comercial, cantidad, factor vigente, precio por unidad comercial y equivalente exacto base. Unidad base inmutable tras uso; cambios futuros de factor no reexpresan documentos ni saldos históricos.
- **Dependencias:** RF-0009; relacionada con RF-0016, RF-0017, RF-0026, RF-0027, RF-0032.
- **RF-0013-CA-01:** dado un producto con al menos dos unidades admitidas, cuando se consulte su configuración de unidades, entonces ambas se conservarán asociadas al mismo producto y serán distinguibles.
- **RF-0013-CA-02:** dado que una caja del producto P equivale a 12 unidades base, cuando se registre una caja válida, entonces su equivalente será 12; una fracción en producto indivisible o un resultado base que exceda 3 decimales se rechazará sin movimiento ni redondeo implícito.
- **RF-0013-CA-03:** dado un documento confirmado y un cambio posterior de conversión por administrador, cuando se consulten el documento y los saldos previos, entonces conservarán unidad, factor y cantidades originales; nuevas operaciones usarán el nuevo factor. Se rechazará cambiar la unidad base usada.
- **Límites / pendientes:** diseño de representación decimal sin alterar las reglas aprobadas.

### 4.3. Inventario y movimientos

#### RF-0014 — Consultar inventario de otra sucursal
- **Fuente:** E §2.1, p. 1 y §3.1, p. 2: «Consultar el inventario de cualquier otra sucursal de la red».
- **Actores / alcance:** usuarios autorizados; cualquier sucursal de la red.
- **Descripción:** el sistema deberá permitir consultar el inventario de una sucursal distinta de la propia.
- **Reglas y datos:** identificar sucursal, físico, disponible y tránsito separado (§5.3). Los tres roles consultan inventario autorizado de otras sucursales sin escritura cruzada; operador no accede a valoración/costos agregados. Gerente conserva dashboard/reportes propios; comparación de rendimiento de red solo administrador.
- **Dependencias:** RF-0003, RF-0007, RF-0015.
- **RF-0014-CA-01:** dadas dos sucursales con existencias distintas, cuando un usuario consulte la otra sucursal, entonces verá el inventario de esa sucursal y no el de la propia.
- **Límites / pendientes:** D, INC-003/D04 e INC-008. Consulta de existencias ajenas no habilita reportes o valoración ajenos restringidos.

#### RF-0015 — Compartir información de inventario
- **Fuente:** E §2–2.1, p. 1: «Comparte información de inventario [...] en tiempo real o near-real-time» y «coherencia de datos».
- **Actores / alcance:** sucursales de la organización.
- **Descripción:** el sistema deberá hacer visibles entre sucursales los cambios del inventario manteniendo coherencia de la información compartida.
- **Reglas y datos:** E no fija mecanismo ni latencia numérica; D, INC-004 aprueba plataforma conectada, actualización visible y aviso de desconexión/datos potencialmente desactualizados. Meta aprobada de máximo 5 segundos en condiciones normales de demostración; protocolo de medición/carga pendiente en RNF (§5.4).
- **Dependencias:** RF-0014, RF-0016, RF-0017, RF-0036.
- **RF-0015-CA-01:** dado un movimiento confirmado en origen, cuando otra sucursal consulte ese inventario tras el intervalo aprobado, entonces observará las existencias actualizadas que correspondan al movimiento.
- **RF-0015-CA-02:** dada pérdida de comunicación, cuando se muestren datos previamente consultados, entonces se advertirá su posible desactualización sin afirmar que una operación no aceptada quedó confirmada.
- **Límites / pendientes:** mecanismo de actualización por definir en arquitectura (consultas periódicas o eventos), sin cambiar REST ni imponer replicación; el umbral no se declara medido.

#### RF-0016 — Registrar ingresos
- **Fuente:** E §3.1, p. 2: «Registrar el ingreso de productos (compras, devoluciones, ajustes de inventario)»; §6.2, p. 5, operador. Precisión H, INC-003, registro §8: gerente autorizado para ajustes en su sucursal.
- **Actores / alcance:** operador y otros perfiles autorizados; gerente para ajustes de incremento solo en su propia sucursal activa.
- **Descripción:** el sistema deberá registrar ingresos de productos por compras, devoluciones y ajustes.
- **Reglas y datos:** datos completos de §5.3/RF-0018. Operador registra ingresos ordinarios y devoluciones; gerente solo ajustes/correcciones manuales propios, sin segundo aprobador automático. Devolución asociada a venta no supera lo vendido menos devoluciones previas y entra al costo histórico de esa salida; sin referencia exige motivo y, cuando corresponda, costo justificado. No implica reembolso ni anulación financiera. Compras y transferencias generan sus propios ingresos, sin duplicación manual.
- **Dependencias:** RF-0005, RF-0013, RF-0018; RF-0024 para compras.
- **RF-0016-CA-01:** dado cada uno de los motivos compra, devolución y ajuste, cuando se registre un ingreso válido, entonces las existencias de la sucursal aumentarán por la cantidad correspondiente a su unidad y el movimiento conservará los datos de RF-0018.
- **RF-0016-CA-02:** dado un gerente de la sucursal A activa y un ajuste de incremento válido, cuando lo registre en A, entonces aumentarán sus existencias por la cantidad correspondiente, sin cambiar las de otras sucursales, y se conservarán fecha, gerente responsable, motivo y cantidad en el historial.
- **RF-0016-CA-03:** dada una venta de 5 unidades con 2 ya devueltas, cuando se devuelvan otras 3, entonces se registrará el ingreso al costo histórico asociado; si se intentan devolver 4, se rechazará sin cambiar stock, valor ni historial de movimientos. Sin referencia, exigir motivo y costo justificado cuando corresponda.
- **Límites / pendientes:** D, INC-009 e INC-013/D06; valoración compensatoria según RF-0026 y H v0.13. No exige módulo independiente de devoluciones ni autoriza correcciones exclusivas de precio por inferencia.

#### RF-0017 — Registrar retiros
- **Fuente:** E §3.1, p. 2: «Registrar el retiro de productos (ventas, mermas, ajustes de inventario)»; §6.2, p. 5. Precisión H, INC-003, registro §8: gerente autorizado para ajustes en su sucursal.
- **Actores / alcance:** operador y otros perfiles autorizados; gerente para ajustes de disminución solo en su propia sucursal activa.
- **Descripción:** el sistema deberá registrar retiros por ventas, mermas y ajustes.
- **Reglas y datos:** operador registra retiros ordinarios/mermas; gerente ajustes/correcciones manuales propios. Registrar datos de RF-0018. Salidas ordinarias y ajuste por conteo sin origen identificado consumen promedio vigente; corrección de cantidad de una entrada identificada utiliza su costo histórico conforme a RF-0026/H v0.13. Validar indivisiblemente existencia suficiente; ninguna salida o compensación puede generar stock físico negativo. No hay reservas ni edición directa del saldo.
- **Dependencias:** RF-0005, RF-0013, RF-0018; RF-0027 y RF-0028 para ventas.
- **RF-0017-CA-01:** dado cada uno de los motivos venta, merma y ajuste, cuando se registre un retiro válido, entonces las existencias disminuirán por la cantidad correspondiente y el movimiento conservará los datos de RF-0018.
- **RF-0017-CA-02:** dado un gerente de la sucursal A activa y un ajuste de disminución válido, cuando lo registre en A, entonces disminuirán sus existencias por la cantidad correspondiente, sin cambiar las de otras sucursales, y se conservarán fecha, gerente responsable, motivo y cantidad en el historial.
- **RF-0017-CA-03:** dada una venta, merma, ajuste o corrección cuya salida exceda el físico disponible, cuando se intente confirmar, entonces se rechazará íntegramente sin movimiento, cambio de stock o valor ni documento confirmado de la operación fallida.
- **Límites / pendientes:** D, INC-008–INC-009 e INC-013/D04, precisados por H v0.13 para compensaciones referenciadas y diferencias de valoración. No confundir corrección de entrada con ajuste por conteo.

#### RF-0018 — Conservar historial auditable
- **Fuente:** E §3.1, p. 2: «Cada ingreso o retiro debe quedar registrado con fecha, responsable, motivo y cantidad, garantizando un historial auditable».
- **Actores / alcance:** todas las operaciones de ingreso/retiro; consulta por perfil autorizado.
- **Descripción:** el sistema deberá conservar un historial auditable completo de ingresos y retiros.
- **Reglas y datos:** fecha, responsable, motivo y cantidad son obligatorios; las operaciones automáticas no eliminan esta obligación. La desactivación de usuarios aprobada en H, INC-002, registro §8, conserva las referencias al responsable histórico conforme a RF-0002. Los ajustes locales del gerente aprobados en H, INC-003, también conservan esos cuatro datos; su permiso no habilita una edición de saldo sin movimiento.
- **Dependencias:** RF-0007, RF-0016, RF-0017; relacionada con RF-0024, RF-0027, RF-0036.
- **RF-0018-CA-01:** dados un ingreso y un retiro registrados, cuando se inspeccione el historial mediante la consulta autorizada, entonces cada movimiento permitirá comprobar fecha, responsable, motivo y cantidad correspondientes.
- **RF-0018-CA-02:** dados varios movimientos sucesivos, cuando se consulte su historial, entonces permanecerán identificables los movimientos anteriores y los nuevos; el saldo actual no sustituirá sus registros.
- **Reglas complementarias:** identificador público estable, clase, sucursal, producto, unidad, cantidad positiva, dirección, motivo, fecha/hora, responsable y referencia relacionada si existe. Movimientos confirmados inmutables; corrección por compensación nueva, vinculada, con autor/fecha/motivo. Afecta stock y valor al confirmar hoy, sin reabrir saldos pasados ni anular financieramente documentos. H v0.13 exige identificar por separado el componente al costo histórico y la diferencia de valoración adicional cuando se agote cantidad; esta diferencia no crea unidades ni movimiento físico de cantidad cero, ni se disfraza de redondeo.
- **RF-0018-CA-03:** dado un movimiento confirmado erróneo, cuando el gerente autorizado registre su compensación válida, entonces el original permanecerá intacto, la nueva operación quedará vinculada y los efectos se reconocerán en su fecha de confirmación; no se permitirá editar o eliminar el hecho original.
- **Límites / pendientes:** D, INC-006/D04 e INC-009/D01,D04–D05; PR-02 resuelta en RF-0026-CA-03–RF-0026-CA-05. Representación técnica de componentes de valoración pendiente de diseño, no de una nueva elección de costo. Sin auditoría ampliada de toda acción administrativa.

#### RF-0019 — Controlar stock mínimo
- **Fuente:** E §3.1, p. 2: «Controlar stock mínimo». Precisión del proyecto: H, resolución de INC-001 registrada en §8.
- **Actores / alcance:** administrador consulta/modifica mínimo por combinación producto-sucursal.
- **Descripción:** el sistema deberá controlar el inventario respecto al stock mínimo aplicable.
- **Reglas y datos:** D, INC-010/D01–D03: valor explícito por producto-sucursal antes de habilitar operación, incluido cero; no equiparar ausencia de configuración a cero. Umbral único de RF-0020/RF-0048: disponible <= mínimo; con mínimo cero alertar a saldo cero.
- **Dependencias:** RF-0001, RF-0013, RF-0016, RF-0017.
- **RF-0019-CA-01:** dado un mínimo y una regla de comparación aprobados, cuando se evalúen existencias a ambos lados del umbral y en igualdad, entonces el resultado coincidirá con dicha regla.
- **RF-0019-CA-02:** dado un mínimo modificado por el administrador, cuando se evalúe posteriormente el inventario para reabastecimiento y proximidad a agotarse, entonces ambas funciones utilizarán el nuevo valor aplicable, sin requerir actualizar otro parámetro.
- **RF-0019-CA-03:** dado mínimo 5, cuando el disponible sea 5 o menor, entonces se cumplirá la condición; con 6, no. Con mínimo cero se cumplirá a saldo cero; sin valor explícito no se habilitará la operación del producto en esa sucursal.
- **Límites / pendientes:** no implica compra automática ni cambio de existencias; representación/validación técnica se concretará sin inventar valores por defecto.

#### RF-0020 — Alertar reabastecimiento
- **Fuente:** E §3.1, p. 2: «generar alertas de reabastecimiento». Precisión del proyecto: H, resolución de INC-001 registrada en §8.
- **Actores / alcance:** destinatarios autorizados; producto y sucursal afectados.
- **Descripción:** el sistema deberá generar alertas cuando se cumpla la condición aprobada de reabastecimiento vinculada al stock mínimo.
- **Reglas y datos:** mismo mínimo y comparación de RF-0019. Aviso persistente en interfaz del gerente afectado y administrador; operador ve indicadores operacionales propios. Una alerta activa por producto-sucursal, actualizada tras cambios relevantes y resuelta cuando disponible > mínimo. Conservar historia de activación/resolución; sin correo ni SMS.
- **Dependencias:** RF-0019.
- **RF-0020-CA-01:** dado un producto que cumple la condición de comparación aprobada respecto al stock mínimo vigente, cuando se evalúe el reabastecimiento, entonces se generará una alerta identificable para el producto y la sucursal afectados utilizando ese mínimo, sin configurar otro umbral.
- **RF-0020-CA-02:** dado disponible 5 y mínimo 5, cuando se evalúe repetidamente, entonces existirá una sola alerta activa de ese producto-sucursal; al subir a 6 se resolverá y conservará su historia. No se notificará por correo/SMS ni se creará compra automática.
- **Límites / pendientes:** D, INC-010/D04–D06; alerta logística de faltante independiente del mínimo. Es alcance básico, no adicional seleccionado.

### 4.4. Compras

#### RF-0021 — Crear órdenes de compra
- **Fuente:** E §3.2, p. 2: «Crear [...] órdenes de compra a proveedores»; §6.2, p. 5, operador registra compras.
- **Actores / alcance:** operador registra compras de su sucursal; gerente supervisa propias y administrador consulta la red. Administrador mantiene proveedor de referencia.
- **Descripción:** el sistema deberá permitir crear órdenes de compra a proveedores.
- **Reglas y datos:** proveedor reutilizable mínimo con identidad estable, código único y nombre, creado/modificado por administrador. No exigir NIT, domicilio, correo, cuenta bancaria ni datos fiscales. Asociar proveedor, productos habilitados y condiciones de RF-0023; creación no incrementa inventario ni genera pagos. H v1.3/02: capturar el nombre vigente del proveedor al crear el borrador, actualizarlo con el nombre vigente al pasar a pendiente de recepción y conservarlo sin cambios desde ese momento, aunque luego se renombre al proveedor. El nombre histórico nunca es nulo.
- **Dependencias:** RF-0009, RF-0023.
- **RF-0021-CA-01:** dados un proveedor y productos identificados, cuando se cree una orden, entonces podrá consultarse con su proveedor, productos y condiciones registradas.
- **RF-0021-CA-02:** dado un proveedor con código único y nombre, cuando el administrador lo cree o modifique, entonces quedará disponible como referencia reutilizable sin exigir datos fiscales/contacto; una compra válida podrá asociarlo sin duplicar proveedor. Rechazar código duplicado sin alterar el catálogo.
- **RF-0021-CA-03:** dado un proveedor con nombre A, cuando se cree una orden en borrador, entonces su nombre histórico será A y no nulo; si el proveedor cambia a B antes de pasar la orden a pendiente de recepción, esa transición actualizará el nombre histórico a B; un cambio posterior a C no modificará B en la orden, tampoco al recibirla o cancelarla.
- **Límites / pendientes:** D, INC-011/D01 e INC-012/D01–D02. Sin gestión/evaluación avanzada de proveedores ni recepción automática.

#### RF-0022 — Gestionar órdenes de compra
- **Fuente:** E §3.2, p. 2: «gestionar órdenes de compra» y «ciclo completo de adquisición».
- **Actores / alcance:** operador gestiona órdenes de su sucursal; gerente supervisa propias y administrador consulta la red.
- **Descripción:** el sistema deberá permitir gestionar las órdenes durante su ciclo de adquisición, incluida su relación con la recepción.
- **Reglas y datos:** borrador → pendiente de recepción → recibida; cancelación antes de recibir. Solo editar borradores. Pendiente no se reescribe: cancelar y crear otra conservando ambas. Recibida no se borra. Sin aprobación de compra, tesorería, pagos ni recepción parcial.
- **Dependencias:** RF-0021, RF-0024.
- **RF-0022-CA-01:** dada una orden, cuando el operador ejecute una transición válida del ciclo aprobado, entonces se reflejará su estado y relación con recepción, si aplica; intentar editar una pendiente o borrar una recibida se rechazará sin alterar sus datos ni inventario.
- **RF-0022-CA-02:** dada una orden pendiente que necesita cambios, cuando se cancele y cree otra, entonces permanecerán consultables ambas con sus estados; cancelar antes de recibir no modificará stock ni promedio ponderado.
- **Límites / pendientes:** D, INC-011/D01–D03,D05; no inventar ciclo financiero.

#### RF-0023 — Registrar condiciones de compra
- **Fuente:** E §3.2, p. 2: «precio unitario, descuentos, plazo de pago».
- **Actores / alcance:** operador y perfiles autorizados; compra.
- **Descripción:** el sistema deberá registrar las condiciones comerciales de la compra.
- **Reglas y datos:** precio unitario bruto por unidad comercial, descuento porcentual por línea entre 0 y 100% (0 explícito), neto no negativo. Plazo entero no negativo en días desde recepción confirmada; conservar vencimiento calculado. COP, sin impuestos, fletes ni conversión de divisas, por decisión del proyecto. No implica pagos/saldos por pagar.
- **Dependencias:** RF-0021, RF-0013.
- **RF-0023-CA-01:** dadas condiciones de compra válidas, cuando se registren y se consulte la compra, entonces se conservarán el precio unitario, los descuentos y el plazo de pago introducidos.
- **RF-0023-CA-02:** dada una caja de 12 unidades base a precio 120 y descuento 10%, cuando se reciba, entonces el neto será 108 por caja y el costo 9 por unidad base; descuento fuera de 0–100% o plazo no entero/negativo se rechazará sin modificar compra ni inventario. Un plazo de cero días vencerá en la fecha de recepción confirmada.
- **Límites / pendientes:** D, INC-012/D03–D05 e INC-019/D01; conservar precisión de §5.4, sin órdenes de pago.

#### RF-0024 — Actualizar inventario al recibir compras
- **Fuente:** E §3.2, p. 2: «Actualizar automáticamente el inventario al confirmar la recepción de mercancía».
- **Actores / alcance:** operador de sucursal receptora activa.
- **Descripción:** el sistema deberá actualizar automáticamente el inventario cuando se confirme la recepción de una compra.
- **Reglas y datos:** confirmar recepción completa de todas las líneas vigentes de orden pendiente, registrando ingresos trazables y valoración una sola vez. Crear orden no sustituye recepción. Repetir petición de una recepción confirmada no incrementa nuevamente stock ni valor.
- **Dependencias:** RF-0021, RF-0016, RF-0018, RF-0026.
- **RF-0024-CA-01:** dada una compra recibida, cuando se confirme su recepción, entonces el inventario receptor aumentará por lo recibido y existirá el ingreso con fecha, responsable, motivo y cantidad, sin registrar manualmente otro ingreso equivalente.
- **RF-0024-CA-02:** dada una recepción de compra confirmada, cuando se repita su confirmación, entonces stock, valor y movimientos conservarán el resultado anterior sin ingresos duplicados. Intentar recepción parcial no confirmará ni modificará parcialmente la compra/inventario.
- **Límites / pendientes:** D, INC-011/D02,D04; contrato API decidirá respuesta existente o conflicto identificable, sin cambiar el resultado funcional.

#### RF-0025 — Consultar histórico de compras
- **Fuente:** E §3.2, p. 2: «Llevar histórico de compras por proveedor y por producto».
- **Actores / alcance:** perfiles autorizados; compras históricas.
- **Descripción:** el sistema deberá conservar y permitir consultar compras por proveedor y por producto.
- **Reglas y datos:** ambas perspectivas obligatorias; incluir e identificar borradores, pendientes, canceladas y recibidas. Estadísticas de compras realizadas distinguen órdenes de recepciones; no presentar una cancelada como recibida.
- **Dependencias:** RF-0021, RF-0023.
- **RF-0025-CA-01:** dadas compras de distintos proveedores y productos, cuando se consulte el histórico por un proveedor, entonces se identificarán sus compras; al consultarlo por un producto, se identificarán las compras de ese producto.
- **RF-0025-CA-02:** dadas órdenes de cada estado aprobado, cuando se consulte por proveedor o producto, entonces se distinguirán sus estados y recepciones sin confundir órdenes con compras recibidas.
- **Límites / pendientes:** D, INC-011/D06 e INC-003; gerente propio, administrador red, operador registros operacionales propios.

#### RF-0026 — Calcular costo promedio ponderado
- **Fuente:** E §3.2, p. 2: «Calcular el costo promedio ponderado del inventario».
- **Actores / alcance:** sistema calcula por producto-sucursal; consulta de valoración por gerente propio/administrador, no operador.
- **Descripción:** el sistema deberá calcular el costo promedio ponderado del inventario.
- **Reglas y datos:** D, INC-013/D01–D07: promedio ponderado móvil por producto-sucursal al confirmar entradas valoradas. Costo base de compra = precio bruto comercial × (1 − descuento) ÷ factor de unidades base. Con stock previo positivo, nuevo promedio = (valor previo registrado del inventario + cantidad entrada × costo entrada) ÷ cantidad resultante; conservar el valor previo con sus diferencias de redondeo, sin reconstruirlo desde un promedio ya redondeado que las pierda. Sin diferencias de redondeo, el valor previo equivale a cantidad previa × promedio previo. Con stock previo cero adoptar costo de recepción. Decimales exactos; promedio a 6 decimales, importes mostrados a 2 y mitad hacia arriba (§5.4).
- **Reglas de salidas/entradas:** ventas, mermas, ajustes por conteo sin origen identificado y despachos consumen promedio vigente sin cambiarlo por la mera salida, con la precisión de última salida aprobada debajo. Transferencia congela costo origen al despacho para tránsito, recepción y pérdida reconocida; compras posteriores en origen no lo modifican. Devolución de venta entra a costo histórico de salida; entrada sin referencia usa promedio actual o costo explícitamente justificado si no hay base. Correcciones actuales vinculadas según H v0.13, sin recalcular historia ni stock negativo; no aplicarles automáticamente el costo del ajuste por conteo.
- **Residual al agotarse (H v0.12):** cuando una salida ordinaria válida deje cantidad cero de un producto en una sucursal, su valoración consumirá el valor restante, dejando también valor cero. Identificar la diferencia entre ese valor y cantidad saliente × promedio unitario redondeado como diferencia de redondeo atribuible a la salida final, no como unidades adicionales, merma ni edición de movimientos pasados. No modificar el promedio unitario ni los precios, descuentos o importes de venta por esta absorción; en transferencias conservar el costo unitario congelado y la trazabilidad separada de la diferencia de valoración, sin perder ni duplicar valor. No ocultar inconsistencias ajenas al redondeo: compensaciones históricas siguen la regla específica H v0.13.
- **Compensación referenciada (H v0.13):** corregir cantidades de una entrada identificada a su costo unitario histórico, mediante movimiento actual vinculado al original; no reescribir entrada ni ventas/compras posteriores. Si queda cantidad positiva, recalcular el promedio actual desde valor y cantidad corregidos, sin recalcular promedios pasados. Un ajuste por conteo sin origen identificado se valora al promedio vigente, no inventa una referencia histórica.
- **Compensación que agota cantidad (H v0.13):** si descontar las unidades corregidas a costo histórico deja cantidad cero y valor remanente, registrar además una diferencia de valoración separadamente identificable, vinculada a la compensación, que deje valor cero. Distinguir costo histórico, diferencia adicional y efecto total; no presentarla como redondeo, merma automática ni cambio del precio/importe de venta. Es corrección de valoración actual, no contabilidad financiera nueva ni edición retroactiva. El tratamiento del redondeo de salidas ordinarias de H v0.12 sigue vigente; esta regla específica resuelve su interacción con compensaciones históricas.
- **Dependencias:** RF-0013, RF-0023, RF-0024.
- **RF-0026-CA-01:** dadas 10 unidades a costo 100 y entrada confirmada de 5 a costo neto 130, cuando se valore, entonces habrá 15 a promedio 110; salida de 3 dejará 12 a promedio 110. Una compra cancelada no altera el resultado.
- **RF-0026-CA-02:** dado despacho de 10 a costo congelado 9 y recepción de 7, cuando se valore el traslado, entonces destino recibirá valor 63 y las 3 pendientes conservarán valor 27 en tránsito; una nueva compra en origen no cambiará esos valores. Al recibir con stock destino previo cero, se adoptará costo 9.
- **RF-0026-CA-03:** dadas 3 unidades con valor total 10 y promedio unitario redondeado 3,333333, cuando salgan dos unidades por separado, entonces quedarán 1 unidad y valor 3,333334. La última salida consumirá 3,333334, con diferencia de redondeo 0,000001 identificada, y dejará cantidad y valor cero sin cambiar las valoraciones previas. Si cada venta usa precio 12 sin descuento, sus importes seguirán siendo 12 por unidad; el residual no cambia el precio ni lo cobrado. Con división exacta, el agotamiento no genera una diferencia artificial.
- **RF-0026-CA-04:** dada una entrada registrada de 10 unidades a costo 10 aunque llegaron 8, venta posterior de 6 y nueva compra de 4 a costo 20, cuando se consulte antes de corregir, entonces habrá saldo registrado de 8 unidades/valor 120/promedio 15. Al corregir las 2 unidades de más de aquella entrada, registrar compensación actual vinculada de -2 unidades y -20 de valor histórico: saldo 6/100 y nuevo promedio 16,666667. Mantener intactas las operaciones previas y sus importes de venta. En un caso distinto con el mismo saldo inicial 8/120, un ajuste por conteo sin origen identificado de -2 se valorará a 15: saldo 6/90 y promedio 15; no se tratarán como casos equivalentes.
- **RF-0026-CA-05:** dado el mismo error de entrada y que después del saldo 8/120 se vendieron otras 6 al costo vigente 15, cuando se corrijan las 2 unidades ficticias restantes, entonces desglosar -20 de valor histórico y -10 de diferencia de valoración vinculada: saldo final 0 unidades/valor 0, sin tocar las ventas ni llamar redondeo a los 10 adicionales. Si una corrección intenta retirar más unidades que las disponibles, rechazarla íntegramente conforme a RF-0017-CA-03, sin modificar stock, valor ni historial.
- **Límites / pendientes:** PR-02 resuelta en el alcance S4–S6 de H: residuales, correcciones de cantidades de entrada identificada, ajustes por conteo y diferencia al agotarse. No generalizar a una funcionalidad de corrección exclusiva de precio sin cantidad ni a casos no especificados. La representación técnica de valor/diferencia corresponde al diseño; si un caso no puede cumplir las reglas ratificadas, registrar la brecha antes de implementar, como exige D §5, sin borrarlo silenciosamente.

### 4.5. Ventas

#### RF-0027 — Registrar ventas
- **Fuente:** E §3.3, p. 2: «producto, cantidad y precio» y «sucursal, fecha y responsable»; §3.1, p. 2, retiros por venta; §6.2, p. 5.
- **Actores / alcance:** operador confirma ventas de su sucursal; gerente supervisa propias y administrador consulta la red.
- **Descripción:** el sistema deberá registrar las ventas y su salida de inventario asociada.
- **Reglas y datos:** producto, cantidad, unidad/factor, precio/lista, descuento, sucursal, fecha y responsable; solo confirmación válida descuenta stock y genera comprobante interno. Borrador o intento fallido no produce movimiento. Evitar doble confirmación; no cambiar precio libremente fuera del descuento registrado. Devolución física no reembolsa ni anula financieramente venta.
- **Dependencias:** RF-0017, RF-0018, RF-0028–RF-0031.
- **RF-0027-CA-01:** dada una venta con disponibilidad validada, cuando se registre, entonces conservará los seis datos exigidos y las existencias de la sucursal disminuirán por la cantidad vendida, con su retiro auditable.
- **RF-0027-CA-02:** dada una venta confirmada, cuando se repita su confirmación, entonces no se duplicarán retiros ni comprobantes. Un borrador o intento fallido no tendrá efectos de venta confirmada; una devolución conservará la venta original y su vínculo, sin reembolso automático.
- **Límites / pendientes:** D, INC-014/D01,D03,D05. Sin cobro electrónico ni factura fiscal.

#### RF-0028 — Validar stock antes de vender
- **Fuente:** E §3.3, p. 2: «Validar disponibilidad de stock antes de confirmar la venta».
- **Actores / alcance:** sistema y responsable de venta; sucursal vendedora.
- **Descripción:** el sistema deberá validar disponibilidad antes de confirmar una venta y no confirmarla si no dispone del stock requerido.
- **Reglas y datos:** disponible físico sin reservas; validar y descontar indivisiblemente al confirmar, no solo en pantalla. Si no cubre todas las cantidades, rechazo total sin venta parcial implícita, retiro ni comprobante.
- **Dependencias:** RF-0013, RF-0027.
- **RF-0028-CA-01:** dada disponibilidad suficiente según la regla aprobada, cuando se confirme la venta, entonces la comprobación permitirá continuar con su registro.
- **RF-0028-CA-02:** dada disponibilidad insuficiente, cuando se solicite confirmar la venta, entonces no quedará confirmada ni producirá el retiro de la venta rechazada.
- **RF-0028-CA-03:** dadas 3 unidades y dos confirmaciones simultáneas de 2 unidades cada una, cuando se resuelvan, entonces solo una podrá confirmarse; quedará 1 unidad y la rechazada no producirá comprobante, retiro ni efectos parciales.
- **Límites / pendientes:** D, INC-008/D01–D03 e INC-014/D04; mecanismo de concurrencia pendiente de diseño, resultado funcional ya aprobado.

#### RF-0029 — Aplicar descuentos de venta
- **Fuente:** E §3.3, p. 2: «Aplicar descuentos».
- **Actores / alcance:** operador aplica descuento al registrar venta propia.
- **Descripción:** el sistema deberá permitir aplicar descuentos a las ventas.
- **Reglas y datos:** descuento porcentual por línea entre 0 y 100%, capturado por operador. Sin descuento por cabecera, promociones ni acumulación de descuentos. Precio de lista y descuento quedan identificables en registro confirmado.
- **Dependencias:** RF-0027, RF-0030.
- **RF-0029-CA-01:** dados un precio y descuento aprobados como ejemplo de INC-014, cuando se aplique el descuento, entonces la venta reflejará el precio resultante esperado y el descuento aplicado.
- **RF-0029-CA-02:** dada una línea a precio 100, cuando se aplique 10%, entonces su neto unitario será 90; 0% mantiene 100 y 100% produce cero. Fuera de 0–100% se rechazará sin confirmar ni alterar stock; no se acumulará otro descuento de cabecera.
- **Límites / pendientes:** D, INC-014/D02; precisión monetaria §5.4.

#### RF-0030 — Gestionar listas de precios
- **Fuente:** E §3.3, p. 2: «gestionar diferentes listas de precios».
- **Actores / alcance:** administrador mantiene listas globales; operador elige al confirmar venta; gerente/operador consultan y usan en acciones permitidas.
- **Descripción:** el sistema deberá gestionar diferentes listas de precios para ventas.
- **Reglas y datos:** múltiples listas globales, precio por producto y unidad; seleccionar lista al confirmar. Conservar precio/lista en documento histórico, sin cambios libres al precio fuera del descuento registrado.
- **Dependencias:** RF-0009, RF-0027.
- **RF-0030-CA-01:** dadas dos listas con precios distintos para un producto, cuando se consulte cada lista, entonces se conservará su precio correspondiente; al aplicar la lista según la política aprobada, la venta tomará el precio que corresponda.
- **Límites / pendientes:** D, INC-003/D06 e INC-014/D01; sin asignación automática por cliente/sucursal ni gestión de clientes por inferencia.

#### RF-0031 — Consultar comprobante o registro de venta
- **Fuente:** E §3.3, p. 2: «Generar comprobantes o registros de venta para consulta posterior».
- **Actores / alcance:** perfiles autorizados; ventas registradas.
- **Descripción:** el sistema deberá generar un comprobante o registro que permita consultar posteriormente cada venta.
- **Reglas y datos:** registro interno consultable e inmutable por venta confirmada: identificador público, fecha, sucursal, responsable, líneas con SKU/unidad/cantidad, lista/precio, descuentos y total. Preservar instantáneas comerciales; identidad estable no sustituye autorización.
- **Dependencias:** RF-0027.
- **RF-0031-CA-01:** dada una venta registrada, cuando se consulte posteriormente su comprobante o registro, entonces se recuperarán producto, cantidad, precio, sucursal, fecha y responsable correspondientes.
- **RF-0031-CA-02:** dada una venta confirmada y cambios posteriores de catálogo/precios, cuando se consulte su registro, entonces conservará todos los datos comerciales originales y total; no se editará el comprobante ni se generará para intentos rechazados.
- **Límites / pendientes:** D, INC-014/D06; sin documento fiscal o impresión obligatoria. Exportación adicional separada en RF-0053.

### 4.6. Transferencias

#### RF-0032 — Solicitar transferencias
- **Fuente:** E §2.1, p. 1; §3.4 paso 1, p. 2: «la sucursal destino (o un administrador) [...] producto, cantidad y origen»; §6.2, p. 5, operador solicita.
- **Actores / alcance:** operador de destino; origen y destino. El administrador general no solicita transferencias (H v0.16).
- **Descripción:** el sistema deberá permitir solicitar formalmente productos desde una sucursal origen hacia una destino.
- **Reglas y datos:** varias líneas con un mismo origen/destino activos y diferentes; producto/unidad y cantidad en cada línea. Una línea por producto/unidad o normalización de repeticiones preservando cantidades de cada etapa. Productos habilitados donde operen y unidades compatibles. Solicitud no reserva ni modifica stock; urgencia excluida de primera versión por D, INC-019/D03. H v0.16: la registra el operador del destino; se elige la alternativa «sucursal destino» de E §3.4 paso 1 y se excluye «o un administrador» como decisión del proyecto. El administrador no la crea ni la sustituye.
- **Dependencias:** RF-0003, RF-0009, RF-0013, RF-0014.
- **RF-0032-CA-01:** dados producto, cantidad, origen y destino, cuando el operador de destino registre la solicitud, entonces podrá consultarse la solicitud con esos datos.
- **RF-0032-CA-02:** dados origen/destino iguales o inactivos, producto no habilitado donde deba operar o unidad incompatible, cuando se intente solicitar, entonces se rechazará sin crear la transferencia ni modificar stock. Una solicitud multilínea válida conservará las cantidades solicitadas sin reservarlas. Un intento del administrador general se rechazará sin crear la transferencia ni modificar stock.
- **Límites / pendientes:** D, INC-015/D01–D02,D09; no pedidos divididos en varios despachos. H v0.16 acota el actor solicitante al operador del destino; E §3.4 admite también al administrador como alternativa no elegida y ello se registra como decisión del proyecto.

#### RF-0033 — Preparar cantidades a enviar
- **Fuente:** E §3.4 paso 2, p. 3: «revisa disponibilidad y confirma o ajusta la cantidad a enviar».
- **Actores / alcance:** operador origen prepara; gerente origen revisa/supervisa y aprueba.
- **Descripción:** el sistema deberá permitir al origen revisar disponibilidad y confirmar o ajustar la cantidad del envío.
- **Reglas y datos:** origen puede proponer menos, visible al destino; no enviar más de lo pedido sin nueva solicitud. Conservar solicitado/preparado/despachado. Preparar no reserva ni descuenta; revalidar todas las líneas antes de despachar; no despachar si todas quedan en cero.
- **Dependencias:** RF-0032, RF-0014.
- **RF-0033-CA-01:** dada una solicitud, cuando origen prepare el envío, entonces podrá revisar disponibilidad y registrar la cantidad confirmada.
- **RF-0033-CA-02:** dada una cantidad solicitada distinta de la preparada, cuando origen ajuste la cantidad, entonces se podrán distinguir la solicitada y la que se enviará.
- **Límites / pendientes:** D, INC-015/D03,D05; conservar cantidades de cada etapa sin reservar inventario ni reabrir recepción ya confirmada.

#### RF-0034 — Aprobar transferencias
- **Fuente:** E §6.2, p. 5: gerente «aprueba transferencias».
- **Actores / alcance:** gerente de sucursal origen, tras preparar/revisar y antes de despachar. Solo este gerente puede rechazar una transferencia de su origen, con motivo obligatorio, únicamente desde «en preparación» y antes de aprobarla (H v1.2/01).
- **Descripción:** el sistema deberá permitir al gerente origen aprobar transferencias preparadas de su sucursal o rechazarlas con motivo dentro del estado autorizado.
- **Reglas y datos:** administrador no recibe aprobación ni rechazo por herencia de rol. H v1.2/01 precisa «Origen rechaza con motivo»: exclusivamente el gerente de origen, desde «en preparación» hacia «rechazada», conservando motivo y responsable, sin modificar stock, tránsito ni cantidades. No se permite rechazar desde solicitada, aprobada, en tránsito u otro estado. Preparación y despacho permanecen a cargo del operador de origen; no se trasladan al gerente. Destino cancela antes de aprobación; aprobada y aún no despachada, solo gerente origen cancela justificadamente. No cancelación automática en tránsito. Rechazo de negocio, cancelación y rechazo de un intento inválido son situaciones distintas.
- **Dependencias:** RF-0007, RF-0032, RF-0033.
- **RF-0034-CA-01:** dada una transferencia elegible y un gerente autorizado según INC-015, cuando la apruebe, entonces su aprobación quedará asociada a esa transferencia y será consultable.
- **RF-0034-CA-02:** dada transferencia antes de aprobar, cuando destino la cancele, entonces quedará cancelada sin modificar stock. Tras aprobar y antes de despachar, solo gerente origen podrá cancelar con motivo; intento de otro rol o cancelación en tránsito se rechazará sin alterar transferencia ni inventario.
- **RF-0034-CA-03:** dada una transferencia «en preparación» de su sucursal origen, cuando el gerente de origen la rechace con motivo, entonces quedará «rechazada» con motivo y responsable consultables, sin aprobación, despacho ni cambios de cantidades, stock o tránsito.
- **RF-0034-CA-04:** dado un intento de rechazo por operador, administrador, gerente de otra sucursal, sin motivo o desde un estado distinto de «en preparación» —incluidos solicitada, aprobada y en tránsito—, cuando se intente confirmar el rechazo, entonces se denegará sin modificar la transferencia ni el inventario. El gerente de destino no obtiene el permiso por supervisar la recepción.
- **Límites / pendientes:** D, INC-015/D01,D06; rechazo motivado no equivale a pérdida ni recepción. Administrador no recibe permisos adicionales de cancelación por herencia; solo los actores/ámbitos expresamente autorizados.

#### RF-0035 — Registrar despacho
- **Fuente:** E §3.4 paso 3, p. 3: «se registra el despacho con fecha estimada de llegada y transportista».
- **Actores / alcance:** operador de origen tras aprobación del gerente origen.
- **Descripción:** el sistema deberá registrar el envío de una transferencia.
- **Reglas y datos:** transportista como nombre/identificador textual, salida real y llegada estimada obligatorios. Confirmación indivisible de todas las líneas, revalidando stock, productos/unidades y sucursales activas/distintas. Descontar físico origen y aumentar tránsito a costo congelado origen; sin alta física destino. Reintentos no duplican salidas/tránsito; fallo no produce despacho parcial implícito.
- **Dependencias:** RF-0033, RF-0034, RF-0040, RF-0042.
- **RF-0035-CA-01:** dada una transferencia preparada conforme al flujo aprobado, cuando se registre su despacho, entonces se podrán consultar su cantidad enviada, fecha estimada de llegada y transportista.
- **RF-0035-CA-02:** dada una transferencia multilínea aprobada, cuando una línea carezca de stock al despachar, entonces se rechazará todo sin salida ni tránsito. Con datos/stock válidos, se descontará origen una sola vez; repetir confirmación no duplicará movimientos y destino seguirá sin ingreso hasta recepción.
- **Límites / pendientes:** D, INC-015/D05,D07–D09 e INC-016/D01; sin CRUD de transportistas ni seguimiento externo.

#### RF-0036 — Confirmar recepción completa
- **Fuente:** E §3.4 paso 4, p. 3: «el inventario de la sucursal destino se actualiza automáticamente».
- **Actores / alcance:** operador destino registra; gerente destino supervisa.
- **Descripción:** el sistema deberá permitir confirmar la recepción completa y actualizar automáticamente el inventario de destino.
- **Reglas y datos:** H v0.11: una única confirmación de llegada por despacho. Es completa si lo recibido coincide con lo despachado en todas las líneas; registra el ingreso físico destino y reduce tránsito al costo congelado del despacho una sola vez. Repetir técnicamente la misma confirmación no genera nuevos movimientos, stock ni valor, ni cambia el estado confirmado. No confundirlo con otro envío físico, que requiere nueva transferencia y despacho.
- **Dependencias:** RF-0035, RF-0016, RF-0018, RF-0042.
- **RF-0036-CA-01:** dada una transferencia enviada y recibida completa, cuando se confirme la recepción, entonces destino aumentará sus existencias por la cantidad recibida y quedará registrado el ingreso con fecha, responsable, motivo y cantidad, sin otro ingreso manual equivalente.
- **RF-0036-CA-02:** dado un despacho de 10 unidades a costo 9 recibido completo y confirmado, cuando se repita técnicamente esa confirmación, entonces destino conservará el único ingreso de 10 y valor 90, tránsito pendiente cero y estado de recepción completa; no habrá otro ingreso ni cambio de valor/estado. Este criterio sustituye el ejemplo anterior de recepción complementaria por corrección humana H v0.11, conservando su identificador.
- **Límites / pendientes:** D, INC-016/D01,D08, con sustitución de D03 por H v0.11. PR-01 resuelta funcionalmente; mecanismo de unicidad/respuesta API a repetición pendiente de diseño, sin cambiar su efecto ni admitir recepciones sucesivas.

#### RF-0037 — Registrar recepción parcial y faltantes
- **Fuente:** E §3.4 paso 5, p. 3: «Confirmación de recepción parcial: se registra la diferencia (faltantes)».
- **Actores / alcance:** responsable de recepción en destino.
- **Descripción:** el sistema deberá registrar la recepción parcial y la diferencia respecto a lo enviado.
- **Reglas y datos:** H v0.11, v0.14 y v1.1 sustituyen las recepciones sucesivas del mismo despacho. Confirmar la llegada una sola vez e ingresar solo lo recibido; distinguir despachado, recibido y diferencia. Con faltante, incluida una llegada confirmada de cero unidades, la transferencia queda **«Recibida con discrepancia»** y la recepción física finaliza, sin complemento del mismo despacho. La llegada de cero unidades no crea movimiento físico de cantidad cero ni incrementa el destino; registra faltante total y genera la alerta vinculada. H v0.14 mantiene ese estado: movimientos de regularización se registran manualmente con autorización/trazabilidad, separados de la alerta. «Resuelto» no modifica cantidades, valor, tránsito ni estado, y el faltante histórico sigue visible. No es merma automática. Reintentos no duplican efectos; rechazar sobrantes antes de confirmar y corregir errores por compensación vinculada, no editando original ni simulando nueva entrega. Costo congelado del despacho.
- **Dependencias:** RF-0035, RF-0038, RF-0039.
- **RF-0037-CA-01:** dada una cantidad enviada de 10 unidades y una recepción de 7 de la misma unidad, cuando se confirme la recepción parcial, entonces quedarán identificadas 7 recibidas y 3 faltantes, vinculadas al envío.
- **RF-0037-CA-02:** dadas 10 despachadas a costo 9 y 7 recibidas, cuando se confirme, entonces físico origen habrá bajado 10, destino subirá 7 y tránsito conservará 3 con valor 27; quedará discrepancia abierta sin merma ni reposición automática. Intentar recibir 4 adicionales se rechazará sin cambiar stock, valor ni recepciones.
- **RF-0037-CA-03:** dadas 10 despachadas y 7 recibidas confirmadas, cuando se consulte la transferencia, entonces mostrará «Recibida con discrepancia», 7 recibidas y 3 faltantes. Repetir técnicamente la confirmación mantendrá stock, valor, movimientos, diferencia y estado sin duplicación. Intentar una segunda recepción, incluso de solo 1 o 3 unidades sin superar las 10 enviadas, se rechazará sin modificar esos datos; otra entrega exige nueva transferencia y despacho.
- **RF-0037-CA-04:** dadas 10 unidades despachadas y una llegada confirmada de cero unidades, cuando el operador de destino la registre, entonces no se creará movimiento físico ni se incrementará el stock del destino; la transferencia quedará «Recibida con discrepancia» con 0 recibidas y 10 faltantes, tránsito/valor pendientes y una alerta vinculada. Una segunda recepción del mismo despacho se rechazará.
- **Límites / pendientes:** D, INC-016/D02,D04–D05,D09, precisadas por H v0.11/v0.14 y H v1.1. La llegada cero queda aprobada como subcaso de recepción parcial, sin movimiento físico de cantidad cero; PR-01 y PR-03 conservan su alcance.

#### RF-0038 — Alertar faltantes
- **Fuente:** E §3.4 paso 5, p. 3: «se genera una alerta».
- **Actores / alcance:** gerentes de origen/destino gestionan discrepancia, administrador supervisa; operador consulta registro operacional propio.
- **Descripción:** el sistema deberá generar una alerta al registrar faltantes en una recepción parcial.
- **Reglas y datos:** alerta persistente en sección de alertas, enlazada a transferencia/faltante e independiente del mínimo. H v0.14: gerentes de origen o destino de la transferencia pueden usar **«Resuelto»**, seleccionando reenvío, ajuste o reclamación; conservar tratamiento, responsable y fecha de resolución, y permitir consulta de alertas resueltas. No exigir nuevas notas obligatorias ni circuito de aprobación en el botón. Marcar resuelta no modifica saldos físicos/de tránsito, valor, movimientos, cantidades ni estado de transferencia; tampoco ejecuta el tratamiento seleccionado ni certifica que el tránsito ya se regularizó. Administrador supervisa y operador consulta según §5.2. No confundir con alertas de stock mínimo, que conservan su resolución automática por umbral (RF-0020).
- **Dependencias:** RF-0037.
- **RF-0038-CA-01:** dada una recepción parcial registrada, cuando se confirmen sus faltantes, entonces se generará una alerta vinculada a la transferencia que permita identificar la diferencia.
- **RF-0038-CA-02:** dada alerta por 10 unidades enviadas y 7 recibidas, cuando un gerente de origen/destino autorizado seleccione ajuste, reenvío o reclamación y pulse «Resuelto», entonces la alerta quedará resuelta con tratamiento, usuario y fecha consultables; la transferencia seguirá «Recibida con discrepancia», con las mismas cantidades, valor, movimientos y saldos físicos/de tránsito anteriores. Sin seleccionar tratamiento, rechazar la resolución sin cambiar alerta ni datos; otro perfil no autorizado no podrá resolverla.
- **Límites / pendientes:** H v0.14 simplifica D, INC-016; sin flujo integrado de ejecución/cierre ni correo/SMS. Alcance básico, no adicional; el botón no se extiende a alertas de reabastecimiento.

#### RF-0039 — Definir tratamiento del faltante
- **Fuente:** E §3.4 paso 5, p. 3: «se define el tratamiento (reenvío, ajuste o reclamación)».
- **Actores / alcance:** gerentes origen/destino gestionan; gerente origen autoriza baja de valor en tránsito, gerente destino valida recibido; administrador supervisa, sin editar stock ajeno.
- **Descripción:** el sistema deberá permitir dejar definido el tratamiento de los faltantes.
- **Reglas y datos:** H v0.14 registra el tratamiento mediante la selección mínima al resolver la alerta de RF-0038, sin ejecutar movimientos automáticamente ni cambiar estado de la transferencia. Personas autorizadas registran aparte los movimientos que correspondan, con motivo, responsable y referencia al traslado; sin edición directa de saldos. Nueva entrega/reposición requiere otra transferencia y despacho, nunca segunda recepción del original. Conservar trazabilidad del faltante original para evitar duplicaciones. Si se reconoce pérdida, registrar manualmente su baja de tránsito y valor una sola vez al costo congelado, con autorización/motivo/evidencia; no volver a descontar físico origen ni sumar unidades ficticias a destino. Seleccionar reclamación solo documenta ese tratamiento: no ingresa mercancía, liquida valor o acredita resolución externa. No se incorpora un ciclo de reclamaciones ni una ejecución automática de reenvío/ajuste desde la alerta.
- **Dependencias:** RF-0037.
- **RF-0039-CA-01:** dado un faltante, cuando el responsable defina reenvío, ajuste o reclamación conforme a la política aprobada, entonces podrá consultarse el tratamiento asociado a ese faltante.
- **RF-0039-CA-02:** dado un faltante en tránsito y una alerta marcada resuelta con tratamiento reclamación, cuando se consulte inventario/historial, entonces seguirá el mismo saldo pendiente sin ingreso ni baja por esa marca. Al registrar aparte pérdida autorizada con evidencia, reducir tránsito/valor una sola vez, sin alterar físico origen/destino ni estado «Recibida con discrepancia». Reenvío usa nueva transferencia/despacho con referencia al faltante para trazabilidad, nunca otra recepción del original o reposición duplicada.
- **Límites / pendientes:** H v0.14 sustituye el flujo de cierre anterior de D, INC-016/D05–D07: selección y resolución de alerta simples, movimientos separados. Conservar costos congelados y compensaciones H v0.13. Sin estado adicional de cierre, nuevas recepciones del mismo despacho ni módulo jurídico/financiero de reclamaciones.

### 4.7. Logística

#### RF-0040 — Registrar y comparar tiempos
- **Fuente:** E §3.5, p. 3: «Registrar y consultar tiempos estimados vs. tiempos reales de entrega».
- **Actores / alcance:** responsables de logística y consulta autorizados; traslados.
- **Descripción:** el sistema deberá registrar y permitir comparar tiempos estimados y reales de entrega.
- **Reglas y datos:** H v0.15: tiempo real = salida real del despacho → única llegada registrada al confirmar recepción; indicar si es completa o «Recibida con discrepancia». Una llegada parcial tiene duración propia de entrega parcial, no se presenta como completa. Sin recepción confirmada, no inventar llegada/duración final. No medir tiempo de cierre administrativo adicional ni usar fecha de «Resuelto» como llegada. Llegada estimada ingresada al despachar, sin tiempos típicos automáticos de ruta. America/Bogota visible.
- **Dependencias:** RF-0035–RF-0037.
- **RF-0040-CA-01:** dado un traslado con estimación y entrega real registradas, cuando se consulte, entonces se mostrarán ambos valores asociados al mismo traslado para compararlos.
- **RF-0040-CA-02:** dado despacho a las 10:00 y única recepción parcial confirmada a las 12:00 del mismo día, cuando se consulte, entonces mostrará 2 horas hasta llegada con «Recibida con discrepancia», no entrega completa. Resolver su alerta a las 15:00 no cambiará esas 2 horas ni creará duración de cierre administrativo o recepción posterior.
- **Límites / pendientes:** H v0.15 sustituye la medición administrativa anterior de D, INC-017/D03 y conserva estimación/zona. PR-04 resuelta; sin nuevo estado o circuito de cierre.

#### RF-0041 — Clasificar rutas
- **Fuente:** E §3.5, p. 3: «Clasificar rutas por prioridad, costo o tiempo».
- **Actores / alcance:** administrador mantiene rutas compartidas; gerente/operador consultan/usan según permisos.
- **Descripción:** el sistema deberá permitir clasificar rutas por prioridad manual alta, media o baja.
- **Reglas y datos:** ruta dirigida origen–destino con identificador estable y clasificación. Se elige prioridad entre alternativas de E, no tres criterios simultáneos. Tiempo se mide por separado; sin tarifas, escalas, mapas, tracking ni optimización.
- **Dependencias:** RF-0003, RF-0040.
- **RF-0041-CA-01:** dadas rutas y un criterio aprobado, cuando se clasifiquen, entonces su clasificación corresponderá a los valores de dicho criterio.
- **Límites / pendientes:** D, INC-017/D01–D02 e INC-003/D06; identidad pública según §5.3 cuando sea recurso individual.

#### RF-0042 — Consultar estados de transferencias
- **Fuente:** E §3.5, p. 3: «en preparación, en tránsito, recibido, con faltantes».
- **Actores / alcance:** usuarios autorizados; transferencias de su ámbito.
- **Descripción:** el sistema deberá mostrar el estado de las transferencias conforme a su situación operativa.
- **Reglas y datos:** estados vigentes: solicitada, en preparación, aprobada, en tránsito, recibida completa, **«Recibida con discrepancia»**, cancelada/rechazada. H v0.14 elimina el estado adicional de cierre con diferencia tratada: resolver la alerta o registrar movimientos aparte no cambia «Recibida con discrepancia». Conservar cantidades históricas, relación con eventos y tratamiento/estado de alerta por separado. No admite más recepciones del mismo despacho. §5.1 valida impedimentos de baja por inventario pendiente, no solo por etiqueta de alerta o estado histórico del traslado.
- **Dependencias:** RF-0032–RF-0037.
- **RF-0042-CA-01:** dadas transferencias en preparación, en tránsito, recibidas completas y recibidas con faltantes, cuando se consulten, entonces cada una mostrará el estado correspondiente a los eventos; una recepción parcial confirmada se mostrará como «Recibida con discrepancia», no como entrega completa ni como despacho abierto a otra recepción.
- **RF-0042-CA-02:** dada una transferencia «Recibida con discrepancia», cuando se resuelva su alerta con tratamiento y se registren aparte movimientos válidos, entonces su estado y cantidades históricas seguirán siendo los de la llegada confirmada; alerta resuelta y regularización de saldos se consultarán por sus propios registros, sin convertirla en recibida completa ni en otro estado de cierre.
- **Límites / pendientes:** D, INC-015/D04 e INC-016, sustituidos en el alcance de cierre por H v0.14. PR-03 resuelta; no crear un ciclo de reclamaciones ni recepciones sucesivas.

#### RF-0043 — Reportar cumplimiento logístico
- **Fuente:** E §3.5, p. 3: «Generar reportes de cumplimiento logístico por sucursal y por ruta»; §6.2, p. 5, gerente consulta reportes.
- **Actores / alcance:** gerente y perfiles autorizados; sucursales y rutas.
- **Descripción:** el sistema deberá generar reportes de cumplimiento logístico por sucursal y por ruta.
- **Reglas y datos:** H v0.15 sustituye el denominador anterior de D, INC-017/D04: KPI = recepciones completas puntuales / total de transferencias con llegada confirmada en el período. Puntual exige recepción completa no posterior a la llegada estimada fijada al despachar; recibidas con discrepancia cuentan en denominador como no cumplidas, aunque alerta esté resuelta o el tratamiento registrado. Ubicar la transferencia en el período por fecha/hora de confirmación de su única recepción, en America/Bogota. Vencidas aún en tránsito se muestran aparte y no integran ese denominador mientras no haya llegada confirmada. Sin llegadas, «No calculable». Conservar apartados origen/destino/ruta y permisos §5.2; «Resuelto» no cambia el KPI.
- **Dependencias:** RF-0040, RF-0041, RF-0042.
- **RF-0043-CA-01:** dados traslados de distintas sucursales y rutas, cuando se genere un reporte por sucursal y otro por ruta, entonces cada reporte reflejará los traslados correspondientes y el cumplimiento calculado según la definición aprobada.
- **RF-0043-CA-02:** dadas en el período una llegada completa puntual, otra con discrepancia y una transferencia vencida aún en tránsito, cuando se reporte, entonces el KPI será 1/2 = 50%; la vencida en tránsito aparecerá aparte. Resolver o no la alerta de discrepancia no cambiará 50%. Una llegada fuera del período no se incluirá por haber sido despachada dentro; sin llegadas confirmadas se mostrará «No calculable», no 0% inventado.
- **Límites / pendientes:** H v0.15 cierra adaptación de D, INC-017/D04; no exige exportación del reporte logístico básico ni medición de cierre administrativo.

### 4.8. Dashboard e indicadores

#### RF-0044 — Comparar ventas mensuales
- **Fuente:** E §3.6, p. 3: «Volumen de ventas del mes en curso vs. meses anteriores».
- **Actores / alcance:** usuarios autorizados de cada sucursal; gerente según §6.2. El administrador y el gerente ven importes; el operador ve número de ventas y unidades, sin importes agregados (RF-0007).
- **Descripción:** el sistema deberá mostrar el volumen de ventas del mes actual y su comparación con meses anteriores.
- **Reglas y datos:** importe de ventas confirmadas neto de descuentos; cantidades físicas en unidad base por producto, sin sumar unidades heterogéneas. Mes actual y tres meses anteriores en America/Bogota; diferenciar meses completos del mes en curso. Devoluciones por separado, no ventas negativas implícitas. D ratifica el período como decisión del proyecto, no imposición del ejemplo externo. Distinguir cero de ausencia de datos, sin fabricar historia ni presentar seeds como producción.
- **Dependencias:** RF-0027, RF-0005.
- **RF-0044-CA-01:** dadas ventas del mes actual y de meses anteriores, cuando se consulte el dashboard de una sucursal, entonces la comparación distinguirá los períodos y coincidirá con las ventas de esa sucursal según la métrica aprobada.
- **Límites / pendientes:** D, INC-018/D01–D02,D07 e INC-019/D02; comparación adicional hasta igual día del mes sigue opcional, no automáticamente incorporada.

#### RF-0045 — Presentar rotación
- **Fuente:** E §3.6, p. 3: «Comportamiento del inventario: rotación».
- **Actores / alcance:** usuarios autorizados; dashboard por sucursal.
- **Descripción:** el sistema deberá presentar la rotación del inventario.
- **Reglas y datos:** rotación por producto-sucursal = unidades vendidas del período / inventario físico promedio temporal diario del período. H v0.15: si no hay historia suficiente que cubra el período consultado para calcular ese promedio, o el denominador es cero, mostrar **«No calculable»**; no extrapolar ni inventar días/saldos. No sustituir promedio por stock actual. Con historia suficiente, promedio positivo y cero ventas, rotación cero real, no ausencia de datos.
- **Dependencias:** RF-0018, RF-0027.
- **RF-0045-CA-01:** dado un conjunto de movimientos y ventas con resultado de rotación aprobado, cuando se consulte el indicador, entonces su valor coincidirá con ese resultado para el ámbito y período definidos.
- **RF-0045-CA-02:** dadas ventas de 20 unidades y promedio físico temporal diario verificable de 10 en el mismo período, cuando se calcule, entonces rotación será 2; con promedio cero se mostrará no calculable.
- **RF-0045-CA-03:** dado período con historia incompleta para el promedio diario, cuando se consulte, entonces se mostrará «No calculable», sin completar días con ceros o estimaciones. Con historia suficiente y promedio 10, 20 unidades vendidas producen rotación 2 y cero ventas producen 0; con promedio cero, «No calculable».
- **Límites / pendientes:** D, INC-018/D03,D07, precisadas por H v0.15; PR-04 resuelta. Diseño posterior de cálculo/representación debe respetar período, cobertura de historia y fórmula aprobada.

#### RF-0046 — Identificar alta y baja demanda
- **Fuente:** E §3.6, p. 3: «productos de alta y baja demanda».
- **Actores / alcance:** usuarios autorizados; productos por sucursal.
- **Descripción:** el sistema deberá mostrar productos de alta y baja demanda en el dashboard.
- **Reglas y datos:** H v0.15: medir unidades base vendidas en ventas confirmadas durante el mes consultado, mes actual por defecto y calendario America/Bogota. Separar rankings cuando las unidades base no sean comparables, sin sumar piezas y kilos ni incorporar categorías nuevas obligatorias. Dentro de cada grupo comparable, N = productos con ventas; alta = hasta 5 mayores cantidades y baja = hasta 5 menores, sin repetir productos. Con N < 10, asignar techo(N/2) a alta y piso(N/2) a baja; con N >= 10, 5 por extremo. Empates por SKU ascendente: elegir alta por cantidad descendente/SKU ascendente y baja entre los no elegidos por cantidad ascendente/SKU ascendente. Productos sin ventas se muestran aparte; si N = 0, no inventar integrantes de alta/baja. Es ranking relativo, no umbral estadístico ni predicción; no medirlo por importe ni restar devoluciones como ventas negativas implícitas.
- **Dependencias:** RF-0009, RF-0027.
- **RF-0046-CA-01:** dados productos que cumplan los criterios aprobados de alta y baja demanda, cuando se consulte el dashboard, entonces cada grupo contendrá los productos que correspondan a dichos criterios.
- **RF-0046-CA-02:** dados A con 30, B con 20 y C con 10 unidades vendidas comparables en el mes, cuando se consulte, entonces alta contendrá A/B y baja C, sin repetición. Con un único producto con ventas, alta contendrá ese producto y baja estará vacía; sin productos con ventas, ambos grupos estarán vacíos y los productos sin ventas irán aparte. Con 10 o más, máximo 5 por extremo.
- **RF-0046-CA-03:** dados tres productos con la misma cantidad vendida y SKU A/B/C, cuando se clasifiquen, entonces alta contendrá A/B y baja C por desempate estable. Productos en unidades base no comparables aparecerán en rankings separados; ventas de otro mes o cambios de precio sin cambios de cantidades no alterarán el ranking del mes consultado.
- **Límites / pendientes:** D, INC-018/D04 precisada por H v0.15; PR-04 resuelta. Sin predicción, categorías nuevas obligatorias ni agregados físicos heterogéneos.

#### RF-0047 — Mostrar transferencias e impacto
- **Fuente:** E §3.6, p. 3: «Estado de las transferencias activas y su impacto en el inventario».
- **Actores / alcance:** usuarios autorizados; dashboard de cada sucursal.
- **Descripción:** el sistema deberá mostrar las transferencias activas y su impacto sobre inventario.
- **Reglas y datos:** solicitado/preparado es previsión sin reserva; despachado es tránsito fuera del físico origen; recibido ingresa físico destino; faltante pendiente se identifica aparte dentro del tránsito pendiente, no como otro stock sumable. No duplicar saldo de la red. Mostrar estados abiertos de §5.1 y distinguir pérdida tratada de recepción.
- **Dependencias:** RF-0032–RF-0037, RF-0042.
- **RF-0047-CA-01:** dadas transferencias activas relacionadas con una sucursal, cuando se consulte su dashboard, entonces se mostrarán sus estados y cantidades de impacto coherentes con las reglas aprobadas de origen, tránsito y destino.
- **Límites / pendientes:** D, INC-018/D05 y H v0.14: diferenciar estado histórico de recepción, alerta resuelta y tránsito pendiente; no sumar ni regularizar cantidades por la marca de alerta.

#### RF-0048 — Mostrar indicadores de reabastecimiento
- **Fuente:** E §3.6, p. 3: «productos próximos a agotarse». Precisión del proyecto: H, resolución de INC-001 registrada en §8.
- **Actores / alcance:** usuarios autorizados; dashboard de sucursal.
- **Descripción:** el sistema deberá identificar en el dashboard productos próximos a agotarse.
- **Reglas y datos:** identificar producto-sucursal con disponible <= mínimo explícito de RF-0019, misma condición que RF-0020; incluido mínimo cero a saldo cero. Sin segundo umbral.
- **Dependencias:** RF-0019, RF-0020.
- **RF-0048-CA-01:** dados productos que cumplen y no cumplen la condición aprobada respecto a su stock mínimo vigente, cuando se consulte el indicador, entonces se identificarán los que la cumplen en la sucursal correspondiente, conforme a la misma condición usada para RF-0020.
- **RF-0048-CA-02:** dado un cambio del stock mínimo realizado por el administrador, cuando se vuelva a evaluar el indicador, entonces se utilizará el nuevo valor sin modificar un parámetro adicional de proximidad a agotarse.
- **Límites / pendientes:** D, INC-010/D01–D03; no reabrir elección de umbral ni crear parámetros duplicados.

#### RF-0049 — Comparar rendimiento entre sucursales
- **Fuente:** E §3.6, p. 3: «Comparativa de rendimiento entre sucursales (visible para perfiles administrativos)». Precisión H, INC-003, registro §8: acceso exclusivo del administrador general.
- **Actores / alcance:** administrador general; sucursales de la organización.
- **Descripción:** el sistema deberá presentar la comparación de rendimiento entre sucursales exclusivamente al administrador general.
- **Reglas y datos:** gerente conserva dashboard/reportes propios; gerente/operador no acceden a comparativa de red, sin restringir RF-0014. Comparar por mismo período importe de ventas confirmadas neto de descuentos, número de ventas y cumplimiento logístico de RF-0043; no atribuir rentabilidad. Distinguir falta de datos, cero y no calculable.
- **Dependencias:** RF-0007, RF-0044–RF-0048 según las métricas que se aprueben.
- **RF-0049-CA-01:** dados datos de dos sucursales y un administrador general autenticado, cuando consulte la comparación, entonces se distinguirá el rendimiento de cada sucursal según las mismas métricas y períodos aprobados.
- **RF-0049-CA-02:** dado un gerente o un operador autenticado, cuando intente consultar la comparativa de rendimiento entre sucursales, entonces no obtendrá esa información; el gerente conservará el acceso a dashboard y reportes de su sucursal y ambos conservarán las consultas de inventario entre sucursales autorizadas.
- **Límites / pendientes:** D, INC-018/D06–D07; mismo calendario de RF-0044 y permisos de §5.2. Sin indicadores financieros no aprobados.

### 4.9. Reportes exportables

#### RF-0050 — Exportar reportes con valor incremental
- **Origen:** solicitud explícita del proyecto. **Obligatoriedad:** compromiso aprobado del proyecto. **Estado:** aprobado; selección del módulo ratificada.
- **Fuente:** P §1 y H, selección de reportes; E §4, p. 4: «al menos una funcionalidad adicional» y alternativa «Módulo de reportes exportables».
- **Actores / alcance:** administrador exporta una/todas las sucursales; gerente solo propia; operador no exporta.
- **Descripción:** el sistema deberá generar reportes exportables como adicional, aportando una salida utilizable fuera de las consultas, dashboards y reportes básicos obligatorios.
- **Reglas y datos:** elección y detalles ratificados en D, INC-020: descarga directa XLSX de movimientos, ventas y transferencias, fechas obligatorias, sucursal según permisos y estado opcional si corresponde. Resultado coherente con datos/metadatos; sin filas, archivo válido con encabezados y nota explícita. Máximo 10.000 filas y 12 meses; exceder produce error claro sin truncar ni alterar operaciones. Sin tareas asíncronas ni repositorio de archivos.
- **Dependencias:** RF-0007, RF-0018, RF-0027, RF-0032–RF-0039, RF-0051–RF-0055.
- **RF-0050-CA-01:** dados datos registrados y una combinación de categoría y formato aprobada, cuando un usuario autorizado exporte el reporte, entonces obtendrá un archivo que podrá abrirse fuera de la aplicación y cuyo contenido corresponderá a los datos y alcance aprobados.
- **RF-0050-CA-02:** dada la misma consulta básica, cuando se compare con la exportación, entonces esta aportará un archivo reutilizable; limitarse a mostrar otra pantalla no satisfará este requisito.
- **RF-0050-CA-03:** dado reporte sin resultados, cuando se exporte, entonces se descargará XLSX válido con encabezados/metadatos y nota de ausencia de filas. Al exceder 10.000 filas o 12 meses, se rechazará con error claro, sin archivo truncado ni cambios operativos; con hasta 10.000 filas dentro del rango permitido, el límite no impedirá exportar.
- **RF-0050-CA-04:** dado gerente de A u operador, cuando intenten exportar otra sucursal o cualquier reporte, respectivamente, entonces se rechazará sin revelar datos fuera de permisos; administrador podrá seleccionar una o todas y gerente solo A.
- **Límites / pendientes:** detalles de capacidad/entorno en RNF (§5.4). No exportar todos los dashboards, usuarios o costos internos indiscriminadamente; sin PDF ni integración ERP.

#### RF-0051 — Exportar en formato .xlsx
- **Origen:** solicitud explícita del proyecto. **Obligatoriedad:** compromiso aprobado del proyecto. **Estado:** aprobado; regla ratificada.
- **Fuente:** D, INC-020/D01; P/H como antecedente de propuesta; E §4, p. 4, admite PDF o Excel sin imponer ambos.
- **Actores / alcance:** usuarios autorizados para reportes.
- **Descripción:** el sistema deberá generar los reportes aprobados en formato Excel `.xlsx` real.
- **Reglas y datos:** extensión y formato real deberán coincidir; no basta renombrar un archivo CSV.
- **Dependencias:** RF-0050.
- **RF-0051-CA-01:** dado un reporte exportado, cuando se abra con un lector compatible, entonces podrá leerse como libro `.xlsx` real y contendrá los datos del reporte; no será CSV renombrado, incluso si no contiene filas de datos.
- **Límites / pendientes:** biblioteca de generación por decidir posteriormente; sin obligación de PDF.

#### RF-0052 — Exportar movimientos
- **Origen:** solicitud explícita del proyecto. **Obligatoriedad:** compromiso aprobado del proyecto. **Estado:** aprobado; regla ratificada.
- **Fuente:** D, INC-020/D02–D06,D09–D10 y DEC-TEC-ID-001 §9.5; E §4, p. 4, alternativa de movimientos; P/H como antecedente.
- **Actores / alcance:** usuarios autorizados; movimientos del ámbito aprobado.
- **Descripción:** el sistema deberá exportar movimientos de inventario del ámbito autorizado.
- **Reglas y datos:** columnas: identificador público inequívocamente rotulado, fecha de registro, sucursal, SKU/nombre de producto, clase/motivo, cantidad/unidad, usuario responsable y documento origen. Omitir costos en exportación estándar; no exponer claves primarias internas. Filtrar por registro según RF-0055.
- **Dependencias:** RF-0018, RF-0050–RF-0051, RF-0055.
- **RF-0052-CA-01:** dados movimientos del ámbito y rango seleccionados, cuando se exporten, entonces corresponderán al historial, conservarán las columnas indicadas, sin costos ni claves internas, y no alterarán inventario ni movimientos.
- **Límites / pendientes:** sin auditoría ampliada; ajustes de presentación técnica no cambiarán columnas/permisos ratificados.

#### RF-0053 — Exportar ventas
- **Origen:** solicitud explícita del proyecto. **Obligatoriedad:** compromiso aprobado del proyecto. **Estado:** aprobado; regla ratificada.
- **Fuente:** D, INC-020/D02–D05,D07,D09–D10 y DEC-TEC-ID-001 §9.5; E §4, p. 4, alternativa de ventas; P/H como antecedente.
- **Actores / alcance:** usuarios autorizados; ventas del ámbito aprobado.
- **Descripción:** el sistema deberá exportar ventas del ámbito autorizado.
- **Reglas y datos:** una fila por línea: identificador público de venta, fecha de confirmación, sucursal, responsable, producto/unidad, cantidad, precio, descuento e importe neto de línea. No repetir total de cabecera como importe sumable; no exponer claves internas. RF-0055 filtra por confirmación.
- **Dependencias:** RF-0027, RF-0031, RF-0050–RF-0051, RF-0055.
- **RF-0053-CA-01:** dadas ventas seleccionadas con varias líneas, cuando se exporten, entonces habrá una fila por línea con sus columnas e importe neto; sumar líneas no duplicará totales de venta y los registros originales no cambiarán.
- **Límites / pendientes:** sin factura fiscal ni exportación contable para ERP.

#### RF-0054 — Exportar transferencias
- **Origen:** solicitud explícita del proyecto. **Obligatoriedad:** compromiso aprobado del proyecto. **Estado:** aprobado; regla ratificada.
- **Fuente:** D, INC-020/D02–D05,D08–D10 y DEC-TEC-ID-001 §9.5; E §4, p. 4, alternativa de transferencias; P/H como antecedente.
- **Actores / alcance:** usuarios autorizados; transferencias del ámbito aprobado.
- **Descripción:** el sistema deberá exportar transferencias del ámbito autorizado.
- **Reglas y datos:** una fila por línea: identificador público, origen, destino, fechas, estado, SKU/unidad, cantidades solicitada/despachada/recibida, diferencia pendiente y tratamiento. Con H v0.11, la cantidad recibida procede de la única confirmación de llegada, no de recepciones sucesivas. No duplicar filas por reintentos ni exponer claves internas. Filtrar por fecha de solicitud y declarar esa semántica; incluir fechas de despacho/recepción existentes.
- **Dependencias:** RF-0032–RF-0039, RF-0042, RF-0050–RF-0051, RF-0055.
- **RF-0054-CA-01:** dadas transferencias seleccionadas con llegada confirmada y reintentos técnicos de confirmación, cuando se exporten, entonces cada línea aparecerá una vez con cantidad recibida y diferencia correctas, columnas aprobadas y estado «Recibida con discrepancia» cuando corresponda; sin modificar cantidades/estados ni duplicar por reintentos. Una nueva transferencia tendrá su propia identidad, despacho y líneas, sin acumular su recepción a la original.
- **Límites / pendientes:** H v0.14 conserva estado de llegada y tratamiento mínimo; no reemplazar reporte básico de cumplimiento logístico ni exportar «Resuelto» como prueba de recepción completa.

#### RF-0055 — Filtrar exportaciones por fechas
- **Origen:** solicitud explícita del proyecto. **Obligatoriedad:** compromiso aprobado del proyecto. **Estado:** aprobado; regla ratificada.
- **Fuente:** D, INC-020/D03–D05,D09–D10 e INC-017/D06; E §4, p. 4: «en un rango de fechas»; P/H como antecedente.
- **Actores / alcance:** usuarios autorizados; categorías aprobadas de exportación.
- **Descripción:** el sistema deberá limitar exportaciones mediante fechas inicial y final obligatorias.
- **Reglas y datos:** rango inclusivo de fechas de negocio America/Bogota, sin incluir día siguiente. Movimientos por registro, ventas por confirmación, transferencias por solicitud; declararlo en metadatos. Sucursal según visibilidad, estado opcional si aplica; rechazar inicio > final y rango >12 meses sin truncar.
- **Dependencias:** RF-0050–RF-0054.
- **RF-0055-CA-01:** dados registros anteriores, interiores, en ambos extremos y al inicio del día siguiente, cuando se exporten, entonces se incluirán solo los del rango inclusivo conforme a la fecha de cada categoría y America/Bogota, sin solaparse con el día siguiente.
- **RF-0055-CA-02:** dado inicio posterior al final o rango superior a 12 meses, cuando se solicite exportar, entonces se rechazará con error claro sin modificar datos ni emitir resultado truncado. Sin resultados válidos, aplicar archivo vacío de RF-0050.
- **Límites / pendientes:** cálculo preciso del tope en límites calendarios se documentará en diseño/criterios sin cambiar los 12 meses ratificados; no agregar filtros avanzados por inferencia.

## 5. Dependencias y relaciones transversales

Las dependencias de las fichas expresan información y reglas compartidas, no una secuencia rígida de implementación ni un grafo de llamadas entre módulos.

| Relación | Coherencia a preservar |
|---|---|
| Administración y acceso → todos los módulos | Identidad, responsabilidad y ámbito de sucursal consistentes. La desactivación de RF-0002 impide acceso sin eliminar al usuario ni las referencias de responsabilidad histórica. |
| CRUD general y ajustes locales | H, INC-003: administrador con CRUD de productos; gerente con ajustes de stock solo de su sucursal activa, siempre mediante RF-0016–RF-0018. No confundir atributos generales del producto con existencias locales ni conceder otros permisos implícitos. |
| Catálogo → inventario, compras, ventas y transferencias | Identidad común, SKU independiente, habilitación local y mínimo explícito. Unidad base y factores por producto, instantáneas históricas sin reexpresión posterior. |
| Ingresos/retiros → historial | Datos completos de RF-0018; inmutabilidad y compensación vinculada en fecha actual. Sin edición directa, cascadas ni saldo negativo. |
| Compra recibida → ingreso → valoración | Recepción completa una sola vez; orden/cancelación no cambia stock ni promedio. H v0.12 resuelve redondeo al agotarse; H v0.13 distingue corrección histórica/conteo y diferencia de valoración separada si se agota cantidad. Sin cambiar venta ni historia; PR-02 resuelta en ese alcance. |
| Venta → disponibilidad → retiro | Confirmación indivisible y sin duplicación; fallo no produce salida, comprobante ni efectos parciales. |
| Transferencia → logística → dashboard | Preparar no reserva; despacho reduce origen y crea tránsito al costo congelado. Una sola llegada confirmada ingresa lo recibido; reintentos técnicos sin efectos nuevos. Parcial queda «Recibida con discrepancia», sin recepción complementaria; otra entrega usa nueva transferencia/despacho. Diferencia sigue su tratamiento, no es pérdida automática ni otro saldo sumable. |
| Stock mínimo → alertas e indicador de proximidad a agotarse | Un mínimo explícito por producto-sucursal, administrado por administrador; disponible <= mínimo, incluido cero. Una alerta activa y resolución al superar mínimo; faltantes con alerta independiente. |
| Datos operativos → exportaciones | Archivos coherentes con fuentes, permisos, fechas y límites aprobados; sin claves internas, costos estándar de movimientos ni repetición de totales/fila por recepción. |

La clasificación `catalog`/`inventory` no fija paquetes Java; su revisión corresponde al diseño de E §8. Los resultados funcionales ante concurrencia y revocación sí están aprobados; mecanismos, persistencia, medición y protección técnica se documentarán después. §5.4 conserva los parámetros ratificados para no perderlos ni presentarlos como verificados.

### 5.1. Restricción común de sucursales inactivas

Precisión aprobada del proyecto en H, registro §8, derivada de la política de desactivación elegida para RF-0003; no es un detalle literal de E. Esta sección complementa las fichas afectadas y evita repetir versiones distintas de la misma regla.

| Requisitos afectados | Aplicación de la restricción |
|---|---|
| RF-0016–RF-0017 | No registrar ingresos ni retiros sobre una sucursal inactiva. |
| RF-0021–RF-0024 | No crear compras ni operar su ciclo/recepción en sucursal inactiva; antes de desactivar cancelar borradores y recibir o cancelar pendientes de recepción. |
| RF-0027–RF-0029 | No registrar ni confirmar ventas o cambios comerciales de ventas sobre una sucursal inactiva. |
| RF-0032–RF-0039 | No iniciar transferencias con origen/destino inactivo ni ejecutar operaciones del traslado sobre una sucursal inactiva. La desactivación debe impedirse antes si hay una transferencia abierta. |
| RF-0004–RF-0005, RF-0018, RF-0025, RF-0031, RF-0042–RF-0043 | Conservar consultas e historial bajo los permisos correspondientes; inactividad no significa borrado. |
| RF-0002, RF-0006–RF-0007 | No cambiar automáticamente el estado de usuarios asociados; solo consultas históricas autorizadas, sin operaciones sobre sucursal inactiva. Revocar accesos cuando se cambie rol/sucursal o se desactive cuenta. |

La validación no cancela documentos ni vacía stock automáticamente. Compras en borrador/pendientes y transferencias solicitadas, en preparación, aprobadas o en tránsito impiden la baja. En «Recibida con discrepancia», conservar el impedimento mientras existan cantidades/valor de tránsito o faltantes operativos pendientes de regularización; «Resuelto» en la alerta no los elimina. Una vez regularizados trazablemente, la etiqueta histórica de recepción con discrepancia no bloquea por sí sola. Recibida completa o cancelada/rechazada no implica por sí sola operación abierta. Resolver impedimentos con sucursal activa. RF-0003-CA-04–RF-0003-CA-09 verifican la restricción; sin nuevo estado «en cierre» ni excepciones automáticas.

### 5.2. Matriz funcional de permisos ratificados

D, INC-003/D01–D08 y decisiones específicas por operación. «Propia» remite a la sucursal asignada; no permite elegir ámbito de escritura desde una consulta. Las consultas históricas conservan sus permisos, incluso con registros inactivos. Toda ejecución exige cuenta autorizada y sucursal/producto operables cuando corresponda.

| Acción | Administrador general | Gerente de sucursal | Operador de inventario |
|---|---|---|---|
| Usuarios, sucursales, mínimo, catálogo y factores de conversión | Mantiene globalmente | Consulta autorizada; sin mantenimiento | Consulta operacional; sin mantenimiento |
| Proveedores mínimos, listas de precios y rutas compartidas | Crea/modifica o mantiene según ficha | Consulta/usa en acciones permitidas | Consulta/usa en acciones permitidas |
| Catálogo e inventario de otras sucursales | Consulta red | Consulta existencias, sin escritura ajena | Consulta existencias, sin valoración/costos agregados |
| Compras, recepción de compra y ventas | Consulta red; no ejecuta por herencia | Consulta/supervisa propias; no confirma | Registra/confirma propias |
| Ingresos/retiros ordinarios, devoluciones y mermas | Consulta; no ejecuta por herencia | No ejecuta por herencia de supervisión | Registra propios según ficha |
| Ajustes manuales y correcciones de existencias | Consulta/supervisa; no ejecuta por herencia | Registra propios con trazabilidad | No registra ajustes/correcciones manuales |
| Solicitud de transferencia | No solicita: H v0.16 reserva la operación al operador del destino | Supervisa; no se le concede solicitud adicional | Solicita desde su destino |
| Preparación/despacho | Consulta/supervisa | Revisa/supervisa origen | Prepara/despacha origen tras aprobación |
| Aprobación y cancelación posterior a aprobación/pre-despacho | No aprueba/cancela por herencia | Gerente origen, con motivo si cancela | No aprueba ni cancela tras aprobación |
| Rechazo motivado de transferencia (H v1.2/01) | No rechaza | Solo gerente de origen, desde «en preparación» y antes de aprobar, con motivo obligatorio | No rechaza; conserva preparación/despacho según RF-0033/RF-0035 |
| Cancelación previa a aprobación | No se concede por herencia | Sin permiso adicional por inferencia | Destino solicitante, según RF-0034 |
| Recepción de transferencia | Supervisa | Gerente destino supervisa/valida recibido | Registra en destino |
| Discrepancias y pérdida en tránsito | Supervisa | Origen/destino gestionan; origen autoriza baja valor tránsito | Registra recepción/incidencia permitida; no autoriza pérdida |
| Marcar alerta de faltante «Resuelto» | Supervisa/consulta, sin permiso adicional por herencia | Gerente origen o destino, seleccionando tratamiento | Consulta; no resuelve |
| Dashboard y valoración | Red y comparativa | Dashboard/valoración propios | Panel operacional local, sin valoración/costos agregados ni importes agregados de venta |
| Reportes logísticos y exportaciones | Una/todas las sucursales | Solo propia | Registros operativos propios; no exporta |

H v0.14: no introducir módulo de tareas personales ni bloquear reasignación por compras/transferencias creadas por el usuario. Pendientes permanecen en la sucursal bajo supervisión del gerente y los ejecutan perfiles autorizados actuales; conservar autoría histórica y revocar accesos previos. Resolver alerta no autoriza ajustar stock ajeno ni cambia permisos de movimientos. No conceder privilegios adicionales al administrador ni reemplazar autorización por conocimiento del identificador.

### 5.3. Glosario e identidad transversal

| Término | Significado aprobado y efecto observable |
|---|---|
| Físico | Cantidad registrada en la sucursal, expresada en unidad base del producto; nunca negativa. |
| Disponible | Físico operable, sin reservas. Preparación/solicitud no lo reduce; validar nuevamente al confirmar salida. |
| Tránsito | Cantidad despachada que dejó origen y aún no fue recibida ni resuelta formalmente; no es físico/disponible destino. |
| Faltante pendiente | Diferencia enviada–recibida identificada y pendiente de resolución formal; no es merma automática ni cantidad adicional al tránsito. Tras «Recibida con discrepancia», no se recibe nuevamente contra el mismo despacho. |
| Recibida con discrepancia | Llegada confirmada con parte de la mercancía faltante: recepción física finalizada, sin complementos del mismo despacho. No equivale a recepción completa ni a tratamiento del faltante resuelto. |
| Pérdida reconocida | Resolución autorizada y motivada que retira tránsito/valor una sola vez; no equivale a entrega física. |
| Movimiento | Hecho confirmado con cantidad positiva y dirección; identidad, clase, sucursal, producto, unidad, motivo, fecha/hora, responsable y referencia si existe. |
| Identidad pública | Referencia estable, única, generada y no editable para recursos expuestos individualmente; distinta de SKU/códigos de negocio y de clave técnica interna. Se conserva en historial y bajas lógicas; no concede permisos. |

**DEC-TEC-ID-001:** aprobada en D §9, que se conserva como registro técnico de origen. Este catálogo solo incorpora sus efectos observables: referencias públicas estables en consultas/operaciones/reportes, códigos comerciales independientes, ausencia de claves internas en respuestas/errores/exportaciones y autorización por recurso/sucursal. No crea RF por columnas ni formularios para claves. Tipos, generación, alcance exacto por recurso, índices y convención de nombres se documentarán una sola vez en arquitectura/modelo/API cuando se elaboren, sin reabrir la identificación dual ni crear aquí esos artefactos.

### 5.4. Parámetros aprobados y destino documental posterior

Estos valores son decisiones del proyecto D §4, no obligaciones numéricas de E ni prueba de cumplimiento. La tabla preserva trazabilidad mientras no existan los artefactos RNF/restricciones/arquitectura; no declara completadas esas partes de §6.1.

| Decisión | Parámetro ratificado / condición | RF y destino posterior |
|---|---|---|
| INC-004/D02 | Visibilidad <=5 segundos tras confirmar, en condiciones normales del entorno de demostración definido | RF-0015; RNF de rendimiento: definir carga, entorno y medición |
| INC-003/D09 | Contraseña 15–128 caracteres, espacios permitidos, sin rotación periódica arbitraria; comprobación débiles/comprometidas condicionada al mecanismo | RF-0006; RNF de seguridad y diseño de credenciales |
| INC-003/D10 | Contraseña temporal individual y cambio inicial; primer administrador por inicialización segura en Compose, sin credenciales públicas | RF-0002/RF-0006; seguridad e infraestructura, sin elegir mecanismo ahora |
| INC-003/D12–D13 | Salida/revocación efectiva, 30 minutos de inactividad y 8 horas máximo absoluto; sin deshacer operaciones confirmadas | RF-0006–RF-0007; RNF seguridad y mecanismo de sesiones |
| INC-003/D14 | Política inicial: 5 fallos en 15 minutos, espera progresiva de 5, 15 y hasta 30 minutos; sin bloqueo permanente automático | RF-0006; detallar reinicio/progresión y mitigación de abuso en RNF/diseño, sin afirmar resuelta la estrategia |
| INC-007/D03 | Hasta 3 decimales en unidad base divisible, rechazo sin redondear silenciosamente | RF-0013 y operaciones; precisión y representación técnica |
| INC-013/D03; H v0.12 | Decimales exactos, promedio a 6 decimales, importes mostrados a 2, mitad hacia arriba. Última salida consume valor restante con diferencia identificada; cantidad/valor cero sin alterar venta | RF-0026-CA-03; RF-0023/RF-0029; RNF precisión y representación técnica posterior. Residual aprobado, no pendiente |
| INC-012/D05 | COP, moneda única de demostración; sin impuestos, fletes o divisas | RF-0023 y valoración/ventas; supuesto de negocio ratificado |
| INC-017/D06 | America/Bogota explícita y uniforme para calendario, KPI y filtros | RF-0040–RF-0049, RF-0055; supuesto de calendario/diseño temporal |
| INC-020/D10 | Hasta 10.000 filas y 12 meses, descarga directa; exceder produce error sin truncar | RF-0050/RF-0055; RNF capacidad y diseño de generación |

**Exclusiones ratificadas (D §6):** sin offline ni escrituras diferidas; integraciones externas; correo/SMS, recuperación automática por correo, autorregistro o MFA; pagos/órdenes de pago, facturación fiscal o contabilidad financiera; impuestos/fletes y múltiples monedas; GPS/optimización, operaciones de transportistas externas o varios despachos por pedido; compras parcialmente recibidas, reservas, predicción de demanda, proveedores avanzados, promociones y exportaciones asíncronas. No confundir exclusiones del proyecto con ausencia de las funciones básicas obligatorias.

## 6. Registro de incertidumbres

**Estado:** INC-001–INC-002 resueltas previamente; las 115 consideraciones de INC-003–INC-020 tienen destino documental desde D, con correcciones humanas H v0.11–v0.16. **PR-01–PR-04 resueltas en sus alcances acordados: ciclo de cuatro precisiones cerrado.** No quedan decisiones abiertas dentro de esos cuatro bloques. Detalles técnicos y funciones adicionales condicionadas conservan su naturaleza de §6.1; no se aprueban por inferencia ni acreditan software. Conservar IDs y evidencia de ratificación/sustitución.

| ID | Fuente / RF afectados | Resolución aprobada y pendiente específico |
|---|---|---|
| INC-001 | E §6.2, §3.1, §3.6; H; RF-0001, RF-0019–RF-0020, RF-0048 | **Resuelta:** mínimo compartido administrado por administrador; D, INC-010 completa granularidad/comparación sin reabrirla. |
| INC-002 | E §6.2; H; RF-0002–RF-0003, §5.1 | **Resuelta:** gestión sin borrado de usuarios/sucursales, atributos y baja condicionada. D completa compras/transferencias abiertas y acceso. |
| INC-003 | E §6.2, §8.2; D, D01–D14; H v0.14; RF-0002–RF-0007 y permisos transversales | **Ratificada e integrada:** reasignación sin bloqueo por autoría de pendientes de la sucursal, sin módulo de tareas personales; historia y revocación conservadas. PR-03 resuelta; mecanismos de seguridad/antiabuso pendientes de diseño. |
| INC-004 | E §2–2.1; D, D01–D04; RF-0005, RF-0015 | **Ratificada e integrada:** conectada, sin offline, aviso de desactualización y meta <=5 segundos. Medición/carga y mecanismo de actualización pendientes de RNF/diseño. |
| INC-005 | E §3.1; D, D01–D04; RF-0008–RF-0012 | **Ratificada e integrada:** catálogo común, identidad/SKU y habilitación local, datos mínimos e historia estable. Secuencia de habilitación/mínimo pendiente de diseño. |
| INC-006 | E §3.1; D, D01–D04; H v1.1; RF-0010–RF-0011, RF-0018 | **Ratificada e integrada:** borrado solo sin uso/referencias; con historia baja lógica condicionada; compensaciones, no edición histórica. H v1.1 aprueba reactivación exclusiva del administrador, sin restaurar habilitaciones locales ni mínimos. |
| INC-007 | E §3.1; D, D01–D05; RF-0013 y operaciones | **Ratificada e integrada:** base/conversión por producto, fracciones compatibles a 3 decimales, factor/precio históricos. Representación técnica pendiente, no elección del modelo. |
| INC-008 | E §3.1, §3.3–3.4; D, D01–D04; RF-0012, RF-0014, RF-0017, RF-0028, RF-0033–RF-0037 | **Ratificada e integrada:** sin reservas/negativos, validación indivisible, tránsito separado. Mecanismos transaccionales pendientes de diseño. |
| INC-009 | E §3.1; D, D01–D05; H v0.13; RF-0016–RF-0018, RF-0026 | **Ratificada e integrada:** compensación actual vinculada sin anulación financiera. Corrección de entrada identificada al costo histórico, conteo a promedio vigente; diferencia de valoración al agotarse separada del redondeo. PR-02 resuelta en alcance aprobado. |
| INC-010 | E §3.1, §3.4, §3.6; D, D01–D06; RF-0001, RF-0019–RF-0020, RF-0038, RF-0048 | **Ratificada e integrada:** mínimo explícito producto-sucursal, disponible <= mínimo, aviso persistente e historia, faltante separado. Sin ratificación general pendiente. |
| INC-011 | E §3.2; D, D01–D06; RF-0021–RF-0025, RF-0003 | **Ratificada e integrada:** ciclo de compra y recepción completa única; borradores/pedidos pendientes impiden baja. API decidirá respuesta a repetición, no su efecto. |
| INC-012 | E §3.2; D, D01–D05; RF-0021, RF-0023, RF-0026 | **Ratificada e integrada:** proveedor mínimo, descuento por línea, plazo desde recepción y COP sin impuestos/fletes. Sin gestión avanzada ni pagos. |
| INC-013 | E §3.2; D, D01–D07; H v0.12–v0.13; RF-0026 y movimientos | **Ratificada e integrada:** promedio móvil, redondeo, corrección de entrada a costo histórico, ajuste por conteo a promedio vigente y diferencia de valoración separada al agotarse. PR-02 resuelta en alcance aprobado; sin recalcular historia ni incorporar correcciones de precio puro por inferencia. |
| INC-014 | E §3.3; D, D01–D06; RF-0027–RF-0031 | **Ratificada e integrada:** listas globales, descuento por línea, confirmación única/indivisible, concurrencia y comprobante interno inmutable. Sin pagos ni factura fiscal. |
| INC-015 | E §3.4, §6.2; D, D01–D09; H v0.14, v0.16 y v1.2/01; RF-0032–RF-0036, RF-0042, RF-0047, RF-0003 | **Ratificada e integrada:** aprobación origen, multilínea, cantidades, cancelación/rechazo y despacho con tránsito. H v0.14 elimina el estado adicional de cierre con diferencia; H v0.16 acota la solicitud al operador del destino. H v1.2/01 permite rechazo con motivo solo al gerente de origen desde «en preparación», antes de aprobar. Preparación/despacho siguen en el operador. No ampliar permisos por herencia. |
| INC-016 | E §3.4; D, D01–D09; H v0.11–v0.14 y v1.1; RF-0035–RF-0039, RF-0042, RF-0047, RF-0054 | **Integrada con corrección humana posterior:** única llegada, «Recibida con discrepancia» permanente para ese resultado, alerta con «Resuelto»/tratamiento/autor/fecha y movimientos manuales separados. PR-01/PR-03 resueltas; regularización no se infiere de la alerta. H v1.1 aprueba llegada cero como recepción parcial sin movimiento físico. |
| INC-017 | E §3.5; D, D01–D07; H v0.15; RF-0040–RF-0043, RF-0055 | **Integrada con corrección humana posterior:** tiempo hasta única llegada completa/parcial, sin cierre administrativo; KPI completas puntuales/llegadas confirmadas por fecha de recepción. Discrepancias no cumplidas, tránsito vencido aparte. PR-04 resuelta; prioridad/ruta/zona conservadas. |
| INC-018 | E §3.6; D, D01–D07; H v0.15; RF-0044–RF-0047, RF-0049 | **Ratificada e integrada:** ranking mensual por unidades comparables, SKU para empates y reparto sin solapamientos; rotación «No calculable» ante historia insuficiente/promedio cero. PR-04 resuelta; comparación adicional hasta igual día sigue opcional, no obligación incorporada. |
| INC-019 | E §6.3; D, D01–D03; RF-0023, RF-0032, RF-0044 | **Ratificada e integrada:** sin órdenes de pago ni urgencia en primera versión; tres meses anteriores aprobados como decisión del proyecto. D02 remite al mismo acuerdo INC-018/D02, no segunda decisión independiente. |
| INC-020 | E §4; D, D01–D10; RF-0050–RF-0055 | **Ratificada e integrada:** XLSX/tres categorías, columnas/permisos/filtros/fechas, vacío coherente y descarga con topes. Diseño de capacidad posterior sin cambiar límites ni truncar. |

### 6.1. Seguimiento de precisiones, sin reabrir lo ratificado

| ID de seguimiento | Alcance pendiente | RF afectados / condición de cierre |
|---|---|---|
| PR-01 | **Resuelta por H v0.11:** recepción única por despacho, parcial «Recibida con discrepancia», sin recepción complementaria. Reintento técnico no repite efectos; otra entrega requiere nueva transferencia/despacho. | RF-0036-CA-02, RF-0037-CA-03, RF-0042-CA-01 y RF-0054-CA-01. Mecanismo de unicidad/respuesta API queda para diseño, sin decisión funcional pendiente sobre duplicación o recepciones sucesivas. |
| PR-02 | **Resuelta en alcance aprobado por H v0.12–v0.13:** residual de redondeo, corrección de cantidad de entrada identificada al histórico, conteo sin origen al promedio vigente y diferencia de valoración separada al agotar cantidad. | RF-0026-CA-03–RF-0026-CA-05; RF-0017-CA-03 y RF-0018-CA-03. Sin stock negativo, reescritura histórica o cambios a precios de venta. No extender a correcciones exclusivas de precio no especificadas; representación técnica posterior. |
| PR-03 | **Resuelta por H v0.14:** alerta con «Resuelto» y selección de tratamiento, sin modificar stock/valor/estado de transferencia ni crear cierre integrado. Movimientos separados; reasignación sin bloqueo por autoría y pendientes bajo sucursal/gerente. | RF-0002-CA-09; RF-0038-CA-02; RF-0039-CA-02; RF-0042-CA-02; §5.1–§5.2. Mantener permisos, trazabilidad y separación entre resolución de alerta y regularización de inventario. |
| PR-04 | **Resuelta por H v0.15:** ranking mensual por unidades comparables, SKU para empates, mitades con extra en alta si N impar y N < 10; sin ventas aparte. Rotación «No calculable» con historia insuficiente/promedio cero. KPI completas puntuales/llegadas confirmadas y duración hasta única llegada completa/parcial, sin cierre administrativo. | RF-0040-CA-02; RF-0043-CA-02; RF-0045-CA-03; RF-0046-CA-02–RF-0046-CA-03. «Resuelto» no altera desempeño ni saldos; vencidas en tránsito aparte. |

**Resoluciones posteriores:** H v1.1 incorpora la reactivación manual exclusiva del administrador, sin restaurar habilitaciones ni mínimos locales, y la llegada confirmada de cero unidades como recepción parcial sin movimiento físico, con faltante total y alerta. Estas decisiones no habilitan comportamientos adicionales por inferencia. La historia insuficiente para promedio diario sí quedó resuelta en PR-04. Mecanismos de actualización/revocación/antiabuso, representación de precisión/identificadores y capacidad corresponden a RNF/diseño posteriores, sin reabrir los valores ratificados. La comparativa adicional hasta igual día sigue opcional. Estos límites no se presentan como decisiones pendientes de PR-01–PR-04 ni como software implementado.

## 7. Matriz de cobertura bidireccional

### 7.1. Fuente → requisitos o destino

**Lectura:** «identificado» significa representado documentalmente, no aprobado, implementado ni probado. «Pendiente» señala precisiones de las fichas, no una exclusión de una obligación. Las citas son fragmentos localizadores; la redacción íntegra sigue en E/M.

| Fuente | Fragmento / elemento revisado | Clasificación y obligatoriedad | Correspondencia / destino | Cobertura documental |
|---|---|---|---|---|
| §1, p. 1 | Diseño, robustez y «¿Por qué se hizo así?» | Objetivo, calidad y justificación | Requisitos no funcionales y decisiones, etapas posteriores | Clasificado; no RF autónomo |
| §2, p. 1 | Independencia local y coherencia de red | Funcional obligatorio y calidad asociada | RF-0005, RF-0015; D, INC-004 | Integrado: plataforma conectada, sin offline; diseño/medición posteriores |
| §2.1, p. 1 | «transacciones locales» | Funcional obligatorio | RF-0005 | Identificado |
| §2.1, p. 1 | «tiempo real o near-real-time» | Función obligatoria y restricción temporal | RF-0015; D, INC-004, §5.4 | Meta <=5 segundos ratificada por proyecto; medición RNF pendiente |
| §2.1, p. 1 | Consultar cualquier sucursal | Funcional obligatorio | RF-0014 | Identificado |
| §2.1, p. 1 | Solicitar y recibir transferencias | Funcional obligatorio | RF-0032, RF-0036–RF-0037 | Identificado |
| §3.1, p. 1 | Creación, consulta, actualización y eliminación | Cuatro operaciones obligatorias | RF-0008–RF-0011; D, INC-005–INC-006; H v1.1 | Catálogo común, política de baja y reactivación administrativa sin restauración local integrados |
| §3.1, p. 1 | Catálogo propio | Funcional obligatorio | RF-0012 | Identificado |
| §3.1, p. 2 | Inventario de otra sucursal | Funcional obligatorio repetido | RF-0014 | Consolidado, no duplicado |
| §3.1, p. 2 | Compras, devoluciones y ajustes de ingreso | Funcional obligatorio | RF-0016; RF-0024 | Tres motivos incluidos |
| §3.1, p. 2 | Ventas, mermas y ajustes de retiro | Funcional obligatorio | RF-0017; RF-0027 | Tres motivos incluidos |
| §3.1, p. 2 | Stock mínimo y alertas | Dos capacidades obligatorias | RF-0019–RF-0020; H/D, INC-001, INC-010 | Mínimo producto-sucursal explícito e inclusivo, aviso/interfaz/historia integrados |
| §3.1, p. 2 | Múltiples unidades por producto | Funcional obligatorio | RF-0013; D, INC-007 | Base, conversión/fracciones/precisión y registro histórico integrados |
| §3.1, p. 2, nota | Fecha, responsable, motivo, cantidad e historial | Funcional obligatorio transversal | RF-0018; enlaces en ingresos/retiros | Cuatro datos incluidos |
| §3.2, p. 2 | Crear y gestionar órdenes | Funcional obligatorio | RF-0021–RF-0022; D, INC-011 | Ciclo integrado, edición solo borrador; sin compras parciales |
| §3.2, p. 2 | Precio unitario, descuentos, plazo | Funcional obligatorio | RF-0023 | Tres condiciones incluidas |
| §3.2, p. 2 | Inventario al confirmar recepción | Funcional obligatorio | RF-0024 | Identificado |
| §3.2, p. 2 | Histórico por proveedor y por producto | Funcional obligatorio | RF-0025 | Ambas perspectivas incluidas |
| §3.2, p. 2 | Promedio ponderado | Funcional obligatorio | RF-0026; D, INC-013, H v0.12–v0.13 | Método, residual y compensaciones de cantidad con ejemplos integrados; PR-02 resuelta en alcance acordado |
| §3.3, p. 2 | Producto, cantidad, precio | Funcional obligatorio | RF-0027 | Incluido |
| §3.3, p. 2 | Sucursal, fecha, responsable | Funcional obligatorio | RF-0027 | Incluido, sin duplicar venta |
| §3.3, p. 2 | Disponibilidad antes de confirmar | Funcional obligatorio | RF-0028 | Incluido |
| §3.3, p. 2 | Descuentos y listas | Funcional obligatorio | RF-0029–RF-0030 | Ambas capacidades incluidas |
| §3.3, p. 2 | Comprobantes o registros posteriores | Funcional obligatorio con alternativa | RF-0031 | «O» conservada |
| §3.4 paso 1, p. 2 | Destino o administrador; producto/cantidad/origen | Funcional obligatorio | RF-0032 | Datos incluidos; H v0.16 elige la sucursal destino y excluye al administrador, como decisión del proyecto |
| §3.4 paso 2, p. 3 | Disponibilidad; confirmar o ajustar envío | Funcional obligatorio | RF-0033 | Ambas opciones incluidas |
| §3.4 paso 3, p. 3 | Despacho, estimación y transportista | Funcional obligatorio | RF-0035 | Incluido |
| §3.4 paso 4, p. 3 | Recepción completa automática | Funcional obligatorio | RF-0036 | Incluido |
| §3.4 paso 5, p. 3 | Recepción parcial y diferencia | Funcional obligatorio | RF-0037–RF-0039; D, INC-016, H v0.11/v0.14 | Única llegada y «Recibida con discrepancia»; alerta resuelta con tratamiento mínimo, movimientos separados; PR-01/PR-03 resueltas |
| §3.4 paso 5, p. 3 | Alerta y tratamiento | Funcional obligatorio | RF-0038–RF-0039 | Reenvío, ajuste o reclamación incluidos |
| §3.5, p. 3 | Tiempos estimados y reales | Funcional obligatorio | RF-0040 | Registro y comparación incluidos |
| §3.5, p. 3 | Rutas por prioridad, costo o tiempo | Funcional obligatorio con alternativa | RF-0041; D, INC-017 | Prioridad manual alta/media/baja elegida por proyecto, sin exigir tres criterios |
| §3.5, p. 3 | Preparación, tránsito, recibido, faltantes | Funcional obligatorio | RF-0042 | Cuatro estados incluidos |
| §3.5, p. 3 | Cumplimiento por sucursal y por ruta | Funcional obligatorio | RF-0043 | Ambas perspectivas incluidas |
| §3.6, p. 3 | Dashboard en cada sucursal | Funcional obligatorio transversal | RF-0044–RF-0048 | Ámbito local en fichas |
| §3.6, p. 3 | Ventas del mes vs. anteriores | Funcional obligatorio | RF-0044 | Identificado |
| §3.6, p. 3 | Rotación, alta y baja demanda | Funcional obligatorio | RF-0045–RF-0046 | Tres conceptos incluidos |
| §3.6, p. 3 | Transferencias e impacto | Funcional obligatorio | RF-0047 | Identificado |
| §3.6, p. 3 | Próximos a agotarse | Funcional obligatorio | RF-0048; H/D, INC-001, INC-010 | Mismo mínimo producto-sucursal, disponible <= mínimo |
| §3.6, p. 3 | Rendimiento entre sucursales; administrativos | Funcional obligatorio y acceso | RF-0049, RF-0007; D, INC-003, INC-018 | Solo administrador; ventas netas, número de ventas y cumplimiento, sin rentabilidad |
| §3.6, nota p. 3 | Gráficas, dashboards o KPI; claridad | Alternativas de presentación y calidad | Diseño de interfaz / calidad posteriores | No se impone una biblioteca |
| §4, p. 4 | Al menos un adicional de valor real | Obligación externa de elección | RF-0050; P/H/D | Selección y detalles de exportación ratificados; no equivalen a software implementado |
| §4, p. 4 | Alertas inteligentes | Idea no seleccionada, no obligatoria por sí misma | No incorporada como adicional; RF-0020 conserva alertas básicas | Analizada, no seleccionada |
| §4, p. 4 | Predicción de demanda | Idea no seleccionada | Fuera del adicional seleccionado | Analizada, no seleccionada |
| §4, p. 4 | Gestión de proveedores ampliada | Idea no seleccionada | Soporte mínimo de RF-0021 no equivale al módulo ampliado | Analizada, no seleccionada |
| §4, p. 4 | Caducidad | Idea no seleccionada | Fuera del adicional seleccionado | Analizada, no seleccionada |
| §4, p. 4 | Auditoría de cada acción | Idea no seleccionada | RF-0018 conserva historial obligatorio, no auditoría ampliada | Analizada, no seleccionada |
| §4, p. 4 | Exportables PDF o Excel; movimientos, ventas o transferencias; fechas | Alternativa seleccionada y concretada por proyecto | RF-0050–RF-0055; D, INC-020 | XLSX/tres categorías/fechas/columnas/topes aprobados; sin PDF |
| §5, pp. 4–5 | Capas, API exclusiva y negocio fuera del cliente | Restricciones técnicas obligatorias | Requisitos técnicos y §8 posteriores; P elige REST | Clasificado, no función nueva |
| §5, p. 5 | Compose y ausencia de configuración manual | Restricción de ejecución obligatoria | Infraestructura / verificación de arranque posteriores | Clasificado, no RF de usuario |
| §5, p. 5 | Stack libre y justificación | Libertad técnica con justificación | P y decisiones de §8 | No atribuir stack a OptiPlant |
| §6.1, p. 5 | Funcionales, no funcionales, restricciones, supuestos y dependencias | Obligación documental | Este catálogo cubre solo funcionales; resto en etapas posteriores | Sección 6.1 no completada íntegramente |
| §6.2, p. 5 | Administrador: configuración, usuarios, sucursales | Funcional obligatorio | RF-0001–RF-0003 | INC-001 e INC-002 resueltas; atributos, operaciones y desactivaciones incorporados; asignaciones/permisos en INC-003 |
| §6.2, p. 5 | Administrador: visibilidad total | Funcional obligatorio | RF-0004 | Incluido |
| §6.2, p. 5 | Gerente: supervisar, aprobar, reportes | Funcional obligatorio | RF-0005, RF-0034, RF-0043–RF-0044; RF-0049 según permisos | Aprobación no omitida |
| §6.2, p. 5 | Operador: ingresos, retiros, transferencias, ventas/compras | Funcional obligatorio | RF-0016–RF-0017, RF-0021, RF-0027, RF-0032 | Actores relacionados |
| §6.2, p. 5 | Sistema externo opcional ERP/POS por API | Opcional externo, excluido por P/H | Sección 1, exclusión aprobada | Analizado y excluido; REST interno no afectado |
| §6.2, p. 5 | Definir actores e interacciones | Obligación documental | Sección 6.2 futura; actores preliminares en fichas | No se da por terminado el modelado |
| §6.3, p. 6 | Historias recomendadas y opcionales | Recomendación documental | Fuera de este catálogo como artefacto | No convertidas en obligación |
| §6.3, ejemplo 1, p. 6 | Precio de ingreso, costo y órdenes de pago | Ejemplo; alcance financiero excluido por proyecto | RF-0023, RF-0026; D, INC-019/D01 | Precio/costo incluidos; sin órdenes de pago |
| §6.3, ejemplo 2, p. 6 | Exactamente tres meses anteriores | Ejemplo, adoptado expresamente por proyecto | RF-0044; D, INC-018/D02 e INC-019/D02 | Mes actual y tres anteriores; no atribuir obligación literal al ejemplo |
| §6.3, ejemplo 3, p. 6 | Urgencia en solicitud | Ejemplo, no incorporado en primera versión | RF-0032; D, INC-019/D03 | Excluida; distinta de prioridad de ruta obligatoria |
| §7.1, p. 6 | Casos de uso, actividades de venta/transferencia, arquitectura, E-R | Cuatro tipos obligatorios; dos flujos mínimos | Modelado de §7 posterior | No son funciones del producto |
| §7.1, nota p. 6 | Herramientas sugeridas y formatos de diagramas | Herramientas orientadoras y entrega documental | Convenciones de P; modelado posterior | Sin imponer herramienta |
| §8.1, p. 7 | Interfaz responsiva, API, backend, persistencia y servicios Docker | Calidad y restricciones obligatorias | Calidad / diseño posteriores; RF-0018 y funciones existentes como respaldo | No duplica funciones por capa |
| §8.1, backend p. 7 | Validaciones, transferencias, costos y reportes | Responsabilidad técnica sobre funciones | RF-0026, RF-0028, RF-0032–RF-0043 | Consolidado con §3 |
| §8.2, p. 7 | Autenticación y autorización implementadas | Exigencia técnica con comportamiento funcional | RF-0006–RF-0007; estrategia en §8 | Comportamiento identificado, mecanismo pendiente |
| §8.2, p. 7 | Sincronización de inventario | Función y decisión técnica a justificar | RF-0015; diseño posterior | No obliga a nodos distribuidos |
| §8.2, p. 7 | Lenguaje, motor/modelo y patrones utilizados | Justificaciones obligatorias | Decisiones de §8 posteriores | No impone Repository, Factory ni CQRS |
| §9, pp. 7–8 | Uso estructural de IA y áreas sugeridas | Obligación de proceso; áreas orientadoras | Evidencia de IA posterior | No exige IA dentro del producto |
| §9.2, p. 8 | Herramientas/etapas, prompts/resultados, evaluación, porcentaje | Cuatro contenidos documentales obligatorios | Evidencia auténtica de IA posterior | No se inventan cifras ni evidencias |
| §10, p. 9 | Repositorio, código, Compose, README, diagramas y sección IA | Entregables obligatorios | Lista de entrega posterior; no RF de la aplicación | No implica permiso de publicar |
| §11 | Sin sección en E/M | Anomalía de numeración | Ningún requisito atribuible | Ausencia registrada |
| §12, p. 9 | Orden sugerido y justificación | Recomendación de organización | Planificación ODD y decisiones posteriores | Sin plazo numérico en E; fecha procede de P |
| P §1 | Reportes seleccionados y valor incremental | Decisión del proyecto | RF-0050 | Incluido |
| P §1 | `.xlsx`, tres categorías y fechas como propuesta inicial | Antecedente posteriormente ratificado en D | RF-0051–RF-0055; D, INC-020 | Detalle ahora aprobado; P se conserva sin edición automática |
| P §1 | Referencia modular y stack | Contexto aprobado / referencia propuesta | Secciones 1 y 5; diseño de §8 | No se convierte en límite Java definitivo |
| H, resolución INC-001, registro §8 | Administrador modifica mínimo; mismo umbral para próximos a agotarse | Precisión aprobada del proyecto sobre funciones externas | RF-0001, RF-0019, RF-0020, RF-0048 | Incorporada; D, INC-010 completa detalles del mínimo |
| H, aprobación de usuarios en INC-002, registro §8 | Crear, listar, consultar, actualizar y desactivar usuarios, sin eliminación física | Precisión aprobada del proyecto sobre gestión y acceso | RF-0002, RF-0006–RF-0007, RF-0018 | Incorporada; otras aprobaciones posteriores se registran por separado |
| H, aprobación de sucursales en INC-002, registro §8 | Crear, listar, consultar, actualizar y desactivar sucursales, sin eliminación física | Precisión aprobada del proyecto sobre gestión de sucursales | RF-0003 | Incorporada; atributos aprobados por separado en la base mínima |
| H, desactivación condicionada, registro §8 | Cero existencias, sin compras por recibir ni transferencias abiertas; conservar historia, no operar en inactivas ni desactivar usuarios automáticamente | Precisión aprobada del proyecto | RF-0003, RF-0005, RF-0007 y restricciones de §5.1 | Incorporada; estados exactos se alinean con INC-011, INC-015 e INC-016 |
| H, permisos de catálogo y stock local, registro §8 | CRUD general del administrador y cambios de stock del gerente solo en su sucursal | Precisión aprobada del proyecto; trazabilidad obligatoria según E §3.1 | RF-0005, RF-0007–RF-0011, RF-0016–RF-0018 | Incorporada y completada en D, INC-003, INC-005–INC-006 |
| H, base mínima alternativa A y acceso por código/contraseña, registro §8 | Atributos de usuarios y sucursales; credencial personal de acceso | Precisión aprobada del proyecto, no atributos literales del PDF | RF-0002–RF-0003, RF-0006 | Incorporada; D completa políticas/permisos y H v0.14 precisa reasignación sin bloqueo por autoría |
| H, asignación simple, registro §8 | Un rol por usuario; administrador global; gerente/operador en una sucursal | Precisión aprobada del proyecto sobre ámbito | RF-0002, RF-0005, RF-0007 | Cardinalidad aprobada; no permite escritura cruzada ni elimina consultas entre sucursales |
| H, separación de comparativas, registro §8 | Comparativas de red exclusivas del administrador; gerente mantiene dashboard/reportes locales | Precisión aprobada del proyecto sobre acceso | RF-0007, RF-0049 | Incorporada; consultas de inventario entre sucursales conservadas |

### 7.2. Requisitos → fundamento

Cada ficha contiene la cita localizada y las dependencias; este índice inverso facilita detectar requisitos sin respaldo.

| Requisitos | Fundamento primario | Relación con otras fuentes |
|---|---|---|
| RF-0001–RF-0004 | E §6.2, administrador | §2 para ámbito multi-sucursal |
| RF-0005 | E §2–2.1 | §6.2 para supervisión local |
| RF-0006–RF-0007 | E §6.2, actores y responsabilidades | E §8.2 exige documentar la estrategia de autenticación ya implementada; credenciales, sesiones y mecanismo son decisión del proyecto (RF-0006) |
| RF-0008–RF-0013 | E §3.1 | §2 para ámbito local; D, INC-005–INC-007 aprueba identidad, bajas y conversiones como precisiones del proyecto |
| RF-0014–RF-0015 | E §2.1 | §3.1 repite consulta; §8.2 pide justificar sincronización |
| RF-0016–RF-0020 | E §3.1 | §6.2 relaciona operador; historial no depende del adicional |
| RF-0021–RF-0026 | E §3.2 | §3.1 para ingreso; §6.2 para registro por operador |
| RF-0027–RF-0031 | E §3.3 | §3.1 para retiro; §6.2 para registro por operador |
| RF-0032–RF-0033 | E §3.4 pasos 1–2 | §2.1 y §6.2 |
| RF-0034 | E §6.2, gerente | D, INC-015 ubica aprobación en origen tras preparar y antes de despachar, con cancelación/rechazo; H v1.2/01 precisa rechazo motivado exclusivo del gerente de origen desde «en preparación», antes de aprobar |
| RF-0035–RF-0039 | E §3.4 pasos 3–5 | §3.1 para trazabilidad de ingresos/retiros |
| RF-0040–RF-0043 | E §3.5 | §6.2 para consulta de reportes |
| RF-0044–RF-0049 | E §3.6 | D, INC-018–INC-019 concreta métricas y tres meses previos; §6.3 sigue siendo ejemplo externo |
| RF-0007 y RF-0049: acceso a comparativas | H, separación de administración global y gestión local, registro §8 | E §3.6 limita a perfiles administrativos sin enumerarlos; exclusividad del administrador general es precisión aprobada del proyecto |
| RF-0050 | P/H, selección y valor incremental | E §4 exige un adicional y ofrece esta alternativa |
| RF-0051–RF-0055 | D, INC-020; P/H como antecedente | E §4 orienta; D ratifica XLSX/categorías/fechas/columnas/topes como compromiso del proyecto |
| RF-0001, RF-0019, RF-0020, RF-0048: precisión de alcance | H, resolución INC-001 y registro §8 | El fundamento externo de las funciones permanece; la consulta/modificación por administrador y el umbral compartido son precisiones aprobadas del proyecto |
| RF-0002, RF-0006–RF-0007, RF-0018: gestión de cuentas y sus efectos | H, aprobación de usuarios en INC-002 y registro §8 | E §6.2 exige gestionar usuarios; las operaciones, desactivación sin borrado y sus efectos de acceso/historial son precisiones del proyecto |
| RF-0003 y requisitos afectados de §5.1: operaciones y baja de sucursales | H, aprobación de sucursales, atributos y desactivación condicionada en INC-002, registro §8 | E §6.2 asigna gestión al administrador; atributos, operaciones, baja sin borrado y sus condiciones/efectos son precisiones aprobadas del proyecto |
| RF-0005, RF-0007–RF-0011, RF-0016–RF-0018: permisos de catálogo y ajustes | H, aprobación inicial INC-003, completada en D | E §3.1 exige CRUD y movimientos trazables; asignaciones funcionales por rol son decisiones del proyecto |
| RF-0002–RF-0003 y RF-0006: atributos e inicio de sesión | H, base mínima alternativa A y código único con contraseña personal, registro §8 | E §3.1, §3.3–§3.6, §6.2 y §8.2 sustentan identidad/responsabilidad/ámbito y autenticación; los campos concretos y mecanismo de acceso son elecciones del proyecto |
| RF-0002, RF-0005 y RF-0007: rol y ámbito único | H, aprobación de asignación simple, registro §8 | E §2 y §6.2 describen autonomía y responsabilidades; limitar a un rol y una sucursal operativa para gerente/operador es decisión del proyecto |

### 7.3. Resultado de revisión documental de esta versión

- Se conservan 55 fichas y sus identificadores: 49 con fundamento funcional externo y seis compromisos aprobados del adicional del proyecto. Las precisiones de D no se atribuyen al enunciado.
- Las 49 fichas externas no equivalen a 49 viñetas originales: hay descomposición de obligaciones y consolidación de repeticiones, con fuentes en ambos sentidos.
- Los seis módulos de §3, las responsabilidades funcionales de §6.2 y las exigencias funcionales de acceso/sincronización de §8.2 tienen correspondencia documental.
- H v0.16 acota la solicitud de transferencia al operador del destino sobre la alternativa «(o un administrador)» de E §3.4; la restricción es decisión del proyecto y queda registrada como tal, no como texto del enunciado.
- La obligación externa del adicional se conserva separada de su selección y de sus detalles. La integración externa opcional tiene exclusión expresa, no omisión.
- Todas las fichas conservan criterios identificados y se añaden criterios de las reglas ratificadas; son especificaciones documentales, no pruebas ejecutadas. La formulación queda aprobada en v1.0; no se acredita implementación ni pruebas ejecutadas.
- Se conservan 20 IDs de incertidumbre y el destino de los 115 códigos de D; §7.4 distingue correcciones humanas v0.11–v0.16. **Las cuatro precisiones PR-01–PR-04 están resueltas y su ciclo cerrado.** Se consolidan reglas y criterios en esta versión, sin elevar funciones adicionales condicionadas ni detalles de RNF/diseño a nuevas obligaciones. No se certifica software implementado/probado ni aprobación de las demás partes de §6.1.
- El catálogo no certifica cumplimiento del software ni finaliza las demás partes de §6.1. No se afirma cobertura de una sección 11 inexistente.

### 7.4. Informe de integración D → RF → criterios

**Resultado:** `I` = incorporado; `P` = incorporado con detalle posterior de diseño/RNF o función adicional expresamente condicionada, no aprobada automáticamente; `S` = alcance sustituido por decisión humana posterior, conservando trazabilidad. `P` no convierte una elección de negocio en diseño: la última columna identifica la naturaleza del límite y §6.1 separa adicionales no comprometidos. H v0.11–v0.16 registra correcciones de recepciones, valoración, alertas, indicadores y alineación documental sin reescribir D. PR-01–PR-04 están cerradas, pero ninguna fila certifica implementación o pruebas. Referencias CA heredan ficha/§5 y parámetros RNF no son controles ejecutados.

| Decisión de D | RF / criterio observable o destino | Resultado / detalle conservado |
|---|---|---|
| INC-003/D01 | RF-0004-CA-01; RF-0007-CA-05; §5.2 | I |
| INC-003/D02 | RF-0007-CA-05; RF-0024-CA-01; RF-0027-CA-01 | I |
| INC-003/D03 | RF-0033-CA-01; RF-0035-CA-01; RF-0036-CA-01; §5.2 | I |
| INC-003/D04 | RF-0009-CA-01; RF-0014-CA-01; RF-0007-CA-05 | I |
| INC-003/D05 | RF-0050-CA-04; RF-0043-CA-01; §5.2 | I |
| INC-003/D06 | RF-0030-CA-01; RF-0041-CA-01; §5.2 | I |
| INC-003/D07 | RF-0002-CA-09; RF-0018-CA-02; H v0.14 | I: pendientes bajo sucursal/gerente, sin bloqueo por autoría ni módulo de tareas personales; historia y revocación conservadas |
| INC-003/D08 | RF-0003-CA-08–RF-0003-CA-09; §5.1 | I |
| INC-003/D09 | RF-0006-CA-06; §5.4 | P: control de contraseñas débiles condicionado al mecanismo |
| INC-003/D10 | RF-0002-CA-01; RF-0006-CA-04; §5.4 | P: provisión técnica segura en Compose |
| INC-003/D11 | RF-0006-CA-04; exclusiones §5.4 | I |
| INC-003/D12 | RF-0006-CA-05; RF-0007-CA-02 | P: mecanismo de revocación |
| INC-003/D13 | RF-0006-CA-05; §5.4 | P: sesión 30 minutos/8 horas, diseño y RNF |
| INC-003/D14 | RF-0006, protección de acceso; §5.4 | P: antiabuso, progresión/reinicio de intentos en RNF/diseño |
| INC-004/D01 | RF-0005-CA-03 | I |
| INC-004/D02 | RF-0015-CA-01; §5.4 | P: medición RNF de meta aprobada |
| INC-004/D03 | RF-0005-CA-03; RF-0015-CA-02 | I |
| INC-004/D04 | RF-0015-CA-01; §5 | P: mecanismo posterior sin cambiar REST |
| INC-005/D01 | RF-0008-CA-02; RF-0012-CA-01 | I |
| INC-005/D02 | RF-0008-CA-01; §5.3 | I: atributos mínimos e identidad pública independiente |
| INC-005/D03 | RF-0008-CA-02; RF-0012-CA-02; RF-0019-CA-03 | P: secuencia técnica habilitación/mínimo |
| INC-005/D04 | RF-0010-CA-02; RF-0031-CA-02 | I |
| INC-006/D01 | RF-0011-CA-01–RF-0011-CA-02 | I |
| INC-006/D02; H v1.1 | RF-0011-CA-02–RF-0011-CA-04; §6.1 | I: reactivación exclusiva del administrador, sin habilitación local ni mínimo restaurados automáticamente |
| INC-006/D03 | RF-0011-CA-03 | I |
| INC-006/D04 | RF-0018-CA-03 | I |
| INC-007/D01 | RF-0013-CA-01–RF-0013-CA-02 | I |
| INC-007/D02 | RF-0013-CA-02 | I |
| INC-007/D03 | RF-0013-CA-02; §5.4 | I: representación técnica posterior |
| INC-007/D04 | RF-0013-CA-03; RF-0023-CA-02 | I |
| INC-007/D05 | RF-0013-CA-03 | I |
| INC-008/D01 | RF-0032-CA-02; RF-0033 reglas; §5.3 | I |
| INC-008/D02 | RF-0017-CA-03; RF-0028-CA-02 | I |
| INC-008/D03 | RF-0028-CA-03; RF-0035-CA-02 | P: mecanismo transaccional |
| INC-008/D04 | RF-0035-CA-02; RF-0037-CA-02 | I |
| INC-009/D01 | RF-0018-CA-01, reglas complementarias y §5.3 | I |
| INC-009/D02 | RF-0016-CA-03 | I |
| INC-009/D03 | RF-0016-CA-02; RF-0017-CA-02; RF-0007-CA-05 | I |
| INC-009/D04 | RF-0018-CA-03; RF-0026-CA-04–RF-0026-CA-05; RF-0027-CA-02 | I: H v0.13 define compensación histórica y diferencia de valoración separada; sin anulación financiera |
| INC-009/D05 | RF-0018-CA-03 | I |
| INC-010/D01 | RF-0001-CA-01; RF-0019-CA-02 | I |
| INC-010/D02 | RF-0019-CA-03; RF-0012-CA-02 | I |
| INC-010/D03 | RF-0019-CA-03; RF-0048-CA-01 | I |
| INC-010/D04 | RF-0020-CA-01, reglas; §5.2 | I |
| INC-010/D05 | RF-0020-CA-02 | I |
| INC-010/D06 | RF-0038-CA-01, reglas | I |
| INC-011/D01 | RF-0022-CA-01 | I |
| INC-011/D02 | RF-0024-CA-01–RF-0024-CA-02 | I |
| INC-011/D03 | RF-0022-CA-01–RF-0022-CA-02 | I |
| INC-011/D04 | RF-0024-CA-02 | P: forma de respuesta API a repetición |
| INC-011/D05 | RF-0003-CA-06; §5.1 | I |
| INC-011/D06 | RF-0025-CA-01–RF-0025-CA-02 | I |
| INC-012/D01 | RF-0021-CA-02 | I |
| INC-012/D02 | RF-0021-CA-02 | I |
| INC-012/D03 | RF-0023-CA-01–RF-0023-CA-02 | I |
| INC-012/D04 | RF-0023-CA-01–RF-0023-CA-02, reglas | I |
| INC-012/D05 | RF-0023 reglas; §5.4 | I: COP ratificado, sin impuestos/fletes/divisas |
| INC-013/D01 | RF-0026-CA-01 | I |
| INC-013/D02 | RF-0023-CA-02; RF-0026 reglas | I |
| INC-013/D03 | RF-0026-CA-01; RF-0026-CA-03; §5.4 | I: H v0.12 resuelve residual a saldo cero, diferencia identificada sin afectar venta |
| INC-013/D04 | RF-0026-CA-01; RF-0026-CA-03–RF-0026-CA-04; RF-0017 reglas | I: salidas ordinarias/conteo a promedio vigente; H v0.13 distingue compensación de entrada identificada al histórico |
| INC-013/D05 | RF-0026-CA-02; RF-0037-CA-02 | I |
| INC-013/D06 | RF-0016-CA-03; RF-0026 reglas | I: referencia histórica/entrada sin base explícita |
| INC-013/D07 | RF-0018-CA-03; RF-0017-CA-03; RF-0026-CA-04–RF-0026-CA-05 | I: H v0.13, compensación actual, histórico frente a conteo y diferencia al agotarse; sin reescritura ni stock negativo |
| INC-014/D01 | RF-0030-CA-01; RF-0027 reglas | I |
| INC-014/D02 | RF-0029-CA-01–RF-0029-CA-02 | I |
| INC-014/D03 | RF-0027-CA-02; RF-0028-CA-02 | I |
| INC-014/D04 | RF-0028-CA-03 | P: mecanismo de concurrencia |
| INC-014/D05 | RF-0027-CA-02; RF-0044 reglas | I |
| INC-014/D06 | RF-0031-CA-01–RF-0031-CA-02 | I |
| INC-015/D01 | RF-0034-CA-01; RF-0032-CA-01; §5.2 | I |
| INC-015/D02 | RF-0032-CA-02, reglas | I: normalización exacta por definir en diseño |
| INC-015/D03 | RF-0033-CA-01–RF-0033-CA-02, reglas | I |
| INC-015/D04 | RF-0042-CA-01–RF-0042-CA-02; RF-0003-CA-07; §5.1 | S: H v0.14 elimina estado adicional de cierre y conserva «Recibida con discrepancia»; alerta y regularización separadas |
| INC-015/D05 | RF-0035-CA-02; RF-0033 reglas | I |
| INC-015/D06; H v1.2/01 | RF-0034-CA-02–RF-0034-CA-04, reglas; §5.2 | I: rechazo con motivo solo por gerente de origen desde «en preparación», antes de aprobar; cancelaciones conservadas, sin herencia administrativa ni cancelación en tránsito |
| INC-015/D07 | RF-0035-CA-01, reglas | I |
| INC-015/D08 | RF-0035-CA-02 | I |
| INC-015/D09 | RF-0032-CA-02; RF-0035-CA-02, reglas | I |
| INC-016/D01 | RF-0035-CA-02; RF-0037-CA-02 | I |
| INC-016/D02 | RF-0037-CA-01–RF-0037-CA-02 | I |
| INC-016/D03; H v1.1 | RF-0036-CA-02; RF-0037-CA-03–RF-0037-CA-04; §8 | S: recepciones sucesivas sustituidas por única llegada por despacho y otra transferencia para nueva entrega; llegada cero aprobada como recepción parcial sin movimiento físico; PR-01 resuelta |
| INC-016/D04 | RF-0037-CA-02; RF-0039-CA-02 | I |
| INC-016/D05 | RF-0039-CA-02, reglas | I |
| INC-016/D06 | RF-0038-CA-02; RF-0039-CA-02; H v0.14 | S: alerta simple con tratamiento, sin ciclo integrado de reclamación/cierre; movimientos manuales aparte |
| INC-016/D07 | RF-0038-CA-02; RF-0039-CA-01–RF-0039-CA-02; §5.2 | I: gerentes gestionan alerta/movimientos según permisos, administrador supervisa; resolver alerta no autoriza cambios de stock |
| INC-016/D08 | RF-0026-CA-02; RF-0039-CA-02 | I |
| INC-016/D09 | RF-0037-CA-02; RF-0018-CA-03; RF-0026-CA-04–RF-0026-CA-05 | I: conservar costo del despacho y corrección vinculada, con criterios H v0.13 en su alcance; sin nueva recepción |
| INC-017/D01 | RF-0041-CA-01, reglas | I |
| INC-017/D02 | RF-0041-CA-01 | I |
| INC-017/D03 | RF-0040-CA-01–RF-0040-CA-02 | S: H v0.15 mide despacho hasta única llegada, completa/parcial; sin medición de cierre administrativo |
| INC-017/D04 | RF-0043-CA-01–RF-0043-CA-02 | S: H v0.15 usa llegadas confirmadas por fecha de recepción; discrepancias no cumplidas, tránsito vencido aparte; alerta sin efecto |
| INC-017/D05 | RF-0035-CA-01; RF-0040 reglas | I |
| INC-017/D06 | RF-0040 reglas; RF-0055-CA-01; §5.4 | I |
| INC-017/D07 | RF-0043-CA-01, reglas | I |
| INC-018/D01 | RF-0044-CA-01, reglas | I |
| INC-018/D02 | RF-0044-CA-01, reglas | P: comparación hasta igual día opcional, no asumida |
| INC-018/D03 | RF-0045-CA-01–RF-0045-CA-03 | I: H v0.15 define «No calculable» ante historia insuficiente/promedio cero, sin fabricar datos |
| INC-018/D04 | RF-0046-CA-01–RF-0046-CA-03 | I: H v0.15, unidades mensuales comparables, SKU, mitades/top5 sin solapamiento y cero ventas aparte |
| INC-018/D05 | RF-0047-CA-01, reglas | I |
| INC-018/D06 | RF-0049-CA-01–RF-0049-CA-02, reglas | I |
| INC-018/D07 | RF-0044–RF-0046, RF-0049 reglas; RF-0045-CA-03 | I: H v0.15 cierra historia insuficiente; distinguir cero/ausencia/No calculable, sin seeds productivos ni datos fabricados |
| INC-019/D01 | RF-0023 reglas/exclusiones; §5.4 | I |
| INC-019/D02 | RF-0044-CA-01, reglas | I: mismo acuerdo INC-018/D02, sin duplicar decisión |
| INC-019/D03 | RF-0032 reglas; RF-0041-CA-01 | I |
| INC-020/D01 | RF-0051-CA-01 | I |
| INC-020/D02 | RF-0052-CA-01; RF-0053-CA-01; RF-0054-CA-01 | I |
| INC-020/D03 | RF-0050-CA-04; RF-0055 reglas | I |
| INC-020/D04 | RF-0052–RF-0054 reglas; RF-0055-CA-01 | I |
| INC-020/D05 | RF-0055-CA-01–RF-0055-CA-02 | I |
| INC-020/D06 | RF-0052-CA-01 | I |
| INC-020/D07 | RF-0053-CA-01 | I |
| INC-020/D08 | RF-0054-CA-01 | I: H v0.11 precisa cantidad de la única recepción, sin acumulación de recepciones sucesivas |
| INC-020/D09 | RF-0050-CA-03; RF-0052-CA-01 | I |
| INC-020/D10 | RF-0050-CA-03; RF-0055-CA-02; §5.4 | P: capacidad/implementación de descarga en RNF/diseño |
| DEC-TEC-ID-001 | §5.3; RF-0007-CA-05; RF-0010-CA-02; RF-0052–RF-0054 | P: efectos funcionales integrados; decisión técnica aprobada en D §9, detalle de arquitectura/API/modelo posterior |

**Balance de integración:** 115/115 códigos funcionales con destino, incluido alcance sustituido por H v0.11, más una decisión técnica transversal sin RF artificial. I/P/S expresan integración, pendientes y sustituciones, no aprobación general nueva ni validación del software.

## 8. Registro de revisiones y aprobaciones

| Fecha / versión | Hecho y evidencia | Alcance de aprobación |
|---|---|---|
| 2026-10-20, anterior a 0.1 | El responsable aprobó en conversación el enfoque incremental, clasificación, IDs globales, plantilla y cobertura bidireccional. | Método de documentación, no contenido de fichas todavía inexistentes. |
| 2026-10-20, anterior a 0.1 | El responsable seleccionó reportes exportables, excluyó integraciones externas y mantuvo organización modular de referencia; decisiones reflejadas en P. | Selección y exclusión aprobadas; `.xlsx`, categorías, fechas y límites Java no definitivamente aprobados. |
| 2026-10-20, 0.1 | Solicitud humana: «Genera el documento de requisitos funcionales». Se elaboró este borrador con asistencia de IA a partir de E/M y P. | Autorización de redacción; revisión humana de fichas pendiente. |
| 2026-10-20, 0.2 | Decisión humana: «Me gusta lo de que el administrador pueda cambiar el stock minimo y tambine me gusta la idea de que se use esta misma variable para evitar duplicar parametros en los umbrales de productos proximos a agotarse, mantengamos esa». Tras la delimitación propuesta, instrucción: «Hagamos las modificaciones necesarias en los requisitos que dependen de esta incidencia y marquemosla como resuelta». | INC-001 resuelta. Actualizados RF-0001, RF-0019, RF-0020 y RF-0048; administrador consulta/modifica mínimo y se comparte el umbral sin alterar existencias, costos o transacciones ni automatizar compras o bloqueos. Granularidad, valores y comparación siguen pendientes en INC-010; el resto del catálogo continúa en revisión. |
| 2026-10-20, 0.3 | Tras proponer creación, listado/consulta, actualización y desactivación de usuarios sin eliminación física ni funciones adicionales de acceso, el responsable indicó: «Si aprobado esto para usuarios, haz las modificaciones a los requisitos pertinentes y marca como resulesta la incidencia». | Aprobadas las operaciones propuestas para RF-0002 y sus efectos de acceso e historial en RF-0006, RF-0007 y RF-0018. INC-002 queda parcialmente resuelta: la aprobación dice «para usuarios» y no decide atributos ni gestión de sucursales. La consulta humana sobre roles de CRUD de productos no constituye aprobación de una matriz de permisos. |
| 2026-10-20, 0.4 | Tras preguntar si aprobaba crear, listar, consultar, actualizar y desactivar sucursales sin eliminación física, el responsable indicó: «Si asignemos estas operaciones tambien al administrador y revisemos la regla que mencionas, que alternativas tenemos y cuales se posicionan como las mejores». | Operaciones incorporadas a RF-0003 y su cobertura. La política de desactivación se analizará como propuesta; no queda aprobada automáticamente. INC-002 continúa parcialmente resuelta. |
| 2026-10-20, 0.5 | Tras presentar la alternativa A de desactivación condicionada, el responsable indicó: «Si aprobemos esa desactivacion, haz las modificaciones necesarias en los requerimientos y pasemos a la siguiente incidencia donde veamos alternativas para la gestion». | Política incorporada a RF-0003, RF-0005, RF-0007 y restricciones comunes de §5.1: stock cero, sin compras por recibir ni transferencias abiertas; preservar historia, impedir operaciones en inactivas y no desactivar automáticamente sus usuarios. INC-002 permanece parcial por atributos. Se autoriza analizar INC-003, no aprobar sus alternativas de permisos. |
| 2026-10-20, 0.6 | Decisión humana: «Yo creo que la opcion que mejor me cuadra es que el administrador tenga la posibilidad de mantener su CRUD general pero tambien considero que el gerente puede hacer cambios locales solo dentro de su sucursal como lo es hacer modificaciones a su stock por ejemplo. Haz lo cambios respectivos dada esta respuesta y dime que queda pendiente para resolver bien la incidencia 2 y si queda pendiente algo para esta tercera». | Aprobados CRUD general de productos para administrador y ajustes de stock local para gerente. Se aplican RF-0005, RF-0007–RF-0011 y RF-0016–RF-0018, conservando trazabilidad de E §3.1 y sin conceder mantenimiento del catálogo general al gerente ni otros permisos no enumerados. INC-002 e INC-003 continúan parciales. |
| 2026-10-20, 0.7 | Aprobación de atributos: «Si aprobemos la base de la alternativa A, me gusta que para acceder sean codigos de usuario unicos, me gustaria saber que otro elemento debemos de suministrar como tipo de contrasena para hacer los inicios de sesion adecuados». Tras proponer código único y contraseña personal dejando política y entrega inicial pendientes: «si aprobemoslo, modifiquemos el documento y resolvamos lo que queda pendiente para la incertidumbre 3 y si ya esta resulta pasemos a la 4». | RF-0002 y RF-0003 incorporan la base mínima aprobada; INC-002 resuelta. RF-0006 incorpora acceso con código único y contraseña personal; INC-003 sigue parcial porque no se han aprobado cardinalidades, matriz completa, política de contraseña/provisión ni sesiones. La solicitud de resolver pendientes no autoriza a seleccionar esas alternativas automáticamente; INC-004 no se aborda hasta continuar la revisión de INC-003. |
| 2026-10-20, 0.8 | Tras proponer un único rol por usuario, administrador global sin sucursal obligatoria y gerente/operador con una única sucursal, el responsable indicó: «Si estoy de acuerdo, continuemos con la siguiente decisioin». | Actualizados RF-0002, RF-0005 y RF-0007, criterios, cobertura y pendientes. La asignación no combina roles ni amplía permisos; las consultas entre sucursales se conservan. INC-003 sigue parcial por las decisiones restantes. |
| 2026-10-20, 0.9 | Tras proponer comparativas entre sucursales exclusivas del administrador general, el responsable indicó: «Mantegamos la separacion entre administracion global y gestion local, no continuemos por ahora te quiero pedir que hagamos otra cosa para agilizar el proceso, guarda esta decision y espera instrucciones». | Decisión registrada en RF-0007 y RF-0049 con criterios y cobertura: comparativas exclusivas del administrador, gerente con dashboard/reportes locales y consulta de inventario entre sucursales conservada. INC-003 permanece parcial. Revisión pausada por instrucción expresa; no se avanza a otra decisión ni a INC-004. |
| 2026-10-20, 0.10 | Fuente D v1.0 aportada como decisiones aprobadas; tras revisión sin modificaciones, instrucción actual: «Okay por ahora hagamos los cambios dadas las decisiones aprobadas e integrarlo al documento de requisitos funcionales. Luego de hacer esto quiero que exploremos directamente esas 4 precisiones que aun dices que estan pendientes con el fin de dar un cierre a este documento». | Integración de las 115 consideraciones, parámetros/exclusiones y efectos funcionales de DEC-TEC-ID-001 en fichas, CA, permisos, glosario, registro y §7.4. RF-0051–RF-0055 dejan de ser propuestas. Se conservan aprobaciones históricas, originales y temporales; sin código, diagramas, commits ni aprobación implícita de PR-01–PR-04/condicionados de §6.1. |
| 2026-10-20, 0.11 | Aprobación humana: «Okay entonces si hagamos esta proteccion contra duplicados, las recepciones parciales son solo eso recepciones parciales donde se debe de destacar que solo se recibio una parte y ya ahi mueren, todo lo otro son otras recepciones». Tras pedir aclaración del cierre y vínculo con el despacho, respuesta: «Quiero decir que se confirma la llegada y se pone que la transferencia fue Recibida con discrepancia, pero no se hacen reintentos de la entrega o se vinculan al mismo despacho, ya seria otra transferencia la que se hace». | PR-01 resuelta: única llegada por despacho, parcial «Recibida con discrepancia», reintentos técnicos sin duplicar efectos y nueva entrega por otra transferencia/despacho. Sustituye recepciones sucesivas de D, INC-016/D03 y precisa estado de INC-015/D04, criterios y exportación. No resuelve ni convierte automáticamente faltantes en pérdida; PR-02–PR-04 continúan pendientes. Se conservan D/G e historia; sin código ni commits. |
| 2026-10-20, 0.12 | Tras explicar el ejemplo de 3 unidades por 10, el residual 0,000001 y su absorción por la última salida, y distinguir costo de inventario de precio de venta, el responsable indicó: «Si entonces si apruebo la precision del redondeo». | Residual de INC-013/D03 resuelto: última salida consume valor restante por producto-sucursal, cantidad/valor cero y diferencia identificada, sin cambiar promedio unitario, precio/importe de venta ni movimientos anteriores. RF-0026-CA-03, parámetros, cobertura y trazabilidad actualizados. PR-02 sigue parcialmente abierta por compensaciones posteriores; no se aprueba su política por inferencia. Sin software, código ni commits. |
| 2026-10-20, 0.13 | Tras distinguir entrada identificada a costo histórico de ajuste por conteo a promedio vigente, el responsable indicó: «Si usalo, porfavor dime que ya solo faltan dos precisiones mas, necesito cerrar rapidamente este ciclo de requisitos funcionales». Se detectó la salvedad 2 unidades/30, compensación histórica -2/-20 → 0/10; a registrar diferencia de valoración separada y vinculada sin modificar historia ni llamarla redondeo, respondió: «Si». | PR-02 resuelta en alcance acordado: compensación de cantidad al histórico frente a conteo al vigente; si se agota cantidad, diferencia de valoración separada deja 0/0. RF-0017/RF-0018/RF-0026 y CA-04–CA-05, cobertura/registro actualizados. Quedan PR-03 y PR-04 como bloques principales; condicionados visibles, sin funcionalidades nuevas por inferencia, módulo contable, código ni commits. |
| 2026-10-20, 0.14 | Caso 2 de reasignación aprobado: «Entiendo perfectamente el caso 2 y la solucion me parece bien». Para el caso 1: «Yo lo que propongo hacer es dejarla como Recibida con discrepancia y que manualmente ya se hagan los movimientos de stock correspondientes, este Recibido con discrepancia creo que es bueno que salga en una seccion de alertas y tenga un boton de resuelto y ya, no quiero complicarme con ello». A conservar selección mínima de reenvío/ajuste/reclamación al resolver la alerta respondió: «Si». | PR-03 resuelta: reasignación sin bloqueo por autoría, pendientes bajo sucursal/gerente y revocación; alerta con tratamiento/autor/fecha, sin cambios automáticos de inventario ni estado de transferencia. Se retira estado adicional de cierre y flujo integrado; movimientos manuales trazables aparte. PR-04 conserva indicadores y adaptación del KPI/hito de gestión, sin inventar base de cálculo nueva por inferencia. Fuentes/temporales intactos; sin código ni commits. |
| 2026-10-20, 0.15 | A la propuesta completa del último bloque (ranking mensual por unidades comparables, SKU, grupos sin solapamiento/mitades; rotación «No calculable»; KPI por llegadas confirmadas, discrepancias no cumplidas y tiempo hasta única llegada completa/parcial), el responsable respondió: «Si». | PR-04 resuelta y ciclo PR-01–PR-04 cerrado. Reglas y CA de RF-0040/RF-0043/RF-0045/RF-0046, registro y trazabilidad consolidados. H v0.15 sustituye base de KPI/medición administrativa anteriores. No amplía funciones condicionadas, no acredita software, RNF o diseño, ni autoriza código, commits o eliminación de temporales. |
| 2026-10-21, 0.16 | Revisión final de cobertura contra la fuente. Para RF-0004: «la correccion es correcta ya que se basa solo en la ejecucion». Para AGENTS.md: «corrige el Agents.md». Para la solicitud de transferencia: «desde el archivo se menciona que el que solicita las transferencia debe de ser el operador entonces dejemos que solo lo haga el operador no el administrador general». Sobre autenticación manifestó su intención de definir JWT y autorización por roles. | H v0.16: RF-0004 precisa que no se hereda la ejecución, no la consulta, y puede consultarlas según §5.2; AGENTS.md alineado con INC-020/D01–D10; RF-0032 y §5.2 reservan la solicitud al operador del destino, acotando la alternativa «(o un administrador)» de E §3.4 como decisión del proyecto; §7.2 corrige el fundamento de RF-0006–RF-0007. JWT y roles no se incorporan al catálogo funcional: corresponden a RNF/diseño. Sin código ni commits. |
| 2026-10-21, 1.0 | Aprobación formal del catálogo: «Declaremoslo aprobado». | Se aprueba la formulación de las 55 fichas y sus 138 criterios como especificación funcional vigente. No se aprueban RNF, restricciones y supuestos, casos de uso, diagramas, arquitectura, evidencia de IA ni entregables; no acredita implementación ni pruebas. D, las fuentes y los temporales se conservan. |

| 2026-10-21, 1.1 | Resolución humana posterior: «Solo el administrador puede reactivar un producto inactivo; la reactivación recupera el producto en el catálogo general, pero no lo vuelve operable automáticamente en ninguna sucursal. Cada sucursal debe habilitarlo explícitamente y definir nuevamente su stock mínimo». Sobre llegada cero: «tratarlo tal cual como si llegaran solo unas cuantas unidades ... como Recibida con discrepancia». | H v1.1: RF-0011 permite reactivación exclusiva del administrador sin restaurar habilitaciones ni mínimos; RF-0037 permite llegada confirmada de cero unidades como recepción parcial, sin movimiento físico, con faltante total, alerta y tratamiento separado. Se actualizan decisiones, casos de uso e historias. Sin código ni commits. |

**Evidencia de IA de esta actividad:** la instrucción humana anterior y las aprobaciones de método y alcance son reales y proceden de la conversación. La asistencia se utilizó para extraer, clasificar, redactar y contrastar cobertura. Las precisiones humanas sobre adicional, exclusión y límites modulares se conservaron; no se inventa una aprobación del borrador. La evaluación global del uso de IA y su estimación porcentual de §9.2 quedan pendientes para su artefacto específico.

**Cierre de esta actividad:** la formulación aprobada se conserva con H v1.1, H v1.2/01 y las dos ratificaciones posteriores H v1.3/01–02, limitadas a baja lógica de productos y nombre histórico del proveedor. Conservar catálogo, fuentes, temporales y trazabilidad; no reabrir otros acuerdos ni incorporar adicionales condicionados por inferencia. Las siguientes actividades de §6.1 (RNF, restricciones/supuestos) y diseño se tratarán por separado cuando se soliciten. Esta aprobación documental no autoriza implementar software, hacer commits ni eliminar los temporales.

### Resolución posterior H v1.2/01 — Rechazo de transferencia

- **Ratificación:** el responsable indicó «Dejemos que el rechazo lo haga el gerente de origen tambien» y respondió «Si confirmamos» a «¿Confirmamos que el gerente de origen podrá rechazar con motivo únicamente cuando la transferencia esté «en preparación», antes de aprobarla?».
- **Alcance integrado:** RF-0034-CA-03–RF-0034-CA-04 y matriz §5.2. Transición `en preparación → rechazada`, exclusivamente gerente de origen con motivo; no cambia inventario. Se mantiene preparación/despacho en el operador de origen. Se resuelve el vacío de actor y fase de INC-015/D06 sin modificar el antecedente ni atribuir esta decisión al enunciado externo.

### Resoluciones posteriores H v1.3/01–02 — Catálogo y nombre histórico del proveedor

- **H v1.3/01, 22 de octubre de 2026:** el responsable ratificó «Todos los productos deberán conservarse en la base de datos. No se permitirá su eliminación física, independientemente de que tengan o no referencias históricas, movimientos o dependencias». Sustituye la alternativa física anterior de INC-006/D01; RF-0011 conserva las condiciones de desactivación y reactivación sin modificar otras funciones. Trazabilidad: RF-0011-CA-01–04 → UC-006 → HU-005 → modelo de datos §§6.2, 7.4 y DAT-011.
- **H v1.3/02, 22 de octubre de 2026:** el responsable ratificó captura del nombre vigente al crear DRAFT, actualización al pasar a PENDING y congelación posterior, manteniendo `supplier_name_snapshot` NOT NULL. Trazabilidad: INC-012/D01 → RF-0021-CA-03 → UC-010 → HU-009 → modelo de datos §6.3 y DAT-009. No cambia estados, recepción ni condiciones de compra.
