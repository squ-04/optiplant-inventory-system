# Supuestos y dependencias del sistema — OptiPlant

**Estado:** documentación de la sección 6.1; registra condiciones de trabajo y relaciones necesarias, no decisiones nuevas.  
**Versión:** 1.0 — 21 de octubre de 2026.  
**Ámbito:** supuestos y dependencias mínimos para el alcance funcional aprobado.

## Fuentes y criterio

| Clave | Fuente | Uso |
|---|---|---|
| E | [Prueba técnica](../00-source/prueba-tecnica.md), §§2 y 5–6.1 | Condiciones externas. El PDF original es la fuente definitiva. |
| P | [Instrucciones del proyecto](../../AGENTS.md), §1 | Contexto y alcance aprobados. |
| D | [Decisiones aprobadas](decisiones-aprobadas-optiplant-v1.0.md), §§2–3 | Decisiones ratificadas que condicionan el sistema. |
| RF | [Requisitos funcionales](requisitos-funcionales.md) | Alcance funcional aprobado al que se aplican estas condiciones. |

Un **supuesto** es una condición tomada como válida para delimitar el trabajo. Una **dependencia** es un elemento o servicio requerido para operar o desarrollar dentro del alcance. Ninguno crea requisitos funcionales adicionales.

## Supuestos

| ID | Supuesto | Fuente |
|---|---|---|
| SUP-01 | El sistema gestionará inventario de varias sucursales pertenecientes a una sola organización. | E §2; RF §1 |
| SUP-02 | La operación local requiere conectividad con el backend; no se soportarán operaciones offline ni sincronización posterior. | D INC-004/D01–D03 |
| SUP-03 | Los usuarios internos operarán con los perfiles administrador, gerente de sucursal u operador de inventario definidos en el alcance funcional. | E §6.2; RF §4.1 |
| SUP-04 | No habrá integración con ERP, POS ni otros sistemas externos en esta versión. | P §1 |
| SUP-05 | La zona temporal de referencia del negocio será `America/Bogota`. | D INC-017/D06 |

## Dependencias

| ID | Dependencia | Propósito / condición | Fuente |
|---|---|---|---|
| DEP-01 | Backend y API REST | El frontend depende de la API para consultar y ejecutar operaciones; las reglas de negocio no residen en el cliente. | E §5; P §1 |
| DEP-02 | Base de datos PostgreSQL | Persistir información operativa e histórica del sistema. | P §1 |
| DEP-03 | Docker Compose | Levantar frontend, backend y base de datos con un único comando y sin configuración manual local. | E §5; P §1 |
| DEP-04 | Entorno de ejecución local | Requiere Docker y Docker Compose disponibles para cumplir la ejecución definida en DEP-03. | E §5 |
| DEP-05 | Catálogo funcional y decisiones ratificadas | La implementación posterior debe conservar los requisitos RF aprobados y las decisiones de negocio aplicables. | RF; D |

## Límites de esta etapa

- No se infieren integraciones, servicios externos, mecanismos de actualización, bibliotecas de seguridad ni componentes visuales que no estén aprobados.
- Las dependencias de detalle de implementación se identificarán y justificarán durante el diseño arquitectónico.
- Un cambio a los supuestos o a las dependencias que altere alcance, arquitectura o reglas de negocio requiere decisión explícita del proyecto.
