import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Plus, ReceiptText, RefreshCw, Search, Trash2, X } from 'lucide-react';
import { ApiClient, ApiError } from '../lib/api/client.ts';
import type { Branch, LocalCatalog, PriceList, Sale, SaleHistory, User } from '../lib/api/client.ts';
import { lineNet, money, sum } from '../lib/purchases/purchases.ts';
import { MAX_SALE_LINES, confirmationFailure, netUnitPrice, shortages, shownDiscount } from '../lib/sales/sales.ts';
import { listPriceOf } from '../lib/prices/prices.ts';
import { newIdempotencyKey } from '../lib/inventory/movements.ts';
import { apiDecimal, dateTime as when, decimal, decimalInputPattern, inputDecimal } from '../lib/format.ts';
import { revealEditor } from '../components/shared/revealEditor.ts';
import { useConfirmation } from '../components/shared/ConfirmDialog.tsx';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';

// Discounts are percentages between 0 and 100 with up to 6 decimals (RF-0029); typed with a comma.
const percentPattern = decimalInputPattern(6, 3);
const quantityPattern = (divisible: boolean) => decimalInputPattern(divisible ? 3 : 0);

interface LineDraft { key: string; product: string; unit: string; quantity: string; discountPercent: string }
const emptyLine = (): LineDraft => ({ key: crypto.randomUUID(), product: '', unit: '', quantity: '', discountPercent: '0' });
interface SaleDraft { priceList: string; lines: LineDraft[] }
const emptySale = (): SaleDraft => ({ priceList: '', lines: [emptyLine()] });

interface Props { client: ApiClient; user: User; onError: (reason: unknown) => void; onChanged: () => void; revision: number; visible: boolean }

export function Sales({ client, user, onError, onChanged, revision, visible }: Props) {
  const operator = user.role === 'OPERATOR';
  const admin = user.role === 'ADMIN';
  const [catalog, setCatalog] = useState<LocalCatalog | null>(null);
  const [lists, setLists] = useState<PriceList[]>([]);
  const [list, setList] = useState<PriceList | null>(null);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [branch, setBranch] = useState('');
  const [consulted, setConsulted] = useState('');
  const [history, setHistory] = useState<SaleHistory | null>(null);
  const [loading, setLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');
  const [selected, setSelected] = useState<Sale | null>(null);
  const [detailError, setDetailError] = useState('');
  const [editorOpen, setEditorOpen] = useState(false);
  // The draft lives only in this form (INC-014/D03): it survives closing the form or changing view, never reaches the server.
  const [draft, setDraft] = useState<SaleDraft>(emptySale);
  // One key per confirmation attempt; it survives a retry whose outcome is unknown (ARQ-007, RF-0027-CA-02).
  const [saleKey, setSaleKey] = useState(newIdempotencyKey);
  // The last confirmation has an unknown outcome: its key must survive even a discarded draft, or re-entering the same
  // sale would bypass the server replay and sell twice (RF-0027-CA-02).
  const [outcomeUnknown, setOutcomeUnknown] = useState(false);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState('');
  const [confirmed, setConfirmed] = useState('');
  const { confirm, dialog } = useConfirmation();

  useEffect(() => {
    if (!visible) return;
    if (admin) client.visibleBranches().then(setBranches).catch(onError);
    if (operator) {
      client.localCatalog().then(setCatalog).catch(onError);
      client.priceLists().then(setLists).catch(onError);
    }
  }, [client, admin, operator, revision, visible]);

  useEffect(() => {
    if (!operator || !draft.priceList) { setList(null); return; }
    let active = true;
    client.priceList(draft.priceList).then(value => { if (active) setList(value); }).catch(reason => {
      if (!active) return;
      setList(null);
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setFormError(reason instanceof Error ? reason.message : 'No se pudo consultar la lista de precios.');
    });
    return () => { active = false; };
  }, [client, operator, draft.priceList, revision]);

  // Only the latest history request may update the view: a slower earlier response (another branch) is discarded.
  const historyRequest = useRef(0);
  async function loadHistory(target = consulted) {
    const request = ++historyRequest.current;
    const current = () => request === historyRequest.current;
    setLoading(true); setHistoryError('');
    try {
      const value = await client.sales(target ? { branch: target } : {});
      if (current()) setHistory(value);
    } catch (reason) {
      if (!current()) return;
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setHistoryError(reason instanceof TypeError
        ? 'Sin comunicación con el servidor. El registro de ventas mostrado puede estar desactualizado.'
        : reason instanceof Error ? reason.message : 'No se pudo consultar el registro de ventas.');
    } finally { if (current()) setLoading(false); }
  }
  useEffect(() => { if (visible) void loadHistory(); }, [visible, consulted, revision]);

  async function openDetail(id: string) {
    setDetailError('');
    try {
      setSelected(await client.sale(id));
      revealEditor('sale-detail');
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setDetailError(reason instanceof Error ? reason.message : 'No se pudo consultar la venta.');
    }
  }

  const prices = list?.prices ?? [];
  const lines = draft.lines.map(line => {
    const item = catalog?.items.find(product => product.productExternalId === line.product);
    const unit = item?.units.find(candidate => candidate.active && candidate.name === line.unit);
    const price = item && unit ? listPriceOf(prices, item.productExternalId, unit.name) : undefined;
    const usable = price && price.productActive && price.unitActive ? price.price : null;
    const quantity = apiDecimal(line.quantity);
    const discount = apiDecimal(line.discountPercent);
    return { line, item, unit, price: usable, net: usable === null ? null : lineNet(quantity, usable, discount),
      netUnit: usable === null ? null : netUnitPrice(usable, discount), gross: usable === null ? null : lineNet(quantity, usable, '0') };
  });
  const short = shortages(lines.filter(entry => entry.unit).map(entry => ({ product: entry.line.product,
    quantity: apiDecimal(entry.line.quantity), factor: entry.unit!.factor })),
    Object.fromEntries((catalog?.items ?? []).map(item => [item.productExternalId, item.availableQuantity])));
  const complete = lines.every(entry => entry.net !== null && entry.gross !== null);
  const totalNet = complete ? sum(lines.map(entry => entry.net!)) : null;
  const totalGross = complete ? sum(lines.map(entry => entry.gross!)) : null;

  function changeLine(key: string, patch: Partial<LineDraft>) {
    setDraft(previous => ({ ...previous, lines: previous.lines.map(line => line.key === key ? { ...line, ...patch } : line) }));
    setConfirmed(''); setFormError('');
  }

  function openEditor() {
    setEditorOpen(true); setFormError(''); setConfirmed('');
    revealEditor('sale-editor');
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const missing = lines.findIndex(entry => entry.price === null);
    if (missing >= 0) {
      setFormError(`Línea ${missing + 1}: la lista elegida no tiene precio para esa unidad. Elija otra unidad u otra lista.`);
      return;
    }
    const accepted = await confirm({
      title: 'Confirmar la venta',
      description: <>Se descontarán de inventario los {lines.length === 1 ? 'productos de la línea' : `productos de las ${lines.length} líneas`} con
        la lista {list?.name}, por un total neto estimado de {totalNet === null ? '—' : `${decimal(money(totalNet))} COP`}. Si
        alguna línea no tiene existencias suficientes, no se confirmará ninguna. Una venta confirmada no se edita.</>,
      confirmLabel: 'Confirmar venta',
    });
    if (!accepted) return;
    const input = { priceList: draft.priceList, lines: draft.lines.map(line => ({ product: line.product, unit: line.unit,
      quantity: apiDecimal(line.quantity), discountPercent: apiDecimal(line.discountPercent) })) };
    setBusy(true); setFormError(''); setConfirmed('');
    try {
      const sale = await client.confirmSale(input, saleKey);
      setConfirmed(`Venta ${sale.code} confirmada por ${decimal(sale.totalNet)} COP. Las existencias de ${sale.branch.name} `
        + 'disminuyeron y el comprobante quedó registrado.');
      setSaleKey(newIdempotencyKey());
      setOutcomeUnknown(false);
      setDraft(previous => ({ ...emptySale(), priceList: previous.priceList }));
      setEditorOpen(false);
      setSelected(sale);
      revealEditor('sale-detail');
      onChanged();
      void loadHistory();
    } catch (reason) {
      const failure = confirmationFailure(reason);
      if (!failure.keepKey) setSaleKey(newIdempotencyKey());
      setOutcomeUnknown(failure.keepKey);
      if (failure.alreadyConfirmed) { onChanged(); void loadHistory(); }
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setFormError(failure.message);
    } finally { setBusy(false); }
  }

  const activeLists = lists.filter(item => item.active);

  return <section aria-labelledby="sales-title">
    <h2 id="sales-title" tabIndex={-1}>{admin ? 'Ventas de la red' : 'Ventas'}</h2>
    <div aria-busy={loading || busy}>
      {dialog}
      <p>{operator
        ? 'Confirme ventas de su sucursal con la lista de precios elegida y un descuento por línea. Solo la venta confirmada descuenta existencias y genera su comprobante.'
        : admin ? 'Consulte los comprobantes de venta de una sucursal o de toda la red. Las confirman los operadores.'
          : 'Supervise las ventas confirmadas de su sucursal. Las confirma el operador.'}</p>

      {operator && catalog && !catalog.branchActive && <p className="notice-warning">
        Sucursal inactiva. Se conserva su historial, pero no admite nuevas ventas.
      </p>}
      {operator && catalog?.branchActive && !editorOpen && <Button onClick={openEditor}>
        <Plus aria-hidden="true" />{draft.lines.some(line => line.product) ? 'Continuar venta en borrador' : 'Nueva venta'}</Button>}
      {confirmed && <p role="status">{confirmed}</p>}

      {operator && editorOpen && <form id="sale-editor" className="purchase-editor" onSubmit={event => void save(event)}
        aria-describedby={formError ? 'sale-error' : undefined}>
        <fieldset disabled={busy}>
          <legend>Nueva venta en {catalog ? `${catalog.branchName} (${catalog.branchCode})` : 'su sucursal'}</legend>
          <div className="purchase-header-fields">
            <div>
              <label htmlFor="sale-price-list">Lista de precios</label>
              <select id="sale-price-list" value={draft.priceList} required
                onChange={event => { setDraft({ ...draft, priceList: event.target.value }); setFormError(''); }}>
                <option value="">Seleccione una lista</option>
                {activeLists.map(item => <option key={item.externalId} value={item.externalId}>{item.code} · {item.name}</option>)}
              </select>
            </div>
          </div>
          {activeLists.length === 0 ? <p className="text-muted-foreground">
            No hay listas de precios activas. El administrador las mantiene desde Precios.
          </p> : <p className="text-muted-foreground">El precio de cada unidad se toma de la lista elegida; solo se registra el
            descuento por línea. Valores en COP, sin impuestos.</p>}

          <fieldset className="purchase-lines">
            <legend>Productos</legend>
            {lines.map(({ line, item, unit, price, net, netUnit }, index) => {
              const units = item?.units.filter(candidate => candidate.active) ?? [];
              const number = index + 1;
              const hintId = `sale-line-hint-${line.key}`;
              return <fieldset key={line.key} className="purchase-line" aria-describedby={item ? hintId : undefined}>
                <legend className="sr-only">Línea {number}</legend>
                <div className="purchase-line-product">
                  <label htmlFor={`sale-product-${line.key}`}>Producto {number}</label>
                  <select id={`sale-product-${line.key}`} value={line.product} required onChange={event => {
                    const next = catalog?.items.find(product => product.productExternalId === event.target.value);
                    changeLine(line.key, { product: event.target.value, unit: next?.units.find(candidate => candidate.base)?.name ?? '' });
                  }}>
                    <option value="">Seleccione un producto habilitado</option>
                    {catalog?.items.map(product => <option key={product.productExternalId} value={product.productExternalId}>
                      {product.sku} {product.name}</option>)}
                  </select>
                </div>
                <div>
                  <label htmlFor={`sale-unit-${line.key}`}>Unidad</label>
                  <select id={`sale-unit-${line.key}`} value={line.unit} required disabled={!item}
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
                <label>Descuento %<Input value={line.discountPercent} required inputMode="decimal" pattern={percentPattern}
                  title="Porcentaje entre 0 y 100 con hasta 6 decimales; 0 si no aplica."
                  onChange={event => changeLine(line.key, { discountPercent: event.target.value })} /></label>
                <p className="purchase-line-net" aria-live="off">
                  <span>Precio de lista</span>{price === null ? '—' : decimal(price)}
                  {price !== null && netUnit !== null && netUnit !== price && <span className="cell-note">Neto {decimal(netUnit)}</span>}
                </p>
                <p className="purchase-line-net" aria-live="off">
                  <span>Neto</span>{net === null ? '—' : decimal(money(net))}
                </p>
                {item && <p id={hintId} className={short.has(item.productExternalId) ? 'sale-line-note notice-warning' : 'sale-line-note text-muted-foreground'}>
                  {unit && draft.priceList && price === null ? 'La lista elegida no tiene precio para esta unidad. ' : ''}
                  Disponible: {decimal(item.availableQuantity)} {item.baseUnitName}.
                  {short.has(item.productExternalId) ? ' Las líneas de este producto superan las existencias mostradas: la venta será rechazada si no alcanzan al confirmar.' : ''}
                </p>}
                {draft.lines.length > 1 && <Button type="button" variant="ghost" size="sm" className="purchase-line-remove"
                  onClick={() => setDraft(previous => ({ ...previous, lines: previous.lines.filter(other => other.key !== line.key) }))}>
                  <Trash2 aria-hidden="true" />Quitar<span className="sr-only"> producto {number}</span>
                </Button>}
              </fieldset>;
            })}
            {catalog && catalog.items.length === 0 && <p className="text-muted-foreground">
              La sucursal no tiene productos habilitados. El administrador los habilita desde el catálogo.
            </p>}
            <Button type="button" variant="outline" disabled={draft.lines.length >= MAX_SALE_LINES}
              onClick={() => setDraft(previous => ({ ...previous, lines: [...previous.lines, emptyLine()] }))}>
              <Plus aria-hidden="true" />Agregar producto
            </Button>
          </fieldset>

          <dl className="sale-totals">
            <div><dt>Subtotal de lista</dt><dd>{totalGross === null ? '—' : decimal(money(totalGross))}</dd></div>
            <div><dt>Descuentos</dt><dd>{totalGross === null || totalNet === null ? '—' : decimal(shownDiscount(totalGross, totalNet))}</dd></div>
            <div className="sale-totals-net"><dt>Total neto estimado</dt><dd>{totalNet === null ? '—' : `${decimal(money(totalNet))} COP`}</dd></div>
          </dl>
          <p className="text-muted-foreground">El servidor valida las existencias de todas las líneas al confirmar y fija el total exacto.</p>
          {formError && <p role="alert" id="sale-error">{formError}</p>}
          <Button type="submit"><ReceiptText aria-hidden="true" />{busy ? 'Confirmando…' : 'Confirmar venta'}</Button>
          <Button variant="ghost" type="button" onClick={() => { setEditorOpen(false); setFormError(''); }}>
            <X aria-hidden="true" />Cerrar y conservar borrador
          </Button>
          <Button variant="ghost" type="button" onClick={() => { setDraft(previous => ({ ...emptySale(), priceList: previous.priceList }));
            setFormError(outcomeUnknown ? 'Borrador descartado. La confirmación anterior no tiene resultado conocido: revise los '
              + 'comprobantes antes de registrarla de nuevo; si la confirma con los mismos datos, no se duplicará.' : '');
            if (!outcomeUnknown) setSaleKey(newIdempotencyKey()); }}>
            <Trash2 aria-hidden="true" />Descartar borrador
          </Button>
        </fieldset>
      </form>}

      <div className="list-heading">
        <div>
          <h3>Comprobantes de venta{history?.branch ? ` de ${history.branch.name} (${history.branch.code})` : admin ? ' de la red' : ''}</h3>
          {history && <p>Más recientes primero. Lectura del servidor: {when(history.readAt)}</p>}
        </div>
        <Button variant="outline" disabled={loading} onClick={() => void loadHistory()}>
          <RefreshCw aria-hidden="true" />{loading ? 'Actualizando…' : 'Actualizar'}
        </Button>
      </div>
      {admin && <form className="purchase-filters" aria-label="Filtro de comprobantes de venta"
        onSubmit={event => { event.preventDefault(); setSelected(null); setConsulted(branch); }}>
        <div><label htmlFor="sales-filter-branch">Sucursal</label>
          <select id="sales-filter-branch" value={branch} onChange={event => setBranch(event.target.value)}>
            <option value="">Toda la red</option>
            {branches.map(item => <option key={item.externalId} value={item.externalId}>
              {item.code} · {item.name}{item.active ? '' : ' (inactiva)'}</option>)}
          </select></div>
        <Button type="submit" disabled={loading}><Search aria-hidden="true" />Consultar</Button>
      </form>}

      {historyError && <p role="alert">{historyError}</p>}
      {loading && !history && <p role="status">Consultando comprobantes de venta…</p>}
      {history && (history.items.length === 0
        ? <p>No hay ventas confirmadas.{operator && catalog?.branchActive ? ' Use «Nueva venta» para registrar la primera.' : ''}</p>
        : <>
          {history.total > history.items.length && <p className="text-muted-foreground">
            Se muestran las {history.items.length} más recientes de {history.total} ventas.</p>}
          <SaleTable history={history} selected={selected?.externalId} onOpen={id => void openDetail(id)} />
        </>)}

      {detailError && <p role="alert">{detailError}</p>}
      {selected && <SaleReceipt key={selected.externalId} sale={selected} onClose={() => setSelected(null)} />}
    </div>
  </section>;
}

function SaleTable({ history, selected, onOpen }: { history: SaleHistory; selected?: string; onOpen: (id: string) => void }) {
  return <div className="table-scroll" role="region" aria-label="Comprobantes de venta" tabIndex={0}>
    <Table className="purchase-history">
      <TableCaption>Importes netos en COP redondeados a 2 decimales, con los nombres y precios que conserva cada venta.</TableCaption>
      <TableHeader><TableRow>
        <TableHead scope="col">Venta</TableHead>{history.branch === null && <TableHead scope="col">Sucursal</TableHead>}
        <TableHead scope="col">Responsable</TableHead><TableHead scope="col">Productos</TableHead>
        <TableHead className="quantity" scope="col">Total neto</TableHead><TableHead scope="col"><span className="sr-only">Acciones</span></TableHead>
      </TableRow></TableHeader>
      <TableBody>{history.items.map(sale => <TableRow key={sale.externalId} aria-current={sale.externalId === selected ? 'true' : undefined}>
        <TableCell><span className="record-code">{sale.code}</span><span className="cell-note">{when(sale.confirmedAt)}</span></TableCell>
        {history.branch === null && <TableCell>{sale.branch.name}<span className="cell-note">{sale.branch.code}</span></TableCell>}
        <TableCell>{sale.confirmedBy.fullName}<span className="cell-note">{sale.confirmedBy.code}</span></TableCell>
        <TableCell className="cell-text">
          {sale.lines.map(line => <span key={line.lineNo} className="block">{decimal(line.commercialQuantity)} {line.unit}: {line.sku} {line.productName}</span>)}
        </TableCell>
        <TableCell className="quantity">{decimal(sale.totalNet)}</TableCell>
        <TableCell><Button variant="outline" size="sm" onClick={() => onOpen(sale.externalId)}>
          Ver comprobante<span className="sr-only"> de {sale.code}</span></Button></TableCell>
      </TableRow>)}</TableBody>
    </Table>
  </div>;
}

/** RF-0031 (INC-014/D06): the internal, immutable record of a confirmed sale; it is not a fiscal invoice. */
function SaleReceipt({ sale, onClose }: { sale: Sale; onClose: () => void }) {
  const listNames = [...new Set(sale.lines.map(line => line.priceListName))];
  return <section id="sale-detail" className="purchase-detail" aria-labelledby="sale-detail-title" tabIndex={-1}>
    <header>
      <h3 id="sale-detail-title">Comprobante de venta {sale.code}</h3>
    </header>
    <dl className="purchase-facts">
      <div><dt>Fecha de confirmación</dt><dd>{when(sale.confirmedAt)}</dd></div>
      <div><dt>Sucursal</dt><dd>{sale.branch.name} ({sale.branch.code})</dd></div>
      <div><dt>Responsable</dt><dd>{sale.confirmedBy.fullName} ({sale.confirmedBy.code})</dd></div>
      <div><dt>Lista de precios</dt><dd>{listNames.join(', ')}</dd></div>
      <div><dt>Identificador</dt><dd className="break-all">{sale.externalId}</dd></div>
    </dl>
    <div className="table-scroll" role="region" aria-label={`Productos de ${sale.code}`} tabIndex={0}>
      <Table className="purchase-lines-table">
        <TableCaption>Registro interno sin validez fiscal. Conserva el SKU, los nombres y el precio de lista vigentes al confirmar.</TableCaption>
        <TableHeader><TableRow>
          <TableHead scope="col">Producto</TableHead><TableHead className="quantity" scope="col">Cantidad</TableHead>
          <TableHead className="quantity" scope="col">Precio de lista</TableHead><TableHead className="quantity" scope="col">Descuento</TableHead>
          <TableHead className="quantity" scope="col">Precio neto</TableHead><TableHead className="quantity" scope="col">Neto</TableHead>
        </TableRow></TableHeader>
        <TableBody>{sale.lines.map(line => <TableRow key={line.lineNo}>
          <TableCell><span className="font-semibold">{line.sku}</span> {line.productName}</TableCell>
          <TableCell className="quantity">{decimal(line.commercialQuantity)} {line.unit}
            {line.unit !== line.baseUnitName && <span className="cell-note">{decimal(line.baseQuantity)} {line.baseUnitName}</span>}</TableCell>
          <TableCell className="quantity">{decimal(line.unitPrice)}<span className="cell-note">por {line.unit}</span></TableCell>
          <TableCell className="quantity">{decimal(line.discountPercent)} %</TableCell>
          <TableCell className="quantity">{decimal(line.netUnitPrice)}</TableCell>
          <TableCell className="quantity">{decimal(line.netAmount)}</TableCell>
        </TableRow>)}</TableBody>
      </Table>
    </div>
    <dl className="sale-totals">
      <div><dt>Subtotal de lista</dt><dd>{decimal(sale.grossTotal)}</dd></div>
      <div><dt>Descuentos</dt><dd>{decimal(sale.discountTotal)}</dd></div>
      <div className="sale-totals-net"><dt>Total neto</dt><dd>{decimal(sale.totalNet)} COP</dd></div>
    </dl>
    {(sale.exactTotalNet.split('.')[1] ?? '').length > 2 && <p className="text-muted-foreground">
      Total exacto: {decimal(sale.exactTotalNet)} COP.</p>}
    <p className="text-muted-foreground">Una devolución se registra en Movimientos referenciando la salida de esta venta; el
      comprobante no cambia.</p>
    <div className="purchase-actions">
      <Button variant="ghost" onClick={onClose}><X aria-hidden="true" />Cerrar comprobante</Button>
    </div>
  </section>;
}
