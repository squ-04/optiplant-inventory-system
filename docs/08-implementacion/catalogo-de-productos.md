# Probar el catálogo de productos

Implementación de RF-0008–RF-0011 sobre las tablas `catalog_product`, `catalog_product_unit` y `catalog_branch_product` ya creadas por V2; no requiere migraciones ni dependencias backend nuevas. Este documento describe la entrega; no modifica el catálogo funcional aprobado.

## Decisión verificada: solo baja lógica

RF-0011, INC-006/D01–D03 (H v1.3/01) y el modelo de datos §6.2/§7.4 establecen que **todos los productos se conservan**: la eliminación es exclusivamente `active=FALSE`, con stock cero en todas las sucursales y sin operaciones pendientes, nunca borrado físico ni en cascada. La entrega lo aplica en tres capas:

- La API no expone ninguna ruta `DELETE`; un método no admitido responde `405` con ProblemDetail `METHOD_NOT_ALLOWED`.
- El servicio solo cambia `active` y, en la misma transacción, deshabilita las filas locales, anula sus mínimos y cierra los episodios de reabastecimiento activos, conservando las filas.
- El rol operativo de PostgreSQL carece de `DELETE` sobre `catalog_product` (V7); una prueba lo comprueba.

## Recorrido manual

1. Levantar el entorno según [acceso y administración](acceso-y-administracion.md) e iniciar sesión como administrador. Abrir **Catálogo**.
2. Pulsar **Nuevo producto** (el formulario aparece y recibe el foco) y crear un producto con SKU, nombre, descripción opcional y unidad base, indicando si admite fracciones. Usar **Elegir todas las sucursales activas** o marcar algunas e indicar un stock mínimo explícito para cada una (se acepta cero). El mensaje confirma que no se registraron existencias; en **Consultas** el producto no aparece con saldo.
3. Repetir el mismo SKU: se rechaza con «El SKU ya está registrado en la red.» y el borrador se conserva. Un mínimo fraccionario en una unidad entera, más de tres decimales o un valor negativo también se rechazan.
4. Editar el producto: nombre y descripción cambian; el identificador público no. Mientras el producto no tenga historial (saldos, movimientos, líneas de compra, venta o transferencia, o precios), SKU y unidad base son editables; después quedan protegidos y el servidor rechaza su cambio con `PRODUCT_IDENTITY_PROTECTED`.
5. Crear una sucursal nueva en **Administración**, volver a **Catálogo**, editar el producto y usar **Habilitar en otra sucursal** con su mínimo. Se conserva la identidad del producto y no se crean existencias.
6. Con datos de demostración, intentar **Desactivar producto** sobre `DEMO-PLANT`: se rechaza porque tiene existencias. Compras en borrador o pendientes, transferencias abiertas, tránsito pendiente y faltantes abiertos también impiden la baja, indicando el motivo.
7. Desactivar el producto creado: queda **Inactivo**, sigue listado y todas sus sucursales pasan a «No habilitado / Sin mínimo». No admite nuevas habilitaciones. Editar sus datos no lo reactiva.
8. **Reactivar producto**: vuelve al catálogo general sin habilitarse en ninguna sucursal; cada sucursal debe habilitarlo de nuevo con un mínimo nuevo.
9. Iniciar sesión como gerente u operador: **Catálogo** muestra la consulta sin botones de mantenimiento ni configuración local; la API responde `403 ROLE_FORBIDDEN` a cualquier escritura.

## Contrato REST

Sesión por cookie y CSRF como en el resto de la API. Las cantidades mínimas son cadenas decimales exactas en unidad base (hasta tres decimales; enteras si la unidad base no es divisible).

| Ruta `/api/v1` | Método y finalidad |
|---|---|
| `/products` | GET: listado del catálogo común para administrador, gerente y operador. POST (administrador): `sku`, `name`, `description` (texto o `null`), `baseUnitName`, `baseDivisible`, `branches: [{branchExternalId, minimumQuantity}]` |
| `/products/{uuid}` | GET: detalle; `branches` incluye la habilitación local solo para el administrador y es `null` para perfiles locales |
| `/products/{uuid}/update` | POST (administrador): los cinco atributos editables; nunca identidad, estado ni habilitaciones |
| `/products/{uuid}/branches` | POST (administrador): `branchExternalId`, `minimumQuantity`; habilita un producto activo en una sucursal activa |
| `/products/local` | GET (gerente u operador): catálogo local de la sucursal de la sesión (RF-0012). La sucursal nunca se recibe en la solicitud; el administrador recibe `403 ROLE_FORBIDDEN` |
| `/products/{uuid}/branches/{branchUuid}/minimum` | POST (administrador): `minimumQuantity`; cambia solo el mínimo de una combinación habilitada (RF-0001) |
| `/products/{uuid}/units` | POST (administrador): `name`, `factor`, `divisible`; agrega una unidad comercial (RF-0013) |
| `/products/{uuid}/units/update` | POST (administrador): `name`, `factor`, `active`; cambia el factor vigente o la disponibilidad de una unidad comercial |
| `/products/{uuid}/units/convert?unit=&quantity=` | GET (todos los perfiles): equivalente exacto en unidad base con el factor vigente |
| `/products/{uuid}/deactivate` | POST (administrador), cuerpo vacío: baja lógica condicionada |
| `/products/{uuid}/reactivate` | POST (administrador), cuerpo vacío: reactivación sin restaurar habilitaciones ni mínimos |

Respuesta: `externalId`, `sku`, `name`, `description`, `baseUnitName`, `baseDivisible`, `active`, `inUse`, `branches` y `units` (`name`, `factor`, `divisible`, `base`, `active`; `null` en el listado). No incluye existencias, costos ni valoración. Errores: `400 VALIDATION_FAILED`; `403 ROLE_FORBIDDEN`; `404 PRODUCT_NOT_FOUND` o `BRANCH_NOT_FOUND`; `405 METHOD_NOT_ALLOWED`; `409 PRODUCT_SKU_EXISTS`, `PRODUCT_IDENTITY_PROTECTED`, `PRODUCT_ALREADY_ENABLED`, `PRODUCT_INACTIVE`, `BRANCH_INACTIVE`, `PRODUCT_DEACTIVATION_BLOCKED`, `PRODUCT_MINIMUM_FRACTIONAL`, `PRODUCT_UNIT_EXISTS`, `PRODUCT_NOT_ENABLED`, `PRODUCT_BASE_UNIT_FIXED` o `PRODUCT_UNITS_INCOMPATIBLE`; `404 PRODUCT_UNIT_NOT_FOUND`.

El catálogo local responde `branchExternalId`, `branchCode`, `branchName`, `branchActive` e `items` con `productExternalId`, `sku`, `name`, `description`, `baseUnitName`, `baseDivisible`, `minimumQuantity`, `physicalQuantity`, `availableQuantity`, `inTransitQuantity` y `units` activas. Disponible equivale al físico porque no existen reservas (§5.3); el tránsito es lo despachado hacia la sucursal pendiente de recepción y no se suma al físico ni al disponible. No incluye valoración ni costos.

## Alcance y límites

- Precisiones derivadas, no requisitos nuevos: el mínimo de una unidad base indivisible debe ser entero (RF-0013, sin redondeo silencioso) y «uso» que protege SKU y unidad base comprende saldos, movimientos, líneas de documentos y precios.
- RF-0001, RF-0012 y RF-0013 se incorporaron después sobre las mismas tablas, sin migraciones; ver la sección siguiente. La deshabilitación local (RF-0019) no forma parte de esta entrega.
- RF-0011-CA-03 exige impedir nuevas operaciones de inventario, compra, venta o transferencia sobre productos inactivos. Esos comandos aún no existen; la entrega impide nuevas habilitaciones y deja cada producto inactivo sin sucursales habilitadas, pero la validación en cada comando se aplicará y probará con sus módulos.
- El listado no tiene filtros ni paginación, porque ningún RF los prescribe.

## Mínimo, catálogo local y unidades (RF-0001, RF-0012, RF-0013)

- **Mínimo (RF-0001):** el administrador lo cambia desde **Editar → Habilitación por sucursal → Cambiar mínimo**. Solo actualiza `catalog_branch_product.minimum_qty` de una combinación habilitada; existencias, costos, documentos y alertas no cambian. Para una sucursal no habilitada responde `PRODUCT_NOT_ENABLED`: primero se habilita con su mínimo. La reevaluación de alertas con el nuevo valor corresponde a RF-0019/RF-0020, aún no implementados.
- **Catálogo local (RF-0012):** gerente y operador ven la pestaña **Mi sucursal** con los productos habilitados, incluso sin stock, con físico, disponible, tránsito y mínimo; la pestaña **Catálogo general** marca «Habilitado / No habilitado» en su sucursal, sin presentar como operable un producto no habilitado.
- **Unidades (RF-0013):** el administrador agrega unidades con su factor en unidad base y edita factor o disponibilidad. Se rechazan factores con más de tres decimales, fracciones o factores decimales sobre una base indivisible y nombres repetidos sin distinguir mayúsculas. La unidad base no se gestiona aquí (factor 1). Un factor nuevo rige desde ese momento: los documentos conservan `unit_name_snapshot`, `factor_snapshot` y cantidades, y los saldos están en unidad base. La conversión rechaza fracciones de unidades indivisibles y equivalentes con más de tres decimales, sin redondear; `UnitConversion` es la regla que reutilizarán compras, ventas, movimientos y transferencias.
- **Precisiones derivadas:** las unidades se identifican por nombre dentro del producto (no se renombran ni cambian su divisibilidad) para no requerir una migración con identificador público; una base que pasa a indivisible exige que sus unidades comerciales sean enteras (`PRODUCT_UNITS_INCOMPATIBLE`).

## Verificaciones

Backend: `ProductHttpTest` (8 pruebas HTTP con PostgreSQL efímero), `ProductConfigurationHttpTest` (RF-0001, RF-0012 y RF-0013) y `UnitConversionTest` dentro del runner Maven Docker de `backend/README.md`. Frontend: `frontend/test/catalog.test.ts` con `pnpm test` y `pnpm build`. Los resultados observados se registran en `odd/tasks/rf-0008-0011.md`.
