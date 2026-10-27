# Informe de exploración funcional E2E — OptiPlant

> Estado: evidencia de exploración generada con asistencia de IA (Claude Code), pendiente de revisión humana. Los hallazgos no son decisiones aprobadas ni alteran el alcance; las correcciones requieren autorización expresa.

- Fecha: 2026-10-27. Herramienta: `playwright-cli` 0.1.22 (Chromium headless). Commit: `01a068a` (main).
- Entorno (opción B): pila Compose desechable `optiplant-e2e-1793113771`, `.env` generado en scratch, datos demo V100 y cuentas de prueba propias. Sin datos de producción ni del `.env` del usuario.
- Servicios: frontend `http://localhost:55431` (Vite dev), backend `http://localhost:55430` (`/actuator/health` = UP), PostgreSQL 17.11 sin publicar al host (accedido vía `docker compose exec`). Los tres `healthy`.
- Datos creados: sucursal `E2E-CAL`, usuarios `E2E-OPERATOR-CODE-1` (operador, luego desactivado) y `E2E-MANAGER` (gerente), producto `E2E-SKU-001` con unidad `Caja` (12), 4 movimientos. No se eliminó ni modificó información preexistente.
- Sin cambios de código.

## Hallazgos priorizados

### Alta

**H1. Catálogo «Mi sucursal» muestra existencias obsoletas tras registrar movimientos.**
- Pasos: operador registra ingreso de 2 Caja (24 u) y retiro de 22 u en `E2E-CAL` → abre Catálogo.
- Esperado: físico/disponible 2.000 y estado coherente. Obtenido: 0.000/0.000 (estado al montar la vista). Repetido: tras ingreso de +5 (7.000 en servidor) la vista seguía en 2.000.
- Evidencia: `GET /api/v1/products/local` devolvía `physicalQuantity: "7.000"` (solicitud de Movimientos), la tabla mostraba 2.000. El recargo completo del navegador lo corrige. Corrección posterior: la vista sí ofrece «Actualizar catálogo» (`frontend/src/app/Catalog.tsx:75`); la afirmación inicial de que el operador no tenía botón de actualización era incorrecta.
- Causa probable: `frontend/src/app/Catalog.tsx:50-58` carga solo al montar o al cambiar `revision`; la vista permanece montada (oculta) y los movimientos no incrementan `revision`.
- Impacto: el operador ve stock e indicador de reabastecimiento erróneos (relacionado con RF-0012/RF-0020 y la frescura RNF pendiente de medir).

### Media

**H2. Error global persistente y contradictorio.** Tras un rechazo (409/400), el `alert` del shell no se limpia al tener éxito ni al cambiar de módulo. Se observó «La solicitud no es válida.» junto a «Usuario guardado.», y luego en Consultas, Catálogo y Movimientos. También el error se anuncia dos veces (`alert` global + `alert` local) para el mismo rechazo.

**H3. Mensajes 400 genéricos sin indicar la regla incumplida.** Crear usuario con contraseña temporal igual al código y crear unidad con factor `0` devuelven `VALIDATION_FAILED` «La solicitud no es válida.» sin campo ni causa; el formulario tampoco anticipa la regla (contraseña ≠ código).

### Baja

- **H4.** El cambio de contraseña acepta la misma contraseña temporal como nueva (`POST /auth/password` → 204). No hay requisito documentado que lo prohíba; se reporta como endurecimiento a decidir.
- **H5.** Tras el primer acceso con cambio obligatorio, la vista inicial es la del hash previo («Mi sesión» o «Contraseña») en lugar de la vista obligatoria/«Consultas».
- **H6.** Formatos inconsistentes: fechas ISO UTC crudas en Consultas (`2026-10-27T15:09:45.221196Z`), `27/10/26, 10:09:45 a. m.` en Movimientos, `27/10/2026, 10:14:11 a.m.` en Alertas; «a.m..» con doble punto en el estado de Consultas. Códigos sin traducir (`CONFIRMED`, `MANUAL_ENTRY`, `IN/OUT`, rol `ADMIN` en «Mi sesión» y tarjeta lateral frente a «Administrador» en la tabla).
- **H7.** Cantidades `8.000 unidad` con punto decimal en interfaz en español (se lee como ocho mil); mínimos de unidades enteras mostrados con 3 decimales; costos con precisión dispar (`48000.000000` / `-240000.00`) y signo `-` frente a `−`.
- **H8.** Un 500 del servidor se describe como «sin comunicación con el servidor» (Consultas). Comportamiento aparentemente intencional (RF-0015), pero el texto es impreciso.
- **H9.** La desactivación de usuario usa `window.confirm` nativo, fuera del patrón visual shadcn/ui.
- **H10.** `favicon.ico` → 404 en consola. El 401 inicial de `/auth/me` aparece como error de consola (esperado).

## Escenarios ejecutados

| # | Escenario | Esperado | Obtenido |
|---|---|---|---|
| 1 | Disponibilidad de servicios | 3 servicios sanos | OK |
| 2 | Login vacío | Validación nativa | OK («Completa este campo») |
| 3 | Login con credenciales erróneas | 401 y mensaje genérico | OK |
| 4 | Primer acceso ADMIN → cambio obligatorio | Bloquea módulos hasta cambiar | OK |
| 5 | Nueva contraseña < 15 caracteres | Rechazo | OK (validación cliente) |
| 6 | Crear sucursal con código duplicado | 409, conserva borrador | OK (ver H2) |
| 7 | Crear y editar sucursal; recargar | Persiste | OK |
| 8 | Crear usuario sin sucursal / contraseña = código | Rechazo | OK (ver H3) |
| 9 | Crear operador y gerente en E2E-CAL | 201 | OK |
| 10 | Perfil Administrador: sucursal opcional | Ámbito global | OK |
| 11 | Consultas: inventario DEMO-BOG con sondeo 2 s | Datos y refresco | OK (ver H6/H7) |
| 12 | Producto con SKU duplicado / mínimo 1.5 / −1 | Rechazo | OK |
| 13 | Crear producto habilitado en E2E-CAL (mín. 3) | 201, sin existencias | OK |
| 14 | Unidad duplicada (`Unidad`), factor 0, 12.5, 12 | 409/400/cliente/200 | OK (ver H3) |
| 15 | Calculadora: 2 Caja | 24 unidad | OK |
| 16 | Admin en Movimientos | Solo consulta de historial | OK |
| 17 | Alertas admin (red) | Alertas activas visibles | OK |
| 18 | Navegación operador | Sin Administración ni Alertas | OK |
| 19 | Operador: ingreso 2 Caja con costo −5, 1.5 Caja, sin motivo | Rechazo | OK |
| 20 | Operador: ingreso válido 2 Caja | +24 u, historial | OK |
| 21 | Operador: retiro 30 > 24 | 409 con mensaje claro | OK |
| 22 | Operador: retiro 22 | Stock 2, alerta | OK |
| 23 | Operador API: `/users`, `/inventory/reorder-alerts` | 403 | OK |
| 24 | Operador: Catálogo local tras movimientos | Stock actual | **FALLA (H1)** |
| 25 | Operador consulta otra sucursal | Permitido, solo lectura | OK |
| 26 | Gerente: alertas propias e historial | Abre/resuelve alerta | OK |
| 27 | Gerente: ajuste por conteo −5 | Stock 2, insignia «1 activa» | OK |
| 28 | Gerente API: `/users` | 403 | OK |
| 29 | Estado de carga en «Actualizar historial» (retraso 4 s) | Botón deshabilitado, aria-busy | OK |
| 30 | Consultas con 500 simulado | Mensaje de error y reintento | OK (ver H8) |
| 31 | Vista móvil 390 px | Sin desplazamiento horizontal | OK |
| 32 | Desactivar operador; reintentar acceso | 401 | OK (ver H9) |
| 33 | Persistencia en PostgreSQL | Registros y saldo 2.000 | OK |

## Evidencia de uso de IA

- Herramientas: Claude Code (Opus 5.5) con `playwright-cli`; consultas de red, consola y cuerpos de respuesta desde la sesión del navegador; consulta de solo lectura a PostgreSQL con `docker compose exec`.
- Solicitud del responsable (resumen): pruebas exploratorias E2E con Playwright de los módulos implementados, solo con cuentas y datos de prueba (opción B), sin modificar código ni eliminar información preexistente, con informe priorizado por severidad.
- Validación: H1 se reprodujo dos veces y se contrastó con la respuesta del servidor y el código fuente; los demás hallazgos se basan en una única observación.
- Corrección durante la sesión: un aparente error con el tipo «Retiro o merma» para el gerente se descartó; los tipos de movimiento por rol son intencionales.
- Decisiones humanas pendientes: priorizar y autorizar la corrección de H1–H3; decidir sobre H4.

## Limitaciones

- Exploración manual asistida por IA; no sustituye pruebas automatizadas.
- No se ejercitaron compras, ventas, transferencias, dashboards ni reportes (no implementados en UI).
- Errores 500 y retrasos fueron simulados con interceptación de red, no fallos reales.
- Sin medición formal de la frescura de 5 s ni de accesibilidad con lector de pantalla.

## Seguimiento

- Las correcciones de H1, H2, H3, H5, H6, H8 y H10 se registran en `odd/tasks/e2e-findings-fixes.md` (rama `fix/e2e-exploration-findings`). Decisiones del responsable (27/10/2026): H4 se acepta como comportamiento vigente (la contraseña temporal puede reutilizarse como nueva); H7 se resuelve con coma decimal y punto de miles en toda la interfaz y en los mensajes del servidor; H9 se resuelve con el `AlertDialog` oficial de shadcn/ui mediante una confirmación compartida.
