# Diagramas UML de casos de uso

Esta carpeta contiene los diagramas editables de la sección 7 en formato [Excalidraw](https://excalidraw.com/). La fuente normativa es [`../especificacion-casos-de-uso.md`](../especificacion-casos-de-uso.md), versión 1.1.

## Organización y cobertura

| Diagrama | Casos de uso definidos |
|---|---|
| `01-administracion-identidad.excalidraw` | UC-001 a UC-005 |
| `02-catalogo.excalidraw` | UC-006 |
| `03-inventario.excalidraw` | UC-007 a UC-009 |
| `04-compras.excalidraw` | UC-010 a UC-012 |
| `05-ventas.excalidraw` | UC-013 y UC-014 |
| `06-transferencias.excalidraw` | UC-015 a UC-017 |
| `07-logistica.excalidraw` | UC-018 |
| `08-dashboard.excalidraw` | UC-019 |
| `09-reportes-exportables.excalidraw` | UC-020 y sus tres especializaciones autorizadas |

Cada archivo contiene el límite de OptiPlant, actores, casos de uso, asociaciones enlazadas a sus elementos y solo las relaciones UML autorizadas. Todos comparten el mismo fondo claro, encabezado, subtítulo, límite de módulo blanco con borde discontinuo y casos de uso azules. Los actores se sitúan fuera del límite, a la izquierda: azul para Administrador general, naranja para Operador de inventario y verde para Gerente de sucursal.

Las asociaciones ordinarias son líneas continuas sin flecha, vinculadas a sus elementos. Cada asociación tiene un texto de acción o permiso vinculado a la propia línea, sin cajas decorativas. Los nombres de los casos se conservan íntegros, distribuidos en varias líneas cuando es necesario. Las notas justificadas se concentran en un recuadro discreto situado 85 px por debajo del último elemento del gráfico, incluidos actores y conectores.

En `01-administracion-identidad.excalidraw` se declara la generalización de Administrador general, Gerente de sucursal y Operador de inventario respecto a Usuario interno. En transferencias se representa `UC-017 <<extend>> UC-016`; en reportes, las tres especializaciones de UC-020.

## Relaciones deliberadamente omitidas

- No hay relaciones `<<include>>`: UC-004 es una precondición transversal, no un caso incluido.
- No se modela ningún sistema externo.
- No se crean casos para validaciones, cálculos, persistencia, permisos o reintentos técnicos.

## Comprobación de coherencia

Las asociaciones se derivan de los actores principales y secundarios de cada ficha. UC-004 y UC-005 muestran a **Usuario interno** como actor principal, conforme a sus fichas; la tabla de relaciones UML de la especificación no enumera esas dos asociaciones, aunque no las contradice.
