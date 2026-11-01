# Uso de inteligencia artificial — OptiPlant

Este documento responde a la sección 9 de la prueba técnica. Los prompts son textuales: se copiaron del historial de las sesiones, incluidas sus erratas. Las cifras provienen de ese historial y del repositorio al 31 de octubre de 2026.

## 1. Enfoque

La IA fue el componente estructural del desarrollo. El responsable definió el alcance, ratificó las decisiones, escribió las instrucciones y verificó los resultados. Los asistentes analizaron, redactaron, implementaron y probaron bajo esas instrucciones.

Los prompts son de autoría propia del responsable. Se ajustaron con ChatGPT para lograr mayor efectividad y un mejor entendimiento por parte del agente.

El trabajo siguió Organic Driven Development (ODD), el flujo de Gentle AI: explorar, clasificar la tarea, implementar y verificar. Las reglas permanentes del proyecto están en [`AGENTS.md`](../../AGENTS.md), y las tareas de mayor tamaño dejaron su registro en [`odd/tasks/`](../../odd/tasks/).

## 2. Herramientas y etapas

| Herramienta | Etapa | Uso |
|---|---|---|
| OpenCode con Gentle AI (GPT-6.1 Sol, GPT-5.6 Terra y, de forma puntual, DeepSeek V4.1 Flash) | 20–25 de octubre: 18 sesiones principales y 41 de subagentes | Análisis, requisitos, casos de uso, arquitectura, modelo de datos, diagramas, base técnica y RF-0002–RF-0007. |
| Claude Code con Gentle AI (Claude Opus 5.5) | 26–31 de octubre: 45 sesiones | RF-0001 y RF-0008–RF-0055, correcciones, exploraciones E2E, guías manuales y revisión final. |
| ChatGPT | Preparación de prompts | Ajuste de los prompts de autoría propia antes de enviarlos a los agentes. |
| Agentes de Gentle AI | Todo el desarrollo | Orquestación, exploración, escritura y verificación independiente. |
| Revisión nativa de Gentle AI | Implementación | Revisión adversarial de los cambios de riesgo alto. |
| Engram | Todo el desarrollo | Memoria persistente de decisiones y hallazgos entre sesiones. |
| CodeGraph | Desde el 26 de octubre | Consulta del código mediante un grafo de símbolos. |
| `playwright-cli` | Pruebas | Pruebas exploratorias de extremo a extremo en navegador. |
| Skill `frontend-design` | Interfaz | Dirección visual, subordinada a requisitos y arquitectura. |

Aplicación de las áreas sugeridas en la sección 9.1:

| Área | Aplicación | Evidencia |
|---|---|---|
| Diseño de arquitectura | Alternativas evaluadas por IA y decisiones PEN-001–PEN-008 ratificadas por el responsable. | [Arquitectura](../04-arquitectura/arquitectura-y-diseno-tecnico.md), [acta de decisiones](../04-arquitectura/respuestas-decisiones-arquitectura-optiplant.md) |
| Generación de código | Backend, frontend, migraciones e infraestructura. | Historial de Git, [`odd/tasks/`](../../odd/tasks/) |
| Generación de tests | Pruebas de backend y frontend junto con cada entrega. | `backend/src/test`, `frontend`, `scripts/run_checks.py` |
| Documentación técnica | Requisitos, casos de uso, modelo, guías, este documento y el README. | [Índice documental](../indice-documental.md) |
| Revisión de código | Verificadores independientes y revisión por riesgo. | Registros `## Log` en [`odd/tasks/`](../../odd/tasks/) |
| Buenas prácticas | Evaluación de versiones y compatibilidad del stack. | Arquitectura §5.4 |

## 3. Ejemplos de prompts y resultados

Cada ejemplo muestra el prompt completo, salvo que se indique lo contrario. Se despliega con un clic.

### 3.1. Análisis inicial de la prueba

Primera interacción del proyecto. Exige separar hechos, inferencias y propuestas, y prohíbe modificar archivos.

<details>
<summary>Prompt del análisis integral (20 de octubre)</summary>

````text
# Análisis integral inicial — Prueba Técnica OptiPlant

Actúa como un ingeniero de software senior especializado en análisis de requisitos, arquitectura de software y gestión de proyectos de desarrollo.

Estamos iniciando una prueba técnica para construir un **Sistema de Inventario Multi-Sucursal** para OptiPlant Consultores.

## 1. Fuentes de información

En `docs/00-source/` se encuentran:

- `prueba-tecnica.pdf`: documento original entregado por la empresa.
- `prueba-tecnica.md`: transcripción en Markdown del documento original.

El PDF es la referencia definitiva para comprobar el enunciado. El Markdown facilita su lectura y procesamiento.

Verifica la equivalencia textual y estructural entre ambos documentos en la medida que las herramientas disponibles lo permitan. Si encuentras discrepancias, infórmalas; no modifiques ninguno de los archivos.

## 2. Objetivo

Realizar un análisis exhaustivo, técnico y profesional de toda la prueba, desde la sección 1 hasta la sección 12, respetando la estructura y numeración originales.

No quiero comenzar todavía a desarrollar el software ni a producir los documentos formales de ingeniería.

Primero necesitamos comprender completamente lo que solicita la empresa.

## 3. Aspectos que debes analizar

1. **Objetivo y alcance:** propósito de la aplicación, problema de negocio, características generales y límites de la solución.
2. **Requisitos funcionales:** identificar y explicar todas las funcionalidades solicitadas, organizadas por módulo.
3. **Obligatoriedad:** diferenciar requisitos obligatorios, funcionalidades adicionales de elección obligatoria, recomendaciones y elementos opcionales.
4. **Reglas de negocio:** identificar las reglas explícitas y aquellas que requieren definición.
5. **Dependencias funcionales:** determinar qué módulos dependen de otros y qué información comparten.
6. **Requisitos técnicos:** examinar arquitectura, comunicación por API, contenedorización y libertad tecnológica.
7. **Ingeniería de software:** identificar todos los artefactos exigidos en las secciones 6, 7 y 8, así como sus relaciones y dependencias.
8. **Decisiones pendientes:** identificar aspectos ambiguos, alternativas técnicas y decisiones que deban ser aprobadas por el responsable del proyecto.
9. **Uso de inteligencia artificial:** analizar las exigencias de documentación, evidencias y evaluación crítica.
10. **Entregables:** establecer qué se debe entregar y cómo podría verificarse su cumplimiento.
11. **Riesgos:** identificar posibles problemas de alcance, consistencia, integración y tiempo de desarrollo.
12. **Estrategia de ingeniería:** proponer un orden razonado para desarrollar la documentación requerida antes de iniciar la implementación.

## 4. Reglas de análisis

- No conviertas recomendaciones en requisitos obligatorios.
- No atribuyas a OptiPlant decisiones que no aparezcan expresamente en el enunciado.
- Separa claramente hechos documentados, inferencias técnicas y propuestas.
- Identifica las secciones originales que respaldan cada conclusión.
- No omitas requisitos por considerarlos secundarios.
- No inventes funcionalidades para completar vacíos.
- No selecciones por tu cuenta alternativas arquitectónicas que requieran aprobación.
- Cuando identifiques incertidumbres, preséntalas junto con su impacto y las alternativas posibles.
- Utiliza español técnico, preciso y natural.

## 5. Metodología

Utiliza las instrucciones de Organic Driven Development (ODD) proporcionadas por Gentle AI.

Esta solicitud corresponde exclusivamente a la fase de exploración y análisis.

Puedes consultar los archivos y utilizar herramientas de investigación cuando sea necesario. No es necesario crear artefactos persistentes de tarea para esta comprobación.

## 6. Restricciones de esta interacción

**Trabaja exclusivamente en modo de análisis y solo lectura.**

No crees, modifiques, elimines ni muevas archivos.

No inicialices Git, no generes código y no instales dependencias.

No registres todavía decisiones persistentes en Engram ni generes documentos definitivos.

Presenta el análisis completo directamente en la conversación.

Al finalizar, entrega un diagnóstico organizado y un plan propuesto para desarrollar la ingeniería de software de las secciones 6 a 8.

Espera mi revisión y autorización antes de realizar cualquier modificación al proyecto.
````

</details>

**Resultado.** Un diagnóstico de las secciones 1 a 12 y un plan para las secciones 6 a 8. De ahí salieron las incertidumbres INC-001–INC-020, que el responsable resolvió en el [registro de decisiones](../01-requerimientos/decisiones-aprobadas-optiplant-v1.0.md).

### 3.2. Integración de decisiones de arquitectura

La decisión es humana; la IA solo la integra en el documento.

<details>
<summary>Prompt de integración de PEN-001–PEN-008 (21 de octubre)</summary>

````text
Adjunto el documento Markdown que contiene las ocho decisiones arquitectónicas PEN-001 a PEN-008, revisadas y ratificadas por el responsable del proyecto OptiPlant, se encuentra en /docs/04-arquitectura/respuestas-decisiones-arquitectura-optiplant.md
Tu tarea es actualizar el documento existente arquitectura-y-diseno-tecnico.md incorporando íntegramente las decisiones, justificaciones, especificaciones y precisiones aprobadas.
Antes de editar, consulta las fuentes documentales vigentes y sigue las instrucciones de integración incluidas en el archivo adjunto.
Conserva la estructura y trazabilidad del documento original, actualiza los estados de las decisiones y elimina los pendientes de selección que ya fueron resueltos. No modifiques los requisitos funcionales ni introduzcas nuevas decisiones o funcionalidades.
El objetivo es dejar finalizada la documentación de las decisiones arquitectónicas correspondientes al punto 8 de la prueba técnica, diferenciando lo aprobado de las verificaciones que corresponden a etapas posteriores.
Realiza la actualización directamente, sin ciclos intermedios de propuestas o aprobación. Al finalizar, presenta un resumen de los cambios y cualquier contradicción documental real que hayas identificado. No generes código ni diagramas en esta actividad.
````

</details>

**Resultado.** El [documento de arquitectura](../04-arquitectura/arquitectura-y-diseno-tecnico.md), con las decisiones ARQ-001–ARQ-010 y su trazabilidad.

### 3.3. Modelo de datos

El prompt completo tiene unos 20.000 caracteres. Este fragmento muestra sus dos primeras secciones: fuentes y autonomía técnica.

<details>
<summary>Fragmento del prompt del modelo de datos (22 de octubre)</summary>

````text
Elaboración del modelo de datos y diagrama entidad-relación — OptiPlant
Necesitamos continuar con la sección 7 de la prueba técnica de OptiPlant, comenzando por la elaboración del modelo entidad-relación y su especificación técnica completa.
Para esta actividad debes realizar directamente el análisis documental, diseño, especificación y generación de los diagramas, sin ciclos intermedios de propuestas, preguntas o aprobaciones sobre decisiones técnicas que puedas resolver fundadamente.
El resultado principal debe ser un documento Markdown completo que contenga tanto la especificación técnica del modelo de datos como los diagramas entidad-relación en Mermaid y su código equivalente en PlantUML, manteniendo compatibilidad con la visualización nativa de Mermaid en GitHub.
Entregalo en /docs/06-modelo-base-datos
1. Fuentes documentales y precedencia
Antes de diseñar, consulta íntegramente las fuentes relevantes y vigentes del proyecto:
Enunciado original de la prueba técnica.
Requisitos funcionales y sus criterios de aceptación.
Decisiones funcionales y de negocio ratificadas.
Casos de uso e historias de usuario disponibles.
Requisitos no funcionales, restricciones y supuestos documentados.
Documento actualizado de arquitectura y diseño técnico, incluyendo las decisiones PEN-001 a PEN-008 y las demás decisiones arquitectónicas y técnicas vigentes.
Decisión DEC-TEC-ID-001 y sus condiciones de aplicación.
Instrucciones y convenciones establecidas en AGENTS.md.
Respeta la precedencia documental definida en el proyecto. Las correcciones funcionales más recientes prevalecen sobre antecedentes sustituidos.
Realiza una búsqueda documental eficiente, aprovechando los índices y mecanismos de recuperación disponibles, evitando lecturas repetitivas innecesarias sin sacrificar la cobertura de las fuentes relevantes.
No introduzcas decisiones provenientes de versiones obsoletas ni reinterpretaciones que contradigan acuerdos ratificados.
2. Autonomía para tomar decisiones técnicas
Tienes autorización expresa para resolver autónomamente las decisiones de diseño lógico y físico necesarias para obtener un modelo de datos completo, siempre que estén fundamentadas en los requisitos, las reglas de negocio y las decisiones arquitectónicas aprobadas.
No debes limitarte a reproducir propuestas preliminares ni dejar indefinidos atributos técnicos que puedas especificar mediante un análisis razonado.

[…]
````

</details>

**Resultado.** El [modelo de datos](../06-modelo-base-datos/modelo-datos.md), con diccionario, restricciones y diagramas E-R.

### 3.4. Dirección visual

<details>
<summary>Prompt de diseño visual (25 de octubre), acompañado de una imagen de referencia</summary>

````text
[Image 1] Utiliza la skill `frontend-design` para orientar el diseño del frontend de OptiPlant.

Toma como referencia visual la imagen proporcionada, siguiendo un estilo **Enterprise SaaS Admin Dashboard / Clean Enterprise UI**, con una estructura App Shell de navegación lateral persistente.

**Lineamientos visuales:**
- Interfaz profesional, minimalista, moderna y orientada a sistemas empresariales.
- Tema claro, fondos neutros, superficies blancas, bordes sutiles y sombras discretas.
- Azul oscuro para jerarquía y estructura visual; naranja como color de acento para las acciones principales.
- Tipografía legible, jerarquías claras y espaciado consistente.
- Uso de tarjetas KPI, tablas de datos, filtros, formularios y navegación contextual por pestañas.
- Componentes reutilizables y comportamiento responsive.
- Estados de carga, error, vacío y confirmación coherentes.
- Accesibilidad, contraste y navegación mediante teclado.

**Restricciones técnicas:**
- Respetar React, las herramientas existentes y el sistema de componentes definido en la arquitectura.
- Priorizar la reutilización de shadcn/ui y los componentes ya implementados.
- No incorporar dependencias adicionales innecesarias.
- No modificar los requisitos funcionales ni las reglas de negocio.

**Consistencia global:**
Establecer una identidad visual compartida para todos los módulos de OptiPlant. Mantener patrones uniformes de navegación, tablas, botones, formularios, colores, mensajes y acciones.

Adaptar la interfaz a las necesidades funcionales de cada módulo, sin replicar innecesariamente todas las estructuras de la pantalla de referencia.
````

</details>

**Resultado.** El [sistema visual](../08-implementacion/sistema-visual.md), con App Shell, tokens y patrones compartidos por todos los módulos.

### 3.5. Implementación de requerimientos funcionales

Todos los bloques de RF se pidieron con una plantilla. La primera versión, para RF-0002–RF-0007, tenía unos 1.800 caracteres; la plantilla creció con la gestión de Git, el diseño visual, la metodología y la entrega hasta unos 5.000. Este es el último bloque.

<details>
<summary>Prompt de implementación de RF-0050–RF-0055 (30 de octubre)</summary>

````text
Implementa de forma completa e integrada los requerimientos funcionales desde el RF-0050 al RF-0055. Pertenecientes a la funcionalidad adicional que he decidido implementar la cual son los reportes exportables en Excel, ten en cuenta el uso de infraestructura del código reutilizable para los reportes y el poder hacer los reportes de movimientos, ventas y transferencias (revisa si hay algún otro factor que fue solicitado para exportar o si de verdad fue descrito el poder exportar estos 3 factores). incluyendo backend, frontend, persistencia y pruebas automatizadas.
1. Preparación y gestión de Git
Antes de realizar cualquier modificación:
Verifica el estado actual del repositorio y asegúrate de no sobrescribir cambios existentes.
Crea una rama de trabajo denominada feat/rf0050-rf0055, tomando como base la versión actualizada de main.
Realiza todos los cambios y commits correspondientes exclusivamente en esta rama.
No realices merges hacia main ni modificaciones directas sobre ella.
No descartes cambios existentes ni ejecutes operaciones destructivas de Git. Si el estado del repositorio impide crear la rama de manera segura, informa del problema.
No realices push al repositorio remoto sin autorización.
2. Implementación funcional
Consulta los requerimientos RF-0050 al RF-0055 directamente en docs/01-requerimientos/requisitos-funcionales.md. Utiliza AGENTS.md como guía para localizar únicamente la documentación complementaria estrictamente necesaria, respetando las decisiones funcionales, arquitectónicas y de datos previamente aprobadas. Evita exploraciones generales del repositorio y lecturas de documentación no relacionada con estos RF.
Aprovecha la infraestructura, los componentes y los servicios existentes. Evita duplicar lógica o introducir tecnologías y dependencias sin justificación.
Implementa completamente los endpoints, DTO, validaciones, reglas de negocio, persistencia y controles de seguridad correspondientes.
Desarrolla las interfaces frontend necesarias e intégralas con los endpoints reales del backend. No utilices datos simulados como sustitutos de la integración.
Garantiza la coherencia entre frontend, backend y base de datos, respetando los contratos API y los mecanismos de autenticación y autorización establecidos.
Añade pruebas automatizadas apropiadas para los comportamientos principales, casos límite, permisos y manejo de errores.
Ejecuta las verificaciones disponibles y corrige los problemas encontrados dentro del alcance de la implementación.
No modifiques los requerimientos aprobados ni amplíes el alcance funcional. Si identificas una contradicción real que impida implementar correctamente, infórmala expresamente.
3. Desarrollo frontend y diseño visual
Para cualquier trabajo relacionado con la interfaz:
Utiliza la skill frontend-design cuando corresponda.
Respeta estrictamente el sistema visual aprobado en docs/08-implementacion/sistema-visual.md y las instrucciones de AGENTS.md.
Aplica la skill para mejorar la composición, jerarquía visual, usabilidad, accesibilidad y diseño responsive, sin alterar la identidad visual, los tokens ni las tecnologías aprobadas.
Prioriza la reutilización de componentes existentes.
Utiliza shadcn/ui para incorporar los componentes necesarios cuando no exista una alternativa reutilizable. Puedes instalar y configurar los componentes requeridos directamente, sin solicitar autorización individual.
Mantén la coherencia visual y funcional con las pantallas previamente implementadas.
Asegura que las interfaces contemplen los estados relevantes de carga, éxito, vacío, validación y error, según corresponda.
4. Metodología de ejecución
Trabaja siguiendo el enfoque ODD de Gentle-AI, realizando únicamente la planificación necesaria para desarrollar y verificar la implementación.
No generes nuevas fases de planificación documental extensa ni solicites aprobaciones sobre decisiones previamente ratificadas.
Prioriza la implementación directa, la reutilización de código y la recuperación eficiente del contexto documental.
Mantén el alcance estrictamente limitado a los RF solicitados y las adaptaciones técnicas indispensables para integrarlos.
Si la implementación resulta extensa, puedes organizar internamente el trabajo en etapas, conservando la integración completa como objetivo de la entrega.
5. Entrega y verificación
Al finalizar:
Resume los requerimientos implementados y sus funcionalidades.
Identifica los principales componentes y archivos modificados.
Indica las pruebas y verificaciones ejecutadas, incluyendo sus resultados.
Reporta las limitaciones o problemas pendientes, si existen.
Explica brevemente cómo probar manualmente cada funcionalidad implementada.
Informa el nombre de la rama utilizada y el estado final de Git, incluyendo los commits realizados.
Implementa directamente conforme a las especificaciones existentes, sin modificar main y sin realizar planificación documental innecesaria.
````

</details>

**Resultado.** El módulo `reports`, la vista de Reportes, pruebas y la [guía de reportes](../08-implementacion/reportes-exportables.md), en la rama `feat/rf0050-rf0055`. La IA comprobó que el enunciado fija exactamente tres categorías, y la revisión obligó a corregir el orden de la guarda de rol. El proceso está en [`odd/tasks/rf-0050-0055.md`](../../odd/tasks/rf-0050-0055.md).

### 3.6. Pruebas exploratorias con Playwright

Se ejecutó cuatro veces: al cerrar cada grupo de requerimientos se cambiaba el foco y se conservaba una regresión de lo anterior. «Opción B» es una pila de Compose desechable con datos de prueba, decidida por el responsable.

<details>
<summary>Prompt de exploración E2E (30 de octubre)</summary>

````text
Utiliza Playwright para realizar pruebas funcionales exploratorias de extremo a extremo sobre OptiPlant. Especialmente enfoquemonos en la exploración de los RF-0044 al RF-0055 siendo estos requerimientos funcionales que no han sido probados de extremo a extremo, sin embargo, también revisa puntos que se hayan tocado de anteriores requerimientos funcionales o hayan sido críticos para esta implementación con el fin de revisar que todo siga funcionando y no se haya roto nada.
Verifica que el frontend, backend y PostgreSQL estén disponibles. Identifica sus direcciones y puertos desde la configuración del proyecto.
Abre la aplicación en un navegador y recorre los módulos actualmente implementados.
Utiliza exclusivamente cuentas y datos de prueba para completar formularios, crear registros, editarlos, consultarlos y verificar su persistencia. (Por ahora la opción B como habíamos charlado antes)
Comprueba las validaciones, los permisos según el rol, los estados de carga y los mensajes de error.
Inspecciona la consola del navegador y las solicitudes HTTP para detectar errores de integración frontend-backend.
Documenta los escenarios ejecutados, los resultados esperados y obtenidos, los problemas encontrados y las evidencias relevantes.
Restricciones:
No utilices datos de producción. (Manten la Opción B por ahora que habiamos decidido)
No modifiques el código ni corrijas errores durante esta primera exploración.
No elimines información preexistente.
Al finalizar, presenta un informe de hallazgos priorizados por severidad.
````

</details>

**Resultado.** 36 hallazgos en cuatro exploraciones. La mayoría se corrigió después en rondas autorizadas:

| Exploración | Alcance | Alta | Media | Baja |
|---|---|---:|---:|---:|
| [27 de octubre](../08-implementacion/exploracion-e2e-2026-10-27.md) | Módulos implementados | 1 | 2 | 7 |
| [28 de octubre](../08-implementacion/exploracion-e2e-2026-10-28.md) | RF-0021–RF-0031 | 0 | 1 | 8 |
| [29 de octubre](../08-implementacion/exploracion-e2e-2026-10-29.md) | RF-0032–RF-0043 | 0 | 2 | 9 |
| [30 de octubre](../08-implementacion/exploracion-e2e-2026-10-30.md) | RF-0044–RF-0055 | 0 | 1 | 5 |

Ejemplo: el hallazgo alto del 27 de octubre mostró que el catálogo seguía con existencias obsoletas tras registrar movimientos, aunque el servidor devolvía el valor correcto.

### 3.7. Guía de verificación manual

Este prompt convierte a la IA en generadora de la prueba que después ejecuta una persona.

<details>
<summary>Prompt del recorrido manual (27 de octubre)</summary>

````text
Quiero realizar una **verificación manual rápida e integral de todas las funcionalidades implementadas hasta el momento**, aprovechando las guías existentes en `docs/08-implementacion/`.

Consulta las siguientes guías:

- `acceso-y-administracion.md`
- `catalogo-de-productos.md`
- `inventario-entre-sucursales.md`
- `movimientos-de-inventario.md`
- `alertas-de-reabastecimiento.md`

A partir de ellas y del estado actual de la aplicación, genera una **única guía práctica, sencilla y secuencial**, que pueda seguir directamente desde la interfaz.

### Objetivo

Verificar las funcionalidades implementadas de todos los módulos de manera rápida y, simultáneamente, **dejar la base de datos PostgreSQL poblada con información coherente, realista y reutilizable para futuras implementaciones**.

### Instrucciones

1. **No crees ni modifiques archivos.** Presenta toda la guía directamente en tu respuesta.

2. Organiza las pruebas como un único recorrido de principio a fin, evitando repetir pasos, crear registros innecesarios o cambiar constantemente entre módulos.

3. Indica exactamente qué debo hacer en cada paso: a qué módulo ingresar, qué opción seleccionar, qué datos introducir y qué resultado debería observar.

4. Proporciona **datos concretos de ejemplo** para usuarios, sucursales, categorías, productos, inventarios y demás entidades que sean necesarias. Reutiliza estos registros en los pasos posteriores para mantener la coherencia entre módulos.

5. Prioriza las operaciones que permitan verificar simultáneamente varias funcionalidades y sus relaciones. Incluye las validaciones negativas esenciales, sin convertir el recorrido en una batería extensa de pruebas.

6. Cuando una operación requiera permisos específicos, indica con qué usuario o rol debo realizarla.

7. Respeta las reglas de negocio y los formatos implementados, incluyendo el uso de la coma decimal cuando corresponda.

8. **Conserva los datos creados.** Evita eliminaciones, reinicios de base de datos o acciones destructivas. La información resultante debe servir para probar los próximos requerimientos funcionales.

9. Si existen datos previamente registrados, indica cómo reutilizarlos o identificarlos para evitar duplicaciones.

10. No inventes funcionalidades ni opciones de interfaz. Basa las instrucciones en lo que realmente está implementado y disponible.

### Formato de respuesta

Presenta el recorrido mediante pasos numerados, utilizando este formato sencillo:

**Paso 1 — [Acción]**
- **Dónde:** módulo o sección de la interfaz.
- **Qué hacer:** instrucciones breves y datos exactos para ingresar.
- **Resultado esperado:** comportamiento que debo comprobar.
- **Estado de los datos:** qué información queda registrada o modificada.

Continúa hasta cubrir todas las funcionalidades implementadas.

Al finalizar, incluye:

- Un resumen de las funcionalidades verificadas y los pasos que permiten comprobarlas.
- Un resumen de los datos que deberían quedar almacenados en PostgreSQL.
- Las funcionalidades que no hayan podido incluirse en el recorrido y cómo verificarlas brevemente.

**Importante:** quiero una guía orientada a la ejecución manual, no una explicación técnica del código ni una transcripción de la documentación. Debe ser lo suficientemente clara para que pueda seguirla sin tener que consultar otros documentos.

Prioriza la rapidez, la cobertura funcional y la reutilización de los datos para las siguientes fases del proyecto.
````

</details>

**Resultado.** El [recorrido manual integral](../08-implementacion/recorrido-manual-integral.md). Se repitió para los demás módulos, y el responsable completó los cuatro recorridos: 25, 25, 26 y 26 pasos.

## 4. Evaluación crítica

### Qué aportó la IA

- **Velocidad con trazabilidad.** 55 requerimientos, su documentación y 187 commits entre el 22 y el 31 de octubre.
- **Detección de vacíos.** El análisis inicial convirtió la ambigüedad del enunciado en decisiones explícitas antes de programar.
- **Consistencia.** Las reglas de `AGENTS.md` y la memoria persistente mantuvieron las decisiones entre sesiones.
- **Pruebas que un desarrollador solo haría menos.** Exploraciones completas de extremo a extremo y comprobaciones en Excel real y Safari.

### Qué fue necesario ajustar manualmente

Estos ejemplos provienen de las sesiones:

| Situación | Intervención humana |
|---|---|
| La IA reportó que pruebas y código se escribieron a la vez. | El responsable exigió revisar lo pendiente: «los codigos deben de estar probados». |
| El orquestador continuó después de superar un límite de delegación. | El responsable lo corrigió y fijó el criterio para las tareas siguientes. |
| Los formularios de creación ya estaban cargados y los botones «Crear» parecían no hacer nada. | El responsable detectó el problema de usabilidad y definió la solución; también pidió el «ojo» de la contraseña y los iconos. |
| Las transferencias no se actualizaban solas y los faltantes no contaban como alertas. | El responsable lo notó en su recorrido manual y pidió evaluarlo e implementarlo. |
| Un operador veía importes de venta agregados en el dashboard. | La IA lo señaló sin resolverlo; la decisión fue humana. |
| La IA propuso un endpoint para renovar la sesión al navegar. | El responsable lo rechazó; se renueva con comandos de negocio. |
| El administrador podía solicitar transferencias según el enunciado. | El responsable lo acotó al operador de destino. |
| La revisión final encontró que `docker compose up` fallaba en un clon limpio, porque la prueba de arranque generaba su propio entorno. | Se añadió el servicio `credentials` tras la decisión del responsable. |
| La valoración no mostraba el valor en tránsito, aunque el cálculo existía. | El responsable pidió implementarlo. |

### Dónde no fue útil

- **Validar una decisión de negocio.** La IA propone, pero no puede saber qué quiere el responsable. Solo decidió donde había autorización expresa.
- **Sustituir el uso real.** Varios problemas de experiencia los encontró una persona usando la aplicación, no una prueba automatizada.
- **Confianza en resultados propios.** Las exploraciones también produjeron falsos positivos: un fallo se debió al localizador de la prueba y otro hallazgo se descartó al repetirlo. Todo hallazgo tuvo que reproducirse.
- **Pruebas que no cubren lo que parecen cubrir.** La comprobación de arranque pasaba en verde mientras el arranque real sin configuración fallaba.
- **Efectos secundarios del entorno.** Las pruebas de integración dejaron imágenes de Docker acumuladas, y hubo que pedir su limpieza más de una vez.
- **Costo de contexto y ceremonia.** Los documentos extensos y los prompts largos consumen mucho contexto, y el protocolo añade pasos que en tareas pequeñas no aportan.

### Limitaciones

- Los requisitos no funcionales no tienen una medición individual.
- El contenido generado se verificó con pruebas y recorridos, no mediante una revisión humana línea por línea.
- Las exploraciones E2E siguen marcadas como evidencia pendiente de revisión; no son decisiones aprobadas.
- Los commits no llevan atribución de IA; este documento es la declaración de su uso.

## 5. Estimación de contenido generado con IA

Es una estimación del responsable a partir del proceso seguido; no es una medición.

| Artefacto | Generado con asistencia de IA | Aporte humano |
|---|---|---|
| Código de backend y frontend | 100 % | Requisitos, instrucciones, correcciones funcionales y verificación. |
| Pruebas automatizadas | 100 % | Criterios de aceptación y exigencia de cobertura. |
| Documentación técnica | ~90 % | Decisiones, correcciones, aprobación y contenido verificado por el responsable. |
| Decisiones de alcance y arquitectura | Propuestas y análisis | Ratificación: la decisión final fue humana. |
