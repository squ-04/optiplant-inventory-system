import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiClient, ApiError } from '../lib/api/client.ts';
import type { Branch, Product, ProductAttributes, ProductEnablement, User } from '../lib/api/client.ts';
import { StatusBadge } from '../components/shared/StatusBadge';
import { revealEditor } from '../components/shared/revealEditor.ts';
import { Ban, Pencil, Plus, RefreshCw, RotateCcw, Save, Store, X } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Textarea } from '../components/ui/textarea';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';

// Base-unit quantities keep at most three decimals; indivisible units admit whole numbers only.
const quantityPattern = (divisible: boolean) => divisible ? '\\d{1,15}(\\.\\d{1,3})?' : '\\d{1,15}';
const quantityHint = (divisible: boolean) => divisible ? 'Hasta 3 decimales, por ejemplo 2.5.' : 'Número entero, por ejemplo 4.';

interface Props { client: ApiClient; user: User; onError: (reason: unknown) => void; revision: number }

export function Catalog({ client, user, onError, revision }: Props) {
  const admin = user.role === 'ADMIN';
  const [products, setProducts] = useState<Product[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [selected, setSelected] = useState<Product | null>(null);
  // The editor exists only after an explicit create or edit action.
  const [editorOpen, setEditorOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function load() {
    const [nextProducts, nextBranches] = await Promise.all([client.products(), admin ? client.branches() : Promise.resolve([])]);
    setProducts(nextProducts); setBranches(nextBranches); setLoaded(true);
  }
  useEffect(() => {
    let active = true;
    setBusy(true);
    Promise.all([client.products(), admin ? client.branches() : Promise.resolve([])]).then(([p, b]) => {
      if (active) { setProducts(p); setBranches(b); setLoaded(true); }
    }).catch(reason => { if (active) onError(reason); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [client, admin, revision]); // Administration changes (new or inactive branches) refresh the choices.

  async function run(action: () => Promise<void>) {
    setBusy(true); setMessage(''); setError('');
    try { await action(); }
    catch (reason) {
      setError(reason instanceof TypeError
        ? 'Sin comunicación con el servidor. No se confirmó la operación; actualice el catálogo antes de reintentar.'
        : reason instanceof Error ? reason.message : 'No se pudo completar la solicitud.');
      // Business rejections stay in this view; only session problems are escalated to the shell.
      if (reason instanceof ApiError && (reason.status === 401 || reason.problem.code === 'PASSWORD_CHANGE_REQUIRED')) onError(reason);
    } finally { setBusy(false); }
  }

  return <section aria-labelledby="catalog-title" aria-busy={busy}>
    <h2 id="catalog-title" tabIndex={-1}>Catálogo de productos</h2>
    <p>{admin
      ? 'Catálogo común de la red. Crear o habilitar un producto no agrega existencias. Los productos no se eliminan: se desactivan y conservan su historial.'
      : 'Catálogo común de la red, solo para consulta. Las existencias de cada sucursal se consultan en Consultas.'}</p>
    <p role="status">{busy ? 'Consultando servidor…' : message}</p>
    {error && <p role="alert">{error}</p>}
    <div className="list-heading">
      <div><h3>Productos registrados</h3><p>Identificación, unidad base y disponibilidad en el catálogo general.</p></div>
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" disabled={busy} onClick={() => void run(load)}><RefreshCw aria-hidden="true" />Actualizar catálogo</Button>
        {admin && <Button disabled={busy} onClick={() => {
          if (selected) setSelected(null);
          setMessage(''); setEditorOpen(true); revealEditor('product-editor');
        }}><Plus aria-hidden="true" />Nuevo producto</Button>}
      </div>
    </div>
    {!loaded && !busy && <p>El catálogo todavía no está disponible.</p>}
    {loaded && products.length === 0 && <p>{admin ? 'No hay productos registrados. Use «Nuevo producto» para crear el primero.' : 'No hay productos registrados.'}</p>}
    {products.length > 0 && <div className="table-scroll" role="region" aria-label="Productos registrados" tabIndex={0}>
      <Table>
        <TableCaption>Productos registrados, ordenados por SKU</TableCaption>
        <TableHeader><TableRow>
          <TableHead scope="col">SKU</TableHead><TableHead scope="col">Producto</TableHead>
          <TableHead scope="col">Unidad base</TableHead><TableHead scope="col">Estado</TableHead>
          {admin && <TableHead scope="col" className="text-right">Acción</TableHead>}
        </TableRow></TableHeader>
        <TableBody>{products.map(item => <TableRow key={item.externalId}>
          <TableCell><span className="record-code whitespace-nowrap">{item.sku}</span></TableCell>
          <TableCell><strong className="block font-medium">{item.name}</strong>
            {item.description && <span className="text-muted-foreground">{item.description}</span>}</TableCell>
          <TableCell>{item.baseUnitName}<span className="block text-muted-foreground">
            {item.baseDivisible ? 'Admite fracciones' : 'Solo cantidades enteras'}</span></TableCell>
          <TableCell><StatusBadge active={item.active} /></TableCell>
          {admin && <TableCell className="text-right"><Button variant="outline" disabled={busy}
            onClick={() => void run(async () => {
              setSelected(await client.product(item.externalId)); setEditorOpen(true); revealEditor('product-editor');
            })}>
            <Pencil aria-hidden="true" />Editar <span className="sr-only">{item.sku}</span>
          </Button></TableCell>}
        </TableRow>)}</TableBody>
      </Table></div>}
    {admin && editorOpen && <>
      <ProductEditor key={selected ? `editor-${JSON.stringify(selected)}` : 'new-product'} product={selected} branches={branches} busy={busy}
        onSave={(attributes, enablements) => run(async () => {
          const result = selected
            ? await client.updateProduct(selected.externalId, attributes)
            : await client.createProduct({ ...attributes, branches: enablements });
          setSelected(result); await load();
          setMessage(selected ? 'Producto actualizado.' : 'Producto creado. No se registraron existencias.');
        })}
        onDeactivate={() => {
          if (selected && window.confirm(`¿Desactivar el producto ${selected.sku}? Se conservará con su historial; `
            + 'se verificará que no tenga existencias ni operaciones pendientes y se retirará de todas las sucursales.')) {
            void run(async () => {
              const result = await client.deactivateProduct(selected.externalId);
              setSelected(result); await load(); setMessage('Producto desactivado. Su registro e historial se conservan.');
            });
          }
        }}
        onReactivate={() => {
          if (selected && window.confirm(`¿Reactivar el producto ${selected.sku}? Volverá al catálogo general, `
            + 'pero cada sucursal deberá habilitarlo de nuevo con su mínimo.')) {
            void run(async () => {
              const result = await client.reactivateProduct(selected.externalId);
              setSelected(result); await load();
              setMessage('Producto reactivado. No está habilitado en ninguna sucursal hasta configurarlo de nuevo.');
            });
          }
        }}
        onClose={() => { setEditorOpen(false); setSelected(null); }} />
      {selected && <EnablementPanel key={`enablement-${JSON.stringify(selected)}`} product={selected} branches={branches} busy={busy}
        onEnable={enablement => void run(async () => {
          const result = await client.enableProduct(selected.externalId, enablement);
          setSelected(result); await load(); setMessage('Producto habilitado en la sucursal. No se registraron existencias.');
        })} />}
    </>}
  </section>;
}

function ProductEditor({ product, branches, busy, onSave, onDeactivate, onReactivate, onClose }: {
  product: Product | null; branches: Branch[]; busy: boolean;
  onSave: (attributes: ProductAttributes, enablements: ProductEnablement[]) => Promise<void>;
  onDeactivate: () => void; onReactivate: () => void; onClose: () => void;
}) {
  const [divisible, setDivisible] = useState(product?.baseDivisible ?? false);
  const [chosen, setChosen] = useState<string[]>([]);
  const locked = product?.inUse ?? false;
  const activeBranches = branches.filter(branch => branch.active);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const description = String(data.get('description') ?? '').trim();
    void onSave({ sku: String(data.get('sku')), name: String(data.get('name')), description: description || null,
      baseUnitName: String(data.get('baseUnitName')), baseDivisible: divisible },
    activeBranches.filter(branch => chosen.includes(branch.externalId)).map(branch => ({
      branchExternalId: branch.externalId, minimumQuantity: String(data.get(`minimum-${branch.externalId}`)) })));
  }

  return <form id="product-editor" onSubmit={submit}>
    <fieldset disabled={busy}>
      <legend>{product ? `Editar producto ${product.sku}` : 'Crear producto'}</legend>
      {product && <p>Identificador público: {product.externalId} · <StatusBadge active={product.active} /></p>}
      {locked && <p className="notice-warning">El producto ya tiene historial: su SKU y su unidad base no pueden modificarse.</p>}
      {product && !product.active && <p className="notice-warning">Producto inactivo: no admite nuevas operaciones ni habilitaciones. Editar sus datos no lo reactiva.</p>}
      <div className="grid gap-4 sm:grid-cols-2">
        <label>SKU<Input name="sku" required maxLength={64} pattern="\S(.*\S)?" autoComplete="off"
          title="Sin espacios al inicio ni al final." defaultValue={product?.sku ?? ''} readOnly={locked} /></label>
        <label>Nombre<Input name="name" required maxLength={160} defaultValue={product?.name ?? ''} /></label>
      </div>
      <label>Descripción (opcional)<Textarea name="description" rows={3} defaultValue={product?.description ?? ''} /></label>
      <div className="grid gap-4 sm:grid-cols-2">
        <label>Unidad base<Input name="baseUnitName" required maxLength={64} placeholder="unidad, kg, litro"
          defaultValue={product?.baseUnitName ?? ''} readOnly={locked} /></label>
        <label className="checkbox-field"><input type="checkbox" checked={divisible} disabled={locked}
          onChange={event => setDivisible(event.target.checked)} />La unidad base admite fracciones</label>
      </div>
      {!product && <fieldset className="nested-fieldset">
        <legend>Habilitación inicial por sucursal</legend>
        <p className="text-muted-foreground">Opcional. Cada sucursal elegida necesita un stock mínimo explícito, incluso cero. {quantityHint(divisible)}</p>
        {activeBranches.length === 0 ? <p>No hay sucursales activas para habilitar.</p> : <>
          <Button type="button" variant="outline" onClick={() => setChosen(
            chosen.length === activeBranches.length ? [] : activeBranches.map(branch => branch.externalId))}>
            {chosen.length === activeBranches.length ? 'Quitar todas las sucursales' : 'Elegir todas las sucursales activas'}
          </Button>
          <ul className="enablement-list">{activeBranches.map(branch => {
            const checked = chosen.includes(branch.externalId);
            return <li key={branch.externalId}>
              <label className="checkbox-field"><input type="checkbox" checked={checked} onChange={event => setChosen(
                event.target.checked ? [...chosen, branch.externalId] : chosen.filter(value => value !== branch.externalId))} />
                {branch.code} · {branch.name}</label>
              {checked && <label>Stock mínimo en {branch.code}<Input name={`minimum-${branch.externalId}`} required
                inputMode={divisible ? 'decimal' : 'numeric'} pattern={quantityPattern(divisible)} title={quantityHint(divisible)} /></label>}
            </li>;
          })}</ul>
        </>}
      </fieldset>}
      <Button type="submit">{product ? <><Save aria-hidden="true" />Guardar cambios</> : <><Plus aria-hidden="true" />Crear producto</>}</Button>
      {product?.active && <Button variant="outline" className="text-destructive" type="button" onClick={onDeactivate}><Ban aria-hidden="true" />Desactivar producto</Button>}
      {product && !product.active && <Button variant="outline" type="button" onClick={onReactivate}><RotateCcw aria-hidden="true" />Reactivar producto</Button>}
      <Button variant="ghost" type="button" onClick={onClose}><X aria-hidden="true" />Cerrar formulario</Button>
    </fieldset>
  </form>;
}

function EnablementPanel({ product, branches, busy, onEnable }: {
  product: Product; branches: Branch[]; busy: boolean; onEnable: (enablement: ProductEnablement) => void;
}) {
  const local = product.branches ?? [];
  const available = branches.filter(branch => branch.active
    && !local.some(item => item.branchExternalId === branch.externalId && item.enabled));

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    onEnable({ branchExternalId: String(data.get('branchExternalId')), minimumQuantity: String(data.get('minimumQuantity')) });
  }

  return <div role="group" aria-labelledby="enablement-title">
    <div className="list-heading">
      <div><h3 id="enablement-title">Habilitación por sucursal</h3>
        <p>Dónde puede operarse {product.sku}. Habilitar no agrega existencias.</p></div>
    </div>
    {local.length === 0 ? <p>El producto no está habilitado en ninguna sucursal.</p>
      : <div className="table-scroll" role="region" aria-label={`Habilitación de ${product.sku} por sucursal`} tabIndex={0}>
        <Table>
          <TableCaption>Configuración local de {product.sku}</TableCaption>
          <TableHeader><TableRow>
            <TableHead scope="col">Sucursal</TableHead><TableHead scope="col">Habilitación</TableHead>
            <TableHead scope="col" className="text-right">Stock mínimo ({product.baseUnitName})</TableHead>
          </TableRow></TableHeader>
          <TableBody>{local.map(item => <TableRow key={item.branchExternalId}>
            <TableCell><strong className="block font-medium">{item.branchName}</strong>
              <span className="text-muted-foreground">{item.branchCode}{item.branchActive ? '' : ' (inactiva)'}</span></TableCell>
            <TableCell><StatusBadge active={item.enabled} activeLabel="Habilitado" inactiveLabel="No habilitado" /></TableCell>
            <TableCell className="quantity">{item.minimumQuantity ?? 'Sin mínimo'}</TableCell>
          </TableRow>)}</TableBody>
        </Table></div>}
    {product.active && available.length === 0 && <p>El producto ya está habilitado en todas las sucursales activas.</p>}
    {product.active ? available.length > 0 && <form onSubmit={submit}>
      <fieldset disabled={busy}>
        <legend>Habilitar en otra sucursal</legend>
        <label htmlFor="enable-branch">Sucursal activa</label>
        <select id="enable-branch" name="branchExternalId" required defaultValue="">
          <option value="" disabled>Seleccione una sucursal</option>
          {available.map(branch => <option key={branch.externalId} value={branch.externalId}>{branch.code} · {branch.name}</option>)}
        </select>
        <label>Stock mínimo ({product.baseUnitName})<Input name="minimumQuantity" required
          inputMode={product.baseDivisible ? 'decimal' : 'numeric'} pattern={quantityPattern(product.baseDivisible)}
          title={quantityHint(product.baseDivisible)} /></label>
        <p className="text-muted-foreground">{quantityHint(product.baseDivisible)} Se acepta cero.</p>
        <Button type="submit"><Store aria-hidden="true" />Habilitar en sucursal</Button>
      </fieldset>
    </form> : <p className="notice-warning">Reactive el producto antes de habilitarlo en una sucursal.</p>}
  </div>;
}
