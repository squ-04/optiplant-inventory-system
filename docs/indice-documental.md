# Índice documental — OptiPlant

Punto de entrada a la documentación del Sistema de Inventario Multi-Sucursal. Este índice orienta la lectura; no sustituye las fuentes ni modifica el estado de aprobación de los documentos enlazados.

## Referencias principales

| Documento | Consultar para |
|---|---|
| [Requisitos funcionales](01-requerimientos/requisitos-funcionales.md) | Comportamiento aprobado, RF-0001–RF-0055, permisos, reglas y criterios de aceptación. |
| [Arquitectura y diseño técnico](04-arquitectura/arquitectura-y-diseno-tecnico.md) | Diseño por capas, organización modular, decisiones ARQ, fundamentos, alternativas y verificaciones pendientes. |
| [Modelo relacional y especificación de datos](06-modelo-base-datos/modelo-datos.md) | Entidades, atributos, relaciones, restricciones, índices, diagramas E-R y trazabilidad. Es diseño técnico; no acredita implementación. |

## Fuentes y decisiones

| Documento | Función y alcance |
|---|---|
| [Prueba técnica original (PDF)](00-source/prueba-tecnica.pdf) | Fuente definitiva de requisitos externos. Prevalece ante discrepancias con la transcripción. |
| [Transcripción de la prueba técnica](00-source/prueba-tecnica.md) | Referencia operativa para localizar y leer el enunciado. |
| [Registro de decisiones aprobadas](01-requerimientos/decisiones-aprobadas-optiplant-v1.0.md) | Resoluciones funcionales ratificadas, exclusiones y decisión técnica de identificación dual. Consultar también las correcciones posteriores expresas del catálogo funcional. |
| [Acta de decisiones arquitectónicas](04-arquitectura/respuestas-decisiones-arquitectura-optiplant.md) | Evidencia de ratificación de PEN-001–PEN-008; su integración se consulta en el documento de arquitectura. |
| [Instrucciones del proyecto](../AGENTS.md) | Convenciones de trabajo, fuentes protegidas, alcance y aprobaciones necesarias. |

Las tecnologías elegidas y las reglas ratificadas por el responsable son decisiones del proyecto, no exigencias externas por defecto. Consultar el estado, las fuentes y la precedencia en cada documento; una aprobación documental no demuestra implementación ni pruebas. Ante una contradicción no resuelta, señalarla y solicitar decisión, sin corregir silenciosamente las fuentes.

## Requisitos complementarios

| Documento | Consultar para |
|---|---|
| [Requisitos no funcionales](01-requerimientos/requisitos-no-funcionales.md) | Condiciones de rendimiento, seguridad, capacidad, usabilidad y precisión. |
| [Restricciones técnicas y de negocio](01-requerimientos/restricciones-tecnicas-y-de-negocio.md) | Límites externos y decisiones del proyecto que condicionan la solución. |
| [Supuestos y dependencias](01-requerimientos/supuestos-y-dependencias-del-sistema.md) | Condiciones de trabajo y dependencias documentadas. |

## Comportamiento y diagramas

| Documento | Consultar para |
|---|---|
| [Especificación de casos de uso](02-casos-de-uso/especificacion-casos-de-uso.md) | Actores, objetivos, flujos y trazabilidad funcional. |
| [Historias de usuario](03-historias%20de%20usuario/historias-de-usuario.md) | Necesidades y valor funcional desde la perspectiva de los actores. |
| [Diagramas de casos de uso — versión 2](02-casos-de-uso/diagramas-v2/indice-diagramas-casos-de-uso.md) | Índice de los diagramas editables por módulo. La especificación textual es su referencia normativa. |
| [Diagramas de arquitectura](04-arquitectura/diagramas/indice-diagramas-arquitectura.md) | Vistas de arquitectura general, monolito modular y composición de un módulo. |
| [Diagramas de actividad](05-diagramas-actividad/indice-diagramas-actividad.md) | Flujos de transferencia entre sucursales y venta. |

Los diagramas E-R están incluidos en el [modelo de datos](06-modelo-base-datos/modelo-datos.md), junto con su diccionario y restricciones; no constituyen un modelo independiente.

## Colaboración y antecedentes

| Documento | Uso y cautela |
|---|---|
| [Git, GitHub y pull requests](07-colaboracion/git-github-y-pull-requests.md) | Guía de colaboración y revisión del repositorio. |
| [Guía temporal de incertidumbres](01-requerimientos/incertidumbres-pendientes-temporal.md) | Antecedente de análisis con alternativas no aprobadas por sí mismas. No sustituye las decisiones ratificadas ni demuestra que todas sus preguntas sigan abiertas. |
| [Diagramas de casos de uso — respaldo anterior](02-casos-de-uso/diagramas/indice-diagramas-casos-de-uso.md) | Versión anterior conservada como respaldo; consultar primero la versión 2 y la especificación textual. |

## Ruta de lectura y mantenimiento

1. Para alcance funcional: enunciado → requisitos funcionales y decisiones aprobadas → requisitos complementarios → casos de uso e historias.
2. Para diseño técnico: referencias funcionales aplicables → arquitectura y acta de ratificación → modelo de datos y diagramas relacionados.
3. Antes de usar una referencia, revisar su estado y las correcciones posteriores que declare. No tratar borradores o antecedentes como decisiones vigentes.

Al crear, mover o renombrar documentación principal, actualizar este índice y comprobar sus enlaces relativos. Mantener aquí las rutas y el propósito de cada referencia, sin duplicar requisitos, decisiones ni diccionarios de datos.
