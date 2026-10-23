# Diagramas de actividad — Transferencias y venta

Diagramas UML editables de los dos flujos solicitados en la sección 7. Conservan el estilo visual aprobado en [`../diagramas-v2/`](../diagramas-v2/), adaptado a la notación de actividad: carriles de responsabilidad, acciones redondeadas, decisiones con guardas, inicio y finales.

| Flujo | Archivo editable | Vista previa |
|---|---|---|
| Transferencia entre sucursales | [01-transferencia-sucursales.excalidraw](01-transferencia-sucursales.excalidraw) | [PNG](vistas-previas/01-transferencia-sucursales.png) |
| Venta | [02-flujo-venta.excalidraw](02-flujo-venta.excalidraw) | [PNG](vistas-previas/02-flujo-venta.png) |

Abrir los archivos `.excalidraw` en Excalidraw. Los conectores y sus guardas están vinculados a sus elementos; los PNG son vistas auxiliares, no sustituyen la fuente editable. La transferencia tiene una composición vertical extensa para evitar cruces: se recomienda consultar el editable con zoom.

## Fuentes y trazabilidad

| Contenido representado | Fuente vigente |
|---|---|
| Obligación de ambos diagramas | [`prueba-tecnica.md`](../../00-source/prueba-tecnica.md), sección 7, líneas 148–155; fuente externa definitiva: PDF original en la misma carpeta. |
| Permisos, actores y alcance de venta | [`especificacion-casos-de-uso.md`](../especificacion-casos-de-uso.md), UC-013 y UC-014; [`requisitos-funcionales.md`](../../01-requerimientos/requisitos-funcionales.md), RF-0027 a RF-0031 y matriz de permisos. |
| Solicitud, preparación, aprobación/rechazo y despacho | RF-0032 a RF-0035; RF-0034-CA-03–RF-0034-CA-04; H v1.2/01; UC-015 y HU-015. |
| Llegada única, recepción completa, parcial y llegada cero | RF-0036 y RF-0037; UC-016 y UC-017. |
| Tratamiento del faltante y resolución de alerta | RF-0038 y RF-0039. |
| Estados históricos de transferencia | RF-0042. |

Se contrastaron las [decisiones ratificadas](../../01-requerimientos/decisiones-aprobadas-optiplant-v1.0.md), las restricciones operativas y los permisos. Las precisiones humanas integradas en los RF vigentes prevalecen sobre los antecedentes sustituidos, conforme a la jerarquía de la especificación de casos de uso.

## Lectura de los flujos

### Transferencia entre sucursales

- Solicita el **operador de destino**; prepara y despacha el **operador de origen**; aprueba o rechaza con motivo el **gerente de origen**, según H v1.2/01.
- El rechazo solo se permite desde **«en preparación»**, antes de aprobar. El sistema valida gerente, sucursal, estado y motivo; un rechazo válido registra **«rechazada»**, motivo y responsable sin alterar inventario. Un intento inválido no registra un rechazo de negocio ni modifica la transferencia.
- La cancelación previa a aprobación corresponde al destino; tras aprobar y antes de despachar, solo al gerente de origen con motivo. En tránsito no se permite cancelar.
- El despacho valida y aplica todas sus líneas indivisiblemente. Un intento fallido no convierte por sí mismo la transferencia en «rechazada» ni produce salida o tránsito parcial.
- El operador de destino registra una sola llegada. Puede ser completa o con faltantes, incluida llegada confirmada de cero unidades: esta última no crea ingreso físico.
- El gerente de origen **o** de destino puede definir tratamiento y marcar la alerta «Resuelto». No se exige aprobación conjunta. El estado **Recibida con discrepancia** se conserva; regularizar cantidades o valor es una operación separada autorizada.
- Los nodos finales de intentos rechazados indican que termina ese recorrido, **no** que la transferencia haya cerrado o desaparecido. Puede continuar aprobada, en tránsito o con discrepancia.

### Venta

- El operador captura los datos comerciales y solicita confirmación.
- La validación de todas las líneas y sus efectos se representan como una confirmación indivisible: no se promete disponibilidad a partir de una comprobación previa independiente.
- Si hay datos inválidos, descuento fuera de rango, falta de stock o agotamiento por concurrencia, no se produce venta confirmada, retiro, comprobante ni efectos parciales.
- El comprobante es interno. No se añaden factura fiscal, cobro, anulación financiera ni reembolso automático.
- Gerente y administrador pueden consultar según su ámbito, pero no sustituyen al operador para registrar ventas.

## Vacío documental resuelto — H v1.2/01

El responsable ratificó explícitamente que **solo el gerente de origen puede rechazar con motivo una transferencia «en preparación», antes de aprobarla**. La rama se ubica tras la preparación del operador y la revisión del gerente, como alternativa a aprobar: `en preparación → rechazada`. El sistema verifica las condiciones antes de registrar el rechazo; otro actor, motivo ausente o estado distinto producen denegación sin cambios.

La resolución se integró en RF-0034, la matriz de permisos, el registro de decisiones, UC-015 y HU-015. No se transfirieron preparación ni despacho al gerente, ni se ampliaron permisos del administrador o gerente de destino. Cancelación y validación fallida siguen siendo distintas del rechazo de negocio.

Los antecedentes del registro de decisiones que permiten solicitudes del administrador o recepciones sucesivas no se adoptaron como alternativas vigentes: RF-0032, RF-0036 y RF-0037 ya explicitan su sustitución. Esas reglas no cambian con H v1.2/01.

## Estilo y verificación

- Fondo `#f8f9fa`; encabezado de 30 px, subtítulo gris y carriles blancos con borde gris discontinuo.
- Acciones y decisiones con fondo `#d0ebff`, borde `#1971c2` y texto oscuro; operador naranja y gerente verde en los encabezados de responsabilidad.
- Una única etiqueta por contenedor, guardas explícitas en las bifurcaciones y flechas de flujo de control vinculadas. Las flechas aquí corresponden a **actividad**, no a asociaciones de casos de uso.
- Notas situadas 85 px por debajo del último carril, sin superponer actores, decisiones ni finales.
- Se comprobaron JSON, identificadores y vínculos, alcanzabilidad de nodos, salidas de decisiones, terminación de recorridos, responsabilidades, separación geométrica de conectores y textos y ajuste tipográfico.
- Tras integrar H v1.2/01, transferencia tiene 46 nodos y 45 flujos; venta conserva 11 nodos y 10 flujos. Se comprobaron trece recorridos documentales: once de transferencia —incluidos rechazo válido y denegado— y dos de venta. La comprobación SHA-256 confirmó intactos los respaldos, ejemplos, otros módulos, fuentes originales y venta.
- Se generaron e inspeccionaron vistas previas PNG con un renderizador local de las coordenadas y textos de las escenas. No son exportaciones nativas de la aplicación Excalidraw; su comportamiento al editar en esa aplicación no fue probado.

Estos diagramas son documentación del comportamiento aprobado, no evidencia de implementación o pruebas de una aplicación.
