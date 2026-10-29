Esta es la guía de recorrido manual en un solo flujo para compras, valoración, listas de precios y ventas (RF-0021–RF-0031). Continúa el [recorrido manual integral](recorrido-manual-integral.md) y parte exactamente de los datos que deja. Se basa en las guías de [compras](compras.md), [listas de precios](listas-de-precios.md) y [ventas](ventas.md), en el [informe E2E del 2026-10-28](exploracion-e2e-2026-10-28.md) y en las etiquetas reales de la interfaz (`App.tsx`, `Purchases.tsx`, `Suppliers.tsx`, `PriceLists.tsx`, `Sales.tsx`, `Valuation.tsx`, `Movements.tsx`). No recorrí la aplicación en el navegador, así que los mensajes que no pude confirmar en el código aparecen como «aproximadamente».

## Preparación

- Levanta el entorno con `docker compose up --build --wait` y abre `http://localhost:5173`. No borres el volumen: esta guía usa los datos del recorrido anterior.
- **Ventana A** (navegador normal): siempre el administrador (`OptiPlant-Admin-2026!`).
- **Ventana B** (ventana privada): los usuarios de sucursal, de uno en uno. Cierra sesión antes de cambiar de usuario.
- **Contraseñas vigentes:** `ope.med` `OptiPlant-OpeMed-2026!`, `ger.med` `OptiPlant-GerMed-2026!`, `ope.bog` `OptiPlant-OpeBog-2026!`, `ger.bog` `OptiPlant-GerBog-2026!`.
- **Decimales:** se escriben con coma (`2,5`). El punto (`1.5`) se rechaza. Los importes se muestran con punto de miles (`1.584.000,00`).
- **Códigos de documentos:** las órdenes se numeran `OC-000001`, `OC-000002`… y las ventas `VT-000001`… Si ya creaste alguna antes, tus códigos serán los siguientes; sustitúyelos donde la guía nombre uno.
- **Punto de partida** (fin del recorrido integral):

  | Sucursal | SUST-001 (bulto) | FERT-NPK (kg) | MAC-14 (unidad) |
  |---|---|---|---|
  | MED-CEN | 37 a 18.500 | 112 a 4.200,75 | 56 a 1.350 |
  | BOG-NOR | 7 a 18.900 (mínimo 8) | 0 (mínimo 10) | 0 (mínimo 12) |

  Unidades comerciales: `estiba` = 40 bulto en SUST-001 y `saco` = 50 kg en FERT-NPK.

---

## Fase 1 — Administrador: proveedores y listas de precios (Ventana A)

**Paso 1 — Revisar los datos existentes**
- **Dónde:** **Compras → Proveedores**, **Compras → Órdenes de compra**, **Precios** y **Ventas**.
- **Qué hacer:** busca los proveedores `PRV-SUST` y `PRV-AGRO`, las listas `DETAL` y `MAYOR`, y cualquier orden o venta previa.
- **Resultado esperado:** sabes qué ya existe. No lo vuelvas a crear. El menú lateral ahora muestra también Compras, Ventas, Precios y Valoración.
- **Estado de los datos:** sin cambios.

**Paso 2 — Crear proveedores y probar un código repetido**
- **Dónde:** **Compras → Proveedores → Nuevo proveedor**.
- **Qué hacer:** crea estos dos y pulsa **Crear proveedor** en cada uno:

  | Código | Nombre |
  |---|---|
  | `PRV-SUST` | Sustratos de Antioquia |
  | `PRV-AGRO` | Agroinsumos del Valle |

  Luego intenta crear `PRV-SUST` / «Duplicado».
- **Resultado esperado:** los dos aparecen como activos, ordenados por código. El duplicado se rechaza con «Ya existe un proveedor con ese código. No se modificó el catálogo.» y la lista no cambia. El formulario solo pide código y nombre (sin NIT ni datos fiscales).
- **Estado de los datos:** 2 proveedores activos.

**Paso 3 — Crear las listas de precios**
- **Dónde:** **Precios → Nueva lista**.
- **Qué hacer:** crea estas dos con **Crear lista**:

  | Código | Nombre |
  |---|---|
  | `DETAL` | Precios al detal |
  | `MAYOR` | Precios al por mayor |

  Luego intenta crear `DETAL` / «Duplicada».
- **Resultado esperado:** «Lista DETAL creada.» y «Lista MAYOR creada.», ambas activas. El duplicado se rechaza con «Ya existe una lista de precios con ese código. No se modificó el catálogo.».
- **Estado de los datos:** 2 listas activas, sin precios.

**Paso 4 — Definir precios por producto y unidad**
- **Dónde:** **Precios → Ver precios** en cada lista → **Definir o reemplazar un precio**.
- **Qué hacer:**
  1. En `DETAL`, define con **Guardar precio**:

     | Producto | Unidad comercial | Precio |
     |---|---|---|
     | SUST-001 | bulto | `32000` |
     | FERT-NPK | kg | `7500` |
     | FERT-NPK | saco | `350000` |
     | MAC-14 | unidad | `2500` |

  2. Prueba un precio `-5` para MAC-14: el navegador lo bloquea.
  3. En la fila de MAC-14 pulsa **Cambiar precio**, escribe `2600` y guarda.
  4. En `MAYOR`, define SUST-001 / bulto `28000` y MAC-14 / unidad `2100`.
- **Resultado esperado:**
  - Cada alta confirma «Precio definido» y aparece su fila con el precio en COP (`32.000,00`).
  - El cambio confirma «Precio actualizado»: la fila de MAC-14 muestra `2.600,00` y no se duplica.
  - Las dos listas conservan precios distintos para el mismo producto (SUST-001: 32.000 en DETAL y 28.000 en MAYOR).
- **Estado de los datos:** DETAL con 4 precios y MAYOR con 2. Sin cambios en existencias.

---

## Fase 2 — Operador de Medellín: órdenes de compra (Ventana B)

**Paso 5 — Lo que ve el operador**
- **Dónde:** Ventana B como `ope.med` → **Precios** y **Compras**.
- **Resultado esperado:**
  - En **Precios** aparecen `DETAL` y `MAYOR` con sus precios, sin **Nueva lista**, **Editar** ni **Cambiar precio**.
  - En **Compras** no hay pestaña **Proveedores**, pero sí **Nueva orden de compra**.
  - No aparece **Valoración** en el menú.
- **Estado de los datos:** sin cambios.

**Paso 6 — Validaciones del formulario de compra**
- **Dónde:** **Compras → Nueva orden de compra**.
- **Qué hacer:** elige `PRV-SUST`, SUST-001, unidad `estiba`, cantidad `1`, precio `760000` y prueba, uno a la vez:
  - Plazo de pago `-1` y luego `1.5`.
  - Descuento `101`.
  - Cantidad `1,5` (la estiba no admite fracciones).
- **Resultado esperado:** el navegador bloquea cada valor con la ayuda del campo: «Número entero de días; 0 vence el día de la recepción.», «Porcentaje entre 0 y 100 con hasta 6 decimales; 0 si no aplica.» y «Número entero.». No se crea ninguna orden.
- **Estado de los datos:** sin cambios. Deja el formulario abierto para el paso 7.

**Paso 7 — Crear un borrador con condiciones**
- **Dónde:** el mismo formulario.
- **Qué hacer:** proveedor `PRV-SUST`, plazo de pago `30` y dos productos (usa **Agregar producto** para el segundo):

  | Producto | Unidad | Cantidad | Precio unitario | Descuento % |
  |---|---|---|---|---|
  | SUST-001 | estiba | `2` | `760000` | `5` |
  | MAC-14 | unidad | `100` | `1400` | `0` |

  Pulsa **Crear borrador**. Después revisa **Catálogo → Mi sucursal**.
- **Resultado esperado:**
  - El neto de cada línea se calcula en vivo: `1.444.000,00` y `140.000,00`. **Total neto estimado** `1.584.000,00 COP`.
  - «Borrador OC-000001 creado con … Las existencias no cambian hasta confirmar la recepción.».
  - En el catálogo, SUST-001 sigue en `37,000` y MAC-14 en `56,000`.
- **Estado de los datos:** OC-000001 en borrador.

**Paso 8 — Editar el borrador**
- **Dónde:** **Compras → Ver detalle** de OC-000001 → **Editar borrador**.
- **Qué hacer:** cambia la cantidad de MAC-14 a `120` y pulsa **Guardar borrador**.
- **Resultado esperado:** «Borrador OC-000001 actualizado: … Sigue en borrador; las existencias no cambian.». El total pasa a `1.612.000,00`.
- **Estado de los datos:** OC-000001 en borrador con 2 estiba de SUST-001 y 120 MAC-14.

**Paso 9 — El nombre del proveedor se congela al enviar**
- **Dónde:** Ventana A en **Compras → Proveedores → Editar** `PRV-SUST`. Ventana B en el detalle de OC-000001.
- **Qué hacer:**
  1. En la Ventana A, renombra el proveedor a `Sustratos de Antioquia S.A.S.` y pulsa **Guardar cambios**.
  2. En la Ventana B, pulsa **Enviar a recepción**. Prueba primero **Cancelar** en el diálogo y luego confirma.
  3. En la Ventana A, renombra el proveedor otra vez a `Sustratos Antioquia Ltda.`.
  4. En la Ventana B, pulsa **Actualizar histórico** y abre de nuevo el detalle.
- **Resultado esperado:**
  - **Cancelar** no envía nada.
  - Al enviar: «OC-000001 quedó pendiente de recepción.». El proveedor aparece como «Sustratos de Antioquia S.A.S.».
  - Tras el segundo renombre, la orden conserva «Sustratos de Antioquia S.A.S.».
  - La orden pendiente ya no ofrece **Editar borrador**; solo **Confirmar recepción** y **Cancelar orden**.
- **Estado de los datos:** OC-000001 pendiente de recepción. El proveedor se llama ahora «Sustratos Antioquia Ltda.».

**Paso 10 — Confirmar la recepción completa**
- **Dónde:** detalle de OC-000001 → **Confirmar recepción**.
- **Qué hacer:** en el diálogo pulsa primero Escape; luego vuelve a pulsar **Confirmar recepción** y confirma. Después revisa **Movimientos** y **Catálogo → Mi sucursal**.
- **Resultado esperado:**
  - El diálogo explica que entran las 2 líneas completas y que empieza a contar el plazo de 30 días. Escape no envía nada.
  - «Recepción de OC-000001 confirmada: el inventario de Sede Medellín Centro aumentó con las 2 líneas recibidas. Vence el …» (hoy + 30 días).
  - La orden queda **Recibida**, con «Recibida por» Carlos Ruiz. El operador no ve la columna **Costo por unidad base**.
  - **Movimientos** muestra dos «Recepción de compra»: +2 estiba de SUST-001 y +120 unidad de MAC-14.
  - El catálogo muestra SUST-001 `117,000` y MAC-14 `176,000`.
- **Estado de los datos:** OC-000001 recibida. MED-CEN: SUST 117, FERT 112, MAC 176.

**Paso 11 — Cancelar una orden pendiente y reemplazarla**
- **Dónde:** **Compras → Nueva orden de compra**.
- **Qué hacer:**
  1. Crea un borrador: `PRV-AGRO`, plazo `0`, FERT-NPK, unidad `saco`, cantidad `2`, precio `225000`, descuento `0` (neto `450.000,00`).
  2. Envíalo a recepción.
  3. Pulsa **Cancelar orden**, escribe el motivo `El proveedor no tiene existencias` y pulsa **Confirmar cancelación**.
  4. En la orden cancelada pulsa **Crear orden de reemplazo**. Cambia el descuento a `4` y pulsa **Crear borrador**.
  5. Abre de nuevo la orden cancelada.
- **Resultado esperado:**
  - La cancelación avisa que no se registra ningún ingreso. La orden queda **Cancelada** y la **Historia de estados** muestra el motivo. FERT-NPK sigue en `112,000`.
  - El reemplazo llega prellenado con los datos de la cancelada. Al guardar: «Borrador OC-000003 creado en reemplazo de OC-000002… Ambas órdenes quedan en el histórico.», con total `432.000,00`.
  - El detalle del reemplazo muestra «Reemplaza a OC-000002»; el de la cancelada, «Reemplazada por OC-000003», y ya no ofrece otro reemplazo.
- **Estado de los datos:** OC-000002 cancelada y OC-000003 en borrador.

**Paso 12 — Recibir el reemplazo con plazo cero**
- **Dónde:** detalle de OC-000003.
- **Qué hacer:** pulsa **Enviar a recepción** y después **Confirmar recepción**.
- **Resultado esperado:** la orden queda **Recibida** con vencimiento hoy (plazo 0). FERT-NPK pasa a `212,000` kg (2 saco × 50).
- **Estado de los datos:** OC-000003 recibida. MED-CEN: SUST 117, FERT 212, MAC 176.

**Paso 13 — Dejar un borrador abierto**
- **Dónde:** **Compras → Nueva orden de compra**.
- **Qué hacer:** `PRV-AGRO`, plazo `15`, MAC-14, unidad, cantidad `50`, precio `1450`, descuento `0`. Pulsa **Crear borrador** y no lo envíes.
- **Resultado esperado:** OC-000004 en borrador por `72.500,00`. MAC-14 sigue en `176,000`.
- **Estado de los datos:** OC-000004 en borrador. **Cerrar sesión**.

---

## Fase 3 — Gerente de Medellín: histórico y valoración (Ventana B)

**Paso 14 — Histórico de compras con filtros**
- **Dónde:** Ventana B como `ger.med` → **Compras**.
- **Qué hacer:**
  1. Observa el resumen sin filtros.
  2. Filtra por **Proveedor** `PRV-SUST` y pulsa **Consultar**.
  3. Filtra por **Producto** FERT-NPK (todos los proveedores).
  4. Filtra por **Estado** «Canceladas».
  5. Abre el detalle de OC-000001.
- **Resultado esperado:**
  - No hay **Nueva orden de compra** ni acciones sobre las órdenes.
  - Resumen: Órdenes 4, Borradores 1, Pendientes de recepción 0, Compras recibidas 2, Canceladas 1.
  - Con PRV-SUST: solo OC-000001. Con FERT-NPK: OC-000002 y OC-000003. Con «Canceladas»: OC-000002.
  - En OC-000001 el gerente sí ve **Costo por unidad base**: SUST-001 `18.050,000000` (760.000 × 0,95 ÷ 40) y MAC-14 `1.400,000000`.
- **Estado de los datos:** sin cambios.

**Paso 15 — Valoración tras las compras**
- **Dónde:** **Valoración**.
- **Resultado esperado:**

  | Producto | Existencias | Costo promedio | Valor |
  |---|---|---|---|
  | SUST-001 | 117,000 bulto | 18.192,307692 | 2.128.500,00 |
  | FERT-NPK | 212,000 kg | 4.257,000000 | 902.484,00 |
  | MAC-14 | 176,000 unidad | 1.384,090909 | 243.600,00 |
  | **Total** | | | **3.274.584,00** |

  El promedio mezcla el saldo anterior con la compra. Por ejemplo, SUST-001: (37 × 18.500 + 80 × 18.050) ÷ 117. La orden cancelada y el borrador no cambiaron nada.
- **Estado de los datos:** sin cambios. **Cerrar sesión**.

---

## Fase 4 — Operador de Medellín: ventas (Ventana B)

**Paso 16 — Primera venta con descuento por línea**
- **Dónde:** Ventana B como `ope.med` → **Ventas → Nueva venta**.
- **Qué hacer:**
  1. Elige la lista `DETAL` y la primera línea: SUST-001, bulto, cantidad `10`, descuento `0`.
  2. **Agregar producto**: SUST-001, estiba, cantidad `3`. Observa el aviso y pulsa **Quitar** en esa línea.
  3. **Agregar producto**: FERT-NPK, kg, cantidad `2,5`, descuento `10`.
  4. **Agregar producto**: MAC-14, unidad, cantidad `20`, descuento `5`.
  5. Pulsa **Confirmar venta**. En el diálogo prueba primero **Cancelar** y luego confirma.
- **Resultado esperado:**
  - Cada línea muestra **Precio de lista**, el neto unitario y «Disponible: …» de la sucursal.
  - La línea de 3 estiba (120 bulto) suma 130 bulto con la primera y supera los 117: aparece el aviso de que la venta será rechazada si no alcanzan.
  - FERT-NPK: precio `7.500,00`, neto unitario `6.750,00`, neto `16.875,00`. MAC-14: precio `2.600,00`, neto `49.400,00`.
  - **Subtotal de lista** `390.750,00`, **Descuentos** `4.475,00`, **Total neto estimado** `386.275,00`.
  - Al confirmar: «Venta VT-000001 confirmada por 386.275,00 COP. Las existencias de Sede Medellín Centro disminuyeron y el comprobante quedó registrado.». Se abre el comprobante.
- **Estado de los datos:** VT-000001. MED-CEN: SUST 107, FERT 209,5, MAC 156.

**Paso 17 — Venta rechazada completa por existencias**
- **Dónde:** **Ventas → Nueva venta**.
- **Qué hacer:** lista `DETAL`, SUST-001 bulto `5` y MAC-14 unidad `200`, ambas con descuento `0`. Pulsa **Confirmar venta** y acepta el diálogo.
- **Resultado esperado:** el servidor rechaza la venta completa con «Existencias insuficientes de MAC-14 Maceta plástica 14 cm: la venta requiere 200,000 y hay 156,000 disponibles en la unidad base. No se confirmó la venta ni se descontó inventario.» (aproximadamente). El borrador se conserva, no aparece otro comprobante y SUST-001 sigue en `107,000` aunque su línea sí alcanzaba.
- **Estado de los datos:** sin cambios. Deja el borrador para el paso 18.

**Paso 18 — Borrador conservado y venta con otra lista**
- **Dónde:** **Catálogo** y de vuelta a **Ventas**.
- **Qué hacer:**
  1. Ve a **Catálogo** y vuelve a **Ventas**. Pulsa **Continuar venta en borrador**.
  2. Quita la línea de MAC-14. Cambia la lista a `MAYOR` y la cantidad de SUST-001 a `10`.
  3. Prueba el descuento `100,5` y luego escribe `0`.
  4. Confirma la venta.
- **Resultado esperado:**
  - El botón dice **Continuar venta en borrador** y las líneas siguen ahí.
  - Con `MAYOR` el precio de lista pasa a `28.000,00`.
  - `100,5` se bloquea con «Porcentaje entre 0 y 100 con hasta 6 decimales; 0 si no aplica.».
  - Se confirma VT-000002 por `280.000,00`. Una venta usa una sola lista para todas sus líneas.
- **Estado de los datos:** VT-000002. SUST-001 en MED-CEN queda en 97.

**Paso 19 — Precio cambiado con el formulario abierto**
- **Dónde:** Ventana B en **Ventas → Nueva venta**. Ventana A en **Precios → Ver precios** de `DETAL`.
- **Qué hacer:**
  1. En la Ventana B: lista `DETAL`, MAC-14 unidad `10`, descuento `0`. El total estimado es `26.000,00`. No confirmes todavía.
  2. En la Ventana A: **Cambiar precio** de MAC-14 a `2700`.
  3. En la Ventana B: pulsa **Confirmar venta**.
  4. Revisa el total y pulsa **Confirmar venta** otra vez.
- **Resultado esperado:**
  - El primer intento no abre el diálogo: «Los precios de la lista Precios al detal cambiaron desde que se consultaron (línea 1). Revise el total actualizado y confirme de nuevo.». El total pasa a `27.000,00`.
  - El segundo intento confirma VT-000003 por `27.000,00`.
- **Estado de los datos:** VT-000003. MAC-14 en MED-CEN queda en 146. MAC-14 en DETAL vale 2.700.

**Paso 20 — Lista desactivada con el formulario abierto**
- **Dónde:** Ventana B en **Ventas → Nueva venta**. Ventana A en **Precios → Editar** `MAYOR`.
- **Qué hacer:**
  1. En la Ventana B: lista `MAYOR`, SUST-001 bulto `1`. No confirmes.
  2. En la Ventana A: desmarca **Lista activa: disponible para nuevas ventas** y pulsa **Guardar cambios**.
  3. En la Ventana B: pulsa **Confirmar venta**. Después pulsa **Descartar borrador**.
  4. En la Ventana B, abre **Precios**.
- **Resultado esperado:**
  - «La lista de precios elegida ya no está disponible: fue desactivada. Elija otra lista; el borrador se conserva.». `MAYOR` sale del selector y no se registra ninguna venta.
  - En la Ventana A, `MAYOR` aparece «Inactiva» y conserva sus 2 precios.
  - En **Precios**, el operador solo ve `DETAL`.
- **Estado de los datos:** MAYOR inactiva. Sin ventas nuevas.

**Paso 21 — Comprobante inmutable y devolución vinculada**
- **Dónde:** **Ventas → Ver comprobante** de VT-000001. Después **Movimientos → Nuevo movimiento → Devolución de cliente**.
- **Qué hacer:**
  1. Revisa el comprobante de VT-000001.
  2. En la devolución, abre **Venta de referencia** y elige la salida de MAC-14 de VT-000001.
  3. Prueba la cantidad `25` y luego `2`, con el motivo «Macetas con defecto de fábrica».
  4. Vuelve al comprobante de VT-000001.
- **Resultado esperado:**
  - El comprobante muestra fecha, sucursal, responsable (Carlos Ruiz), lista «Precios al detal», identificador y las 3 líneas. MAC-14 conserva el precio de lista `2.600,00`, aunque la lista ya vale 2.700. Indica «Registro interno sin validez fiscal».
  - El selector identifica la venta por su código: «Venta VT-000001, …: 20,000 unidad de MAC-14…».
  - `25` se rechaza: «La devolución supera lo vendido menos lo ya devuelto…» (aproximadamente).
  - `2` se registra vinculada a la venta. MAC-14 queda en `148,000`.
  - El comprobante no cambia: la devolución no edita la venta ni genera reembolso.
- **Estado de los datos:** MED-CEN: SUST 97, FERT 209,5, MAC 148. **Cerrar sesión**.

---

## Fase 5 — Supervisión en Medellín y operación en Bogotá (Ventanas B y A)

**Paso 22 — Ventas y valoración del gerente**
- **Dónde:** Ventana B como `ger.med` → **Ventas** y **Valoración**.
- **Resultado esperado:**
  - **Ventas** lista VT-000003, VT-000002 y VT-000001, de la más reciente a la más antigua, con **Ver comprobante** y sin **Nueva venta**.
  - **Valoración** (las salidas consumen el promedio sin cambiarlo; la devolución entra a su costo histórico):

    | Producto | Existencias | Costo promedio | Valor |
    |---|---|---|---|
    | SUST-001 | 97,000 bulto | 18.192,307692 | 1.764.653,85 |
    | FERT-NPK | 209,500 kg | 4.257,000000 | 891.841,50 |
    | MAC-14 | 148,000 unidad | 1.384,090909 | 204.845,45 |
    | **Total** | | | **2.861.340,80** |

    Puede haber una diferencia de ±0,01 por redondeo en SUST-001 y MAC-14.
- **Estado de los datos:** sin cambios. **Cerrar sesión**.

**Paso 23 — Compra en Bogotá que resuelve una alerta**
- **Dónde:** Ventana A en **Alertas** filtrado por BOG-NOR. Ventana B como `ope.bog` → **Compras**.
- **Qué hacer:**
  1. Comprueba que `ope.bog` no ve ninguna orden de MED-CEN.
  2. Crea una orden: `PRV-AGRO`, plazo `15`, FERT-NPK, `saco`, cantidad `1`, precio `230000`, descuento `0`.
  3. Envíala a recepción y confirma la recepción.
- **Resultado esperado:**
  - El histórico de `ope.bog` estaba vacío antes de crear la orden.
  - OC-000005 queda **Recibida** por `230.000,00`, con vencimiento hoy + 15 días. FERT-NPK en BOG-NOR pasa a `50,000` kg.
  - En la Ventana A, la alerta de FERT-NPK en BOG-NOR desaparece sin pulsar nada (50 > 10). En **Historial completo** figura como «Resuelta».
- **Estado de los datos:** OC-000005. BOG-NOR: SUST 7, FERT 50, MAC 0.

**Paso 24 — Venta en Bogotá que mantiene la alerta**
- **Dónde:** Ventana B como `ope.bog` → **Ventas → Nueva venta**.
- **Qué hacer:** lista `DETAL`, SUST-001 bulto `3`, descuento `0`. Confirma.
- **Resultado esperado:** VT-000004 por `96.000,00`. SUST-001 en BOG-NOR queda en `4,000`. Su alerta sigue activa y sigue siendo una sola (4 ≤ 8). En **Ventas**, `ope.bog` solo ve VT-000004.
- **Estado de los datos:** VT-000004. BOG-NOR: SUST 4, FERT 50, MAC 0. **Cerrar sesión**.

---

## Fase 6 — Administrador: vista de red (Ventana A)

**Paso 25 — Compras, ventas y valoración de la red**
- **Dónde:** **Compras → Órdenes de compra**, **Ventas** y **Valoración**.
- **Qué hacer:**
  1. En **Compras**, elige «Toda la red» y pulsa **Consultar**. Luego filtra por `PRV-AGRO`.
  2. En **Ventas**, elige «Toda la red».
  3. En **Valoración**, elige `BOG-NOR` y pulsa **Consultar valoración**. Luego elige `MED-CEN`.
- **Resultado esperado:**
  - El título es «Compras de la red», con la columna **Sucursal** y sin **Nueva orden de compra**. Resumen: Órdenes 5, Borradores 1, Pendientes de recepción 0, Compras recibidas 3, Canceladas 1. Con `PRV-AGRO`: OC-000002 a OC-000005, de las dos sucursales.
  - «Ventas de la red» muestra las 4 ventas con su sucursal y responsable, sin **Nueva venta**.
  - Valoración de BOG-NOR: SUST-001 4,000 a 18.900,000000 = `75.600,00`; FERT-NPK 50,000 a 4.600,000000 = `230.000,00`; total `305.600,00`. MED-CEN coincide con el paso 22.
- **Estado de los datos:** sin cambios.

---

## Resumen de cobertura

| Funcionalidad | Pasos |
|---|---|
| Proveedores y creación de órdenes con condiciones (RF-0021, RF-0023) | 2, 6, 7, 13 |
| Nombre histórico del proveedor (RF-0021-CA-03) | 9 |
| Ciclo de vida: edición de borradores, envío, cancelación y reemplazo (RF-0022) | 8, 9, 11, 12 |
| Recepción completa por el servicio de inventario y vencimiento (RF-0024) | 10, 12, 23 |
| Histórico por proveedor, producto y estado (RF-0025) | 14, 23, 25 |
| Valoración por promedio ponderado (RF-0026) | 14, 15, 22, 25 |
| Registro de ventas, doble confirmación y borrador local (RF-0027) | 16, 18, 19 |
| Validación indivisible de existencias (RF-0028) | 16, 17 |
| Descuento por línea (RF-0029) | 16, 18 |
| Listas de precios: alta, precios, cambio, desactivación y consulta por perfil (RF-0030) | 3, 4, 5, 19, 20 |
| Comprobante inmutable y devolución vinculada (RF-0031) | 16, 21, 22 |
| Permisos por perfil y aislamiento por sucursal (§5.2) | 5, 14, 22, 23, 24, 25 |
| Integración con alertas (RF-0020) | 23, 24 |

## Datos que deberían quedar en PostgreSQL

- **Proveedores:** `PRV-SUST` «Sustratos Antioquia Ltda.» y `PRV-AGRO` «Agroinsumos del Valle», ambos activos.
- **Listas de precios:**
  - `DETAL` (activa): SUST-001 bulto 32.000; FERT-NPK kg 7.500; FERT-NPK saco 350.000; MAC-14 unidad 2.700.
  - `MAYOR` (inactiva): SUST-001 bulto 28.000; MAC-14 unidad 2.100.
- **Órdenes de compra:**

  | Orden | Sucursal | Proveedor (nombre conservado) | Estado | Total neto |
  |---|---|---|---|---|
  | OC-000001 | MED-CEN | Sustratos de Antioquia S.A.S. | Recibida (vence hoy + 30) | 1.612.000,00 |
  | OC-000002 | MED-CEN | Agroinsumos del Valle | Cancelada, con motivo | 450.000,00 |
  | OC-000003 | MED-CEN | Agroinsumos del Valle | Recibida, reemplaza a OC-000002 | 432.000,00 |
  | OC-000004 | MED-CEN | Agroinsumos del Valle | Borrador | 72.500,00 |
  | OC-000005 | BOG-NOR | Agroinsumos del Valle | Recibida (vence hoy + 15) | 230.000,00 |

- **Ventas:**

  | Venta | Sucursal | Lista | Total neto |
  |---|---|---|---|
  | VT-000001 | MED-CEN | Precios al detal | 386.275,00 |
  | VT-000002 | MED-CEN | Precios al por mayor | 280.000,00 |
  | VT-000003 | MED-CEN | Precios al detal | 27.000,00 |
  | VT-000004 | BOG-NOR | Precios al detal | 96.000,00 |

  La venta rechazada del paso 17 y la del paso 20 no dejan ningún registro.
- **Existencias:**

  | Sucursal | SUST-001 | FERT-NPK | MAC-14 | SEM-TOM |
  |---|---|---|---|---|
  | MED-CEN | 97 | 209,5 | 148 | — |
  | BOG-NOR | 4 | 50 | 0 | — |
  | CAL-SUR | 0 | — | — | 0 |

- **Movimientos nuevos:** 11, uno por línea de cada documento.
  - MED-CEN: 9. Son 3 «Recepción de compra» (2 de OC-000001 y 1 de OC-000003), 5 «Venta» (3 de VT-000001, 1 de VT-000002 y 1 de VT-000003) y 1 «Devolución de cliente» vinculada.
  - BOG-NOR: 1 «Recepción de compra» y 1 «Venta».
- **Alertas activas:** SUST-001 y MAC-14 en BOG-NOR; SUST-001 y SEM-TOM en CAL-SUR. FERT-NPK en BOG-NOR queda resuelta.

## No incluidas en el recorrido y cómo verificarlas

- **Repetir una recepción o una venta (idempotencia):** desde la interfaz no se puede reenviar con otra clave. En la API, repetir `POST /api/v1/purchases/orders/{id}/receive` con otra `Idempotency-Key` responde `409 PURCHASE_ALREADY_RECEIVED`; con la misma clave devuelve el resultado original. Para la venta, desactiva la red en las herramientas del navegador justo antes de **Confirmar venta**, reconecta y reenvía sin cambios: debe quedar una sola venta.
- **Recepción parcial:** no existe en la interfaz. En la API, enviar líneas en el cuerpo de `/receive` responde `400 PARTIAL_RECEIPT_NOT_SUPPORTED`.
- **Baja de sucursal con compras abiertas:** MED-CEN tiene el borrador OC-000004, pero su baja ya se rechaza antes por las existencias. Para aislar esta regla («La sucursal tiene compras en borrador o pendientes de recepción.») hace falta una sucursal sin existencias con un operador activo.
- **Ventas simultáneas sobre el mismo saldo:** requiere dos sesiones del mismo operador confirmando a la vez; está cubierto por `SaleHttpTest` y por el informe E2E (escenario 25).
- **Perfiles que intentan escribir por la API:** el gerente y el administrador reciben `403` (`PURCHASE_FORBIDDEN`, `SALE_FORBIDDEN`) aunque el cuerpo sea inválido; el operador recibe `403 VALUATION_FORBIDDEN` en la valoración.
- **CAL-SUR:** queda fuera porque desde el recorrido anterior no tiene operador activo (`resp.cal` es gerente y `ope.temporal` está inactivo).
- **Costo congelado en tránsito (RF-0026-CA-02):** depende de las transferencias, aún no implementadas.
