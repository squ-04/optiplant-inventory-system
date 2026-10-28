import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Ban, CopyPlus, PackageCheck, Pencil, Plus, RefreshCw, Search, Send, Save, Trash2, X } from 'lucide-react';
import { ApiClient, ApiError } from '../lib/api/client.ts';
import type {
  Branch, LocalCatalog, Product, PurchaseHistory, PurchaseHistoryFilter, PurchaseOrder, PurchaseStatus, Supplier, User,
} from '../lib/api/client.ts';
import {
  MAX_PAYMENT_TERM_DAYS, actionFor, cancellable, dueDate, editable, lineNet, money, paymentTerm, purchaseStatusLabel,
  replaceable, sum,
} from '../lib/purchases/purchases.ts';
import type { PurchaseAction } from '../lib/purchases/purchases.ts';
import { keepsIdempotencyKey, newIdempotencyKey } from '../lib/inventory/movements.ts';
import { apiDecimal, dateTime as when, decimal, decimalInputPattern, inputDecimal } from '../lib/format.ts';
import { revealEditor } from '../components/shared/revealEditor.ts';
import { useConfirmation } from '../components/shared/ConfirmDialog.tsx';
import { PurchaseStatusBadge } from '../components/shared/PurchaseStatusBadge.tsx';
import { SectionTabs } from '../components/shared/SectionTabs.tsx';
import { Suppliers } from './Suppliers.tsx';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Textarea } from '../components/ui/textarea';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';

// Prices keep up to 6 decimals and discounts are percentages between 0 and 100 (RF-0023); typed with a comma.
const pricePattern = decimalInputPattern(6, 18);
const percentPattern = decimalInputPattern(6, 3);
const quantityPattern = (divisible: boolean) => decimalInputPattern(divisible ? 3 : 0);

interface LineDraft { key: string; product: string; unit: string; quantity: string; unitPrice: string; discountPercent: string }
const emptyLine = (): LineDraft => ({ key: crypto.randomUUID(), product: '', unit: '', quantity: '', unitPrice: '', discountPercent: '0' });
interface OrderDraft { supplier: string; paymentTermDays: string; lines: LineDraft[] }
const emptyOrder = (): OrderDraft => ({ supplier: '', paymentTermDays: '0', lines: [emptyLine()] });
/** The editor form prefilled with an order's supplier, term and lines, typed as the operator would. */
const draftFrom = (order: PurchaseOrder): OrderDraft => ({
  supplier: order.supplier.externalId, paymentTermDays: String(order.paymentTermDays),
  lines: order.lines.map(line => ({ key: crypto.randomUUID(), product: line.productExternalId, unit: line.unit,
    quantity: inputDecimal(line.commercialQuantity), unitPrice: inputDecimal(line.unitPrice),
    discountPercent: inputDecimal(line.discountPercent) })),
});
// RF-0022: a new order, the rewrite of a draft, or the order that replaces a cancelled one.
type EditorMode = { mode: 'create' } | { mode: 'edit'; order: PurchaseOrder } | { mode: 'replace'; order: PurchaseOrder };
const MAX_CANCEL_REASON = 500;

const STATUS_FILTERS: { value: PurchaseStatus | ''; label: string }[] = [
  { value: '', label: 'Todos los estados' }, { value: 'DRAFT', label: 'Borradores' },
  { value: 'PENDING', label: 'Pendientes de recepción' }, { value: 'RECEIVED', label: 'Recibidas' },
  { value: 'CANCELLED', label: 'Canceladas' },
];

const problem = (reason: unknown, fallback: string) => reason instanceof TypeError
  ? 'Sin comunicación con el servidor. No se sabe si la operación quedó registrada: repítala sin cambios y no se duplicará.'
  : reason instanceof ApiError && reason.status >= 500
    ? 'El servidor no pudo completar la solicitud. Repítala sin cambios; no se duplicará.'
    : reason instanceof Error ? reason.message : fallback;

interface Props { client: ApiClient; user: User; onError: (reason: unknown) => void; onChanged: () => void; revision: number; visible: boolean }

export function Purchases(props: Props) {
  const orders = <PurchaseOrders {...props} />;
  return <section aria-labelledby="purchases-title">
    <h2 id="purchases-title" tabIndex={-1}>{props.user.role === 'ADMIN' ? 'Compras de la red' : 'Compras'}</h2>
    {props.user.role === 'ADMIN'
      ? <SectionTabs label="Compras" items={[
        { label: 'Órdenes de compra', content: orders },
        { label: 'Proveedores', content: <Suppliers client={props.client} onError={props.onError} onChanged={props.onChanged} revision={props.revision} /> },
      ]} />
      : orders}
  </section>;
}

function PurchaseOrders({ client, user, onError, onChanged, revision, visible }: Props) {
  const operator = user.role === 'OPERATOR';
  const admin = user.role === 'ADMIN';
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [catalog, setCatalog] = useState<LocalCatalog | null>(null);
  const [filter, setFilter] = useState<PurchaseHistoryFilter>({});
  const [consulted, setConsulted] = useState<PurchaseHistoryFilter>({});
  const [history, setHistory] = useState<PurchaseHistory | null>(null);
  const [loading, setLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');
  const [selected, setSelected] = useState<PurchaseOrder | null>(null);
  const [detailError, setDetailError] = useState('');
  const [editor, setEditor] = useState<EditorMode | null>(null);
  const editorOpen = editor !== null;
  const [draft, setDraft] = useState<OrderDraft>(emptyOrder);
  // Which editor the typed draft and its key belong to: reopening the same editor keeps both, so a request whose outcome
  // is unknown is retried with the same key instead of creating a duplicate (ARQ-007).
  const [draftFor, setDraftFor] = useState('create');
  // One key per submission attempt; it survives a retry whose outcome is unknown (ARQ-007).
  const [createKey, setCreateKey] = useState(newIdempotencyKey);
  const [actionKeys, setActionKeys] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState('');
  const [actionError, setActionError] = useState('');
  const [confirmed, setConfirmed] = useState('');
  const { confirm, dialog } = useConfirmation();

  useEffect(() => {
    if (!visible) return;
    client.suppliers().then(setSuppliers).catch(onError);
    client.products().then(setProducts).catch(onError);
    if (admin) client.visibleBranches().then(setBranches).catch(onError);
    if (operator) client.localCatalog().then(setCatalog).catch(onError);
  }, [client, admin, operator, revision, visible]);

  async function loadHistory(target = consulted) {
    setLoading(true); setHistoryError('');
    try { setHistory(await client.purchaseHistory(target)); } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setHistoryError(reason instanceof TypeError
        ? 'Sin comunicación con el servidor. El histórico mostrado puede estar desactualizado.'
        : reason instanceof Error ? reason.message : 'No se pudo consultar el histórico de compras.');
    } finally { setLoading(false); }
  }
  useEffect(() => { if (visible) void loadHistory(); }, [visible, consulted, revision]);

  async function openDetail(id: string) {
    setDetailError(''); setActionError('');
    try {
      setSelected(await client.purchase(id));
      revealEditor('purchase-detail');
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setDetailError(reason instanceof Error ? reason.message : 'No se pudo consultar la orden.');
    }
  }

  const activeSuppliers = suppliers.filter(item => item.active);
  const lineNets = draft.lines.map(line => lineNet(apiDecimal(line.quantity), apiDecimal(line.unitPrice), apiDecimal(line.discountPercent)));
  const estimatedTotal = lineNets.every(value => value !== null) ? sum(lineNets as string[]) : null;

  function changeLine(key: string, patch: Partial<LineDraft>) {
    setDraft(previous => ({ ...previous, lines: previous.lines.map(line => line.key === key ? { ...line, ...patch } : line) }));
    setConfirmed('');
  }

  function openEditor(mode: EditorMode = { mode: 'create' }) {
    setEditor(mode); setFormError(''); setConfirmed(''); setActionError('');
    const owner = mode.mode === 'create' ? 'create' : `${mode.mode}:${mode.order.externalId}`;
    if (owner !== draftFor) {
      setDraft(mode.mode === 'create' ? emptyOrder() : draftFrom(mode.order));
      setCreateKey(newIdempotencyKey());
      setDraftFor(owner);
    }
    if (mode.mode === 'edit') setSelected(null);
    revealEditor('purchase-editor');
  }

  function closeEditor() { setEditor(null); setFormError(''); }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const term = paymentTerm(draft.paymentTermDays);
    if (term === null) {
      setFormError(`El plazo de pago debe ser un número entero de días entre 0 y ${MAX_PAYMENT_TERM_DAYS}.`);
      return;
    }
    const input = { supplier: draft.supplier, paymentTermDays: term, lines: draft.lines.map(line => ({
      product: line.product, unit: line.unit, quantity: apiDecimal(line.quantity), unitPrice: apiDecimal(line.unitPrice),
      discountPercent: apiDecimal(line.discountPercent),
    })) };
    const target = editor;
    const request = target?.mode === 'edit' ? client.updatePurchase(target.order.externalId, input, createKey)
      : client.createPurchase(target?.mode === 'replace' ? { ...input, replacesOrder: target.order.externalId } : input, createKey);
    setBusy(true); setFormError(''); setConfirmed('');
    request.then(order => {
      const products = `${order.lines.length} ${order.lines.length === 1 ? 'producto' : 'productos'} por ${decimal(order.totalNet)} COP`;
      setConfirmed(target?.mode === 'edit'
        ? `Borrador ${order.code} actualizado: ${products}. Sigue en borrador; las existencias no cambian.`
        : target?.mode === 'replace'
          ? `Borrador ${order.code} creado en reemplazo de ${target.order.code}, con ${products}. Ambas órdenes quedan en el histórico.`
          : `Borrador ${order.code} creado con ${products}. Las existencias no cambian hasta confirmar la recepción.`);
      setCreateKey(newIdempotencyKey());
      setDraft(emptyOrder());
      setDraftFor('create');
      setEditor(null);
      setSelected(order);
      void loadHistory();
    }).catch(reason => {
      if (!keepsIdempotencyKey(reason)) setCreateKey(newIdempotencyKey());
      if (reason instanceof ApiError && reason.status === 401) { onError(reason); return; }
      setFormError(problem(reason, target?.mode === 'edit' ? 'No se pudo guardar el borrador.' : 'No se pudo crear la orden.'));
    }).finally(() => setBusy(false));
  }

  async function transition(order: PurchaseOrder, action: PurchaseAction) {
    const receive = action === 'receive';
    const accepted = await confirm(receive ? {
      title: `Confirmar la recepción de ${order.code}`,
      description: <>Se registrará el ingreso de las {order.lines.length} líneas completas en el inventario de {order.branch.name},
        valoradas a su costo neto, y comenzará a contar el plazo de pago de {order.paymentTermDays} días. La recepción no
        admite cantidades parciales ni puede repetirse.</>,
      confirmLabel: 'Confirmar recepción',
    } : {
      title: `Enviar ${order.code} a recepción`,
      description: <>La orden quedará pendiente de recepción con el nombre actual del proveedor. Desde ese momento sus
        productos y condiciones no se modifican.</>,
      confirmLabel: 'Enviar a recepción',
    });
    if (!accepted) return;
    const slot = `${order.externalId}:${action}`;
    const key = actionKeys[slot] ?? newIdempotencyKey();
    setActionKeys(previous => ({ ...previous, [slot]: key }));
    setBusy(true); setActionError(''); setConfirmed('');
    try {
      const updated = receive ? await client.receivePurchase(order.externalId, key) : await client.submitPurchase(order.externalId, key);
      setActionKeys(({ [slot]: _done, ...rest }) => rest);
      // The transition answers with the full order, state history included.
      setSelected(updated);
      setConfirmed(receive
        ? `Recepción de ${updated.code} confirmada: el inventario de ${updated.branch.name} aumentó por las ${updated.lines.length} líneas. `
          + `Vence el ${updated.dueDate ? dueDate(updated.dueDate) : ''}.`
        : `${updated.code} quedó pendiente de recepción.`);
      if (receive) onChanged();
      void loadHistory();
    } catch (reason) {
      if (!keepsIdempotencyKey(reason)) setActionKeys(({ [slot]: _rejected, ...rest }) => rest);
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setActionError(problem(reason, 'No se pudo completar la operación.'));
    } finally { setBusy(false); }
  }

  async function cancel(order: PurchaseOrder, reason: string) {
    const slot = `${order.externalId}:cancel`;
    const key = actionKeys[slot] ?? newIdempotencyKey();
    setActionKeys(previous => ({ ...previous, [slot]: key }));
    setBusy(true); setActionError(''); setConfirmed('');
    try {
      const updated = await client.cancelPurchase(order.externalId, reason.trim() || null, key);
      setActionKeys(({ [slot]: _done, ...rest }) => rest);
      setSelected(updated);
      setConfirmed(`${updated.code} quedó cancelada. Las existencias y el costo promedio no cambiaron.`
        + (order.status === 'PENDING' ? ' Si necesita la compra con otros datos, cree una orden de reemplazo.' : ''));
      void loadHistory();
      return true;
    } catch (reason) {
      if (!keepsIdempotencyKey(reason)) setActionKeys(({ [slot]: _rejected, ...rest }) => rest);
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setActionError(problem(reason, 'No se pudo cancelar la orden.'));
      return false;
    } finally { setBusy(false); }
  }

  const editorTitle = editor?.mode === 'edit' ? `Editar borrador ${editor.order.code}`
    : editor?.mode === 'replace' ? `Orden de reemplazo de ${editor.order.code}`
      : `Nueva orden de compra en ${catalog ? `${catalog.branchName} (${catalog.branchCode})` : 'su sucursal'}`;

  return <div aria-busy={loading || busy}>
    {dialog}
    <p>{operator
      ? 'Registre órdenes a proveedores con sus condiciones y confirme su recepción completa: solo entonces aumentan las existencias de la sucursal.'
      : admin ? 'Consulte las compras de una sucursal o de toda la red, por proveedor y por producto. Las registran y reciben los operadores.'
        : 'Supervise las compras de su sucursal por proveedor y por producto. Las registra y recibe el operador.'}</p>

    {operator && catalog && !catalog.branchActive && <p className="notice-warning">
      Sucursal inactiva. Se conserva su histórico, pero no admite nuevas compras.
    </p>}
    {operator && catalog?.branchActive && !editorOpen && <Button onClick={() => openEditor()}><Plus aria-hidden="true" />Nueva orden de compra</Button>}
    {confirmed && <p role="status">{confirmed}</p>}

    {operator && editorOpen && <form id="purchase-editor" className="purchase-editor" onSubmit={save}
      aria-describedby={formError ? 'purchase-error' : undefined}>
      <fieldset disabled={busy}>
        <legend>{editorTitle}</legend>
        {editor?.mode === 'replace' && <p className="purchase-replacement-note">
          Se crea un borrador nuevo con los datos de {editor.order.code} para corregirlos. La orden cancelada se conserva sin
          cambios y ambas quedan vinculadas en el histórico.
        </p>}
        <div className="purchase-header-fields">
          <div>
            <label htmlFor="purchase-supplier">Proveedor</label>
            <select id="purchase-supplier" value={draft.supplier} required
              onChange={event => setDraft({ ...draft, supplier: event.target.value })}>
              <option value="">Seleccione un proveedor</option>
              {activeSuppliers.map(item => <option key={item.externalId} value={item.externalId}>{item.code} · {item.name}</option>)}
            </select>
          </div>
          <label>Plazo de pago en días
            <Input value={draft.paymentTermDays} onChange={event => setDraft({ ...draft, paymentTermDays: event.target.value })}
              required inputMode="numeric" pattern="\d{1,5}" title="Número entero de días; 0 vence el día de la recepción." /></label>
        </div>
        {activeSuppliers.length === 0 && <p className="text-muted-foreground">
          No hay proveedores disponibles. El administrador los crea desde Compras › Proveedores.
        </p>}
        <p className="text-muted-foreground">El plazo cuenta desde la recepción confirmada. Valores en COP, sin impuestos ni fletes.</p>

        <fieldset className="purchase-lines">
          <legend>Productos y condiciones</legend>
          {draft.lines.map((line, index) => {
            const item = catalog?.items.find(product => product.productExternalId === line.product);
            const units = item?.units.filter(unit => unit.active) ?? [];
            const unit = units.find(candidate => candidate.name === line.unit);
            const net = lineNets[index];
            const number = index + 1;
            return <fieldset key={line.key} className="purchase-line">
              <legend className="sr-only">Línea {number}</legend>
              <div className="purchase-line-product">
                <label htmlFor={`line-product-${line.key}`}>Producto {number}</label>
                <select id={`line-product-${line.key}`} value={line.product} required onChange={event => {
                  const next = catalog?.items.find(product => product.productExternalId === event.target.value);
                  changeLine(line.key, { product: event.target.value, unit: next?.units.find(candidate => candidate.base)?.name ?? '' });
                }}>
                  <option value="">Seleccione un producto habilitado</option>
                  {catalog?.items.map(product => <option key={product.productExternalId} value={product.productExternalId}>
                    {product.sku} {product.name}</option>)}
                </select>
              </div>
              <div>
                <label htmlFor={`line-unit-${line.key}`}>Unidad</label>
                <select id={`line-unit-${line.key}`} value={line.unit} required disabled={!item}
                  onChange={event => changeLine(line.key, { unit: event.target.value })}>
                  {!item && <option value="">—</option>}
                  {units.map(candidate => <option key={candidate.name} value={candidate.name}>
                    {candidate.base ? candidate.name : `${candidate.name} (${inputDecimal(candidate.factor)} ${item?.baseUnitName})`}
                  </option>)}
                </select>
              </div>
              <label>Cantidad<Input value={line.quantity} required inputMode={unit?.divisible ? 'decimal' : 'numeric'}
                pattern={quantityPattern(unit?.divisible ?? false)}
                title={unit?.divisible ? 'Hasta 3 decimales con coma, por ejemplo 2,5.' : 'Número entero.'}
                onChange={event => changeLine(line.key, { quantity: event.target.value })} /></label>
              <label>Precio unitario{unit ? ` por ${unit.name}` : ''}<Input value={line.unitPrice} required inputMode="decimal"
                pattern={pricePattern} title="Decimal no negativo con hasta 6 decimales y coma, por ejemplo 120,50."
                onChange={event => changeLine(line.key, { unitPrice: event.target.value })} /></label>
              <label>Descuento %<Input value={line.discountPercent} required inputMode="decimal" pattern={percentPattern}
                title="Porcentaje entre 0 y 100 con hasta 6 decimales; 0 si no aplica."
                onChange={event => changeLine(line.key, { discountPercent: event.target.value })} /></label>
              <p className="purchase-line-net" aria-live="off">
                <span>Neto</span>{net === null ? '—' : decimal(money(net))}
              </p>
              {draft.lines.length > 1 && <Button type="button" variant="ghost" size="sm" className="purchase-line-remove"
                onClick={() => setDraft(previous => ({ ...previous, lines: previous.lines.filter(other => other.key !== line.key) }))}>
                <Trash2 aria-hidden="true" />Quitar<span className="sr-only"> producto {number}</span>
              </Button>}
            </fieldset>;
          })}
          {catalog && catalog.items.length === 0 && <p className="text-muted-foreground">
            La sucursal no tiene productos habilitados. El administrador los habilita desde el catálogo.
          </p>}
          <Button type="button" variant="outline" disabled={draft.lines.length >= 100}
            onClick={() => setDraft(previous => ({ ...previous, lines: [...previous.lines, emptyLine()] }))}>
            <Plus aria-hidden="true" />Agregar producto
          </Button>
        </fieldset>

        <p className="purchase-total"><span>Total neto estimado</span>
          <strong>{estimatedTotal === null ? '—' : `${decimal(money(estimatedTotal))} COP`}</strong></p>
        <p className="text-muted-foreground">Se guarda como borrador. El servidor confirma el neto exacto; el costo por unidad
          base se fija al recibir la compra.</p>
        {formError && <p role="alert" id="purchase-error">{formError}</p>}
        <Button type="submit"><Save aria-hidden="true" />{editor?.mode === 'edit'
          ? busy ? 'Guardando…' : 'Guardar borrador' : busy ? 'Creando…' : 'Crear borrador'}</Button>
        <Button variant="ghost" type="button" onClick={closeEditor}>
          <X aria-hidden="true" />{editor?.mode === 'edit' ? 'Descartar cambios' : 'Cerrar formulario'}
        </Button>
      </fieldset>
    </form>}

    <div className="list-heading">
      <div>
        <h3>Histórico de compras{history?.branch ? ` de ${history.branch.name} (${history.branch.code})` : admin ? ' de la red' : ''}</h3>
        {history && <p>Más recientes primero. Lectura del servidor: {when(history.readAt)}</p>}
      </div>
      <Button variant="outline" disabled={loading} onClick={() => void loadHistory()}>
        <RefreshCw aria-hidden="true" />{loading ? 'Actualizando…' : 'Actualizar histórico'}
      </Button>
    </div>
    <form className="purchase-filters" aria-label="Filtros del histórico de compras"
      onSubmit={event => { event.preventDefault(); setSelected(null); setConsulted({ ...filter }); }}>
      {admin && <div><label htmlFor="purchases-filter-branch">Sucursal</label>
        <select id="purchases-filter-branch" value={filter.branch ?? ''} onChange={event => setFilter({ ...filter, branch: event.target.value || undefined })}>
          <option value="">Toda la red</option>
          {branches.map(item => <option key={item.externalId} value={item.externalId}>
            {item.code} · {item.name}{item.active ? '' : ' (inactiva)'}</option>)}
        </select></div>}
      <div><label htmlFor="purchases-filter-supplier">Proveedor</label>
        <select id="purchases-filter-supplier" value={filter.supplier ?? ''} onChange={event => setFilter({ ...filter, supplier: event.target.value || undefined })}>
          <option value="">Todos los proveedores</option>
          {suppliers.map(item => <option key={item.externalId} value={item.externalId}>{item.code} · {item.name}</option>)}
        </select></div>
      <div><label htmlFor="purchases-filter-product">Producto</label>
        <select id="purchases-filter-product" value={filter.product ?? ''} onChange={event => setFilter({ ...filter, product: event.target.value || undefined })}>
          <option value="">Todos los productos</option>
          {products.map(item => <option key={item.externalId} value={item.externalId}>{item.sku} {item.name}</option>)}
        </select></div>
      <div><label htmlFor="purchases-filter-status">Estado</label>
        <select id="purchases-filter-status" value={filter.status ?? ''}
          onChange={event => setFilter({ ...filter, status: (event.target.value || undefined) as PurchaseStatus | undefined })}>
          {STATUS_FILTERS.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}
        </select></div>
      <Button type="submit" disabled={loading}><Search aria-hidden="true" />Consultar</Button>
    </form>

    {historyError && <p role="alert">{historyError}</p>}
    {loading && !history && <p role="status">Consultando histórico de compras…</p>}
    {history && <>
      <dl className="purchase-summary" aria-label="Órdenes que cumplen los filtros de proveedor y producto, por estado">
        <div><dt>Órdenes</dt><dd>{history.summary.orders}</dd></div>
        <div><dt>Borradores</dt><dd>{history.summary.drafts}</dd></div>
        <div><dt>Pendientes de recepción</dt><dd>{history.summary.pending}</dd></div>
        <div className="purchase-summary-received"><dt>Compras recibidas</dt><dd>{history.summary.received}</dd></div>
        <div><dt>Canceladas</dt><dd>{history.summary.cancelled}</dd></div>
      </dl>
      {history.items.length === 0
        ? <p>No hay compras que cumplan estos filtros.{operator && catalog?.branchActive ? ' Use «Nueva orden de compra» para registrar la primera.' : ''}</p>
        : <HistoryTable history={history} selected={selected?.externalId} onOpen={id => void openDetail(id)} />}
    </>}

    {detailError && <p role="alert">{detailError}</p>}
    {selected && <PurchaseDetail key={selected.externalId} order={selected} action={actionFor(user.role, selected)} valued={!operator}
      canEdit={editable(user.role, selected) && !editorOpen} canCancel={cancellable(user.role, selected)}
      canReplace={replaceable(user.role, selected) && !editorOpen && catalog?.branchActive === true}
      busy={busy} error={actionError} onAction={action => void transition(selected, action)}
      onEdit={() => openEditor({ mode: 'edit', order: selected })} onReplace={() => openEditor({ mode: 'replace', order: selected })}
      onCancel={reason => cancel(selected, reason)} onOpen={id => void openDetail(id)} onClose={() => setSelected(null)} />}
  </div>;
}

function HistoryTable({ history, selected, onOpen }: { history: PurchaseHistory; selected?: string; onOpen: (id: string) => void }) {
  return <div className="table-scroll" role="region" aria-label="Histórico de compras" tabIndex={0}>
    <Table className="purchase-history">
      <TableCaption>Importes netos en COP redondeados a 2 decimales. El proveedor se muestra con el nombre que conserva cada orden.</TableCaption>
      <TableHeader><TableRow>
        <TableHead scope="col">Orden</TableHead>{history.branch === null && <TableHead scope="col">Sucursal</TableHead>}
        <TableHead scope="col">Proveedor</TableHead><TableHead scope="col">Estado</TableHead>
        <TableHead scope="col">Productos</TableHead><TableHead className="quantity" scope="col">Total neto</TableHead>
        <TableHead scope="col">Recepción</TableHead><TableHead scope="col"><span className="sr-only">Acciones</span></TableHead>
      </TableRow></TableHeader>
      <TableBody>{history.items.map(order => <TableRow key={order.externalId} aria-current={order.externalId === selected ? 'true' : undefined}>
        <TableCell><span className="record-code">{order.code}</span><span className="cell-note">{when(order.createdAt)}</span>
          {order.replacesOrder && <span className="cell-note">Reemplaza a {order.replacesOrder.code}</span>}
          {order.replacedBy && <span className="cell-note">Reemplazada por {order.replacedBy.code}</span>}</TableCell>
        {history.branch === null && <TableCell>{order.branch.name}<span className="cell-note">{order.branch.code}</span></TableCell>}
        <TableCell>{order.supplier.name}<span className="cell-note">{order.supplier.code}</span></TableCell>
        <TableCell><PurchaseStatusBadge status={order.status} /></TableCell>
        <TableCell className="cell-text">
          {order.lines.map(line => <span key={line.lineNo} className="block">{decimal(line.commercialQuantity)} {line.unit}: {line.sku} {line.productName}</span>)}
        </TableCell>
        <TableCell className="quantity">{decimal(order.totalNet)}</TableCell>
        <TableCell>{order.receivedAt ? <>{when(order.receivedAt)}
          <span className="cell-note">Vence {order.dueDate && dueDate(order.dueDate)}</span></> : <span className="text-muted-foreground">Sin recibir</span>}</TableCell>
        <TableCell><Button variant="outline" size="sm" onClick={() => onOpen(order.externalId)}>
          Ver detalle<span className="sr-only"> de {order.code}</span></Button></TableCell>
      </TableRow>)}</TableBody>
    </Table>
  </div>;
}

// INC-003/D04: the server sends the valuation cost to supervisors only; operators see the conditions they registered.
function PurchaseDetail({ order, action, valued, canEdit, canCancel, canReplace, busy, error, onAction, onEdit, onReplace, onCancel,
  onOpen, onClose }: {
  order: PurchaseOrder; action: PurchaseAction | null; valued: boolean; canEdit: boolean; canCancel: boolean; canReplace: boolean;
  busy: boolean; error: string; onAction: (action: PurchaseAction) => void; onEdit: () => void; onReplace: () => void;
  onCancel: (reason: string) => Promise<boolean>; onOpen: (id: string) => void; onClose: () => void;
}) {
  const received = order.status === 'RECEIVED';
  // The cancellation form keeps its reason while the detail stays open (AGENTS.md §6).
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState('');
  return <section id="purchase-detail" className="purchase-detail" aria-labelledby="purchase-detail-title" tabIndex={-1}>
    <header>
      <h3 id="purchase-detail-title">Orden {order.code}</h3>
      <PurchaseStatusBadge status={order.status} />
    </header>
    <dl className="purchase-facts">
      <div><dt>Proveedor</dt><dd>{order.supplier.name} ({order.supplier.code})</dd></div>
      <div><dt>Sucursal</dt><dd>{order.branch.name} ({order.branch.code})</dd></div>
      <div><dt>Plazo de pago</dt><dd>{order.paymentTermDays} {order.paymentTermDays === 1 ? 'día' : 'días'} desde la recepción</dd></div>
      <div><dt>Vencimiento</dt><dd>{order.dueDate ? dueDate(order.dueDate) : 'Se calcula al confirmar la recepción'}</dd></div>
      <div><dt>Registrada por</dt><dd>{order.createdBy.fullName} ({order.createdBy.code})</dd></div>
      <div><dt>Recibida por</dt><dd>{order.receivedBy && order.receivedAt
        ? `${order.receivedBy.fullName} (${order.receivedBy.code}), ${when(order.receivedAt)}` : 'Sin recibir'}</dd></div>
      {order.replacesOrder && <div><dt>Reemplaza a</dt><dd><Button variant="link" className="purchase-link"
        onClick={() => onOpen(order.replacesOrder!.externalId)}>{order.replacesOrder.code}<span className="sr-only"> (orden cancelada)</span></Button></dd></div>}
      {order.replacedBy && <div><dt>Reemplazada por</dt><dd><Button variant="link" className="purchase-link"
        onClick={() => onOpen(order.replacedBy!.externalId)}>{order.replacedBy.code}</Button></dd></div>}
    </dl>
    <div className="table-scroll" role="region" aria-label={`Productos de ${order.code}`} tabIndex={0}>
      <Table className="purchase-lines-table">
        <TableCaption>Total neto: {decimal(order.totalNet)} COP{(order.exactTotalNet.split('.')[1] ?? '').length > 2
          ? ` (exacto: ${decimal(order.exactTotalNet)})` : ''}.{valued ? (received
          ? ' El costo por unidad base es el neto de la línea dividido por su equivalente, fijado al recibir.'
          : ' El costo por unidad base se fija al confirmar la recepción.') : ''}</TableCaption>
        <TableHeader><TableRow>
          <TableHead scope="col">Producto</TableHead><TableHead className="quantity" scope="col">Cantidad</TableHead>
          <TableHead className="quantity" scope="col">Precio unitario</TableHead><TableHead className="quantity" scope="col">Descuento</TableHead>
          <TableHead className="quantity" scope="col">Neto</TableHead>
          {valued && <TableHead className="quantity" scope="col">Costo por unidad base</TableHead>}
        </TableRow></TableHeader>
        <TableBody>{order.lines.map(line => <TableRow key={line.lineNo}>
          <TableCell><span className="font-semibold">{line.sku}</span> {line.productName}</TableCell>
          <TableCell className="quantity">{decimal(line.commercialQuantity)} {line.unit}
            {line.unit !== line.baseUnitName && <span className="cell-note">{decimal(line.baseQuantity)} {line.baseUnitName}</span>}</TableCell>
          <TableCell className="quantity">{decimal(line.unitPrice)}<span className="cell-note">por {line.unit}</span></TableCell>
          <TableCell className="quantity">{decimal(line.discountPercent)} %</TableCell>
          <TableCell className="quantity">{decimal(line.netAmount)}</TableCell>
          {valued && <TableCell className="quantity">{line.unitBaseCost === null ? '—' : <>{decimal(line.unitBaseCost)}
            <span className="cell-note">por {line.baseUnitName}</span></>}</TableCell>}
        </TableRow>)}</TableBody>
      </Table>
    </div>
    {order.events && order.events.length > 0 && <>
      <h4>Historia de estados</h4>
      <ol className="purchase-timeline">{order.events.map(event => <li key={`${event.toStatus}-${event.occurredAt}`}>
        <strong>{purchaseStatusLabel(event.toStatus)}</strong> el {when(event.occurredAt)}, por {event.actor.fullName} ({event.actor.code})
        {event.reason && <span className="cell-note">Motivo: {event.reason}</span>}
      </li>)}</ol>
    </>}
    {error && <p role="alert">{error}</p>}
    {cancelling ? <form className="purchase-cancel" aria-labelledby="purchase-cancel-title" onSubmit={event => {
      event.preventDefault();
      void onCancel(reason).then(done => { if (done) { setCancelling(false); setReason(''); } });
    }}>
      <fieldset disabled={busy}>
        <legend id="purchase-cancel-title">Cancelar {order.code}</legend>
        <p>La orden quedará cancelada y se conservará en el histórico. {order.status === 'PENDING'
          ? 'No se registra ningún ingreso: las existencias y el costo promedio no cambian. Después podrá crear otra orden que la reemplace.'
          : 'El borrador no tuvo efectos en el inventario.'} La cancelación no se puede deshacer.</p>
        <label htmlFor="purchase-cancel-reason">Motivo (opcional)</label>
        <Textarea id="purchase-cancel-reason" value={reason} maxLength={MAX_CANCEL_REASON} rows={2}
          aria-describedby="purchase-cancel-hint" onChange={event => setReason(event.target.value)} />
        <p id="purchase-cancel-hint" className="text-muted-foreground">Hasta {MAX_CANCEL_REASON} caracteres. Queda en la historia de estados.</p>
        <Button type="submit" variant="destructive"><Ban aria-hidden="true" />{busy ? 'Cancelando…' : 'Confirmar cancelación'}</Button>
        <Button type="button" variant="ghost" onClick={() => { setCancelling(false); setReason(''); }}>Volver sin cancelar</Button>
      </fieldset>
    </form> : <div className="purchase-actions">
      {action === 'submit' && <Button disabled={busy} onClick={() => onAction('submit')}><Send aria-hidden="true" />Enviar a recepción</Button>}
      {action === 'receive' && <Button disabled={busy} onClick={() => onAction('receive')}><PackageCheck aria-hidden="true" />Confirmar recepción</Button>}
      {canEdit && <Button variant="outline" disabled={busy} onClick={onEdit}><Pencil aria-hidden="true" />Editar borrador</Button>}
      {canReplace && <Button variant="outline" disabled={busy} onClick={onReplace}><CopyPlus aria-hidden="true" />Crear orden de reemplazo</Button>}
      {canCancel && <Button variant="outline" disabled={busy} onClick={() => setCancelling(true)}><Ban aria-hidden="true" />Cancelar orden</Button>}
      <Button variant="ghost" onClick={onClose}><X aria-hidden="true" />Cerrar detalle</Button>
    </div>}
  </section>;
}
