import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiClient, sessionProblem } from '../lib/api/client.ts';
import type {
  Branch, LocalCatalog, Product, ProductAttributes, ProductEnablement, ProductUnit, ProductUnitChange, ProductUnitInput,
  UnitConversion, User,
} from '../lib/api/client.ts';
import { RestockBadge } from '../components/shared/RestockBadge.tsx';
import { useConfirmation } from '../components/shared/ConfirmDialog.tsx';
import { StatusBadge } from '../components/shared/StatusBadge';
import { SectionTabs } from '../components/shared/SectionTabs.tsx';
import { revealEditor } from '../components/shared/revealEditor.ts';
import { Ban, Calculator, Pencil, Plus, RefreshCw, RotateCcw, Save, Store, X } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Textarea } from '../components/ui/textarea';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';
import { apiDecimal, decimal, decimalInputPattern, inputDecimal } from '../lib/format.ts';

// Base-unit quantities keep at most three decimals; indivisible units admit whole numbers only. Decimals are typed with a
// comma and sent to the API with a dot.
const quantityPattern = (divisible: boolean) => decimalInputPattern(divisible ? 3 : 0);
const quantityHint = (divisible: boolean) => divisible ? 'Hasta 3 decimales con coma, por ejemplo 2,5.' : 'Número entero, por ejemplo 4.';
const equivalence = (unit: ProductUnit, baseUnitName: string) => unit.base ? 'Unidad base' : `1 ${unit.name} = ${decimal(unit.factor)} ${baseUnitName}`;

function failure(reason: unknown) {
  return reason instanceof TypeError
    ? 'Sin comunicación con el servidor. No se confirmó la operación; actualice el catálogo antes de reintentar.'
    : reason instanceof Error ? reason.message : 'No se pudo completar la solicitud.';
}

interface Props { client: ApiClient; user: User; onError: (reason: unknown) => void; revision: number; visible: boolean }

export function Catalog({ client, user, onError, revision, visible }: Props) {
  const admin = user.role === 'ADMIN';
  const [products, setProducts] = useState<Product[]>([]);
  // Local profiles also see the catalog of their own branch (RF-0012); administrators have no branch of their own.
  const [local, setLocal] = useState<LocalCatalog | null>(null);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [selected, setSelected] = useState<Product | null>(null);
  // The editor exists only after an explicit create or edit action.
  const [editorOpen, setEditorOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const { confirm, dialog } = useConfirmation();

  const fetchAll = () => Promise.all([client.products(), admin ? client.branches() : Promise.resolve([]),
    admin ? Promise.resolve(null) : client.localCatalog()]);
  async function load() {
    const [nextProducts, nextBranches, nextLocal] = await fetchAll();
    setProducts(nextProducts); setBranches(nextBranches); setLocal(nextLocal); setLoaded(true);
  }
  useEffect(() => {
    // The view stays mounted while hidden; reading on each display keeps local stock current after movements elsewhere.
    if (!visible) return;
    let active = true;
    setBusy(true); setError('');
    fetchAll().then(([p, b, l]) => {
      if (active) { setProducts(p); setBranches(b); setLocal(l); setLoaded(true); }
    }).catch(reason => {
      // Like the actions below, a failed read stays in this view; only session problems reach the shell.
      if (!active) return;
      setError(failure(reason));
      if (sessionProblem(reason)) onError(reason);
    })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [client, admin, revision, visible]); // Administration changes (new or inactive branches) refresh the choices.

  async function run(action: () => Promise<void>) {
    setBusy(true); setMessage(''); setError('');
    try { await action(); }
    catch (reason) {
      setError(failure(reason));
      // Business rejections stay in this view; only session problems are escalated to the shell.
      if (sessionProblem(reason)) onError(reason);
    } finally { setBusy(false); }
  }

  const general = <>
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
          {!admin && <TableHead scope="col">En mi sucursal</TableHead>}
          {admin && <TableHead scope="col" className="text-right">Acción</TableHead>}
        </TableRow></TableHeader>
        <TableBody>{products.map(item => <TableRow key={item.externalId}>
          <TableCell><span className="record-code whitespace-nowrap">{item.sku}</span></TableCell>
          <TableCell><strong className="block font-medium">{item.name}</strong>
            {item.description && <span className="text-muted-foreground">{item.description}</span>}</TableCell>
          <TableCell>{item.baseUnitName}<span className="block text-muted-foreground">
            {item.baseDivisible ? 'Admite fracciones' : 'Solo cantidades enteras'}</span></TableCell>
          <TableCell><StatusBadge active={item.active} /></TableCell>
          {!admin && <TableCell><StatusBadge active={Boolean(local?.items.some(entry => entry.productExternalId === item.externalId))}
            activeLabel="Habilitado" inactiveLabel="No habilitado" /></TableCell>}
          {admin && <TableCell className="text-right"><Button variant="outline" disabled={busy}
            onClick={() => void run(async () => {
              setSelected(await client.product(item.externalId)); setEditorOpen(true); revealEditor('product-editor');
            })}>
            <Pencil aria-hidden="true" />Editar <span className="sr-only">{item.sku}</span>
          </Button></TableCell>}
        </TableRow>)}</TableBody>
      </Table></div>}
  </>;

  return <section aria-labelledby="catalog-title" aria-busy={busy}>
    <h2 id="catalog-title" tabIndex={-1}>Catálogo de productos</h2>
    <p>{admin
      ? 'Catálogo común de la red. Crear o habilitar un producto no agrega existencias. Los productos no se eliminan: se desactivan y conservan su historial.'
      : 'Productos habilitados en su sucursal, con sus existencias, y catálogo común de la red. Solo consulta.'}</p>
    <p role="status">{busy ? 'Consultando servidor…' : message}</p>
    {error && <p role="alert">{error}</p>}
    {admin ? general : <SectionTabs label="Catálogo" items={[
      { label: 'Mi sucursal', content: <LocalCatalogPanel client={client} catalog={local} loaded={loaded} /> },
      { label: 'Catálogo general', content: general },
    ]} />}
    {admin && editorOpen && <>
      <ProductEditor key={selected ? `editor-${JSON.stringify(selected)}` : 'new-product'} product={selected} branches={branches} busy={busy}
        onSave={(attributes, enablements) => run(async () => {
          const result = selected
            ? await client.updateProduct(selected.externalId, attributes)
            : await client.createProduct({ ...attributes, branches: enablements });
          setSelected(result); await load();
          setMessage(selected ? 'Producto actualizado.' : 'Producto creado. No se registraron existencias.');
        })}
        onDeactivate={async () => {
          if (selected && await confirm({ title: `¿Desactivar el producto ${selected.sku}?`, confirmLabel: 'Desactivar producto',
            destructive: true, description: 'Se conservará con su historial; se verificará que no tenga existencias ni '
              + 'operaciones pendientes y se retirará de todas las sucursales.' })) {
            void run(async () => {
              const result = await client.deactivateProduct(selected.externalId);
              setSelected(result); await load(); setMessage('Producto desactivado. Su registro e historial se conservan.');
            });
          }
        }}
        onReactivate={async () => {
          if (selected && await confirm({ title: `¿Reactivar el producto ${selected.sku}?`, confirmLabel: 'Reactivar producto',
            description: 'Volverá al catálogo general, pero cada sucursal deberá habilitarlo de nuevo con su mínimo.' })) {
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
        })}
        onMinimum={(branchId, minimum) => void run(async () => {
          const result = await client.updateMinimum(selected.externalId, branchId, minimum);
          setSelected(result); setMessage('Stock mínimo actualizado. Existencias, costos y documentos no cambiaron.');
        })} />}
      {selected && <UnitsPanel key={`units-${JSON.stringify(selected.units)}`} client={client} product={selected} busy={busy}
        onCreate={unit => void run(async () => {
          setSelected(await client.createUnit(selected.externalId, unit)); setMessage(`Unidad ${unit.name} agregada.`);
        })}
        onUpdate={change => void run(async () => {
          setSelected(await client.updateUnit(selected.externalId, change));
          setMessage(`Unidad ${change.name} actualizada. Los documentos y saldos anteriores conservan su unidad y factor.`);
        })} />}
    </>}
    {dialog}
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
      branchExternalId: branch.externalId, minimumQuantity: apiDecimal(String(data.get(`minimum-${branch.externalId}`))) })));
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

function EnablementPanel({ product, branches, busy, onEnable, onMinimum }: {
  product: Product; branches: Branch[]; busy: boolean; onEnable: (enablement: ProductEnablement) => void;
  onMinimum: (branchId: string, minimumQuantity: string) => void;
}) {
  // The minimum editor opens on demand for one enabled branch at a time.
  const [editing, setEditing] = useState<string | null>(null);
  const editedBranch = (product.branches ?? []).find(item => item.branchExternalId === editing && item.enabled);
  const local = product.branches ?? [];
  const available = branches.filter(branch => branch.active
    && !local.some(item => item.branchExternalId === branch.externalId && item.enabled));

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    onEnable({ branchExternalId: String(data.get('branchExternalId')), minimumQuantity: apiDecimal(String(data.get('minimumQuantity'))) });
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
            <TableHead scope="col" className="text-right">Acción</TableHead>
          </TableRow></TableHeader>
          <TableBody>{local.map(item => <TableRow key={item.branchExternalId}>
            <TableCell><strong className="block font-medium">{item.branchName}</strong>
              <span className="text-muted-foreground">{item.branchCode}{item.branchActive ? '' : ' (inactiva)'}</span></TableCell>
            <TableCell><StatusBadge active={item.enabled} activeLabel="Habilitado" inactiveLabel="No habilitado" /></TableCell>
            <TableCell className="quantity">{item.minimumQuantity === null ? 'Sin mínimo' : decimal(item.minimumQuantity)}</TableCell>
            <TableCell className="text-right">{item.enabled ? <Button variant="outline" disabled={busy} onClick={() => {
              setEditing(item.branchExternalId); revealEditor('minimum-editor');
            }}><Pencil aria-hidden="true" />Cambiar mínimo <span className="sr-only">en {item.branchCode}</span></Button>
              : <span className="text-muted-foreground">Habilite para configurar</span>}</TableCell>
          </TableRow>)}</TableBody>
        </Table></div>}
    {editedBranch && <form id="minimum-editor" onSubmit={event => {
      event.preventDefault();
      onMinimum(editedBranch.branchExternalId, apiDecimal(String(new FormData(event.currentTarget).get('minimumQuantity'))));
    }}>
      <fieldset disabled={busy}>
        <legend>Stock mínimo de {product.sku} en {editedBranch.branchCode}</legend>
        <p className="text-muted-foreground">Se usa para las alertas de reabastecimiento y para identificar productos próximos a
          agotarse. Cambiarlo no modifica existencias, costos ni documentos, no bloquea operaciones ni genera compras.</p>
        <label>Nuevo stock mínimo ({product.baseUnitName})<Input name="minimumQuantity" required
          defaultValue={editedBranch.minimumQuantity === null ? '' : inputDecimal(editedBranch.minimumQuantity)} inputMode={product.baseDivisible ? 'decimal' : 'numeric'}
          pattern={quantityPattern(product.baseDivisible)} title={quantityHint(product.baseDivisible)} /></label>
        <p className="text-muted-foreground">{quantityHint(product.baseDivisible)} Se acepta cero.</p>
        <Button type="submit"><Save aria-hidden="true" />Guardar mínimo</Button>
        <Button variant="ghost" type="button" onClick={() => setEditing(null)}><X aria-hidden="true" />Cerrar formulario</Button>
      </fieldset>
    </form>}
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

function UnitsPanel({ client, product, busy, onCreate, onUpdate }: {
  client: ApiClient; product: Product; busy: boolean;
  onCreate: (unit: ProductUnitInput) => void; onUpdate: (change: ProductUnitChange) => void;
}) {
  const units = product.units ?? [];
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<string | null>(null);
  const [divisible, setDivisible] = useState(false);
  const edited = units.find(unit => unit.name === editing && !unit.base);

  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    onCreate({ name: String(data.get('name')), factor: apiDecimal(String(data.get('factor'))), divisible: product.baseDivisible && divisible });
  }

  function update(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    if (edited) onUpdate({ name: edited.name, factor: apiDecimal(String(data.get('factor'))), active: data.get('active') === 'on' });
  }

  return <div role="group" aria-labelledby="units-title">
    <div className="list-heading">
      <div><h3 id="units-title">Unidades de medida</h3>
        <p>Conversiones propias de {product.sku} hacia su unidad base, {product.baseUnitName}.</p></div>
      <Button variant="outline" disabled={busy} onClick={() => { setCreating(true); revealEditor('unit-creator'); }}>
        <Plus aria-hidden="true" />Nueva unidad</Button>
    </div>
    <div className="table-scroll" role="region" aria-label={`Unidades de ${product.sku}`} tabIndex={0}>
      <Table>
        <TableCaption>Unidades de {product.sku}; la unidad base aparece primero</TableCaption>
        <TableHeader><TableRow>
          <TableHead scope="col">Unidad</TableHead><TableHead scope="col">Equivalencia</TableHead>
          <TableHead scope="col">Cantidades</TableHead><TableHead scope="col">Estado</TableHead>
          <TableHead scope="col" className="text-right">Acción</TableHead>
        </TableRow></TableHeader>
        <TableBody>{units.map(unit => <TableRow key={unit.name}>
          <TableCell><strong className="font-medium">{unit.name}</strong></TableCell>
          <TableCell>{equivalence(unit, product.baseUnitName)}</TableCell>
          <TableCell>{unit.divisible ? 'Admite fracciones' : 'Solo enteras'}</TableCell>
          <TableCell><StatusBadge active={unit.active} activeLabel="Activa" inactiveLabel="Inactiva" /></TableCell>
          <TableCell className="text-right">{unit.base
            ? <span className="text-muted-foreground">Se edita con el producto</span>
            : <Button variant="outline" disabled={busy} onClick={() => { setEditing(unit.name); revealEditor('unit-editor'); }}>
              <Pencil aria-hidden="true" />Editar <span className="sr-only">unidad {unit.name}</span></Button>}</TableCell>
        </TableRow>)}</TableBody>
      </Table></div>
    {creating && <form id="unit-creator" onSubmit={create}>
      <fieldset disabled={busy}>
        <legend>Agregar unidad a {product.sku}</legend>
        <div className="grid gap-4 sm:grid-cols-2">
          <label>Nombre de la unidad<Input name="name" required maxLength={64} pattern="\S(.*\S)?" placeholder="caja, saco"
            title="Sin espacios al inicio ni al final." autoComplete="off" /></label>
          <label>Equivale a ({product.baseUnitName})<Input name="factor" required inputMode={product.baseDivisible ? 'decimal' : 'numeric'}
            pattern={quantityPattern(product.baseDivisible)} title={quantityHint(product.baseDivisible)} /></label>
        </div>
        <p className="text-muted-foreground">Cantidad de {product.baseUnitName} contenida en una unidad. {quantityHint(product.baseDivisible)}</p>
        {product.baseDivisible
          ? <label className="checkbox-field"><input type="checkbox" checked={divisible}
            onChange={event => setDivisible(event.target.checked)} />La unidad admite fracciones</label>
          : <p className="text-muted-foreground">La unidad base no admite fracciones: la nueva unidad tampoco.</p>}
        <Button type="submit"><Plus aria-hidden="true" />Agregar unidad</Button>
        <Button variant="ghost" type="button" onClick={() => setCreating(false)}><X aria-hidden="true" />Cerrar formulario</Button>
      </fieldset>
    </form>}
    {edited && <form id="unit-editor" key={edited.name} onSubmit={update}>
      <fieldset disabled={busy}>
        <legend>Editar unidad {edited.name}</legend>
        <p className="notice-warning">El nuevo factor rige desde ahora. Los documentos y saldos anteriores conservan la unidad, el
          factor y las cantidades con que se registraron.</p>
        <label>Equivale a ({product.baseUnitName})<Input name="factor" required defaultValue={inputDecimal(edited.factor)}
          inputMode={product.baseDivisible ? 'decimal' : 'numeric'} pattern={quantityPattern(product.baseDivisible)}
          title={quantityHint(product.baseDivisible)} /></label>
        <label className="checkbox-field"><input type="checkbox" name="active" defaultChecked={edited.active} />
          Unidad disponible para nuevas operaciones</label>
        <Button type="submit"><Save aria-hidden="true" />Guardar unidad</Button>
        <Button variant="ghost" type="button" onClick={() => setEditing(null)}><X aria-hidden="true" />Cerrar formulario</Button>
      </fieldset>
    </form>}
    <ConversionForm client={client} productId={product.externalId} sku={product.sku} baseUnitName={product.baseUnitName}
      units={units} />
  </div>;
}

/** Exact base equivalent computed by the server with the factor in force; nothing is rounded in the browser. */
function ConversionForm({ client, productId, sku, baseUnitName, units }: {
  client: ApiClient; productId: string; sku: string; baseUnitName: string; units: ProductUnit[];
}) {
  const choices = units.filter(unit => unit.active);
  const [unitName, setUnitName] = useState(choices.find(unit => !unit.base)?.name ?? choices[0]?.name ?? '');
  const [result, setResult] = useState<UnitConversion | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const unit = choices.find(item => item.name === unitName);
  if (choices.length === 0) return null;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const quantity = apiDecimal(String(new FormData(event.currentTarget).get('quantity')));
    setBusy(true); setResult(null); setError('');
    try { setResult(await client.convertUnit(productId, unitName, quantity)); }
    catch (reason) { setError(failure(reason)); }
    finally { setBusy(false); }
  }

  return <form onSubmit={event => void submit(event)} aria-busy={busy}>
    <fieldset disabled={busy}>
      <legend>Calcular equivalencia de {sku}</legend>
      <div className="grid gap-4 sm:grid-cols-2">
        <label htmlFor={`convert-unit-${productId}`}>Unidad
          <select id={`convert-unit-${productId}`} value={unitName} onChange={event => {
            setUnitName(event.target.value); setResult(null); setError('');
          }}>
            {choices.map(item => <option key={item.name} value={item.name}>{item.name} ({equivalence(item, baseUnitName)})</option>)}
          </select></label>
        <label>Cantidad<Input name="quantity" required inputMode={unit?.divisible ? 'decimal' : 'numeric'}
          pattern={quantityPattern(unit?.divisible ?? false)} title={quantityHint(unit?.divisible ?? false)} /></label>
      </div>
      <Button type="submit" variant="outline"><Calculator aria-hidden="true" />Calcular equivalencia</Button>
    </fieldset>
    <p role="status">{result ? `${decimal(result.quantity)} ${result.unitName} = ${decimal(result.baseQuantity)} ${result.baseUnitName} `
      + `(factor ${decimal(result.factor)}).` : ''}</p>
    {error && <p role="alert">{error}</p>}
  </form>;
}

function LocalCatalogPanel({ client, catalog, loaded }: { client: ApiClient; catalog: LocalCatalog | null; loaded: boolean }) {
  const [selected, setSelected] = useState('');
  if (!catalog) return <p>{loaded ? 'El catálogo de su sucursal no está disponible.' : 'Consultando el catálogo de su sucursal…'}</p>;
  const item = catalog.items.find(entry => entry.productExternalId === selected);
  return <div role="group" aria-labelledby="local-catalog-title">
    <div className="list-heading">
      <div><h3 id="local-catalog-title">Catálogo de {catalog.branchName} ({catalog.branchCode})</h3>
        <p>Productos habilitados para operar en su sucursal, aunque no tengan existencias. Cantidades en unidad base.</p></div>
    </div>
    {!catalog.branchActive && <p className="notice-warning">Sucursal inactiva. Se conservan las consultas; no admite nuevas operaciones.</p>}
    {catalog.items.length === 0 ? <p>Su sucursal no tiene productos habilitados. El administrador los habilita desde el catálogo.</p>
      : <div className="table-scroll" role="region" aria-label={`Catálogo de ${catalog.branchCode}`} tabIndex={0}>
        <Table>
          <TableCaption>Productos habilitados en {catalog.branchCode}; el tránsito no forma parte del físico ni del disponible</TableCaption>
          <TableHeader><TableRow>
            <TableHead scope="col">SKU</TableHead><TableHead scope="col">Producto</TableHead>
            <TableHead scope="col" className="text-right">Físico</TableHead>
            <TableHead scope="col" className="text-right">Disponible</TableHead>
            <TableHead scope="col" className="text-right">En tránsito hacia la sucursal</TableHead>
            <TableHead scope="col" className="text-right">Stock mínimo</TableHead>
            <TableHead scope="col">Reabastecimiento</TableHead>
            <TableHead scope="col">Unidades</TableHead>
          </TableRow></TableHeader>
          <TableBody>{catalog.items.map(entry => <TableRow key={entry.productExternalId}>
            <TableCell><span className="record-code whitespace-nowrap">{entry.sku}</span></TableCell>
            <TableCell><strong className="block font-medium">{entry.name}</strong>
              {entry.description && <span className="text-muted-foreground">{entry.description}</span>}</TableCell>
            <TableCell className="quantity">{decimal(entry.physicalQuantity)} {entry.baseUnitName}</TableCell>
            <TableCell className="quantity">{decimal(entry.availableQuantity)} {entry.baseUnitName}</TableCell>
            <TableCell className="quantity">{decimal(entry.inTransitQuantity)} {entry.baseUnitName}</TableCell>
            <TableCell className="quantity">{decimal(entry.minimumQuantity)} {entry.baseUnitName}</TableCell>
            <TableCell><RestockBadge active={entry.restockAlert} /></TableCell>
            <TableCell><ul>{entry.units.map(unit => <li key={unit.name}>{equivalence(unit, entry.baseUnitName)
              .replace('Unidad base', `${unit.name} (unidad base)`)}</li>)}</ul></TableCell>
          </TableRow>)}</TableBody>
        </Table></div>}
    {catalog.items.length > 0 && <>
      <label htmlFor="local-convert-product">Producto para calcular equivalencias</label>
      <select id="local-convert-product" value={selected} onChange={event => setSelected(event.target.value)}>
        <option value="">Seleccione un producto</option>
        {catalog.items.map(entry => <option key={entry.productExternalId} value={entry.productExternalId}>{entry.sku} · {entry.name}</option>)}
      </select>
      {item && <ConversionForm key={item.productExternalId} client={client} productId={item.productExternalId} sku={item.sku}
        baseUnitName={item.baseUnitName} units={item.units} />}
    </>}
  </div>;
}
