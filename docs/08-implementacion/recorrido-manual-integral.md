Esta es la guía de recorrido manual en un solo flujo, basada en las cinco guías y en las etiquetas reales de la interfaz (`App.tsx`, `Administration.tsx`, `Catalog.tsx`, `Movements.tsx`, `lib/inventory/movements.ts`). No recorrí la aplicación en el navegador, así que los mensajes que no pude confirmar en el código aparecen como «aproximadamente».

## Preparación

- Levanta el entorno con `docker compose up --build --wait` y abre `http://localhost:5173`.
- **Ventana A** (navegador normal): siempre el administrador.
- **Ventana B** (ventana privada): los usuarios de sucursal, de uno en uno. Cierra sesión antes de cambiar de usuario.
- **Decimales:** se escriben con coma (`25,5`). El punto (`1.5`) se rechaza.
- **Datos previos:** antes de crear nada, revisa los listados (paso 2). Si ya existe un código o SKU de esta guía, reutilízalo y omite su alta. Si la demostración está activa (`DEMO-BOG`, `DEMO-MED`, `DEMO-PLANT`), no la mezcles con estos datos.

---

## Fase 1 — Administrador: acceso y estructura de la red (Ventana A)

**Paso 1 — Primer ingreso del administrador**
- **Dónde:** pantalla de inicio de sesión.
- **Qué hacer:** entra con el código y la contraseña del administrador definidos en `.env`. Si te pide el cambio inicial, escribe la contraseña actual y la nueva `OptiPlant-Admin-2026!` y pulsa **Actualizar contraseña**. Vuelve a iniciar sesión con la nueva.
- **Resultado esperado:** primero aparece «Debe cambiar su contraseña temporal antes de operar.», y después «Contraseña actualizada. Inicie sesión con la nueva contraseña.». Ya dentro, el menú lateral muestra Administración, Catálogo, Consultas, Movimientos, Alertas y Mi sesión.
- **Estado de los datos:** se guarda la nueva contraseña del administrador.

**Paso 2 — Revisar los datos existentes**
- **Dónde:** **Administración → Sucursales** y **Usuarios**; después **Catálogo**.
- **Qué hacer:** busca los códigos `MED-CEN`, `BOG-NOR`, `CAL-SUR`, los usuarios `ger.med`, `ope.med`, `ger.bog`, `ope.bog`, `resp.cal`, `admin.aux`, `ope.temporal` y los SKU `SUST-001`, `FERT-NPK`, `MAC-14`, `SEM-TOM`.
- **Resultado esperado:** sabes qué ya existe. No lo vuelvas a crear.
- **Estado de los datos:** sin cambios.

**Paso 3 — Crear las sucursales y probar un código repetido**
- **Dónde:** **Administración → Sucursales → Nueva sucursal**.
- **Qué hacer:** crea estas tres:

  | Código | Nombre |
  |---|---|
  | `MED-CEN` | Sede Medellín Centro |
  | `BOG-NOR` | Sede Bogotá Norte |
  | `CAL-SUR` | Sede Cali Sur |

  Luego intenta crear `MED-CEN` / «Duplicada».
- **Resultado esperado:** las tres aparecen como activas y cada una tiene su **Identificador público**. El duplicado muestra un error en la misma vista y no se crea.
- **Estado de los datos:** 3 sucursales activas.

**Paso 4 — Editar una sucursal**
- **Dónde:** **Sucursales → Editar** en `CAL-SUR`.
- **Qué hacer:** cambia el nombre a `Sede Cali Valle del Lili` y pulsa **Guardar sucursal**.
- **Resultado esperado:** cambia el nombre. El código y el identificador público siguen iguales, y el código no se puede editar.
- **Estado de los datos:** se actualiza el nombre de `CAL-SUR`.

**Paso 5 — Crear usuarios y probar las contraseñas temporales**
- **Dónde:** **Administración → Usuarios → Nuevo usuario**.
- **Qué hacer:** primero, con el código `ope.med`, prueba dos contraseñas temporales que deben fallar:
  - `ope.med`, igual al código. Mensaje: «La contraseña temporal debe ser distinta del código de usuario.»
  - `corta123`, demasiado corta. Mensaje: «…debe tener entre 15 y 128 caracteres.»

  Después crea estos usuarios:

  | Código | Nombre completo | Perfil | Sucursal | Contraseña temporal |
  |---|---|---|---|---|
  | `ger.med` | Laura Gómez | Gerente | MED-CEN | `Temporal-GerMed-2026` |
  | `ope.med` | Carlos Ruiz | Operador | MED-CEN | `Temporal-OpeMed-2026` |
  | `ger.bog` | Andrea Torres | Gerente | BOG-NOR | `Temporal-GerBog-2026` |
  | `ope.bog` | Julián Mora | Operador | BOG-NOR | `Temporal-OpeBog-2026` |
  | `resp.cal` | Sofía Herrera | Operador | CAL-SUR | `Temporal-RespCal-2026` |
  | `admin.aux` | Paula Rendón | Administrador | (ninguna) | `Temporal-AdmAux-2026` |
  | `ope.temporal` | Usuario de prueba | Operador | CAL-SUR | `Temporal-OpeTmp-2026` |
- **Resultado esperado:** cada rechazo se muestra una sola vez en la propia vista. Cada alta muestra «Usuario guardado. Entregue la contraseña temporal…». El administrador se crea sin sucursal.
- **Estado de los datos:** 7 usuarios activos, todos con cambio de contraseña pendiente.

---

## Fase 2 — Administrador: catálogo, unidades y mínimos (Ventana A)

**Paso 6 — Crear los productos y probar las validaciones**
- **Dónde:** **Catálogo → Nuevo producto**.
- **Qué hacer:**
  1. Crea `SUST-001`:
     - Nombre: «Sustrato de turba 50 L».
     - Unidad base: `bulto`, sin fracciones.
     - Pulsa **Elegir todas las sucursales activas**.
     - Mínimos: MED-CEN `10`, BOG-NOR `5`, CAL-SUR `3`.
  2. Intenta crear otro producto con SKU `SUST-001`.
  3. En un borrador nuevo, prueba un mínimo `2,5` con una unidad base sin fracciones.
  4. Crea `FERT-NPK`:
     - Nombre: «Fertilizante NPK 15-15-15».
     - Unidad base: `kg`, **con** fracciones.
     - Mínimos: MED-CEN `25,5`, BOG-NOR `10`.
  5. Crea `MAC-14`:
     - Nombre: «Maceta plástica 14 cm».
     - Unidad base: `unidad`, sin fracciones.
     - Solo MED-CEN, mínimo `20`.
  6. Crea `SEM-TOM`:
     - Nombre: «Semilla de tomate chonto (sobre)».
     - Unidad base: `sobre`, sin fracciones.
     - Solo CAL-SUR, mínimo `0`.
- **Resultado esperado:**
  - Cada alta confirma que no se registraron existencias.
  - El SKU repetido se rechaza con «El SKU ya está registrado en la red.» y el borrador se conserva.
  - El mínimo `2,5` se rechaza.
- **Estado de los datos:** 4 productos activos y 7 habilitaciones por sucursal, todas con existencia cero.

**Paso 7 — Unidades de medida y calculadora de equivalencias**
- **Dónde:** **Catálogo → Editar** `SUST-001` → **Unidades de medida → Nueva unidad**.
- **Qué hacer:**
  1. Crea `estiba` con equivalencia `40` y pulsa **Agregar unidad**.
  2. Prueba tres rechazos: `Estiba` con otra capitalización, factor `0` y factor `12,5`.
  3. En la calculadora elige `estiba`, cantidad `2`, y pulsa **Calcular equivalencia**. Luego prueba la cantidad `1,5`.
  4. Repite en `FERT-NPK`: crea la unidad `saco` con equivalencia `50` y sin fracciones.
- **Resultado esperado:**
  - `estiba` aparece como Activa, «Solo enteras», con «1 estiba = 40 bulto».
  - Los tres valores inválidos no crean la unidad. El factor `0` muestra «El factor debe ser positivo…».
  - La calculadora muestra «2 estiba = 80,000 bulto», y `1,5` se rechaza.
- **Estado de los datos:** unidad `estiba` (×40) en SUST-001 y unidad `saco` (×50) en FERT-NPK.

**Paso 8 — Habilitar en otra sucursal y cambiar un mínimo**
- **Dónde:** **Catálogo → Editar**.
- **Qué hacer:**
  1. En `MAC-14`, usa **Habilitar en otra sucursal** con BOG-NOR y mínimo `12`.
  2. En `SUST-001` → **Habilitación por sucursal** → BOG-NOR, pulsa **Cambiar mínimo**. Prueba `1,5`, `-1` y `1.5`.
  3. Después escribe `6` y pulsa **Guardar mínimo**.
- **Resultado esperado:**
  - MAC-14 queda habilitado en BOG-NOR sin existencias.
  - Los tres mínimos inválidos se rechazan y se conserva el anterior.
  - El mínimo `6` se acepta.
  - En CAL-SUR, MAC-14 muestra «Habilite para configurar».
- **Estado de los datos:** MAC-14 habilitado en BOG-NOR con mínimo 12. SUST-001 en BOG-NOR con mínimo 6.

**Paso 9 — Desactivar y reactivar un producto**
- **Dónde:** **Catálogo → Editar** `SEM-TOM`.
- **Qué hacer:**
  1. Pulsa **Desactivar producto**. En el diálogo de confirmación prueba primero **Cancelar** y luego confirma.
  2. Pulsa **Reactivar producto**.
  3. Vuelve a habilitarlo en CAL-SUR con mínimo `0`.
- **Resultado esperado:**
  - **Cancelar** no envía nada.
  - Al desactivarlo queda **Inactivo**, sigue en el listado y su sucursal pasa a «No habilitado / Sin mínimo».
  - Al reactivarlo vuelve sin ninguna sucursal habilitada.
  - Después de habilitarlo de nuevo queda operable en CAL-SUR.
- **Estado de los datos:** SEM-TOM activo y habilitado en CAL-SUR con mínimo 0.

**Paso 10 — Alertas iniciales de toda la red**
- **Dónde:** **Alertas** con «Todas las sucursales».
- **Qué hacer:** observa la tabla y luego filtra por `BOG-NOR`.
- **Resultado esperado:** todas las combinaciones habilitadas tienen existencia 0, menor o igual que su mínimo. Debe haber **8 alertas activas**:
  - SUST-001 en las 3 sucursales.
  - FERT-NPK en 2 sucursales.
  - MAC-14 en 2 sucursales.
  - SEM-TOM en CAL-SUR (mínimo 0).

  La tabla muestra la columna **Sucursal** y hay un aviso persistente. Con el filtro BOG-NOR quedan 3.
- **Estado de los datos:** 8 episodios de alerta activos.

---

## Fase 3 — Operador de Medellín: ingresos y retiros (Ventana B)

**Paso 11 — Primer ingreso del operador y su catálogo local**
- **Dónde:** Ventana B → iniciar sesión → **Catálogo**.
- **Qué hacer:**
  1. Entra como `ope.med` con `Temporal-OpeMed-2026`.
  2. Cambia la contraseña a `OptiPlant-OpeMed-2026!` y vuelve a entrar.
  3. Revisa las pestañas **Mi sucursal** y **Catálogo general**.
- **Resultado esperado:**
  - No ves **Administración** ni **Alertas**, y no hay botones de mantenimiento.
  - **Mi sucursal** lista SUST-001, FERT-NPK y MAC-14 en `0,000`, con **Físico**, **Disponible** y **En tránsito** separados, y «Requiere reabastecimiento».
  - En **Catálogo general**, SEM-TOM aparece como «No habilitado».
- **Estado de los datos:** se guarda la contraseña de `ope.med`.

**Paso 12 — Ingresos con costo justificado y con unidad comercial**
- **Dónde:** **Movimientos → Nuevo movimiento → Ingreso ordinario**.
- **Qué hacer:** registra tres ingresos:
  1. SUST-001, unidad `estiba`, cantidad `1`, motivo «Compra inicial de contado», costo unitario por bulto `18500`.
  2. FERT-NPK, unidad `saco`, cantidad `2`, motivo «Inventario inicial», costo por kg `4200,75`.
  3. MAC-14, unidad `unidad`, cantidad `60`, motivo «Inventario inicial», costo `1350`.

  Después registra un cuarto ingreso de FERT-NPK en `kg`, cantidad `12,5`, motivo «Reposición menor».
- **Resultado esperado:**
  - Con existencia cero, el formulario pide «Costo unitario justificado por …».
  - Las existencias resultantes son SUST-001 `40,000`, FERT-NPK `100,000` y después `112,500`, y MAC-14 `60,000`.
  - El cuarto ingreso no pide costo porque ya hay existencias y se usa el promedio vigente.
  - El historial no muestra costos al operador.
- **Estado de los datos:** saldos de MED-CEN: SUST 40, FERT 112,5, MAC 60. Las alertas de MED-CEN se resuelven solas.

**Paso 13 — Consulta en tiempo real y retiro rechazado y aceptado**
- **Dónde:** en la Ventana A, **Consultas** → elegir `MED-CEN` → **Consultar sucursal**, y dejar la vista abierta. En la Ventana B, **Movimientos**.
- **Qué hacer:** en la Ventana B, registra un **Retiro o merma** de SUST-001: primero cantidad `100`, luego cantidad `3` con el motivo «Bultos rotos en bodega».
- **Resultado esperado:**
  - El retiro de `100` se rechaza con «…excede las existencias físicas disponibles». El formulario conserva los datos y el historial no cambia.
  - El retiro de `3` se registra con existencias resultantes `37,000`.
  - En la Ventana A, sin pulsar nada, la tabla pasa de `40,000` a `37,000` en menos de 5 s. La hora de la última lectura avanza cada 2 s.
- **Estado de los datos:** SUST-001 en MED-CEN queda en 37.

**Paso 14 — Devolución sin venta de referencia**
- **Dónde:** **Movimientos → Nuevo movimiento → Devolución de cliente**.
- **Qué hacer:** MAC-14, cantidad `1`, motivo «Devolución en mostrador».
- **Resultado esperado:** como no hay ventas, aparece «No hay ventas con unidades por devolver…». Se registra sin referencia y MAC-14 queda en `61,000`.
- **Estado de los datos:** MAC-14 en MED-CEN queda en 61. Después, **Cerrar sesión**.

---

## Fase 4 — Gerente de Medellín: ajustes, correcciones y valoración (Ventana B)

**Paso 15 — Ajuste por conteo y corrección de una entrada**
- **Dónde:** **Movimientos**, como `ger.med`. Cambia la contraseña inicial a `OptiPlant-GerMed-2026!`.
- **Qué hacer:**
  1. Revisa el historial.
  2. **Nuevo movimiento → Ajuste por conteo**, **Disminuye**, FERT-NPK `0,5` kg, motivo «Conteo físico semanal».
  3. **Corrección de una entrada**. En «Entrada que corrige», elige el ingreso de 60 de MAC-14. Cantidad `5`, motivo «Se registraron 5 de más».
- **Resultado esperado:**
  - El historial muestra **Costo unitario** y **Efecto en valor**.
  - FERT-NPK queda en `112,000`.
  - La corrección aparece vinculada al ingreso original, que conserva sus datos. MAC-14 queda en `56,000`.
  - El gerente no ve las opciones de ingreso ordinario ni de retiro: son del operador.
- **Estado de los datos:** MED-CEN queda con SUST 37, FERT 112, MAC 56. Después, **Cerrar sesión**.

---

## Fase 5 — Bogotá: consulta entre sucursales y ciclo de alerta (Ventanas B y A)

**Paso 16 — Alertas del gerente de Bogotá**
- **Dónde:** Ventana B, como `ger.bog`. Cambia la contraseña a `OptiPlant-GerBog-2026!`. Abre **Alertas → Ver alertas de reabastecimiento**.
- **Resultado esperado:** hay un aviso de 3 productos en su stock mínimo o por debajo (SUST-001 con mínimo 6, FERT-NPK con 10 y MAC-14 con 12). El aviso permanece al cambiar de sección. Cada fila muestra disponible `0,000`, el mínimo y el estado «Activa».
- **Estado de los datos:** sin cambios. **Cerrar sesión**.

**Paso 17 — Consultar otra sucursal sin poder escribir**
- **Dónde:** Ventana B, como `ope.bog`. Cambia la contraseña a `OptiPlant-OpeBog-2026!`. Abre **Consultas** y elige `MED-CEN`.
- **Resultado esperado:**
  - Se muestran SUST `37,000`, FERT `112,000` y MAC `56,000`, sin costos.
  - Un aviso indica que es una consulta de solo lectura de otra sucursal.
  - No hay acceso al historial comercial de MED-CEN.
  - **Movimientos** solo opera sobre BOG-NOR.
- **Estado de los datos:** sin cambios.

**Paso 18 — Umbral inclusivo y resolución automática**
- **Dónde:** Ventana A, en **Alertas** filtrado por BOG-NOR. Ventana B (`ope.bog`), en **Movimientos → Ingreso ordinario**.
- **Qué hacer:**
  1. Registra un ingreso de SUST-001: `6` bulto, costo `18900`, motivo «Inventario inicial».
  2. Después otro ingreso de `1` bulto, motivo «Ajuste de recepción».
- **Resultado esperado:**
  - Con el primer ingreso, el disponible (6) es igual al mínimo (6): la alerta sigue activa y sigue siendo **una sola**.
  - Con el segundo, el disponible (7) supera el mínimo: la alerta desaparece sin pulsar nada.
  - En **Historial completo** figura como «Resuelta», con su fecha.
- **Estado de los datos:** SUST-001 en BOG-NOR queda en 7, con un episodio resuelto.

**Paso 19 — Un mínimo nuevo abre un episodio nuevo**
- **Dónde:** Ventana A, **Catálogo → Editar** `SUST-001` → BOG-NOR → **Cambiar mínimo**.
- **Qué hacer:** escribe `8` y pulsa **Guardar mínimo**. Ve a **Alertas**.
- **Resultado esperado:** se abre un episodio nuevo con mínimo `8,000`, porque 7 ≤ 8. El episodio anterior sigue como «Resuelta».
- **Estado de los datos:** SUST-001 en BOG-NOR con mínimo 8 y una alerta activa.

---

## Fase 6 — Administrador: reglas de protección, revocación y bajas (Ventana A)

**Paso 20 — Datos protegidos por el uso**
- **Dónde:** **Catálogo**.
- **Qué hacer:**
  1. Edita `SUST-001` e intenta cambiar su SKU a `SUST-002`.
  2. Pulsa **Desactivar producto** en `SUST-001`.
- **Resultado esperado:**
  - El SKU no se puede cambiar porque el producto ya tiene historial: rechazo de identidad protegida.
  - La baja se rechaza porque tiene existencias, y se indica el motivo.
- **Estado de los datos:** sin cambios.

**Paso 21 — Baja de una sucursal con existencias**
- **Dónde:** **Administración → Sucursales → Desactivar sucursal** en `MED-CEN`.
- **Resultado esperado:** se rechaza indicando el impedimento (existencias). No se hace ningún ajuste automático.
- **Estado de los datos:** sin cambios.

**Paso 22 — Historial de la red como administrador**
- **Dónde:** **Movimientos** (elegir `MED-CEN` → **Consultar historial**) y **Consultas** (`BOG-NOR`).
- **Resultado esperado:**
  - El historial de MED-CEN es completo e incluye la valoración.
  - No aparece **Nuevo movimiento**.
  - En **Consultas** el administrador ve saldos y referencias de cualquier sucursal.
- **Estado de los datos:** sin cambios.

**Paso 23 — Revocar una sesión al cambiar el perfil**
- **Dónde:** Ventana B como `resp.cal` (cambia la contraseña a `OptiPlant-RespCal-2026!` y deja la sesión abierta). Ventana A en **Administración → Usuarios → Editar** `resp.cal`.
- **Qué hacer:** cambia su perfil a **Gerente** y pulsa **Guardar usuario**. En la Ventana B, navega a cualquier sección.
- **Resultado esperado:** la Ventana B exige iniciar sesión de nuevo. Al volver a entrar, `resp.cal` ve **Alertas** de CAL-SUR, donde está la alerta de SEM-TOM con mínimo 0.
- **Estado de los datos:** CAL-SUR tiene ahora un gerente.

**Paso 24 — Desactivar un usuario**
- **Dónde:** **Administración → Usuarios → Desactivar usuario** en `ope.temporal`.
- **Qué hacer:** confirma la baja. En la Ventana B, intenta entrar con `ope.temporal` / `Temporal-OpeTmp-2026`. Luego, en la Ventana A, intenta crear otro usuario con el código `ope.temporal`.
- **Resultado esperado:** el registro se conserva como inactivo, el acceso se rechaza y el código queda reservado (el alta se rechaza).
- **Estado de los datos:** `ope.temporal` inactivo.

**Paso 25 — Cambio voluntario de contraseña y cierre de sesión**
- **Dónde:** Ventana B como `admin.aux` (cambio inicial a `OptiPlant-AdmAux-2026!`) → **Mi sesión → Contraseña**.
- **Qué hacer:**
  1. Cambia la contraseña de forma voluntaria a `OptiPlant-AdmAux-2026!!`.
  2. Vuelve a entrar con la nueva.
  3. Pulsa **Cerrar sesión** y usa el botón «Atrás» del navegador.
- **Resultado esperado:** el cambio cierra todas las sesiones de ese usuario. Después de cerrar sesión no se pueden ver datos protegidos. `admin.aux` opera sin sucursal asignada.
- **Estado de los datos:** se guarda la contraseña final de `admin.aux`.

---

## Resumen de cobertura

| Funcionalidad | Pasos |
|---|---|
| Autenticación, cambio inicial y voluntario, cierre de sesión (RF-0006) | 1, 11, 25 |
| Sucursales: alta, edición, duplicado, baja con impedimento (RF-0002/0003/0007) | 3, 4, 21 |
| Usuarios: alta, validación de contraseña, revocación, baja lógica (RF-0002/0003) | 5, 23, 24 |
| Permisos por rol y visibilidad local o de red (RF-0004/0005) | 11, 15, 17, 22 |
| Catálogo: alta, SKU único, identidad protegida, baja y reactivación (RF-0008–0011) | 6, 9, 20 |
| Mínimos (RF-0001) y catálogo local (RF-0012) | 8, 11, 19 |
| Unidades y equivalencias (RF-0013) | 7, 12 |
| Consulta entre sucursales y actualización en vivo (RF-0014/0015) | 13, 17 |
| Ingresos, retiros, devolución, ajuste, corrección e historial (RF-0016–0018) | 12–15 |
| Valoración por promedio y costo justificado (RF-0026) | 12, 15 |
| Stock mínimo y alertas (RF-0019/0020) | 10, 16, 18, 19, 23 |

## Datos que deberían quedar en PostgreSQL

- **Sucursales:**
  - MED-CEN: Sede Medellín Centro.
  - BOG-NOR: Sede Bogotá Norte.
  - CAL-SUR: Sede Cali Valle del Lili.
  - Las tres activas.
- **Usuarios:** el administrador inicial, `admin.aux`, `ger.med`, `ope.med`, `ger.bog`, `ope.bog`, `resp.cal` (gerente de CAL-SUR) y `ope.temporal` (inactivo).
- **Productos:**
  - SUST-001 (bulto, con la unidad `estiba` ×40).
  - FERT-NPK (kg, con fracciones, con la unidad `saco` ×50).
  - MAC-14 (unidad).
  - SEM-TOM (sobre, reactivado).
- **Existencias:**

  | Sucursal | SUST-001 | FERT-NPK | MAC-14 | SEM-TOM |
  |---|---|---|---|---|
  | MED-CEN | 37 | 112 | 56 | — |
  | BOG-NOR | 7 | 0 | 0 | — |
  | CAL-SUR | 0 | — | — | 0 |

- **Movimientos:** 10 en total.
  - MED-CEN: 8 (4 ingresos, 1 retiro, 1 devolución, 1 ajuste y 1 corrección).
  - BOG-NOR: 2 ingresos.

  El retiro de 100 rechazado no deja ningún registro.
- **Alertas activas:** SUST-001, FERT-NPK y MAC-14 en BOG-NOR; SUST-001 y SEM-TOM en CAL-SUR. Más los episodios resueltos de MED-CEN y el primero de SUST-001 en BOG-NOR.

## No incluidas en el recorrido y cómo verificarlas

- **Envío sin conexión e idempotencia:** desactiva la red en las herramientas del navegador justo antes de **Registrar movimiento**. Debe aparecer «No se sabe si el movimiento quedó registrado…». Reconecta y reenvía sin cambios: debe registrarse una sola vez.
- **Caída del servidor:** con **Consultas** abierta, ejecuta `docker compose stop backend`. Debe aparecer la advertencia ámbar y la tabla conserva la última lectura. Después ejecuta `docker compose start backend`. No borra datos.
- **Baja efectiva de una sucursal y consulta de una sucursal inactiva:** omitida para conservar las tres sucursales. Si la quieres probar, crea una sucursal vacía `PRU-BAJA` y desactívala.
- **Cambio de factor e inactivación de una unidad:** omitido porque alteraría `estiba` y `saco`. Si quieres probarlo, usa una unidad extra, por ejemplo `display` ×6 en MAC-14.
- **Devolución vinculada a una venta:** todavía no hay módulo de ventas, así que solo se puede probar sin referencia.
