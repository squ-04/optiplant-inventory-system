# Probar el catálogo de productos

Implementación de RF-0008–RF-0011 sobre las tablas `catalog_product`, `catalog_product_unit` y `catalog_branch_product` ya creadas por V2; no requiere migraciones ni dependencias backend nuevas. Este documento describe la entrega; no modifica el catálogo funcional aprobado.

## Decisión verificada: solo baja lógica

RF-0011, INC-006/D01–D03 (H v1.3/01) y el modelo de datos §6.2/§7.4 establecen que **todos los productos se conservan**: la eliminación es exclusivamente `active=FALSE`, con stock cero en todas las sucursales y sin operaciones pendientes, nunca borrado físico ni en cascada. La entrega lo aplica en tres capas:

- La API no expone ninguna ruta `DELETE`; un método no admitido responde `405` con ProblemDetail `METHOD_NOT_ALLOWED`.
- El servicio solo cambia `active` y, en la misma transacción, deshabilita las filas locales, anula sus mínimos y cierra los episodios de reabastecimiento activos, conservando las filas.
- El rol operativo de PostgreSQL carece de `DELETE` sobre `catalog_product` (V7); una prueba lo comprueba.

## Recorrido manual

1. Levantar el entorno según [acceso y administración](acceso-y-administracion.md) e iniciar sesión como administrador. Abrir **Catálogo**.
2. Crear un producto con SKU, nombre, descripción opcional y unidad base, indicando si admite fracciones. Usar **Elegir todas las sucursales activas** o marcar algunas e indicar un stock mínimo explícito para cada una (se acepta cero). El mensaje confirma que no se registraron existencias; en **Consultas** el producto no aparece con saldo.
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
| `/products/{uuid}/deactivate` | POST (administrador), cuerpo vacío: baja lógica condicionada |
| `/products/{uuid}/reactivate` | POST (administrador), cuerpo vacío: reactivación sin restaurar habilitaciones ni mínimos |

Respuesta: `externalId`, `sku`, `name`, `description`, `baseUnitName`, `baseDivisible`, `active`, `inUse` y `branches`. No incluye existencias, costos ni valoración. Errores: `400 VALIDATION_FAILED`; `403 ROLE_FORBIDDEN`; `404 PRODUCT_NOT_FOUND` o `BRANCH_NOT_FOUND`; `405 METHOD_NOT_ALLOWED`; `409 PRODUCT_SKU_EXISTS`, `PRODUCT_IDENTITY_PROTECTED`, `PRODUCT_ALREADY_ENABLED`, `PRODUCT_INACTIVE`, `BRANCH_INACTIVE`, `PRODUCT_DEACTIVATION_BLOCKED`, `PRODUCT_MINIMUM_FRACTIONAL` o `PRODUCT_UNIT_EXISTS`.

## Alcance y límites

- Precisiones derivadas, no requisitos nuevos: el mínimo de una unidad base indivisible debe ser entero (RF-0013, sin redondeo silencioso) y «uso» que protege SKU y unidad base comprende saldos, movimientos, líneas de documentos y precios.
- Unidades comerciales adicionales y conversiones (RF-0013), catálogo local por sucursal (RF-0012), modificación posterior del mínimo y deshabilitación local (RF-0019) no forman parte de esta entrega.
- RF-0011-CA-03 exige impedir nuevas operaciones de inventario, compra, venta o transferencia sobre productos inactivos. Esos comandos aún no existen; la entrega impide nuevas habilitaciones y deja cada producto inactivo sin sucursales habilitadas, pero la validación en cada comando se aplicará y probará con sus módulos.
- El listado no tiene filtros ni paginación, porque ningún RF los prescribe.

## Verificaciones

Backend: `ProductHttpTest` (8 pruebas HTTP con PostgreSQL efímero) dentro del runner Maven Docker de `backend/README.md`. Frontend: `frontend/test/catalog.test.ts` con `pnpm test` y `pnpm build`. Los resultados observados se registran en `odd/tasks/rf-0008-0011.md`.
