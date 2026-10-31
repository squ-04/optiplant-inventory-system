> Estado: realizada. El responsable completó los 26 pasos en su entorno local, a continuación del recorrido de transferencias (confirmado el 31/10/2026), sin contraste posterior de la base local. Generada con IA (Claude Code) el 30/10/2026. El flujo de datos, las cifras y los libros se comprobaron ese día en una pila desechable sobre una copia del volumen local; los textos del Dashboard y de Reportes se leyeron en Chromium para administrador, gerente y operador. Las operaciones de catálogo, ventas y transferencias se ejecutaron por API en esa copia, por lo que sus mensajes de interfaz no se repiten aquí, salvo los ya confirmados en guías anteriores. La copia se eliminó después; tus datos no cambiaron. El 31/10/2026 se ajustaron los pasos 15–26 porque TR-000010 se solicitó solo con SUST-001; esas cifras derivan de la ejecución original.

Esta es la guía de recorrido manual en un solo flujo para el dashboard (RF-0044–RF-0049) y los reportes exportables (RF-0050–RF-0055). Continúa el [recorrido manual de transferencias](recorrido-manual-transferencias.md) y parte exactamente de los datos que deja. Se basa en [reportes exportables](reportes-exportables.md), en el [informe E2E del 2026-10-30](exploracion-e2e-2026-10-30.md), incluidas las correcciones H1–H5, y en las etiquetas reales de `Dashboard.tsx`, `DashboardIndicators.tsx` y `Reports.tsx`.

## Preparación

- Levanta el entorno con `docker compose up --build --wait` y abre `http://localhost:5173`. No borres el volumen: esta guía usa los datos de los recorridos anteriores.
- **Ventana A** (navegador normal): siempre un administrador. En la copia, `ADMIN` con `OptiPlant-Admin-2026!` fue rechazado; `admin.aux` con `OptiPlant-AdmAux-2026!!` funcionó. Usa tu contraseña vigente de `ADMIN` o esa cuenta.
- **Ventana B** (ventana privada): los usuarios de sucursal, de uno en uno. Cierra sesión antes de cambiar de usuario.
- **Contraseñas vigentes:** `ope.med` `OptiPlant-OpeMed-2026!`, `ger.med` `OptiPlant-GerMed-2026!`, `ope.bog` `OptiPlant-OpeBog-2026!`, `ger.bog` `OptiPlant-GerBog-2026!`.
- **Hazla completa antes del 1 de noviembre (hora de Bogotá).** Las cifras esperadas suponen que octubre es el mes en curso. Desde noviembre, la ventana pasa a agosto–noviembre, las ventas nuevas caerían en noviembre y los totales de octubre de esta guía no coincidirían.
- **Decimales y fechas:** se escriben con coma (`2,5`), las cantidades se muestran con tres decimales (`20,000`) y las horas en hora de Bogotá.
- **Códigos:** la guía crea `VT-000005`, `VT-000006` y `TR-000007`–`TR-000010`. Si creas algo fuera de la guía, sustituye los códigos y ajusta las cifras.
- **Datos demo:** `DEMO-BOG` y `DEMO-MED` aparecen en selectores y en la comparativa, pero la guía no opera con ellos.
- **Excel:** ten a mano Excel para abrir los libros de la fase 7. Las descargas van a la carpeta de descargas del navegador.
- **Punto de partida** (fin del recorrido de transferencias, contrastado en la copia):

  | Sucursal | SUST-001 (bulto) | FERT-NPK (kg) | MAC-14 (unidad) | Mínimos |
  |---|---|---|---|---|
  | MED-CEN | 77 | 164,5 | 113 | 10 / 25,5 / 20 |
  | BOG-NOR | 24 | 95 | 32 | 8 / 10 / 12 |

  Hay 4 ventas (3 de MED-CEN y 1 de BOG-NOR), 2 devoluciones en MED-CEN, 35 movimientos y ninguna transferencia activa. CAL-SUR no tiene existencias ni ventas.

---

## Fase 1 — Perfiles y sucursal (Ventanas A y B)

**Paso 1 — Lo que ve cada perfil**
- **Dónde:** menú lateral en la Ventana A y en la Ventana B como `ger.med` y `ope.med`. Abre **Dashboard** con cada uno.
- **Resultado esperado:**
  - Administrador: menú con **Dashboard**, **Cumplimiento**, **Reportes** y **Alertas**; cinco pestañas: **Ventas y rotación**, **Demanda**, **Transferencias**, **Próximos a agotarse** y **Comparativa**.
  - `ger.med`: **Reportes** en el menú; cuatro pestañas, sin **Comparativa** ni selector de sucursal.
  - `ope.med`: sin **Reportes**, **Cumplimiento**, **Valoración** ni **Alertas**; cuatro pestañas, sin **Comparativa**.
- **Estado de los datos:** sin cambios. **Cerrar sesión** en la Ventana B.

**Paso 2 — El administrador elige una sucursal**
- **Dónde:** Ventana A → **Dashboard**.
- **Qué hacer:** antes de elegir, recorre las cuatro primeras pestañas. Luego elige `MED-CEN · Sede Medellín Centro` en **Sucursal**.
- **Resultado esperado:** sin sucursal, cada pestaña lo pide, por ejemplo «Seleccione una sucursal para ver sus ventas mensuales y la rotación de su inventario.». Al elegir, el encabezado dice «Indicadores de Sede Medellín Centro (MED-CEN)…».
- **Estado de los datos:** sin cambios.

---

## Fase 2 — Ventas mensuales y rotación con tus datos (Ventanas A y B)

**Paso 3 — Comparación mensual con importes (RF-0044)**
- **Dónde:** Ventana A, MED-CEN, pestaña **Ventas y rotación**. Después elige `BOG-NOR` y `CAL-SUR`.
- **Resultado esperado:**
  - «Sucursal registrada el 27/10/2026.» en MED-CEN y BOG-NOR.
  - Julio, agosto y septiembre de 2026: «Sin datos» en ventas, importe y devoluciones, con la nota «Sin datos: la sucursal aún no estaba registrada». No son cero.
  - Octubre: «Mes en curso, hasta el …, desde el registro de la sucursal».

    | Sucursal | Ventas | Importe neto | Devoluciones |
    |---|---|---|---|
    | MED-CEN | 3 ventas | 693.275,00 COP | 2 devoluciones |
    | BOG-NOR | 1 venta | 96.000,00 COP | Ninguna |
    | CAL-SUR | Sin ventas | 0,00 COP | Ninguna |

  - El gráfico de MED-CEN y BOG-NOR solo tiene barra en octubre, con trama de mes incompleto y la leyenda «Sin barra: sin datos». CAL-SUR: «Sin ventas confirmadas en estos meses: no hay importes que graficar.».
  - **Unidades vendidas por producto** en MED-CEN: FERT-NPK `2,500`, MAC-14 `30,000` («Devuelto: 3,000») y SUST-001 `20,000`. Cada fila conserva su unidad; no hay un total.
- **Por qué:** 693.275 sale de VT-000001 (386.275), VT-000002 (280.000) y VT-000003 (27.000). Las devoluciones no restan.
- **Estado de los datos:** sin cambios.

**Paso 4 — El operador no ve importes agregados**
- **Dónde:** Ventana B como `ope.med` → **Dashboard**, pestaña **Ventas y rotación**.
- **Resultado esperado:** el texto dice «Número de ventas confirmadas y unidades vendidas.». No hay gráfico, columna **Importe neto** ni montos en COP. Sí aparecen «3 ventas» y «2 devoluciones», y las unidades por producto. Tampoco hay selector de sucursal.
- **Estado de los datos:** sin cambios. **Cerrar sesión**.

**Paso 5 — Rotación: historia insuficiente y resultados calculados (RF-0045)**
- **Dónde:** Ventana B como `ger.med` → **Dashboard**, sección **Rotación del inventario**.
- **Qué hacer:**
  1. Revisa el período propuesto, del 1 de octubre a hoy.
  2. Pon **Desde** posterior a **Hasta** y pulsa **Calcular rotación**. Luego pon **Hasta** en mañana.
  3. Usa `28/10/2026` a `29/10/2026`.
  4. Usa `29/10/2026` a `29/10/2026`.
- **Resultado esperado:**
  1. Los tres productos muestran «Sin historia», **No calculable** y «Historia insuficiente: el inventario de la sucursal se registra desde el 27/10/2026». El sistema no rellena del 1 al 26 con ceros.
  2. «La fecha final del período no puede ser anterior a la inicial.» y «La fecha final del período no puede ser posterior a hoy: los días futuros no tienen inventario registrado.».
  3. Período con historia completa:

     | Producto | Vendidas | Inventario promedio | Rotación |
     |---|---|---|---|
     | FERT-NPK | 2,500 | 169,162 | 0,01 |
     | MAC-14 | 30,000 | 111,728 | 0,27 |
     | SUST-001 | 20,000 | 73,666 | 0,27 |

  4. Las tres rotaciones son `0,00` con «Sin ventas en el período, con existencias»: un cero real, distinto de «No calculable».
- **Por qué:** el primer día válido es el 28 porque el 27 las existencias aparecieron a media tarde. Comprueba a mano MAC-14: 30 ÷ 111,728 = 0,27. El período 28–29 ya cerró, así que estos valores no cambian con lo que hagas después.
- **Estado de los datos:** sin cambios. **Cerrar sesión**.

**Paso 6 — Rotación por sucursal (Ventana A)**
- **Dónde:** Ventana A, sucursal `BOG-NOR`, con `28/10/2026` a `29/10/2026`. Después `CAL-SUR`.
- **Resultado esperado:**
  - BOG-NOR: SUST-001 vendidas `3,000`, promedio `7,435`, rotación `0,40`. FERT-NPK y MAC-14: «Sin historia», porque su primer ingreso fue el 28 y el 29.
  - CAL-SUR: «La sucursal no tiene inventario registrado en este período. Elija un período posterior a su primer movimiento.».
- **Estado de los datos:** sin cambios.

---

## Fase 3 — Demanda y próximos a agotarse (Ventanas A y B)

**Paso 7 — Rankings separados por unidad (RF-0046)**
- **Dónde:** Ventana A → pestaña **Demanda**, con `MED-CEN`, `BOG-NOR` y `CAL-SUR`.
- **Qué hacer:** con cualquiera de ellas, consulta también `2026-09` y luego el mes siguiente al actual.
- **Resultado esperado:**
  - MED-CEN: tres rankings («Productos vendidos en bulto», «en kg» y «en unidad»), cada uno con «1 producto con ventas». El producto queda en **Alta demanda** y **Baja demanda** dice «Con un solo producto vendido no hay grupo de baja demanda.».
  - BOG-NOR: un ranking en bulto (SUST-001 `3,000`) y **Sin ventas en octubre de 2026** con FERT-NPK y MAC-14.
  - CAL-SUR: «Ningún producto tuvo ventas confirmadas en octubre de 2026.» y la lista sin ventas con SEM-TOM y SUST-001.
  - Septiembre: «No hay datos de septiembre de 2026: el mes terminó antes de registrar la sucursal.».
  - Mes futuro: «El mes consultado no puede ser posterior al mes en curso.».
- **Estado de los datos:** sin cambios.

**Paso 8 — Transferencias activas y stock mínimo antes de operar (RF-0047, RF-0048)**
- **Dónde:** Ventana A → pestañas **Transferencias** y **Próximos a agotarse**, con `MED-CEN`, `BOG-NOR`, `CAL-SUR` y `DEMO-MED`.
- **Resultado esperado:**
  - **Transferencias:** «Esta sucursal no tiene transferencias activas.» en todas. TR-000002 no aparece porque su faltante ya está tratado y sin tránsito.
  - **Próximos a agotarse:** MED-CEN y BOG-NOR dicen «Ningún producto de esta sucursal está en su mínimo o por debajo.».
  - CAL-SUR: «2 productos»: SEM-TOM `0,000 sobre` con mínimo `0,000` y SUST-001 `0,000 bulto` con mínimo `3,000`, ambos «Sin existencias». Mínimo cero con saldo cero también cuenta.
  - DEMO-MED: 1 producto.
- **Estado de los datos:** sin cambios.

---

## Fase 4 — Comparativa inicial (Ventana A y Ventana B)

**Paso 9 — Comparar sucursales (RF-0049)**
- **Dónde:** Ventana A → pestaña **Comparativa**.
- **Qué hacer:** revisa octubre y luego consulta `2026-09`. Cambia la **Sucursal** de arriba y comprueba que la comparativa no cambia.
- **Resultado esperado:**

  | Sucursal | Ventas | Importe neto | Como origen | Como destino |
  |---|---|---|---|---|
  | BOG-NOR | 1 venta | 96.000,00 COP | 0,00 % (0 de 1) | 50,00 % (1 de 2) |
  | CAL-SUR | Sin ventas | 0,00 COP | No calculable | No calculable |
  | DEMO-BOG | 1 venta | 30,00 COP | No calculable | No calculable |
  | DEMO-MED | Sin ventas | 0,00 COP | No calculable | No calculable |
  | MED-CEN | 3 ventas | 693.275,00 COP | 50,00 % (1 de 2) | 0,00 % (0 de 1) |

  Las celdas de cumplimiento dicen «Sin llegadas confirmadas en el período» cuando no son calculables. Los porcentajes coinciden con el reporte de **Cumplimiento** del recorrido anterior. Septiembre: todas las filas en «Sin datos», y «Ninguna sucursal tiene ventas confirmadas en este mes: no hay importes que graficar.».
- **Estado de los datos:** sin cambios.

---

## Fase 5 — Datos nuevos: demanda comparable y mínimos (Ventanas A y B)

**Paso 10 — Dos productos con la misma unidad base**
- **Dónde:** Ventana A → **Catálogo → Nuevo producto**. Después **Precios**, lista `DETAL`.
- **Qué hacer:**
  1. Crea `MAC-10`, «Maceta plástica 10 cm», unidad base `unidad` sin fracciones, solo MED-CEN, mínimo `5`.
  2. Crea `MAC-20`, «Maceta plástica 20 cm», con la misma configuración.
  3. En `DETAL`, asigna `2.000` a MAC-10 y `3.500` a MAC-20, ambos por `unidad`.
- **Resultado esperado:** ambos productos quedan activos, habilitados en MED-CEN y con precio. Sin existencias, aparecen en **Próximos a agotarse** y **Alertas** de MED-CEN hasta el paso siguiente.
- **Estado de los datos:** 2 productos nuevos con precio.

**Paso 11 — Ingresar, vender y devolver**
- **Dónde:** Ventana B como `ope.med` → **Movimientos → Nuevo movimiento**, y **Ventas → Nueva venta**.
- **Qué hacer:**
  1. **Ingreso ordinario:** MAC-10 unidad `100`, costo unitario `900`, motivo `Inventario inicial`.
  2. **Ingreso ordinario:** MAC-20 unidad `60`, costo unitario `1.800`, motivo `Inventario inicial`.
  3. **Nueva venta**, lista `DETAL`: MAC-10 unidad `20`, descuento `0`; MAC-20 unidad `10`, descuento `10`. Confirma.
  4. **Devolución de cliente**, venta de referencia `VT-000005`, línea de MAC-10, cantidad `2`, motivo `Cliente devuelve 2 macetas`.
- **Resultado esperado:** VT-000005 suma bruto 75.000,00, descuento 3.500,00 y neto `71.500,00`. Las alertas de MAC-10 y MAC-20 se resuelven con el ingreso.
- **Estado de los datos:** MAC-10 `82`, MAC-20 `50`. **Cerrar sesión**.

**Paso 12 — El Dashboard se actualiza sin alterar las reglas**
- **Dónde:** Ventana A, sucursal `MED-CEN`, pestañas **Ventas y rotación** y **Demanda**. Pulsa **Actualizar ventas** si hace falta.
- **Resultado esperado:**
  - Octubre: «4 ventas», `764.775,00 COP`, «3 devoluciones». MAC-10 muestra `20,000` y «Devuelto: 2,000»; MAC-20 `10,000`.
  - El importe sube exactamente 71.500: la devolución no lo reduce.
  - **Rotación** con `28/10/2026` a hoy: MAC-10 y MAC-20 «No calculable», con historia desde hoy.
  - **Demanda**, ranking en unidad, «3 productos con ventas»: **Alta demanda** MAC-14 `30,000` y MAC-10 `20,000`; **Baja demanda** MAC-20 `10,000`. La devolución no rebaja MAC-10, y los rankings en bulto y kg siguen separados. Es el caso RF-0046-CA-02: 30, 20 y 10.
- **Estado de los datos:** sin cambios.

**Paso 13 — Un mínimo nuevo cambia el indicador (RF-0048-CA-02)**
- **Dónde:** Ventana A en **Catálogo → Editar** `SUST-001` → BOG-NOR → **Cambiar mínimo**, y **Dashboard**, sucursal `BOG-NOR`, pestaña **Próximos a agotarse**.
- **Qué hacer:** pon el mínimo en `24`, guarda y abre el Dashboard. Revisa también **Alertas** de BOG-NOR.
- **Resultado esperado:** SUST-001 aparece con `24,000 bulto`, mínimo `24,000` y «En su mínimo». **Alertas** muestra el mismo producto activo, con mínimo `24,000`: no existe un segundo umbral.
- **Estado de los datos:** mínimo temporal 24.

**Paso 14 — Por debajo del mínimo y vuelta al valor original**
- **Dónde:** Ventana B como `ope.bog` → **Ventas → Nueva venta**. Después Ventana A.
- **Qué hacer:**
  1. `ope.bog`: lista `DETAL`, SUST-001 bulto `1`, descuento `0`. Confirma. Abre su **Dashboard**, pestaña **Próximos a agotarse**, y **Cerrar sesión**.
  2. Ventana A: pulsa **Actualizar productos**. Después devuelve el mínimo de SUST-001 en BOG-NOR a `8` y actualiza de nuevo.
- **Resultado esperado:**
  - VT-000006 de `32.000,00`. `ope.bog` y el administrador ven `23,000 bulto`, mínimo `24,000`, «Por debajo de su mínimo».
  - Con mínimo 8: «Ningún producto de esta sucursal está en su mínimo o por debajo.». La alerta queda resuelta.
- **Estado de los datos:** BOG-NOR SUST 23, mínimo 8.

---

## Fase 6 — Transferencias activas e impacto (Ventanas A y B)

**Paso 15 — Crear cuatro transferencias en estados distintos**
- **Dónde:** Ventana B, cambiando de usuario. Las cuatro van de `MED-CEN` a BOG-NOR.
- **Qué hacer:**
  1. `ope.bog` solicita, una por una: TR-000007 SUST-001 bulto `5`; TR-000008 FERT-NPK saco `1`; TR-000009 MAC-14 unidad `10`; TR-000010 SUST-001 bulto `4`.
  2. `ope.med`: **Preparar envío** en TR-000008, TR-000009 y TR-000010, sin cambiar cantidades.
  3. `ger.med`: **Aprobar transferencia** en las tres.
  4. `ope.med`: **Registrar despacho** de TR-000009 y TR-000010, transportista `Transportes La Montaña`, llegada estimada 2 horas después de la salida.
- **Resultado esperado:** TR-000007 «Solicitada», TR-000008 «Aprobada», TR-000009 y TR-000010 «En tránsito». El catálogo de MED-CEN queda en SUST `73` y MAC-14 `103`; BOG-NOR no cambia.
- **Estado de los datos:** dos transferencias en tránsito, dentro de plazo. **Cerrar sesión**.

**Paso 16 — Impacto visto desde los dos lados (RF-0047)**
- **Dónde:** Ventana A → **Dashboard**, pestaña **Transferencias**, con `MED-CEN` y luego `BOG-NOR`.
- **Resultado esperado** (mismas cantidades en ambas sucursales, en unidad base):

  | Transferencia | Producto | Previsión | En tránsito | Recibido | Faltante pendiente | Pérdida tratada |
  |---|---|---|---|---|---|---|
  | TR-000007 Solicitada | SUST-001 | 5,000 bulto | No aplica | No aplica | No aplica | No aplica |
  | TR-000008 Aprobada | FERT-NPK | 50,000 kg | No aplica | No aplica | No aplica | No aplica |
  | TR-000009 En tránsito | MAC-14 | No aplica | 10,000 unidad | No aplica | No aplica | No aplica |
  | TR-000010 En tránsito | SUST-001 | No aplica | 4,000 bulto | No aplica | No aplica | No aplica |

  - MED-CEN lee «Sale hacia Sede Bogotá Norte (BOG-NOR)» y BOG-NOR «Llega desde Sede Medellín Centro (MED-CEN)».
  - Previsión: «Previsión: aún no reserva ni mueve existencias»; el saco se muestra como 50 kg.
  - En tránsito: «Ya salió del físico de esta sucursal» en origen, «Aún no entra al físico de esta sucursal» en destino, y **Llegada estimada**.
  - «No aplica» no equivale a cero.
- **Estado de los datos:** sin cambios.

**Paso 17 — Recepción con faltante**
- **Dónde:** Ventana B como `ope.bog` → detalle de TR-000010 → **Confirmar recepción**. Después Ventana A, pestaña **Transferencias**, `BOG-NOR`, **Actualizar transferencias**.
- **Qué hacer:** SUST-001 `2`. Confirma y acepta el diálogo.
- **Resultado esperado:** TR-000010 «Recibida con discrepancia». En el Dashboard, su línea muestra **Recibido** `2,000 bulto` y **Faltante pendiente** `2,000 bulto`. La nota dice «Lo recibido ya está en el físico de esta sucursal. Alerta de faltante abierta» (en MED-CEN, «…en el físico de destino…»).
- **Estado de los datos:** BOG-NOR SUST `25`, MAC-14 `32`; 2 SUST-001 en tránsito. **Cerrar sesión**.

**Paso 18 — Pérdida tratada y alerta resuelta**
- **Dónde:** Ventana B como `ger.med` → detalle de TR-000010. Después `ger.bog` → **Alertas**, sección «Faltantes de transferencias». Revisa el Dashboard de la Ventana A tras cada acción.
- **Qué hacer:**
  1. `ger.med`: **Registrar pérdida en tránsito**, motivo `Dos bultos rotos en el trayecto`, evidencia `Acta de entrega firmada`. Confirma.
  2. `ger.bog`: **Resolver** TR-000010 con tratamiento «Ajuste».
- **Resultado esperado:**
  - Tras la pérdida: SUST-001 pasa a **Faltante pendiente** `0,000 bulto` y **Pérdida tratada** `2,000 bulto`. La transferencia sigue listada porque la alerta continúa abierta.
  - Tras resolver: TR-000010 desaparece de las activas, sin cambiar existencias.
- **Estado de los datos:** tránsito de TR-000010 en 0. **Cerrar sesión**.

**Paso 19 — Salida de las transferencias activas**
- **Dónde:** Ventana B como `ope.bog`.
- **Qué hacer:** cancela TR-000007 sin motivo. Recibe TR-000009 completa con `10`, antes de su llegada estimada. Abre **Dashboard → Transferencias** con el mismo usuario.
- **Resultado esperado:** solo queda TR-000008 «Aprobada», con «Llega desde Sede Medellín Centro (MED-CEN)». Cancelada y recibida completa ya no son activas.
- **Estado de los datos:** BOG-NOR MAC-14 `42`. TR-000008 sigue aprobada. **Cerrar sesión**.

**Paso 20 — Comparativa final**
- **Dónde:** Ventana A → **Dashboard → Comparativa**, octubre. Pulsa **Comparar sucursales**.
- **Resultado esperado:**

  | Sucursal | Ventas | Importe neto | Como origen | Como destino |
  |---|---|---|---|---|
  | BOG-NOR | 2 ventas | 128.000,00 COP | 0,00 % (0 de 1) | 50,00 % (2 de 4) |
  | MED-CEN | 4 ventas | 764.775,00 COP | 50,00 % (2 de 4) | 0,00 % (0 de 1) |

  Las demás filas no cambian. TR-000009 suma a tiempo y TR-000010 no. Si recibiste TR-000009 después de la llegada estimada, verás 1 de 4 (25,00 %).
- **Estado de los datos:** sin cambios.

---

## Fase 7 — Reportes en Excel (Ventanas B y A)

Si ejecutas la guía el 31 de octubre, **Hasta** ya propone esa fecha; usa `30/10/2026` o `31/10/2026`, el resultado es el mismo mientras todo se haya hecho en octubre.

**Paso 21 — Vista del gerente y ventas (RF-0050, RF-0051, RF-0053)**
- **Dónde:** Ventana B como `ger.med` → **Reportes**.
- **Qué hacer:** comprueba las tres categorías. Elige **Ventas**, período del 1 de octubre a hoy, y pulsa **Descargar Excel**. Abre el archivo.
- **Resultado esperado:**
  - No hay selector de sucursal; la nota termina en «El reporte incluye solo su sucursal.».
  - Nota de fechas: «Las fechas se aplican a la fecha de confirmación de cada venta. Ambas fechas se incluyen completas.».
  - «Se descargó ventas_2026-10-01_2026-10-31.xlsx con 7 filas.» (el nombre lleva tus fechas).
  - El libro abre en Excel, con una hoja, metadatos (sucursal, período, fecha del filtro, zona horaria, generación y filas), encabezado inmovilizado y autofiltro.
  - Filas: VT-000005 ×2, VT-000003, VT-000002 y VT-000001 ×3. No hay fila de total. VT-000001 muestra MAC-14 a 2.600 con 5 % y neto 49.400.
  - Suma **Importe neto de línea (COP)**: `764.775`, igual al Dashboard.
- **Estado de los datos:** sin cambios: exportar no modifica nada.

**Paso 22 — Movimientos (RF-0052)**
- **Dónde:** el mismo usuario, categoría **Movimientos de inventario**, mismo período.
- **Resultado esperado:**
  - 30 filas de MED-CEN. Columnas: **ID público del movimiento**, **Fecha de registro**, **Sucursal**, **SKU**, **Producto**, **Clase**, **Motivo**, **Cantidad**, **Unidad**, **Usuario responsable**, **Documento origen**.
  - Sin costos: el `900` y el `1.800` del paso 11 no aparecen.
  - Las salidas son negativas: la venta de MAC-10 es `-20` con documento «Venta VT-000005»; la devolución es `2`, clase «Devolución de cliente». Los despachos llevan motivo «Despacho de la transferencia TR-000010…».
  - La pérdida del paso 18 no figura, porque no toca el físico.
- **Estado de los datos:** sin cambios.

**Paso 23 — Transferencias y filtro por estado (RF-0054)**
- **Dónde:** el mismo usuario, categoría **Transferencias**, mismo período.
- **Qué hacer:** descarga con **Estado** «Todos». Después elige «Recibidas con discrepancia» y descarga de nuevo.
- **Resultado esperado:**
  - Todos: 13 filas (una por línea), de TR-000001 a TR-000010. TR-000007, TR-000003 y las demás sin despacho dejan vacías esas fechas y cantidades. El metadato **Fecha del filtro** es la de solicitud.
  - Filtrado: 3 filas y metadato **Estado** «Recibida con discrepancia»:

    | Transferencia | SKU | Solicitada | Despachada | Recibida | Diferencia pendiente | Tratamiento |
    |---|---|---|---|---|---|---|
    | TR-000010 | SUST-001 | 4 | 4 | 2 | 0 | Ajuste; pérdida en tránsito registrada |
    | TR-000002 | MAC-14 | 10 | 10 | 7 | 0 | Reclamación; pérdida en tránsito registrada |
    | TR-000002 | FERT-NPK | 5 | 5 | 5 | 0 | |

  - La diferencia es 0 porque el tránsito ya se dio de baja. El estado sigue siendo discrepancia: nunca aparece «Resuelto».
- **Estado de los datos:** sin cambios.

**Paso 24 — Límites de fechas y reporte vacío (RF-0050-CA-03, RF-0055)**
- **Dónde:** el mismo usuario, categoría **Ventas**.
- **Qué hacer:** prueba, uno a la vez:
  1. **Desde** `30/10/2026`, **Hasta** `01/10/2026`.
  2. **Desde** `01/01/2026`, **Hasta** `01/01/2027`.
  3. **Desde** `01/01/2026`, **Hasta** `31/12/2026`.
  4. **Desde** y **Hasta** `28/10/2026`.
  5. **Desde** y **Hasta** `29/10/2026`.
  6. **Desde** `01/01/2020`, **Hasta** `31/01/2020`.
- **Resultado esperado:**
  1. «La fecha final del período no puede ser anterior a la inicial.». No se descarga nada.
  2. «El período de un reporte no puede superar 12 meses. Reduzca el rango de fechas.». No se descarga nada.
  3. Se admite: 7 filas.
  4. 5 filas: ambas fechas del día cuentan completas. VT-000005 no aparece.
  5. Sin filas: MED-CEN no vendió ese día, y el 29 no hereda las ventas del 28.
  6. «Se descargó ventas_2020-01-01_2020-01-31.xlsx sin filas: no hay registros para esos filtros.», en aviso destacado. El libro abre con encabezados, `Filas` 0 y la nota «Sin resultados: no hay registros para los filtros indicados.».
- **Estado de los datos:** sin cambios. **Cerrar sesión**.

**Paso 25 — Otro gerente y operador (RF-0050-CA-04)**
- **Dónde:** Ventana B como `ger.bog` → **Reportes**. Después `ope.bog`.
- **Qué hacer:** `ger.bog` descarga **Ventas** y **Movimientos de inventario** de octubre. Después entra `ope.bog`.
- **Resultado esperado:**
  - Ventas: 2 filas, VT-000006 y VT-000004, ambas de BOG-NOR.
  - Movimientos: 13 filas de BOG-NOR.
  - `ope.bog` no tiene **Reportes** en el menú.
- **Estado de los datos:** sin cambios. **Cerrar sesión**.

**Paso 26 — Administrador: red y sucursal**
- **Dónde:** Ventana A → **Reportes**.
- **Qué hacer:** con **Toda la red** y octubre, descarga las tres categorías. Luego elige `BOG-NOR`, **Ventas**, y descarga.
- **Resultado esperado:**
  - **Sucursal** ofrece «Toda la red» y cada sucursal.
  - Toda la red: Movimientos 45 filas, Ventas 10 filas, Transferencias 13 filas. El metadato de sucursal lo indica.
  - Movimientos y Ventas incluyen las filas de `DEMO-BOG`. Es correcto: están en la base.
  - `BOG-NOR`: las mismas 2 filas del paso 25.
- **Estado de los datos:** sin cambios.

---

## Resumen de cobertura

| Funcionalidad | Pasos |
|---|---|
| Ventas mensuales, períodos, sin datos frente a cero, devoluciones aparte (RF-0044) | 3, 4, 12 |
| Rotación calculada, cero real y no calculable (RF-0045) | 5, 6, 12 |
| Alta y baja demanda, unidades separadas y sin ventas (RF-0046) | 7, 12 |
| Transferencias activas e impacto (RF-0047) | 8, 16–19 |
| Próximos a agotarse con el mínimo vigente (RF-0048) | 8, 10, 13, 14 |
| Comparativa exclusiva del administrador (RF-0049) | 1, 9, 20 |
| Exportación, vacíos y permisos (RF-0050) | 21, 24–26 |
| Libro `.xlsx` real (RF-0051) | 21–23 |
| Movimientos (RF-0052) | 22 |
| Ventas por línea (RF-0053) | 21 |
| Transferencias, estado, diferencia y tratamiento (RF-0054) | 23 |
| Filtro por fechas inclusivas y 12 meses (RF-0055) | 24 |
| Importes ocultos al operador (RF-0007, corrección H1) | 4 |

## Datos que deberían quedar en PostgreSQL

- **Productos nuevos:** `MAC-10` y `MAC-20`, solo en MED-CEN, mínimo 5, con precio en `DETAL`.
- **Ventas nuevas:** VT-000005 (MED-CEN, 71.500,00) y VT-000006 (BOG-NOR, 32.000,00). Octubre: MED-CEN 764.775,00 y BOG-NOR 128.000,00.
- **Devoluciones:** 3 en MED-CEN, una nueva sobre VT-000005.
- **Transferencias nuevas:**

  | Transferencia | Origen → destino | Estado | Notas |
  |---|---|---|---|
  | TR-000007 | MED-CEN → BOG-NOR | Cancelada, sin motivo | 5 bulto |
  | TR-000008 | MED-CEN → BOG-NOR | Aprobada | 1 saco; sigue activa |
  | TR-000009 | MED-CEN → BOG-NOR | Recibida completa, a tiempo | 10 unidad |
  | TR-000010 | MED-CEN → BOG-NOR | Recibida con discrepancia | Faltan 2 SUST-001; pérdida registrada y alerta resuelta con ajuste |

- **Existencias:**

  | Sucursal | SUST-001 | FERT-NPK | MAC-14 | MAC-10 | MAC-20 |
  |---|---|---|---|---|---|
  | MED-CEN | 73 | 164,5 | 103 | 82 | 50 |
  | BOG-NOR | 25 | 95 | 42 | — | — |

- **Mínimos:** SUST-001 en BOG-NOR vuelve a 8.
- **Movimientos:** 45 en total, 10 nuevos: 2 ingresos, 3 de venta, 1 devolución, 2 despachos y 2 recepciones.
- **Tránsito:** nada pendiente.
- **Alertas de stock activas:** las de CAL-SUR y DEMO-MED. Las de MAC-10, MAC-20 y SUST-001 en BOG-NOR quedan resueltas.

## No incluidas en el recorrido y cómo verificarlas

- **Cambio real de mes:** no se probó con el reloj del servidor. Desde el 1 de noviembre, octubre debe aparecer como mes cerrado e incompleto («Mes incompleto: desde el registro de la sucursal»), y noviembre como mes en curso. Revísalo ese día en **Ventas y rotación** y **Comparativa**.
- **RF-0045-CA-02 con 20 vendidas y promedio 10:** tus datos no dan ese caso exacto; lo cubre la exploración E2E del 2026-10-30, escenario 6. El paso 5 aplica la misma fórmula.
- **Rotación con promedio cero:** requiere un período con saldo cero y con historia; la nota sería «Sin existencias físicas durante el período». Se probó en la exploración E2E, escenario 7.
- **Más de 5 productos por extremo y empates por SKU:** necesitan 10 o más productos con la misma unidad; ver escenario 9.
- **Alerta resuelta con faltante aún pendiente:** el paso 18 registra primero la pérdida para mostrar la columna **Pérdida tratada**. En el orden inverso, la nota diría «Alerta resuelta con ajuste; no cambia las cantidades» y el faltante seguiría listado.
- **10.000 filas:** tus datos no lo alcanzan. Más filas responden `422 REPORT_TOO_LARGE`; ver escenario 34.
- **Texto que empieza por `=`:** queda como texto, no como fórmula; ver escenario 26.
- **Permisos por API:** un operador en `/api/v1/reports/sales` y en `/api/v1/dashboard/branch-comparison` recibe `403 ROLE_FORBIDDEN`; `ger.med` con `branch` de BOG-NOR recibe `403 BRANCH_FORBIDDEN`. Las tres respuestas se confirmaron en la copia; un gerente en la comparativa también recibe `403`.
- **Redacción observada:** la comparativa muestra «0 de 1 llegada completas a tiempo». Es un defecto menor de concordancia, sin efecto en las cifras.
