# Restricciones técnicas y de negocio — OptiPlant

**Estado:** documentación de la sección 6.1; no sustituye los requisitos funcionales ni aprueba diseño, implementación o pruebas.  
**Versión:** 1.0 — 21 de octubre de 2026.  
**Ámbito:** restricciones explícitas de la prueba técnica y decisiones ya aprobadas del proyecto.

## Fuentes y criterio

| Clave | Fuente | Uso |
|---|---|---|
| E | [Prueba técnica](../00-source/prueba-tecnica.md), §§2, 4, 5 y 6.1 | Restricciones externas. El PDF original es la fuente definitiva. |
| P | [Instrucciones del proyecto](../../AGENTS.md), §1 | Decisiones tecnológicas y de alcance aprobadas. |
| D | [Decisiones aprobadas](decisiones-aprobadas-optiplant-v1.0.md), §§2–3 | Delimitaciones ratificadas del proyecto. |

Las restricciones marcadas como **externas** provienen de E. Las marcadas como **del proyecto** no se atribuyen a OptiPlant como imposiciones tecnológicas.

## Restricciones técnicas

| ID | Tipo | Restricción | Fuente |
|---|---|---|---|
| RT-01 | Externa | La solución tendrá frontend, backend y base de datos como capas separadas, con responsabilidades definidas. | E §5 |
| RT-02 | Externa | El frontend se comunicará exclusivamente con el backend mediante una API bien definida; no contendrá lógica de negocio. | E §5 |
| RT-03 | Externa | El sistema completo deberá ejecutarse localmente con un único comando de Docker Compose, sin configuración manual adicional del entorno local. | E §5 |
| RT-04 | Externa | El stack es libre si se cumplen RT-01 a RT-03; las decisiones tecnológicas deberán justificarse. | E §5 |
| RT-05 | Del proyecto | La API será REST; React y Spring Boot se comunicarán únicamente por esa API. | P §1 |
| RT-06 | Del proyecto | El backend será un monolito modular con separación interna por capas; usará Java, Spring Boot, Spring Data JPA/Hibernate y PostgreSQL. El frontend usará React, TypeScript y Vite. | P §1 |
| RT-07 | Del proyecto | Docker Compose será la infraestructura de ejecución local. Recharts es la alternativa inicial para dashboards; la biblioteca visual y el sistema de estilos aún se definirán durante el diseño técnico. | P §1 |

## Restricciones de negocio y alcance

| ID | Tipo | Restricción | Fuente |
|---|---|---|---|
| RB-01 | Externa | El sistema atenderá varias sucursales de una misma organización: cada una opera localmente y comparte visibilidad del inventario general. | E §2 |
| RB-02 | Externa | Debe implementarse al menos una funcionalidad adicional que aporte valor real. | E §4 |
| RB-03 | Del proyecto | La funcionalidad adicional seleccionada son reportes exportables con Excel `.xlsx` real para movimientos de inventario, ventas y transferencias; no se incluye PDF. | P §1; D INC-020/D01–D10 |
| RB-04 | Del proyecto | Las integraciones con ERP, POS u otros sistemas externos quedan fuera de alcance. El actor opcional «Sistema externo» no se implementará. | P §1 |
| RB-05 | Del proyecto | La operación es sobre una plataforma conectada: no habrá almacenamiento ni sincronización diferida para operaciones offline. Una operación no aceptada por el backend no se confirma. | D INC-004/D01–D03 |
| RB-06 | Del proyecto | La zona horaria de negocio única es `America/Bogota` para calendario, indicadores y filtros de exportación. | D INC-017/D06 |

## Límites de esta etapa

- Esta documentación no duplica las reglas observables, permisos, flujos y criterios del [catálogo de requisitos funcionales](requisitos-funcionales.md).
- La especificación de requisitos no funcionales, API, modelo de datos, diagramas y decisiones pendientes de diseño se documentarán en sus etapas correspondientes.
- No se agregan obligaciones por recomendación, ejemplo o tecnología no aprobada.
