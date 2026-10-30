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
| [Acceso, administración y consultas: prueba manual](08-implementacion/acceso-y-administracion.md) | Guía de la implementación de RF-0002–RF-0007, rutas, pruebas manuales y límites por módulos dependientes. No sustituye requisitos aprobados. |
| [Catálogo de productos: prueba manual](08-implementacion/catalogo-de-productos.md) | Guía de la implementación de RF-0008–RF-0011: baja exclusivamente lógica, rutas, prueba manual y límites. No sustituye requisitos aprobados. |
| [Inventario entre sucursales: prueba manual](08-implementacion/inventario-entre-sucursales.md) | Guía de la implementación de RF-0014–RF-0015: consulta de existencias de otra sucursal, polling, advertencia de desactualización, rutas, prueba manual y límites. No sustituye requisitos aprobados. |
| [Movimientos de inventario: prueba manual](08-implementacion/movimientos-de-inventario.md) | Guía de la implementación de RF-0016–RF-0018: servicio único de movimientos, valoración y compensaciones, idempotencia, rutas, prueba manual y límites. No sustituye requisitos aprobados. |
| [Alertas de reabastecimiento: prueba manual](08-implementacion/alertas-de-reabastecimiento.md) | Guía de la implementación de RF-0019–RF-0020: condición única de stock mínimo, episodios de alerta con historia, destinatarios, ruta REST, prueba manual y límites. No sustituye requisitos aprobados. |
| [Compras: prueba manual](08-implementacion/compras.md) | Guía de la implementación de RF-0021–RF-0025 y de la consulta de valoración de RF-0026: proveedores mínimos, órdenes con condiciones, edición de borradores, cancelación y reemplazo, recepción completa por el servicio único de movimientos, histórico por proveedor y producto, valoración por sucursal, rutas, prueba manual y límites. No sustituye requisitos aprobados. |
| [Listas de precios: prueba manual](08-implementacion/listas-de-precios.md) | Guía de la implementación de RF-0030: listas globales mantenidas por el administrador, precio por producto y unidad, desactivación sin borrado, consulta por perfil, precio que tomará la venta, rutas, prueba manual y límites. No sustituye requisitos aprobados. |
| [Ventas y comprobantes: prueba manual](08-implementacion/ventas.md) | Guía de la implementación de RF-0027–RF-0029 y RF-0031: confirmación con lista y descuento por línea, validación indivisible de existencias mediante `InventoryLedger`, idempotencia, comprobante interno inmutable, rutas, prueba manual y límites. No sustituye requisitos aprobados. |
| [Transferencias: prueba manual](08-implementacion/transferencias.md) | Guía de la implementación de RF-0032–RF-0043: solicitud multilínea del destino, preparación del origen sin superar lo solicitado, aprobación, rechazo y cancelación por el actor autorizado, despacho indivisible hacia tránsito por la ruta activa, recepción única completa, parcial o en cero, alerta de faltante con tratamiento, pérdida en tránsito y reenvío vinculado, rutas dirigidas clasificadas por prioridad, tiempos estimados y reales, estados y reporte de cumplimiento logístico por sucursal y ruta; contrato REST, recorrido manual y verificaciones. No sustituye requisitos aprobados. |
| [Sistema visual de las interfaces](08-implementacion/sistema-visual.md) | Dirección visual aprobada, tokens, componentes oficiales shadcn/ui y patrones reutilizables; fuentes, adaptaciones, comprobaciones y límites de verificación. No sustituye ARQ-001 ni define marca institucional. |
| [Exploración funcional E2E del 2026-10-27](08-implementacion/exploracion-e2e-2026-10-27.md) | Evidencia de pruebas exploratorias con Playwright sobre los módulos implementados: escenarios, hallazgos priorizados por severidad y uso de IA. Borrador pendiente de revisión; no constituye decisiones aprobadas. |
| [Exploración funcional E2E del 2026-10-28](08-implementacion/exploracion-e2e-2026-10-28.md) | Evidencia de pruebas exploratorias con Playwright de compras, valoración, listas de precios y ventas (RF-0021–RF-0031) y regresión de módulos previos. Borrador pendiente de revisión; no constituye decisiones aprobadas. |
| [Exploración funcional E2E del 2026-10-29](08-implementacion/exploracion-e2e-2026-10-29.md) | Evidencia de pruebas exploratorias con Playwright de transferencias, rutas, faltantes, tiempos y cumplimiento logístico (RF-0032–RF-0043) y regresión de módulos previos. Borrador pendiente de revisión; no constituye decisiones aprobadas. |
| [Recorrido manual integral](08-implementacion/recorrido-manual-integral.md) | Recorrido secuencial desde la interfaz que verifica los módulos implementados (RF-0001–RF-0020 y RF-0026) y deja datos de ejemplo coherentes y reutilizables en PostgreSQL. Generado con IA a partir de las guías de prueba manual; realizado por el responsable (25 de 25 pasos, confirmado el 29/10/2026). |
| [Recorrido manual de compras y ventas](08-implementacion/recorrido-manual-compras-ventas.md) | Continuación del recorrido integral que verifica desde la interfaz compras, valoración, listas de precios y ventas (RF-0021–RF-0031) partiendo de los datos que este deja. Generado con IA a partir de las guías de prueba manual; realizado por el responsable (25 de 25 pasos, confirmado el 29/10/2026), con el estado final contrastado en la base local. |
| [Recorrido manual de transferencias](08-implementacion/recorrido-manual-transferencias.md) | Continuación del recorrido de compras y ventas que verifica desde la interfaz transferencias, rutas, faltantes, tiempos de entrega y cumplimiento logístico (RF-0032–RF-0043) partiendo de los datos que este deja. Generado con IA a partir de la guía de transferencias y de la base local; realizado por el responsable (26 de 26 pasos, confirmado el 29/10/2026), con el estado final contrastado en la base local. |
| [Git, GitHub y pull requests](07-colaboracion/git-github-y-pull-requests.md) | Guía de colaboración y revisión del repositorio. |
| [Guía temporal de incertidumbres](01-requerimientos/incertidumbres-pendientes-temporal.md) | Antecedente de análisis con alternativas no aprobadas por sí mismas. No sustituye las decisiones ratificadas ni demuestra que todas sus preguntas sigan abiertas. |
| [Diagramas de casos de uso — respaldo anterior](02-casos-de-uso/diagramas/indice-diagramas-casos-de-uso.md) | Versión anterior conservada como respaldo; consultar primero la versión 2 y la especificación textual. |

## Ruta de lectura y mantenimiento

1. Para alcance funcional: enunciado → requisitos funcionales y decisiones aprobadas → requisitos complementarios → casos de uso e historias.
2. Para diseño técnico: referencias funcionales aplicables → arquitectura y acta de ratificación → modelo de datos y diagramas relacionados.
3. Antes de usar una referencia, revisar su estado y las correcciones posteriores que declare. No tratar borradores o antecedentes como decisiones vigentes.

Al crear, mover o renombrar documentación principal, actualizar este índice y comprobar sus enlaces relativos. Mantener aquí las rutas y el propósito de cada referencia, sin duplicar requisitos, decisiones ni diccionarios de datos.
