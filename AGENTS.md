# Instrucciones del proyecto — OptiPlant

Estas instrucciones complementan las instrucciones globales de Gentle AI y Organic Driven Development (ODD); no las sustituyen ni redefinen.

## 1. Contexto y decisiones aprobadas

- Proyecto: Sistema de Inventario Multi-Sucursal de OptiPlant Consultores.
- Backend: monolito modular con separación interna por capas, Java y Spring Boot.
- Persistencia: Spring Data JPA con Hibernate y PostgreSQL.
- Frontend: React con TypeScript y Vite.
- Comunicación: exclusivamente mediante API REST, alternativa inicial aprobada.
- Dashboards: Recharts como alternativa inicial.
- Biblioteca de componentes visuales y sistema de estilos: pendientes de definición durante el diseño técnico.
- Infraestructura: Docker Compose.
- Presentación prevista: 27 de octubre de 2026.
- Funcionalidad adicional seleccionada: módulo de reportes exportables, conforme a la alternativa propuesta en la sección 4 del enunciado oficial.
- Distinguir la obligación externa de implementar al menos una funcionalidad adicional de la decisión del proyecto de implementar reportes exportables. Estos deberán aportar valor incremental respecto a los dashboards, consultas y reportes básicos obligatorios.
- Los formatos de exportación y las categorías concretas de reportes quedaron ratificados en el registro de decisiones aprobadas (INC-020/D01–D10): Excel `.xlsx` real para movimientos de inventario, ventas y transferencias, con fechas obligatorias, columnas, permisos y topes definidos. La propuesta inicial de Excel filtrado por rangos de fechas fue su antecedente; ya no debe tratarse como alcance pendiente ni reabrirse sin una decisión expresa.
- Integraciones con sistemas externos (ERP, POS u otros): excluidas del alcance del proyecto. El actor opcional de sistema externo no se implementará. Conservar su referencia en la matriz de cobertura como funcionalidad opcional analizada y excluida por decisión del proyecto. Esta exclusión no afecta la API REST entre React y Spring Boot.
- Organización modular de referencia propuesta para el backend: `identity`, `catalog`, `inventory`, `purchases`, `sales`, `transfers`, `logistics`, `dashboard` y `reports`. La clasificación funcional no impone los límites definitivos de los paquetes Java; sus límites y dependencias deberán revisarse y justificarse durante el diseño arquitectónico de la sección 8.

Estas elecciones pertenecen al proyecto: no deben presentarse como requisitos tecnológicos impuestos por OptiPlant. Las alternativas iniciales no autorizan sustituciones automáticas.

## 2. Fuentes y trazabilidad

- Utilizar `docs/00-source/prueba-tecnica.pdf` como fuente original y definitiva de los requisitos externos.
- Utilizar `docs/00-source/prueba-tecnica.md` como transcripción operativa; contrastarla con el PDF ante dudas o discrepancias.
- No modificar los documentos originales sin autorización expresa.
- Diferenciar requisitos explícitos, requisitos derivados, propuestas y decisiones aprobadas. Identificar su fuente o justificación.
- Mantener trazabilidad entre requisitos, casos de uso, diagramas, decisiones arquitectónicas, implementación y pruebas.
- No completar vacíos del enunciado inventando funcionalidades ni convertir recomendaciones en obligaciones.
- Asignar identificadores únicos y estables a los requisitos, casos de uso y decisiones arquitectónicas, para facilitar su trazabilidad y evitar referencias ambiguas.

## 3. Trabajo y aprobaciones

- Aplicar ODD conforme a las instrucciones globales, sin crear un protocolo alternativo.
- Planificar antes de ejecutar tareas complejas o con incertidumbre significativa.
- Solicitar aprobación antes de adoptar decisiones arquitectónicas importantes, cambiar el alcance o resolver conflictos entre documentos.
- Durante el desarrollo de las secciones 6, 7 y 8, no generar código de aplicación salvo autorización expresa.
- Respetar las restricciones de cada interacción, especialmente las tareas de solo lectura.
- No realizar commits, merges, publicaciones ni operaciones destructivas sin autorización expresa; la autorización general de una tarea no basta.
- No modificar las instrucciones globales, crear agentes personalizados adicionales ni forzar cambios automáticos entre Plan, Build y Gentle-Orchestrator.
- Evitar sobreingeniería y funcionalidades ajenas al alcance aprobado.

## 4. Documentación y diagramas

- Utilizar español técnico, preciso y profesional en documentación y explicaciones.
- Priorizar Markdown para documentación y PlantUML, Mermaid o Excalidraw para diagramas versionables.
- Mantener consistencia de términos, reglas y estados entre los distintos artefactos.
- Documentar las razones y consecuencias de las decisiones significativas; conservar las alternativas pendientes como tales.
- Distinguir los artefactos en borrador de aquellos revisados y aprobados. No utilizar decisiones pendientes o documentos en borrador como fundamento definitivo de otros artefactos.

## 5. Verificación y evidencia de IA

- Verificar los artefactos frente a los requisitos originales y las decisiones aprobadas.
- Detectar y comunicar omisiones y contradicciones; no resolverlas silenciosamente.
- Conservar evidencia auténtica del uso de IA: herramientas, etapas, prompts, resultados y validaciones.
- Incluir las decisiones y correcciones humanas relevantes, así como las limitaciones de los resultados generados.
- No fabricar evidencias ni presentar propuestas de IA como decisiones aprobadas.
