import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Eye, Pencil, Plus, RefreshCw, Save, Tag, X } from 'lucide-react';
import { ApiClient, ApiError } from '../lib/api/client.ts';
import type { PriceList, Product, User } from '../lib/api/client.ts';
import { apiDecimal, decimalInputPattern, inputDecimal, price as unitPrice } from '../lib/format.ts';
import { listPriceOf, unavailableReason } from '../lib/prices/prices.ts';
import { revealEditor } from '../components/shared/revealEditor.ts';
import { StatusBadge } from '../components/shared/StatusBadge.tsx';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';

interface Props { client: ApiClient; user: User; onError: (reason: unknown) => void; revision: number; visible: boolean }

// catalog_price_entry.gross_price is NUMERIC(24,6).
const pricePattern = decimalInputPattern(6, 18);

const failure = (reason: unknown, fallback: string) => reason instanceof TypeError
  ? 'Sin comunicación con el servidor. No se confirmó el cambio.'
  : reason instanceof Error ? reason.message : fallback;

/**
 * RF-0030 (INC-003/D06, INC-014/D01): the administrator maintains global price lists with one price per product unit;
 * managers and operators consult the active lists they will choose from when confirming a sale.
 */
export function PriceLists({ client, user, onError, revision, visible }: Props) {
  const admin = user.role === 'ADMIN';
  const [lists, setLists] = useState<PriceList[] | null>(null);
  const [loadError, setLoadError] = useState('');
  const [selected, setSelected] = useState<PriceList | null>(null);
  // The list the user is looking at; reloads read it here, not from a stale render.
  const selectedId = useRef<string | null>(null);
  const [detailError, setDetailError] = useState('');
  const [editing, setEditing] = useState<PriceList | 'new' | null>(null);
  const [draft, setDraft] = useState({ code: '', name: '', active: true });
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState('');
  const [confirmed, setConfirmed] = useState('');

  function handle(reason: unknown, show: (text: string) => void, fallback: string) {
    if (reason instanceof ApiError && reason.status === 401) onError(reason);
    else show(failure(reason, fallback));
  }

  async function load() {
    setLoadError('');
    try {
      const found = await client.priceLists();
      setLists(found);
      const target = found.find(list => list.externalId === selectedId.current) ?? found[0];
      if (target) await open(target.externalId); else { selectedId.current = null; setSelected(null); }
    } catch (reason) { handle(reason, setLoadError, 'No se pudo consultar las listas de precios.'); }
  }
  useEffect(() => { if (visible) void load(); }, [client, revision, visible]);

  async function open(id: string) {
    selectedId.current = id;
    setDetailError('');
    try { setSelected(await client.priceList(id)); } catch (reason) {
      handle(reason, setDetailError, 'No se pudo consultar los precios de la lista.');
    }
  }

  function edit(target: PriceList | 'new') {
    setEditing(target); setFormError(''); setConfirmed('');
    setDraft(target === 'new' ? { code: '', name: '', active: true } : { code: target.code, name: target.name, active: target.active });
    revealEditor('price-list-editor');
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editing) return;
    const data = { code: draft.code.trim(), name: draft.name.trim() };
    setBusy(true); setFormError('');
    (editing === 'new' ? client.createPriceList(data) : client.updatePriceList(editing.externalId, { ...data, active: draft.active }))
      .then(saved => {
        setConfirmed(editing === 'new' ? `Lista ${saved.code} creada.` : `Lista ${saved.code} actualizada.`);
        setEditing(null);
        selectedId.current = saved.externalId;
        void load();
      }).catch(reason => handle(reason, setFormError, 'No se pudo guardar la lista.'))
      .finally(() => setBusy(false));
  }

  return <section aria-labelledby="prices-title">
    <h2 id="prices-title" tabIndex={-1}>Listas de precios</h2>
    <p>{admin
      ? 'Listas globales para las ventas de toda la red. Cada precio corresponde a una unidad comercial de un producto, en COP. Cambiar un precio no modifica las ventas ya confirmadas.'
      : 'Listas vigentes para elegir al confirmar una venta. Los precios los mantiene el administrador; en la venta solo se registra el descuento permitido.'}</p>
    <div className="list-heading">
      <div><h3>Listas</h3><p>{admin ? 'Activas e inactivas, ordenadas por código.' : 'Solo se muestran las listas activas.'}</p></div>
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" disabled={busy} onClick={() => void load()}><RefreshCw aria-hidden="true" />Actualizar listas</Button>
        {admin && !editing && <Button onClick={() => edit('new')}><Plus aria-hidden="true" />Nueva lista</Button>}
      </div>
    </div>
    {confirmed && <p role="status">{confirmed}</p>}
    {admin && editing && <form id="price-list-editor" onSubmit={submit} aria-describedby={formError ? 'price-list-error' : undefined}>
      <fieldset disabled={busy}>
        <legend>{editing === 'new' ? 'Nueva lista de precios' : `Editar lista ${editing.code}`}</legend>
        <div className="grid gap-4 sm:grid-cols-2">
          <label>Código<Input value={draft.code} onChange={event => setDraft({ ...draft, code: event.target.value })}
            required maxLength={64} autoComplete="off" /></label>
          <label>Nombre<Input value={draft.name} onChange={event => setDraft({ ...draft, name: event.target.value })}
            required maxLength={160} autoComplete="off" /></label>
        </div>
        {editing !== 'new' && <label className="checkbox-field"><input type="checkbox" checked={draft.active}
          onChange={event => setDraft({ ...draft, active: event.target.checked })} />Lista activa: disponible para nuevas ventas</label>}
        {formError && <p role="alert" id="price-list-error">{formError}</p>}
        <Button type="submit"><Save aria-hidden="true" />{busy ? 'Guardando…' : editing === 'new' ? 'Crear lista' : 'Guardar cambios'}</Button>
        <Button variant="ghost" type="button" onClick={() => { setEditing(null); setFormError(''); }}>
          <X aria-hidden="true" />Cerrar formulario
        </Button>
      </fieldset>
    </form>}
    {loadError && <p role="alert">{loadError}</p>}
    {!lists && !loadError && <p role="status">Consultando listas de precios…</p>}
    {lists && (lists.length === 0
      ? <p>{admin ? 'Todavía no hay listas de precios. Cree la primera para que los operadores puedan vender con precios definidos.'
        : 'No hay listas de precios activas. El administrador debe crear una antes de registrar ventas.'}</p>
      : <div className="table-scroll" role="region" aria-label="Tabla de listas de precios" tabIndex={0}>
        <Table>
          <TableCaption>Listas de precios ordenadas por código.</TableCaption>
          <TableHeader><TableRow>
            <TableHead scope="col">Código</TableHead><TableHead scope="col">Nombre</TableHead>
            <TableHead scope="col">Estado</TableHead><TableHead scope="col"><span className="sr-only">Acciones</span></TableHead>
          </TableRow></TableHeader>
          <TableBody>{lists.map(item => <TableRow key={item.externalId}
            aria-current={selected?.externalId === item.externalId ? 'true' : undefined}>
            <TableCell><span className="record-code">{item.code}</span></TableCell>
            <TableCell>{item.name}</TableCell>
            <TableCell><StatusBadge active={item.active} activeLabel="Activa" inactiveLabel="Inactiva" /></TableCell>
            <TableCell><div className="flex flex-wrap gap-2">
              <Button variant={selected?.externalId === item.externalId ? 'secondary' : 'outline'} size="sm"
                onClick={() => void open(item.externalId)}>
                <Eye aria-hidden="true" />Ver precios<span className="sr-only"> de {item.code}</span>
              </Button>
              {admin && <Button variant="outline" size="sm" onClick={() => edit(item)}>
                <Pencil aria-hidden="true" />Editar<span className="sr-only"> {item.code}</span>
              </Button>}
            </div></TableCell>
          </TableRow>)}</TableBody>
        </Table>
      </div>)}
    {detailError && <p role="alert">{detailError}</p>}
    {selected && <ListPrices key={selected.externalId} client={client} list={selected} admin={admin}
      onSaved={setSelected} onError={onError} revision={revision} />}
  </section>;
}

function ListPrices({ client, list, admin, onSaved, onError, revision }: {
  client: ApiClient; list: PriceList; admin: boolean; onSaved: (list: PriceList) => void;
  onError: (reason: unknown) => void; revision: number;
}) {
  const prices = list.prices ?? [];
  const [products, setProducts] = useState<Product[]>([]);
  const [product, setProduct] = useState<Product | null>(null);
  const [draft, setDraft] = useState({ product: '', unit: '', price: '' });
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState('');
  const [confirmed, setConfirmed] = useState('');

  useEffect(() => {
    if (!admin) return;
    client.products().then(found => setProducts(found.filter(item => item.active))).catch(onError);
  }, [client, admin, revision]);

  function chooseProduct(id: string) {
    setDraft({ product: id, unit: '', price: '' }); setProduct(null); setFormError('');
    if (id) client.product(id).then(setProduct).catch(reason => setFormError(failure(reason, 'No se pudo consultar el producto.')));
  }

  function chooseUnit(unit: string) {
    const current = listPriceOf(prices, draft.product, unit);
    setDraft({ ...draft, unit, price: current ? inputDecimal(current.price) : '' });
  }

  function editPrice(productId: string, unit: string, price: string) {
    setConfirmed(''); setFormError('');
    setDraft({ product: productId, unit, price: inputDecimal(price) });
    client.product(productId).then(setProduct).catch(reason => setFormError(failure(reason, 'No se pudo consultar el producto.')));
    revealEditor('list-price-editor');
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true); setFormError(''); setConfirmed('');
    const replaced = listPriceOf(prices, draft.product, draft.unit) !== undefined;
    client.setListPrice(list.externalId, { product: draft.product, unit: draft.unit, price: apiDecimal(draft.price) }).then(saved => {
      setConfirmed(`${replaced ? 'Precio actualizado' : 'Precio definido'}: ${product?.name ?? 'producto'}, ${draft.unit}, ${unitPrice(apiDecimal(draft.price))} COP.`);
      setDraft({ product: draft.product, unit: '', price: '' });
      onSaved(saved);
    }).catch(reason => {
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setFormError(failure(reason, 'No se pudo guardar el precio.'));
    }).finally(() => setBusy(false));
  }

  const units = product?.units?.filter(unit => unit.active) ?? [];
  return <section aria-labelledby="list-prices-title" className="mt-6">
    <div className="list-heading">
      <div><h3 id="list-prices-title"><Tag aria-hidden="true" className="inline size-5 align-text-bottom" /> Precios de {list.name}</h3>
        <p><span className="record-code">{list.code}</span> {list.active ? 'Disponible para nuevas ventas.' : 'Inactiva: no se ofrece en nuevas ventas.'}</p></div>
    </div>
    {admin && <form id="list-price-editor" onSubmit={submit} aria-describedby={formError ? 'list-price-error' : undefined}>
      <fieldset disabled={busy}>
        <legend>Definir o reemplazar un precio</legend>
        <div className="grid gap-4 sm:grid-cols-3">
          <div>
            <label htmlFor="list-price-product">Producto</label>
            <select id="list-price-product" value={draft.product} required onChange={event => chooseProduct(event.target.value)}>
              <option value="">Seleccione un producto</option>
              {products.map(item => <option key={item.externalId} value={item.externalId}>{item.sku} · {item.name}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="list-price-unit">Unidad comercial</label>
            <select id="list-price-unit" value={draft.unit} required disabled={!product}
              onChange={event => chooseUnit(event.target.value)}>
              <option value="">{product ? 'Seleccione una unidad' : 'Elija primero el producto'}</option>
              {units.map(unit => <option key={unit.name} value={unit.name}>{unit.name}{unit.base ? ' (base)' : ''}</option>)}
            </select>
          </div>
          <label>Precio por unidad (COP)
            <Input value={draft.price} onChange={event => setDraft({ ...draft, price: event.target.value })} required
              inputMode="decimal" pattern={pricePattern} autoComplete="off"
              title="Número mayor o igual que cero, con coma decimal y hasta 6 decimales." /></label>
        </div>
        <p className="text-muted-foreground">Si la unidad ya tiene precio en esta lista, se reemplaza. Use coma para los decimales.</p>
        {products.length === 0 && <p className="text-muted-foreground">No hay productos activos a los que asignar precio.</p>}
        {formError && <p role="alert" id="list-price-error">{formError}</p>}
        <Button type="submit"><Save aria-hidden="true" />{busy ? 'Guardando…' : 'Guardar precio'}</Button>
      </fieldset>
    </form>}
    {confirmed && <p role="status">{confirmed}</p>}
    {prices.length === 0
      ? <p>{admin ? 'Esta lista todavía no tiene precios. Defina el primero con el formulario.'
        : 'Esta lista todavía no tiene precios definidos.'}</p>
      : <div className="table-scroll" role="region" aria-label={`Tabla de precios de ${list.name}`} tabIndex={0}>
        <Table>
          <TableCaption>Precio bruto por unidad comercial, en COP, ordenado por SKU.</TableCaption>
          <TableHeader><TableRow>
            <TableHead scope="col">SKU</TableHead><TableHead scope="col">Producto</TableHead>
            <TableHead scope="col">Unidad</TableHead><TableHead scope="col" className="text-right">Precio (COP)</TableHead>
            {admin && <TableHead scope="col"><span className="sr-only">Acciones</span></TableHead>}
          </TableRow></TableHeader>
          <TableBody>{prices.map(item => {
            const reason = unavailableReason(item);
            return <TableRow key={`${item.productExternalId}-${item.unitName}`}>
              <TableCell><span className="record-code whitespace-nowrap">{item.sku}</span></TableCell>
              <TableCell>{item.productName}
                {reason && <span className="block text-muted-foreground">{reason}: no se usa en nuevas ventas</span>}</TableCell>
              <TableCell>{item.unitName}</TableCell>
              <TableCell className="text-right tabular-nums">{unitPrice(item.price)}</TableCell>
              {admin && <TableCell>{!reason && <Button variant="outline" size="sm"
                onClick={() => editPrice(item.productExternalId, item.unitName, item.price)}>
                <Pencil aria-hidden="true" />Cambiar precio<span className="sr-only"> de {item.sku} en {item.unitName}</span>
              </Button>}</TableCell>}
            </TableRow>;
          })}</TableBody>
        </Table>
      </div>}
  </section>;
}
