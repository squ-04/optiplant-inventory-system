# Diagramas de arquitectura — OptiPlant

Tres vistas editables en Excalidraw de la arquitectura ratificada. Conservan la presentación de los últimos diagramas: fondo claro, encabezados legibles, límites grises discontinuos, componentes azules, conexiones vinculadas y notas inferiores separadas del contenido.

| Vista | Fuente editable | Vista previa |
|---|---|---|
| Frontend, backend y base de datos | [01-arquitectura-general.excalidraw](01-arquitectura-general.excalidraw) | [PNG](vistas-previas/01-arquitectura-general.png) |
| Interior del monolito modular | [02-monolito-modular.excalidraw](02-monolito-modular.excalidraw) | [PNG](vistas-previas/02-monolito-modular.png) |
| Composición general de un módulo | [03-composicion-modulo.excalidraw](03-composicion-modulo.excalidraw) | [PNG](vistas-previas/03-composicion-modulo.png) |

## Alcance de cada vista

### 1. Arquitectura general

Presenta las tres responsabilidades principales y el stack ratificado, sin fijar versiones ni puertos pendientes. Solo hay dos conexiones: frontend → backend y backend → PostgreSQL. Las respuestas regresan por los mismos enlaces.

El frontend representa **React ejecutándose en el navegador, servido por Vite en desarrollo/demostración**. No representa a Vite como cliente de REST ni como proxy. La vista es lógica, no un diagrama de despliegue: los tres servicios de Docker Compose se indican en la nota, sin situar erróneamente el navegador dentro de un contenedor.

El backend concentra reglas y autorización. PostgreSQL es la base compartida para datos y sesiones. Se conserva Spring Security con Spring Session JDBC; no se sustituye por JWT. Flyway, Apache POI y OpenAPI forman parte del backend, no son servicios independientes.

### 2. Monolito modular

Muestra exactamente los nueve módulos aprobados y una descripción representativa de cada uno: `identity`, `catalog`, `inventory`, `purchases`, `sales`, `transfers`, `logistics`, `dashboard` y `reports`.

Es una **vista de composición**, no un grafo completo de dependencias; por eso no añade flechas arbitrarias entre módulos. Las notas recuerdan las colaboraciones expresas: `transfers` coordina `logistics` e `inventory`; compras y ventas reutilizan `inventory`; `dashboard` y `reports` consumen consultas. No se asigna definitivamente la entidad sucursal a un módulo, pues ese detalle sigue pendiente del modelo de datos.

### 3. Interior de un módulo

Separa el recorrido conceptual `controller → service → repository → PostgreSQL` de los contratos y representaciones de apoyo: `dto`, `mapper` y `entity`. Estos últimos **no son tres pasos consecutivos de ejecución** ni se fija qué clase debe llamar al mapper.

`api`, `enums` y `exception` se muestran como complementos cuando sean necesarios. El paquete base y las clases concretas no se inventan. Es una composición general, no exige que cada módulo tenga todos esos subpaquetes ni impone una arquitectura hexagonal.

## Fuentes de las decisiones

Fuente principal: [Arquitectura y diseño técnico](../arquitectura-y-diseno-tecnico.md), contrastada con el [acta ratificada](../respuestas-decisiones-arquitectura-optiplant.md).

| Elementos | Trazabilidad |
|---|---|
| React, herramientas del frontend, Java/Spring Boot y REST | ARQ-001–ARQ-002; PEN-004, PEN-006 y PEN-008. |
| PostgreSQL, JPA/Hibernate, Flyway y ejecución local | ARQ-003–ARQ-004; PEN-005 y PEN-007. |
| Seguridad y sesiones persistidas | ARQ-005; PEN-001. |
| Módulos, capas, propiedad de inventario y contratos internos | ARQ-008, especialmente §§ de subpaquetes y propiedad/coordinación; PEN-004. |
| Límites transaccionales del servicio | ARQ-007–ARQ-008; PEN-002. |
| Exportación XLSX y descarga | ARQ-010; PEN-008. |

Se usa la decisión ratificada posterior cuando las instrucciones o referencias anteriores todavía presentan módulos, estilos o Recharts como propuestas. Ese desfase ya está reconocido en la sección 1.2 del documento arquitectónico; no se modificó ninguna fuente ni se tomó una nueva decisión de arquitectura.

## Verificación y límites

- Tres escenas JSON válidas con elementos editables, una etiqueta vinculada por contenedor y vínculos recíprocos de conectores.
- Dos conexiones en la vista general, ningún acceso frontend → base de datos, nueve módulos exactos y tres conexiones conceptuales en la vista interna.
- Revisión geométrica de textos, componentes, etiquetas y notas; sin superposiciones ni conexiones que atraviesen componentes ajenos.
- Vistas previas PNG generadas con un renderizador local de coordenadas y textos e inspeccionadas visualmente; medición tipográfica sin desbordamientos. No son exportaciones nativas de Excalidraw y no se probó su interfaz de edición.
- Se preservaron los documentos previos y diagramas existentes, comprobados mediante SHA-256. No se generó código de aplicación ni se hicieron commits.

Los diagramas representan **diseño aprobado**, no software implementado o probado. Las versiones concretas, entidades completas, contratos exhaustivos y evidencia ejecutable siguen siendo trabajos posteriores según la arquitectura.
