# Modelo de datos OptiPlant
Objetivo: especificación relacional autocontenida y vistas Mermaid/PlantUML en `docs/06-modelo-base-datos/modelo-datos.md`. Rama actual; sin commits ni publicación autorizados.
Entrega: un documento; previsión superior a 400 líneas por diccionario y representaciones equivalentes. No se planifican PR. Verificación: controles estructurales locales y revisión independiente del modelo; no implementar PostgreSQL.

## Specs

- S1: «Antes de diseñar, consulta íntegramente las fuentes relevantes y vigentes del proyecto». Fuentes leídas por explorador y contraste selectivo por responsable; respetar correcciones vigentes y PEN-001–008/DEC-TEC-ID-001.
- S2: «El entregable principal debe ser un único Markdown que contenga la especificación y las representaciones Mermaid y PlantUML solicitadas». Incluir inventario, diccionario completo, cardinalidades, restricciones, índices, historia, transacciones, infraestructura, trazabilidad y decisiones justificadas.
- S3: «cantidades divisibles de hasta tres decimales, costos promedio con seis decimales, importes presentados con dos decimales, redondeo HALF_UP y tratamiento vigente de residuales y compensaciones».
- S4: «Una única base PostgreSQL con organización lógica por módulos»; «Identificadores internos BIGINT y external_id UUIDv4 para los recursos públicos correspondientes, según DEC-TEC-ID-001»; «Despacho y confirmación única de llegada, incluyendo llegadas de cero unidades».
- S5: «No generes todavía código Java, entidades JPA, repositorios ni servicios»; «No generes migraciones Flyway ejecutables en esta actividad»; «No implementes físicamente la base de datos»; «No realices cambios de infraestructura»; «No modifiques documentos fuente ni decisiones previamente aprobadas».
- S6: «Antes de finalizar, ejecuta una revisión integral de consistencia entre los requisitos, el modelo relacional, su especificación y los diagramas». Distinguir inspección textual de validación automática/renderizado; informar incertidumbres reales y limitaciones.

## Tasks

- T1 [S1] — inline con mapa delegado por presupuesto de evidencia; completada: explorador leyó íntegramente fuentes vigentes y PDF; contraste PEN-002/004/005, RF numéricos y transferencias observado; sin commit.
- T2 [S2–S5] — inline; completada: 32 tablas propias, seis parejas de vistas, diccionario, trazabilidad y decisiones en documento; cierre verificado en T3; sin commit.
- T3 [S6] — inline + verificador independiente por riesgo semántico item 6; completada: seis pruebas documentales OK y tres bloqueadores cerrados tras un lote/recheck limitado; sin commit. Parser/renderizado no disponibles: inspección sintáctica explícita, no se afirma ejecución.

## Log

- L1 (2026-10-22): solicitud original: «Elaboración del modelo de datos y diagrama entidad-relación — OptiPlant». El mensaje humano completo de esta sesión es la referencia de alcance; las citas S1–S6 conservan sus restricciones principales. Limitación de captura: esta entrada no reproduce íntegramente el mensaje extenso; no sustituye ni resume normativamente sus apartados 1–10.2.
- L2: autorización expresa para resolver diseño técnico autónomamente, no funciones. Sin ciclos de aprobación. El documento es una especificación técnica derivada, no prueba de implementación ni cambio de decisiones funcionales.
- L3: mapa delegado sesión ses_e9b227145ffeC11QRM2QAzzuY0; RF v1.2, decisiones v1.2, arquitectura v1.1. Correcciones RF posteriores sustituyen recepciones sucesivas, cierre administrativo y administrador solicitante.
- L4: estado inicial: `.DS_Store`, `.atl/`, `AGENTS.md`, `docs/`, `odd/` no rastreados. No modificar ni limpiar artefactos ajenos; no hacer commits.
- L5: prueba RED/GREEN no aplicable a documentación sin implementación ejecutable. Se verificarán estructura, equivalencia de vistas y ejemplos aritméticos; no se afirmará integridad SQL probada ni renderizado sin herramientas.
- L6: controles locales detectaron relación sucursal/cuenta obligatoria en integral pero opcional en diccionario/modular; corregida a opcional para admitir administrador sin adscripción. Cuatro ejemplos aritméticos RF verificados con Decimal. Modo RDD global OFF; no iniciar revisión nativa. Mermaid/PlantUML no instalados en búsquedas locales iniciales; Java 21 disponible. Riesgo semántico item 6: no hay implementación ejecutable cuya suite pruebe el modelo de valoración; verificador independiente revisará especificación completa.
- L7: verificador ses_e9b1145c5ffe1mGFmNxZBpE4C3 detectó B1 piso económico no ratificado; B2 netos a 15 causaban doble redondeo; B3 faltaba obligación persistente de cambio de contraseña inicial. Un lote: valores derivados firmados sin alterar precios ingresados/stock no negativos, neto exacto NUMERIC(51,17), must_change_password BOOLEAN TRUE y cambio atómico. UF-DAT-001 retirada: conflicto del diseño, no ausencia funcional. Recheck limitado cerró B1–B3; no nuevos hallazgos.
- L8: probe durable odd/probes/test_modelo_datos.py (verificación documental, no aplicación). Primera ejecución 5 OK/1 fallo por regex que contaba encabezados fuera del diccionario; corregido alcance; última ejecución 6 OK tanto parent como copia independiente. 32 tablas, seis parejas, 72 relaciones. Git sin HEAD impide baseline histórico. Fuentes intactas, sin aplicación/migraciones/infraestructura/commits. Sintaxis inspeccionada textualmente sin parsers ni renderizadores disponibles.
- L9: ejecución final tras incorporar evidencia: 6 OK. Comparación automática adicional de atributos visibles/tipos/marcadores PK/FK/UK entre seis pares: OK; enlaces Markdown locales: OK. Scratch externo del verificador retirado por parent. Documento principal 1732 líneas / 113885 bytes. Lectura local de tareas completada; espejo Engram se sincroniza al cierre. Risk: item 6 (especificación semántica sin implementación ejecutable; revisión independiente realizada).
