# Diagramas UML de casos de uso

**Objetivo:** producir diagramas UML editables en Excalidraw para la sección 7, preservando la trazabilidad UC-001 a UC-020.
**Estrategia de entrega:** documentación local, sin commit ni publicación; verificación estructural y visual local.
**Herramientas:** Excalidraw como fuente editable; sin representación SVG en esta entrega.

## Specs

### S1
Generar una colección de diagramas UML de casos de uso organizada por los nueve ámbitos: administración e identidad, catálogo, inventario, compras, ventas, transferencias, logística, dashboard y reportes exportables.

### S2
Conservar los identificadores UC-001 a UC-020 y su trazabilidad; representar referencias entre ámbitos sin duplicar definiciones ni inventar funcionalidades.

### S3
Identificar límite del sistema o ámbito funcional, casos de uso, actores, asociaciones autorizadas y solo relaciones UML justificadas. Respetar la generalización de roles respecto a Usuario interno, `UC-017 <<extend>> UC-016` y las especializaciones de exportación de UC-020, usando conexiones enlazadas a sus elementos Excalidraw, con acciones identificables y sin cruces ambiguos.

### S4
No modelar pasos internos, comprobaciones técnicas, operaciones de base de datos, mecanismos de autenticación como casos adicionales ni sistemas externos excluidos. Respetar rigurosamente la matriz funcional: administrador sin operaciones locales por herencia; gerente sin operaciones exclusivas del operador.

### S5
Usar Excalidraw editable, mantener estructura documental y comprobar cobertura UC-001 a UC-020, coherencia de asociaciones, ausencia de casos inventados, omisiones y permisos incorrectos. Incluir al final de cada gráfico las decisiones importantes que representa. No modificar especificación, requisitos ni decisiones aprobadas.

### S6
Unificar los nueve diagramas según la referencia visual aprobada: fondo `#f8f9fa`; encabezado y subtítulo; límite de módulo blanco con borde gris discontinuo; casos uniformes con borde `#1971c2` y fondo `#d0ebff`; actores a la izquierda con azul para administrador, naranja para operador y verde para gerente; asociaciones limpias, continuas, sin flechas ni cajas decorativas, con acciones identificables vinculadas a sus líneas; y notas discretas por debajo del último elemento. Conservar íntegros los nombres de los casos de uso.

## Tasks

- [x] T1 | S1-S6 | direct inline | completada | Evidencia: 9 escenas `.excalidraw` JSON válidas, con estilo unificado de la referencia, asociaciones continuas enlazadas y sin etiquetas decorativas, notas discretas y comprobación local de UC-001..UC-020 y relaciones UML autorizadas.

## Log

- L1 (2026-10-21): Necesitamos elaborar los diagramas UML de casos de uso correspondientes a la sección 7 de la prueba técnica de OptiPlant.
Utiliza como fuente principal especificacion-casos-de-uso.md, contrastándola con los requisitos funcionales, las decisiones ratificadas, la arquitectura y las instrucciones vigentes del proyecto. Genera una carpeta para estos diagramas de documentacion
Organización de los diagramas
Genera una colección de diagramas UML de casos de uso organizados por módulos funcionales, en lugar de diagramas independientes por actor.
Toma como distribución inicial los nueve ámbitos: administración e identidad, catálogo, inventario, compras, ventas, transferencias, logística, dashboard y reportes exportables.
Conserva los identificadores UC-001 a UC-020 y su trazabilidad. Si un mismo caso interviene conceptualmente en varios módulos, representa las referencias necesarias sin duplicar su definición ni inventar funcionalidades.
Representación UML
Cada diagrama debe identificar claramente:
El límite del sistema o ámbito funcional.
Los casos de uso correspondientes y sus identificadores.
Los actores que interactúan con ellos.
Las asociaciones que correspondan a las responsabilidades autorizadas.
Las relaciones UML de generalización, <<include>> y <<extend>> únicamente cuando estén justificadas por la especificación.
Respeta la generalización de los roles respecto a Usuario interno, la extensión UC-017 sobre UC-016 y las especializaciones de exportación de UC-020.
Evita representar pasos internos, comprobaciones técnicas, operaciones de base de datos o mecanismos de autenticación como casos de uso adicionales.
No incorpores sistemas externos excluidos por el alcance.
Precisión de roles y permisos
Verifica rigurosamente que las relaciones entre actores y casos de uso respeten la matriz funcional vigente.
Distingue los permisos de consulta, supervisión, administración y ejecución de operaciones.
No atribuyas al administrador operaciones locales por herencia, ni al gerente operaciones que correspondan exclusivamente al operador.
Generación y presentación
Utiliza PlantUML como formato fuente editable y genera también las representaciones SVG para visualización.
Organiza los archivos siguiendo la estructura documental del proyecto.
Prioriza la legibilidad, con tamaños adecuados, separación entre actores y casos y ausencia de cruces innecesarios.
No fuerces relaciones UML para conectar visualmente casos que solo comparten datos o reglas internas.
Verificación final
Comprueba que todos los casos UC-001 a UC-020 estén representados al menos una vez, que sus asociaciones sean coherentes con la especificación y que no existan casos inventados, omisiones o permisos incorrectos.
Realiza directamente la generación y verificación de los diagramas, sin ciclos intermedios de aprobación. No modifiques la especificación original, los requisitos ni las decisiones funcionales aprobadas.
Entrega los diagramas editables y sus representaciones visuales, indicando brevemente la cobertura de casos de uso y cualquier discrepancia documental real identificada.
- L2 (2026-10-21): La exploración confirma que `especificacion-casos-de-uso.md` v1.1 es definitiva para el modelado. La matriz de relaciones omite explícitamente las asociaciones de Usuario interno con UC-004 y UC-005, aunque sus fichas lo establecen como actor principal; se representarán por ser asociaciones directas de las fichas, sin añadir relaciones entre casos.
- L3 (2026-10-21): No se detectó PlantUML ni Graphviz instalados. Se producirán fuentes `.puml` y SVG equivalentes generados localmente, dejando constancia de la limitación de renderer nativo.
- L4 (2026-10-21): Se crearon `docs/02-casos-de-uso/diagramas/` y `svg/` con nueve diagramas por ámbito. La comprobación local confirmó cobertura exacta UC-001..UC-020, nueve SVG XML válidos, ausencia de relaciones `<<include>>` y de sistemas externos, la extensión UC-017 sobre UC-016, especializaciones de UC-020 y la ausencia de asociaciones del administrador con operaciones de transferencia.
- L5 (2026-10-21): No me ha gustado lo que has generado, cambiemos el modo de representacion a .excallidraw trata de que las conexiones y todo quede bien hecho no necesitas hacer las representaciones en SVG en el momento,entonces elimina lo que acabas de hacer con puml y los svg y genera los excallidraw
- L6 (2026-10-21): Se eliminaron las nueve fuentes PlantUML y el directorio SVG. Se generaron nueve escenas Excalidraw con límite de sistema, actores UML, casos, etiquetas y flechas enlazadas mediante `startBinding`/`endBinding`. La comprobación local validó JSON, cobertura UC-001..UC-020, asociaciones enlazadas, extensión UC-017 y especializaciones de UC-020.
- L7 (2026-10-21): Si no has hecho los cambios en los 9 diagramas, hazlos
- L8 (2026-10-21): Se actualizaron los nueve diagramas: cada asociación usa una ruta ortogonal en carril propio y etiqueta de acción, y todos incluyen el bloque «Decisiones importantes representadas» al pie. La validación confirmó las conexiones enlazadas, cobertura UC-001..UC-020 y las relaciones UML autorizadas.
- L9 (2026-10-21): Necesito corregir y unificar el diseño visual de los diagramas UML de casos de uso que estás generando en Excalidraw para OptiPlant.

Te proporciono dos archivos de referencia:

- `casos_de_uso_02_inventario_ejemplo.excalidraw`: **referencia visual principal**, cuyo estilo de presentación quiero adoptar.
- `03-inventario.excalidraw`: diagrama actual que debes corregir visualmente.

**Objetivo:** conservar íntegramente el contenido funcional y las relaciones UML del diagrama actual, pero rediseñar su presentación siguiendo el estilo, proporciones, colores, tipografía, distribución y limpieza visual del archivo de ejemplo.

### 1. Estilo visual

Adopta las siguientes características del archivo de referencia:

- Fondo general claro (`#f8f9fa`).
- Título principal destacado, alineado a la izquierda, con tipografía legible y aproximadamente 30 px.
- Subtítulo descriptivo más pequeño y gris.
- Contenedor del módulo con fondo blanco, borde gris discontinuo y título centrado en la parte superior.
- Casos de uso representados mediante óvalos amplios, uniformes y con bordes azules (`#1971c2`), fondo azul claro (`#d0ebff`) y texto oscuro.
- Identificador del caso de uso en una primera línea, con tipografía secundaria azul; nombre descriptivo centrado debajo.
- Tipografía y tamaños consistentes, tomando como referencia las proporciones del ejemplo.
- Espaciado generoso entre elementos para mejorar la lectura y evitar saturación.

### 2. Actores y distribución

Organiza los actores fuera del límite del sistema, preferentemente a la izquierda, y los casos de uso dentro de él, organizados verticalmente.

Utiliza la diferenciación cromática del ejemplo: azul para administrador, naranja para operador y verde para gerente, cuando estos actores estén presentes.

Conserva la notación UML convencional de actores cuando sea necesaria para mantener la corrección formal del diagrama. Puedes aplicar la paleta y el estilo del ejemplo sin sustituir obligatoriamente los actores por óvalos.

No elimines, agregues ni reasignes actores por motivos estéticos. Verifica sus asociaciones contra la especificación de casos de uso y la matriz de permisos aprobada.

### 3. Conectores y relaciones UML

Este es uno de los principales aspectos que debes corregir.

- Prioriza conexiones simples, visualmente limpias y fáciles de seguir.
- Reduce los recorridos ortogonales largos, los cambios de dirección innecesarios y los cruces entre conectores.
- Redistribuye los actores y casos de uso cuando sea necesario para mejorar el recorrido de las asociaciones.
- Evita que las líneas atraviesen textos, óvalos, títulos u otros elementos.
- Utiliza líneas continuas sin flechas para las asociaciones ordinarias entre actores y casos de uso.
- Conserva la notación UML correspondiente para generalización, `<<include>>` y `<<extend>>` únicamente cuando esas relaciones existan y estén justificadas.
- Elimina etiquetas decorativas sobre las asociaciones, como las cajas amarillas del diagrama actual. Si una distinción de permisos es necesaria, represéntala correctamente o consérvala en una nota discreta sin cambiar el significado funcional.

Los conectores deben mantenerse correctamente vinculados a los elementos de Excalidraw para facilitar modificaciones posteriores.

### 4. Notas y elementos complementarios

Elimina los recuadros informativos visualmente dominantes que rompan la composición principal.

Cuando una nota sea relevante y esté justificada, colócala en un recuadro discreto en la parte inferior, siguiendo el ejemplo de referencia, con borde gris discontinuo y fondo neutro.

No introduzcas explicaciones extensas ni reglas de negocio dentro del diagrama. Estas pertenecen principalmente al documento de especificación de casos de uso.

### 5. Integridad funcional y documental

El archivo de ejemplo debe utilizarse exclusivamente como referencia estética, nunca como fuente para sustituir los casos de uso.

En particular:

- Conserva los identificadores oficiales UC-001 a UC-020 según los diagramas correspondientes.
- No reemplaces los casos UC-007, UC-008 y UC-009 del archivo actual por los CU-INV del ejemplo.
- Respeta los nombres, actores, asociaciones y relaciones aprobadas.
- No inventes casos de uso para completar espacios vacíos.
- No dividas ni fusiones casos de uso únicamente por razones visuales.
- No modifiques los requisitos funcionales, las decisiones ratificadas ni la especificación original.
- No sacrifiques la corrección UML en favor del diseño visual.

### 6. Consistencia entre diagramas

Establece una guía visual uniforme a partir del archivo de referencia y aplícala al conjunto de diagramas por módulo.

Los diagramas deben compartir colores, tipografía, tamaños relativos, disposición de actores, estilo de conexiones y encabezados, manteniendo flexibilidad de composición según la cantidad de casos de uso.

Evita que cada módulo parezca generado con una plantilla diferente.

### 7. Ejecución y verificación

Comienza corrigiendo `03-inventario.excalidraw` y utilízalo como referencia de estilo para los demás diagramas existentes.

Trabaja directamente sobre los archivos editables `.excalidraw`, conservando su formato válido. No los reemplaces únicamente por imágenes.

Antes de finalizar, inspecciona el resultado visual de los diagramas generados, verificando:

- Legibilidad de los textos.
- Alineación y espaciado de óvalos y actores.
- Ausencia de superposiciones.
- Conectores comprensibles y correctamente vinculados.
- Consistencia con la referencia visual.
- Corrección de identificadores, actores y relaciones UML.

Si existe capacidad de renderizado, genera vistas previas para comprobar el resultado antes de considerar terminada la actividad.

**Resultado esperado:** diagramas UML de casos de uso formalmente correctos, editables en Excalidraw, con una estética uniforme y profesional que reproduzca el estilo visual del ejemplo sin alterar el contenido funcional aprobado.

Realiza las correcciones directamente, sin ciclos intermedios de propuestas o aprobación.
- L10 (2026-10-21): Se rediseñaron los nueve diagramas en el estilo de la referencia: fondo claro, encabezado/subtítulo, límite blanco discontinuo, óvalos azules uniformes, actores tipo persona y colores por rol. Las asociaciones ordinarias son continuas, sin flechas ni cajas amarillas, y permanecen vinculadas. Las notas inferiores son discretas. La validación confirmó JSON, estilos, bindings, textos contenidos, UC-001..UC-020, UC-017 sobre UC-016 y especializaciones de UC-020.
- L11: Revisemos nuevamente los estilos, menten los nombres de los casos de uso de los ovalos y las acciones de las lineas que se identifiquen, nuevamente revisa la instruccion de estilo, manten las anotaciones abajo del diagrama mas abajo del ultimo elemento, actualiza los 9 diagramas
- L12: Se actualizaron los nueve archivos conservando los nombres originales y los extremos y tipos de todas las relaciones. Se añadieron 39 textos de acción vinculados a sus asociaciones, sin cajas; se ajustaron el espaciado y el salto de línea de los nombres. Las notas se sitúan 85 px bajo el último elemento. La validación comprobó cobertura UC-001..UC-020, integridad de bindings, preservación de nombres y relaciones y ausencia de superposiciones entre textos de acción y otros textos. No se realizó renderizado nativo de Excalidraw.
- L13: Me ha gustado como lo has hecho me gustaria que hicieramos otra version de la carpeta con el fin de mantener una copia de seguridad de estos, lo que quiero es tomar esta version que tenemos actualemten que es buenisima y lo unico que quiero es que en los Ovalos azules de los UC (UC-007,UC-008 y demas) pongas un nombre representativo del UC para no dejarlo solo con el identificador si no que sea tambien suficientemente descriptivo para no buscar a que se refier el codigo en otro docuemento
- L14: Se creó `docs/02-casos-de-uso/diagramas-v2/`, conservando la carpeta `diagramas/` sin cambios y verificando todos sus archivos mediante SHA-256. En los nueve diagramas nuevos, el código y nombre completo de cada UC forman una única etiqueta vinculada a su óvalo; antes había dos textos vinculados a cada óvalo. Las tres especializaciones conservan su nombre, sin inventar identificadores. Se validaron los 20 UC, nueve escenas JSON y las conexiones. Actores, geometría, acciones, notas y relaciones se conservaron sin cambios; no se realizó renderizado nativo.
