# Sistema visual de las interfaces OptiPlant

**Estado:** dirección visual actualizada por solicitud expresa del responsable el 25 de octubre de 2026, siguiendo su referencia «Enterprise SaaS Admin Dashboard / Clean Enterprise UI». Aplicada a acceso, administración y consultas. Sustituye la propuesta anterior azul petróleo; es una identidad de interfaz, no un manual de marca institucional. La integración con backend real se comprueba por separado.

## Uso y alcance

Reutilizar los tokens y patrones de `frontend/src/styles/base.css` al implementar nuevos requisitos funcionales. Mantener el vocabulario, los permisos, los estados y las confirmaciones definidos por backend; no crear pantallas ficticias para completar la navegación.

La referencia arquitectónica sigue siendo [ARQ-001](../04-arquitectura/arquitectura-y-diseno-tecnico.md#arq-001--presentación-web-y-herramientas-del-frontend). Tailwind CSS y su adaptador Vite están instalados en la versión aprobada 4.3.3. Los componentes oficiales incorporados se adaptan al sistema visual existente; las demás bibliotecas aprobadas se incorporarán cuando su funcionalidad lo requiera.

La autorización del responsable permite instalar y adaptar componentes oficiales de shadcn/ui y sus dependencias necesarias sin aprobación individual. Antes de añadir uno, consultar `frontend/components.json`, `frontend/src/components/ui` y `frontend/src/components/shared`; reutilizar lo existente. Una biblioteca ajena al stack y a esas dependencias requiere justificación y autorización previa. Esta autorización está registrada también en [AGENTS.md](../../AGENTS.md#6-diseño-del-frontend-y-componentes-autorizados).

## Tokens reutilizables

| Función | Token CSS | Valor |
|---|---|---|
| Fondo de trabajo | `--color-background` | `#F7F9FC` |
| Superficie | `--color-surface` | `#FFFFFF` |
| Texto y estructura azul oscuro | `--color-text` | `#14213D` |
| Texto secundario | `--color-muted` | `#53647E` |
| Acción principal naranja | `--color-action` | `#C2410C` |
| Foco y enlaces | `--color-focus` | `#254B87` |
| Bordes sutiles | `--color-border` | `#DFE5EF` |
| Advertencia | `--color-warning` | `#895700` |
| Error y acción destructiva | `--color-danger` | `#B42318` |

- Tipografía del sistema, sin fuentes externas. Títulos de 24–32 px, subtítulos de 24 px y texto de 16 px con interlineado de 1,5 en la configuración predeterminada del navegador.
- Espaciado mediante `--space-*`, basado en incrementos de 4 px; radios diferenciados para controles y paneles.
- Alineación izquierda, textos explicativos de hasta 74 caracteres aproximados por línea y cantidades alineadas a la derecha con cifras tabulares.
- Números decimales con coma decimal y punto de miles (`1.250,000`), conservando la escala enviada por el servidor; se escriben con coma y se envían a la API con punto, sin redondear en el navegador (`frontend/src/lib/format.ts`). Fechas y horas con el formato `es-CO` compartido `dd/mm/aaaa, h:mm:ss a. m.` (año de cuatro dígitos, como en los documentos comerciales colombianos, y reloj de 12 horas); fechas sin hora, como el vencimiento, `dd/mm/aaaa`. Importes en COP con 2 decimales; precios unitarios con al menos 2 decimales y hasta los 6 que conservan las listas (`1.000,00`, `11.000,50`, `0,123456`); costos promedio con 6. Los porcentajes se escriben entre 0 y 100 con coma. Si un campo no cumple su formato, el navegador muestra la ayuda del propio campo (`title`) en lugar del mensaje genérico (`frontend/src/components/shared/formatHints.ts`); la ayuda se revisa de nuevo al pulsar un botón o Enter, para que un valor corregido sin escribir, como un formulario precargado, nunca quede bloqueado. Los avisos de éxito se retiran al salir de la vista. Códigos de rol, tipo, dirección y estado se muestran con su etiqueta en español.
- Reservar naranja para las acciones principales y el contexto de navegación activo. Se usa un naranja más oscuro que el de la imagen para dar contraste al texto blanco; sombras discretas, sin gradientes decorativos ni iconos multicolor.

## Patrones de interfaz

| Necesidad | Patrón actual |
|---|---|
| App Shell | `AppShell`, en `frontend/src/components/shared`: barra lateral persistente desde 1024 px, identidad, navegación y resumen de sesión; en móvil la navegación se adapta al ancho disponible. |
| Navegación operativa | `.section-nav`, enlaces por fragmento a vistas existentes con `aria-current`; una vista visible a la vez, historial del navegador y foco en el título al cambiar. No sustituye la adopción aprobada de React Router. |
| Navegación contextual | `SectionTabs` compone los `Tabs` oficiales con primitivas Radix: roles ARIA, flechas, Inicio y Fin. `forceMount` y `hidden` mantienen paneles y formularios sin exponer los inactivos al teclado. |
| Acceso al contenido | `.skip-link`, primer enlace de teclado, con destino enfocable. |
| Contexto de usuario y sucursal | `.session-panel`, sin ocultar ni abreviar los identificadores. |
| Acción principal | `Button` compartido, variante predeterminada naranja; texto que describe la operación. |
| Iconografía | Lucide React 1.34.0 (ARQ-001/A PEN-006, arquitectura §5.4.2), con importaciones de iconos concretos. El icono acompaña al texto, nunca lo sustituye, y se marca `aria-hidden`. Navegación: Administración `Building2`, Consultas `Search`, Catálogo `Package`, Mi sesión `UserRound`, Contraseña `KeyRound`. Alertas de reabastecimiento `BellRing`. Compras `ShoppingCart`. Ventas `ReceiptText` (también en «Confirmar venta»). Valoración `Coins`. Acciones: crear `Plus`, actualizar listado `RefreshCw`, editar `Pencil`, guardar `Save`, desactivar `Ban`, reactivar `RotateCcw`, habilitar en sucursal `Store`, calcular equivalencia de unidades `Calculator`, cerrar formulario `X`, quitar una línea `Trash2`, enviar a recepción `Send`, confirmar recepción `PackageCheck`, cancelar una orden `Ban` (misma familia que desactivar: deja el registro sin efecto y lo conserva), crear orden de reemplazo `CopyPlus`, iniciar y cerrar sesión `LogIn`/`LogOut`. Reutilizar el mismo icono para la misma acción en todas las vistas. |
| Formularios de alta y edición | Se montan solo tras «Nuevo…» o «Editar»; no hay formularios inactivos al cargar. `revealEditor` lleva el formulario a la vista y enfoca su primer campo editable, también cuando ya estaba abierto, sin descartar el borrador. «Cerrar formulario» lo retira de forma explícita. Los borradores abiertos se conservan al cambiar de pestaña o sección. |
| Contraseñas | `PasswordField`, en `components/shared`: etiqueta asociada y botón con icono `Eye`/`EyeOff`, fuera de la etiqueta para no alterar el nombre accesible del campo, con nombre «Mostrar/Ocultar …», `aria-pressed` y `aria-controls`. Vuelve a ocultarse al enviar o restablecer el formulario. |
| Acción secundaria o destructiva | `Button variant="outline"`; las bajas añaden texto rojo y conservan reglas existentes. Las desactivaciones y reactivaciones se confirman con `useConfirmation` (`components/shared/ConfirmDialog.tsx`), que compone el `AlertDialog` oficial: título con el registro afectado, consecuencias, «Cancelar» con foco inicial y acción destructiva en rojo. No usar `window.confirm`. |
| Campos | `Input` compartido, con nombres, límites, autocompletado y validación HTML existentes; `Textarea` para texto libre opcional. Mantener `select` y casillas nativas (`.checkbox-field`) cuando no se necesite una interacción adicional. |
| Catálogo de productos | Tabla con SKU destacado, nombre y descripción, unidad base y estado; edición por registro solo para administración. La habilitación local se muestra en una tabla separada de los datos del producto y nunca como existencias. La desactivación es una acción destructiva con confirmación; la reactivación, una acción secundaria. Los rechazos de negocio se muestran en la vista; solo los problemas de sesión se elevan al contenedor. |
| Administración tabular | `Table` compartido con código destacado, nombre legible, estado textual con `StatusBadge` y edición por registro. Los usuarios muestran perfil traducido y sucursal asignada desde datos ya consultados, sin nuevas llamadas ni campos de negocio. |
| Consulta tabular | `.table-scroll`, región con nombre y foco de teclado; conservar `caption`, encabezados y precisión textual de cantidades. |
| Carga y resultado confirmado | `role="status"`; conservar las regiones vivas y `aria-busy` existentes. |
| Error o desconexión | `role="alert"`, texto explicativo y acento rojo, en la vista donde ocurrió. El aviso general del shell queda reservado a problemas de sesión y se descarta al cambiar de vista. No confirmar éxito ni sincronización offline. |
| Advertencia | `.notice-warning`, texto explícito y acento ámbar, como una sucursal inactiva. |
| Existencias con actualización automática | Encabezado con sucursal identificada, `StatusBadge` de estado y «Actualizar ahora» (`RefreshCw`); estado con hora de la última lectura del servidor y `aria-live="off"` para no anunciar cada polling; ante desconexión, `.notice-warning` conserva la tabla previa. Físico, disponible y tránsito en columnas separadas con unidad base. |
| Elección de tipo con efecto | `.choice-list` y `.choice`: radios nativos dentro de una etiqueta con nombre y descripción breve; la opción elegida se marca con borde azul oscuro además del radio, sin depender del color. `.choice-inline` para dos opciones cortas como «Incrementa»/«Disminuye». |
| Historial auditable | Tabla `movement-history` en `.table-scroll`, con ancho mínimo para no partir palabras y desplazamiento horizontal propio; cantidad con signo (+/−) y unidad, equivalente en unidad base y responsable como `.cell-note`. Costos solo para perfiles autorizados. Icono de navegación `ArrowLeftRight`. |
| Alertas de reabastecimiento | Gerente y administrador: entrada **Alertas** con conteo visible (`.nav-count`, oculto a lectores) y nombre accesible con el número en palabras; aviso persistente `.notice-warning.restock-notice` con icono `TriangleAlert` y enlace a la vista, fuera de regiones vivas para no anunciarse en cada polling. La vista reutiliza el patrón de existencias con actualización automática, `.choice-inline` para «Alertas activas»/«Historial completo» y tabla `restock-table` con disponible y mínimo vigente alineados a la derecha y los valores «Al activarse» como `.cell-note`. `RestockBadge` (`components/shared`, sobre `Badge`) muestra el estado como texto con el token `--color-warning-muted`. El operador ve solo la columna **Reabastecimiento** de su catálogo local. |
| Compras | Vista **Compras**: el operador abre «Nueva orden de compra»; cada línea es un `fieldset` con producto, unidad, cantidad, precio, descuento y neto exacto calculado sin convertir a número (`lib/purchases`), apilado en móvil y en una fila desde 1024 px; el total neto estimado cierra el formulario. Filtros con etiquetas explícitas, resumen por estado calculado por el servidor (`.purchase-summary`, sin cifras derivadas del listado), tabla `purchase-history` desplazable y panel de detalle con condiciones, líneas e historia de estados en lista ordenada. `PurchaseStatusBadge` (sobre `Badge`) nombra el estado con texto; pendiente usa el tono de advertencia, recibida el de éxito y cancelada se tacha. Enviar a recepción y confirmar recepción pasan por `useConfirmation`. El administrador ve «Órdenes de compra» y «Proveedores» con `SectionTabs`. RF-0022: el detalle agrupa sus acciones en `.purchase-actions`; «Cancelar orden» despliega en el propio panel un formulario `.purchase-cancel` (borde izquierdo de peligro) con motivo opcional, «Confirmar cancelación» destructivo y «Volver sin cancelar», que conserva el texto mientras el detalle siga abierto. «Editar borrador» y «Crear orden de reemplazo» reutilizan el editor de la orden; el reemplazo muestra una nota `.purchase-replacement-note` y los vínculos «Reemplaza a» / «Reemplazada por» abren la otra orden. |
| Ventas | Vista **Ventas** (RF-0027–RF-0029, RF-0031): el operador abre «Nueva venta», elige la lista de precios y cada línea reutiliza el `fieldset` `.purchase-line` con producto, unidad, cantidad, descuento %, precio de lista (con el neto unitario como nota) y neto exacto (`lib/sales`, sin convertir a número). Bajo cada línea, `.sale-line-note` muestra el disponible en unidad base y cambia a `notice-warning` cuando las líneas del producto lo superan; es un aviso, el servidor decide. Los totales usan `.sale-totals` (lista de definiciones alineada a la derecha: subtotal de lista, descuentos y total neto destacado). «Confirmar venta» pasa por `useConfirmation`; el borrador se conserva al cerrar el formulario y se descarta solo con «Descartar borrador». El listado reutiliza `.purchase-history` y el comprobante el panel `.purchase-detail` con `.purchase-facts`, tabla desplazable y aviso de registro interno sin validez fiscal. Estados de carga, vacío, éxito, rechazo y error de comunicación. |
| Transferencias | Vista **Transferencias** (RF-0032–RF-0034): el operador de destino abre «Solicitar transferencia» con el origen y líneas `.purchase-line.transfer-line` (producto, unidad, cantidad). El detalle muestra la ruta origen→destino (`.transfer-route`, flecha en el color de acción) y el avance del ciclo como lista ordenada `.transfer-steps` (2×2 en móvil, 4 columnas desde 640 px; el paso actual en color de acción con `aria-current="step"`); estados cerrados se explican en `.notice-warning` con responsable y motivo. La tabla separa «Solicitado», «A enviar» y, solo para el origen y el administrador, «Disponible en origen». La preparación se edita en el propio panel (`.transfer-prepare`) con aviso por línea: superar lo solicitado bloquea el guardado; superar la disponibilidad solo advierte. Aprobar pasa por `useConfirmation`; rechazar y cancelar reutilizan `.purchase-cancel` con motivo. `TransferStatusBadge` (sobre `Badge`) nombra el estado; cancelada y rechazada se tachan. RF-0035: «Registrar despacho» abre en el panel `.transfer-dispatch` (sobre `.purchase-cancel`) con las cantidades que salen, la ruta con su prioridad o un `.notice-warning` si no hay ruta activa, y transportista, salida real y llegada estimada en `datetime-local` (America/Bogota, indicada en la pista); la pista se convierte en el mensaje bloqueante tras intentar confirmar. Despachada, `.purchase-facts` añade transportista, fechas y ruta, y la tabla la columna «Enviado». |
| Rutas | Vista **Rutas** (RF-0041), icono `Route`: un grupo `.route-group` por prioridad, de alta a baja, cada uno con su conteo y tabla desplazable; un grupo vacío lo dice. `RoutePriorityBadge` (`components/shared`, sobre `Badge`) nombra la prioridad: alta con el tinte de acento, media con el secundario y baja con contorno; nunca depende solo del color. El administrador registra y edita en un formulario con etiquetas explícitas; al editar, origen y destino se muestran como `.transfer-route` fijo. Estados de carga, vacío (que indica quién debe registrar rutas), éxito y error. |
| Valoración | Vista **Valoración** (RF-0026), solo para gerente y administrador: el administrador elige la sucursal en un formulario con etiqueta explícita; el resumen reutiliza `.purchase-summary` (valor total con el acento de éxito y número de productos) y la tabla `valuation-table` desplazable muestra existencias, costo promedio a 6 decimales y valor a 2, con el valor exacto como nota cuando tiene más decimales, y un pie `TableFooter` (shadcn/ui) con el total. Estados de carga, vacío («La sucursal no tiene existencias valoradas…») y error de comunicación. |
| Vacío y datos desactualizados | Conservar mensajes de ausencia de registros y fecha de consulta; no inventar datos ni estados de éxito. |

Los controles tienen un mínimo de 44 px de alto con el tamaño de fuente predeterminado. El foco es visible; los estados no dependen exclusivamente del color. No se añaden animaciones automáticas y se respeta la preferencia de movimiento reducido. La región de tabla admite desplazamiento horizontal sin extender el documento.

### Adaptación funcional de la referencia

No copiar tarjetas KPI, auditoría, correo electrónico, idiomas o módulos que no estén implementados. Los indicadores y sus cálculos pertenecen al servidor según ARQ-001; no generar cifras a partir de listados para decorar la pantalla. Cuando sus requisitos se implementen, las tarjetas KPI usarán superficie blanca, etiqueta legible y valor con unidad y contexto, sin depender solo del color. Los filtros y la paginación se incorporarán con los contratos funcionales correspondientes, sin controles inertes ni filtrado que contradiga la coordinación con backend.

La consulta de sucursales conserva su selector real y la fecha de actualización. La navegación no cambia el ámbito de escritura, no concede permisos y no descarta formularios ocultos. Los componentes nuevos son de presentación; no contienen reglas de negocio ni llamadas REST.

## Componentes oficiales incorporados

Incorporación manual y adaptación del registro oficial `new-york-v4`, consultado el 25/10/2026; no se ejecutó un CLI flotante ni se instaló una biblioteca runtime llamada `shadcn/ui`. Su código está en `frontend/src/components/ui`, con [licencia MIT conservada](../../frontend/src/components/ui/LICENSE.txt). Si se usa el CLI en futuras incorporaciones, arquitectura §5.4.2 fija `shadcn` 4.22.1; esa versión no congela el registro remoto.

| Componente | Fuente oficial | Uso actual |
|---|---|---|
| Button | [Registro](https://ui.shadcn.com/r/styles/new-york-v4/button.json) | Acceso, cambio de contraseña, administración y consulta. |
| Input | [Registro](https://ui.shadcn.com/r/styles/new-york-v4/input.json) | Campos de acceso, sucursales y usuarios. |
| Badge | [Registro](https://ui.shadcn.com/r/styles/new-york-v4/badge.json) | Estado activo/inactivo, mediante `StatusBadge`; estado de reabastecimiento, mediante `RestockBadge`; estado de compra, mediante `PurchaseStatusBadge`; estado de transferencia, mediante `TransferStatusBadge`. |
| Table | [Registro](https://ui.shadcn.com/r/styles/new-york-v4/table.json) | Sucursales, usuarios y existencias. `TableFooter`, presente en la misma instantánea del registro y omitido en la primera adaptación, se incorporó el 27/10/2026 para el total de la valoración (RF-0026), con las clases originales. |
| Tabs | [Registro](https://ui.shadcn.com/r/styles/new-york-v4/tabs.json) | Navegación contextual de administración mediante `SectionTabs`. |
| Textarea | [Registro](https://ui.shadcn.com/r/styles/new-york-v4/textarea.json) | Descripción opcional del producto en el catálogo, motivo de los movimientos y motivo opcional de la cancelación de una orden de compra. |
| Alert Dialog | [Registro](https://ui.shadcn.com/r/styles/new-york-v4/alert-dialog.json), consultado el 27/10/2026 | Confirmación de desactivaciones y reactivaciones mediante `useConfirmation`. |

SHA-256 del campo `files[0].content` original, antes de las adaptaciones locales; sirve para identificar la instantánea consultada, no como hash del archivo adaptado:

```text
button 79dd6f75f8136394442202d6b8b922fb269eaad0a5dba579397c9d5b41f893bb
input  0c9457181f6ddc80969bcf854e92c362903a55b6e889fbef3fd85343ecc4af5b
badge  13a9380dfff853ee730c14a52df49f22aecd31f40f2998d7c1e7ec458b6e1650
table  bd1a9fc83a5a2149e504038e274bb7e5a0351af59f6896df52ac4d5b439695ac
tabs   e1df6e2f95ece373ca909c1d54b8ba8cf4c47dd425c1784bbb9a01c38dd0936b
textarea 5cafcfeeee29534f449387a16b585f5923d2faab1a0cdc50a874416b175f59ce
alert-dialog b3dfb83e54ed8c82071df5289e5149628bf573458fc2da2d62f93f8380041b28
```

**Adaptaciones locales:** alias `@/lib/utils` para `cn`; `Textarea` con superficie, foco y altura mínima alineados con `Input`, sin variantes oscuras; primitivas individuales oficiales `@radix-ui/react-tabs` y `@radix-ui/react-slot` en lugar de importar el conjunto completo `radix-ui`; tamaños táctiles mínimos de 44 px; variantes y colores OptiPlant; sin variantes oscuras, verticales o tamaños no utilizados. `AlertDialog` usa la primitiva individual `@radix-ui/react-alert-dialog`, superficie `bg-card`, cabecera alineada a la izquierda, sin clases de animación (el proyecto no incluye su hoja `tw-animate-css`) y sin `Trigger`, `Media` ni tamaño `sm`, que no se utilizan. La tabla usa una sola región externa identificada y enfocable para desplazarla con teclado, evitando dos contenedores de scroll. El montaje persistente de las pestañas se configura en la composición compartida. No se presenta el código adaptado como una copia sin modificaciones del registro.

**Dependencias directas necesarias:** `lucide-react` 1.34.0 (iconos aprobados en arquitectura), `@radix-ui/react-tabs` 1.2.1, `@radix-ui/react-alert-dialog` 1.2.1 (versión estable publicada el 12/10/2026, incorporada el 27/10/2026), `@radix-ui/react-slot` 1.5.0, `class-variance-authority` 0.7.1, `clsx` 2.1.1 y `tailwind-merge` 3.7.0. Tailwind y `@tailwindcss/vite` 4.3.3 son herramientas de construcción. Sus versiones exactas y transitivas quedan fijadas en el manifiesto y lockfile; no se añaden gráficas, calendarios ni otro sistema de componentes; Lucide React se incorporó después con la versión fijada en arquitectura.

Tailwind importa sus capas de tema y utilidades, sin Preflight, para no reiniciar los estilos de HTML existentes. Los estilos de aplicación se ubican en `@layer components`, por debajo de las utilidades. No sobrescribir estilos de componentes oficiales con reglas globales de mayor precedencia; preferir sus variantes y `className` con `cn`. `components.json` y los alias de TypeScript/Vite documentan dónde reutilizar o incorporar código.

## Comprobaciones reproducibles

```sh
nvm use 24.22.1
cd frontend
pnpm test
pnpm build
```

Usar las versiones fijadas y la [guía de instalación nativa](../../frontend/README.md#ejecución-nativa-en-macos-arm64). Para servir el frontend en el puerto de la comprobación, ejecutar `pnpm dev --host 127.0.0.1 --port 4175 --strictPort` dentro de `frontend`. Con ese servidor activo y `playwright-cli` ya disponible, ejecutar la comprobación visual desde un directorio temporal externo al repositorio:

```sh
playwright-cli -s=optiplant-visual open http://127.0.0.1:4175
playwright-cli -s=optiplant-visual run-code --filename=<ruta-del-repositorio>/scripts/check_visual_browser.js
playwright-cli -s=optiplant-visual run-code --filename=<ruta-del-repositorio>/scripts/check_component_regressions.js
playwright-cli -s=optiplant-visual close
```

El script usa respuestas API simuladas y comprueba barra lateral, vista seleccionada, navegación de pestañas por teclado, carga de editores desde las tablas, conservación de formularios al navegar, visibilidad de enlaces por rol y cambio inicial de contraseña, formato de cantidades y ausencia de desbordamiento a 1440, 768, 390 y 320 px. No realiza escrituras en el backend ni demuestra integración full stack.

### Evidencia y límites de esta aplicación

- Se consultaron requisitos funcionales, RNF y ARQ-001 antes de definir la propuesta con `frontend-design`; el responsable aprobó su aplicación antes de editar.
- La instalación local inicial contenía la variante Linux ARM64 de Rolldown, pero no la de macOS ARM64; Vite fallaba antes de editar. Tras autorización expresa, se instaló Node 24.22.1 desde la distribución oficial con SHA-256 verificado y se reinstaló con pnpm 11.24.0 y `--frozen-lockfile`. No se modificaron `package.json` ni `pnpm-lock.yaml`. Las 14 pruebas, TypeScript y el build completo de producción pasan.
- Sin un servidor Vite ejecutable no se observó un ciclo RED→GREEN visual previo a la implementación. Se verificó la presentación en Chromium mediante un arnés temporal externo, generado desde los TSX y CSS actuales con TypeScript y React ya instalados; la comprobación de navegador pasa y se inspeccionaron capturas de escritorio y móvil. Este arnés no sustituye la compilación de producción.
- Una segunda ejecución del mismo script pasó con el servidor Vite real y las versiones fijadas, a 1440, 768, 390 y 320 px. Esto reemplaza la limitación del arnés temporal para la comprobación de presentación; las respuestas API siguen siendo simuladas.
- Para la adaptación a la nueva imagen se amplió la comprobación antes de editar: falló con «Authenticated shell must have a persistent sidebar» sobre la versión anterior. Tras implementar el App Shell y las pestañas, la comprobación ampliada pasa; también pasan las 14 pruebas existentes y el build. Se inspeccionaron capturas de escritorio y móvil. No se añaden herramientas de UI o navegación al manifiesto.
- En la etapa posterior, autorizada expresamente para shadcn/ui, la prueba ampliada falló antes de implementar con «Login must reuse the official shared button». Se incorporaron los cinco componentes y sus dependencias, conservando las versiones previamente fijadas del stack. La activación de pestañas por teclado espera la actualización asíncrona de foco de Radix antes de comprobar el resultado. Pasan la instalación congelada, las 14 pruebas, TypeScript, build y navegador; se comprobó además un rechazo sin cambios de datos simulados, sin reintento ni éxito previo retenido.
- Un verificador independiente contrastó esta migración con la instantánea previa, en directorios temporales externos: instalación congelada limpia desde caché, pruebas, build y navegador pasan; no reprodujo bloqueos. También comprobó validación vacía, conservación de borradores entre pestañas, controles deshabilitados durante solicitudes y rechazo sin reintento. Ese probe se conserva en `scripts/check_component_regressions.js`. La estructura de Docker/lockfile se revisó, pero no se ejecutó una construcción Docker/Linux en esta etapa.
- La preparación posterior de commits incluyó una comprobación real de 10 escenarios UI/API sobre una pila Compose desechable, sin tocar los volúmenes del responsable. Pasaron backend (31 pruebas), humo de integración y frontend Docker (14 pruebas, tipos y build). Se corrigió el checker para no copiar `node_modules` del host hacia Linux, con regresiones y verificación independiente del riesgo 5. La evidencia de entrega y los límites funcionales pendientes se registran en [las tareas RF-0002–RF-0007](../../odd/tasks/rf-0002-0007.md). La instalación de componentes por sí sola no acredita integración ni funcionalidades todavía no implementadas.
