# Requisitos no funcionales — OptiPlant

**Estado:** documentación de la sección 6.1; requisitos delimitados por la prueba técnica y decisiones ya aprobadas. No acredita diseño, implementación ni pruebas.  
**Versión:** 1.0 — 21 de octubre de 2026.  
**Ámbito:** rendimiento, seguridad, escalabilidad, usabilidad, precisión y capacidad que condicionan el alcance funcional aprobado.

## Fuentes y criterio

| Clave | Fuente | Uso |
|---|---|---|
| E | [Prueba técnica](../00-source/prueba-tecnica.md), §§5, 6.1 y 8 | Exigencias externas. El PDF original es la fuente definitiva. |
| D | [Decisiones aprobadas](decisiones-aprobadas-optiplant-v1.0.md), INC-003, INC-004, INC-013, INC-017 e INC-020 | Parámetros ratificados del proyecto. |
| RF | [Requisitos funcionales](requisitos-funcionales.md), §§4 y 5.4 | Reglas funcionales que requieren una cualidad técnica verificable. |

Los requisitos **externos** proceden de E. Los requisitos **del proyecto** conservan su fuente D y no se presentan como imposiciones literales de OptiPlant. No se inventan métricas, mecanismos ni tecnologías no definidos.

## Rendimiento y capacidad

| ID | Tipo | Requisito | Fuente |
|---|---|---|---|
| RNF-REN-01 | Del proyecto | Tras confirmar una operación, la información visible en pantalla deberá actualizarse en un máximo de 5 segundos bajo condiciones normales del entorno de demostración. La carga, el entorno y el método de medición deberán definirse en diseño y pruebas. | D INC-004/D02; RF §5.4 |
| RNF-CAP-01 | Del proyecto | Cada reporte exportable admitirá como máximo 10.000 filas y un rango de hasta 12 meses. Si se supera alguno de los límites, se informará un error claro sin truncar datos ni modificar operaciones. | D INC-020/D10; RF-0050–RF-0055 |

## Seguridad

| ID | Tipo | Requisito | Fuente |
|---|---|---|---|
| RNF-SEG-01 | Externa | La arquitectura deberá documentar e implementar una estrategia de autenticación y autorización. | E §8.2 |
| RNF-SEG-02 | Del proyecto | Las contraseñas personales no se expondrán en consultas ni registros de actividad. La provisión del primer administrador será segura mediante Compose, sin credenciales públicas. | D INC-003/D10; RF-0006 |
| RNF-SEG-03 | Del proyecto | Las contraseñas permitirán entre 15 y 128 caracteres, incluidos espacios, sin rotación periódica arbitraria. | D INC-003/D09; RF-0006 |
| RNF-SEG-04 | Del proyecto | Una sesión expirará tras 30 minutos de inactividad o 8 horas de duración máxima absoluta. El cierre de sesión, la desactivación de cuenta y el cambio de rol o sucursal revocarán los accesos previos. | D INC-003/D12–D13; RF-0006 |
| RNF-SEG-05 | Del proyecto | La protección frente a intentos fallidos de acceso deberá concretarse durante el diseño de seguridad. Como punto de partida ratificado se consideran 5 intentos en 15 minutos y esperas progresivas de 5, 15 y hasta 30 minutos, sin bloqueo permanente automático. | D INC-003/D14; RF §5.4 |

## Usabilidad, precisión y escalabilidad

| ID | Tipo | Requisito | Fuente |
|---|---|---|---|
| RNF-USA-01 | Externa | La capa de presentación deberá ofrecer una interfaz de usuario responsiva y comunicarse con el backend únicamente mediante la API definida. | E §8.1 |
| RNF-PRE-01 | Del proyecto | Los cálculos de cantidades y costos deberán conservar decimales exactos; el costo promedio unitario se manejará con 6 decimales y los importes se mostrarán con 2, usando redondeo comercial mitad hacia arriba. | D INC-013/D03; RF-0026 |
| RNF-ESC-01 | Externa | La documentación de requisitos deberá cubrir la escalabilidad. La prueba técnica no fija métricas, volumen de usuarios, disponibilidad ni estrategia de escalado; tales valores no se definen en este documento. | E §6.1 |

## Límites de esta etapa

- Este documento no sustituye el [catálogo funcional](requisitos-funcionales.md), las [restricciones técnicas y de negocio](restricciones-tecnicas-y-de-negocio.md) ni el diseño arquitectónico.
- La selección de JWT, OAuth, bibliotecas, mecanismo de actualización, controles antiabuso detallados y estrategia de escalabilidad pertenece al diseño técnico posterior.
- Los requisitos anteriores deben verificarse en pruebas y evidencia de implementación; esta versión solo fija su especificación documental.
