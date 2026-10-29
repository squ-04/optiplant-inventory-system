import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ArrowRight, Ban, CheckCircle2, ClipboardCheck, PackageCheck, Plus, RefreshCw, Search, Send, Trash2, Truck, X, XCircle } from 'lucide-react';
import { ApiClient, ApiError } from '../lib/api/client.ts';
import type {
  Branch, LocalCatalog, Route, ShortageTreatment, TransitLossInput, Transfer, TransferDispatchInput, TransferHistory,
  TransferHistoryFilter, TransferReceptionLine, TransferStatus, User,
} from '../lib/api/client.ts';
import {
  TREATMENT_LABEL, TRANSFER_STEPS, blocksPreparation, canCancel, canDecide, canPrepare, canReceive, cancelReasonRequired,
  checkPreparation, compareDecimal, missingLines, preparationMessage, side, stepLabel, transferStatusLabel,
} from '../lib/transfers/transfers.ts';
import { CLOSED_BEFORE_DISPATCH, STEP_OF, TRANSFER_STATUS, TRANSFER_STATUSES } from '../lib/transfers/statuses.ts';
import { canDispatch, routeFor } from '../lib/logistics/routes.ts';
import { RoutePriorityBadge } from '../components/shared/RoutePriorityBadge.tsx';
import { TransferDispatchForm, emptyDispatchDraft } from './TransferDispatchForm.tsx';
import { TransferReceptionForm, initialReception } from './TransferReceptionForm.tsx';
import { TransferShortagePanel } from './TransferShortagePanel.tsx';
import type { DispatchDraft } from '../lib/logistics/routes.ts';
import { keepsIdempotencyKey, newIdempotencyKey } from '../lib/inventory/movements.ts';
import { apiDecimal, dateTime as when, decimal, decimalInputPattern, inputDecimal } from '../lib/format.ts';
import { revealEditor } from '../components/shared/revealEditor.ts';
import { useConfirmation } from '../components/shared/ConfirmDialog.tsx';
import { TransferStatusBadge } from '../components/shared/TransferStatusBadge.tsx';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Textarea } from '../components/ui/textarea';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';

const quantityPattern = (divisible: boolean) => decimalInputPattern(divisible ? 3 : 0);
const MAX_REASON = 500;
const MAX_LINES = 100;

interface LineDraft { key: string; product: string; unit: string; quantity: string }
const emptyLine = (): LineDraft => ({ key: crypto.randomUUID(), product: '', unit: '', quantity: '' });
/** `source` is the shortage a resend refers to (RF-0039-CA-02). */
interface RequestDraft { origin: string; lines: LineDraft[]; source?: { externalId: string; transferCode: string } }
const emptyRequest = (): RequestDraft => ({ origin: '', lines: [emptyLine()] });

// Every state of lib/transfers/statuses.ts is a filter option, so a new or renamed state needs no change here.
const STATUS_FILTERS: { value: TransferStatus | ''; label: string }[] = [
  { value: '', label: 'Todos los estados' },
  ...TRANSFER_STATUSES.map(status => ({ value: status, label: TRANSFER_STATUS[status].filterLabel })),
];

const problem = (reason: unknown, fallback: string) => reason instanceof TypeError
  ? 'Sin comunicación con el servidor. No se sabe si la operación quedó registrada: repítala sin cambios y no se duplicará.'
  : reason instanceof ApiError && reason.status >= 500
    ? 'El servidor no pudo completar la solicitud. Repítala sin cambios; no se duplicará.'
    : reason instanceof Error ? reason.message : fallback;

const lineCount = (count: number) => count === 1 ? '1 producto' : `${count} productos`;

const SHORTAGE_FILTERS: { value: NonNullable<TransferHistoryFilter['discrepancy']> | ''; label: string }[] = [
  { value: '', label: 'Con o sin faltantes' },
  { value: 'OPEN', label: 'Alerta de faltante abierta' },
  { value: 'RESOLVED', label: 'Alerta de faltante resuelta' },
  { value: 'ALL', label: 'Con faltantes' },
];

interface Props { client: ApiClient; user: User; onError: (reason: unknown) => void; revision: number; visible: boolean }

export function Transfers({ client, user, onError, revision, visible }: Props) {
  const operator = user.role === 'OPERATOR';
  const admin = user.role === 'ADMIN';
  const [origins, setOrigins] = useState<Branch[]>([]);
  const [catalog, setCatalog] = useState<LocalCatalog | null>(null);
  const [branches, setBranches] = useState<Branch[]>([]);
  // RF-0035: the dispatch uses the active route of the transfer's origin and destination.
  const [routes, setRoutes] = useState<Route[] | null>(null);
  const [routesError, setRoutesError] = useState('');
  const [filter, setFilter] = useState<TransferHistoryFilter>({});
  const [consulted, setConsulted] = useState<TransferHistoryFilter>({});
  const [history, setHistory] = useState<TransferHistory | null>(null);
  const [loading, setLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');
  const [selected, setSelected] = useState<Transfer | null>(null);
  const [detailError, setDetailError] = useState('');
  const [editorOpen, setEditorOpen] = useState(false);
  // The typed request and its key survive closing the form, so a request whose outcome is unknown is retried with the
  // same key instead of creating a duplicate (ARQ-007).
  const [draft, setDraft] = useState<RequestDraft>(emptyRequest);
  const [requestKey, setRequestKey] = useState(newIdempotencyKey);
  const [actionKeys, setActionKeys] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState('');
  const [actionError, setActionError] = useState('');
  const [confirmed, setConfirmed] = useState('');
  const { confirm, dialog } = useConfirmation();

  useEffect(() => { if (!visible) setConfirmed(''); }, [visible]);

  useEffect(() => {
    if (!visible) return;
    if (operator) {
      client.transferOrigins().then(setOrigins).catch(onError);
      client.localCatalog().then(setCatalog).catch(onError);
      setRoutesError('');
      client.routes().then(setRoutes).catch(reason => {
        if (reason instanceof ApiError && reason.status === 401) onError(reason);
        else setRoutesError(`No se pudieron consultar las rutas (${problem(reason, 'error desconocido')}). Vuelva a abrir la vista para reintentar.`);
      });
    }
    if (admin) client.visibleBranches().then(setBranches).catch(onError);
  }, [client, admin, operator, revision, visible]);

  async function loadHistory(target = consulted) {
    setLoading(true); setHistoryError('');
    try { setHistory(await client.transfers(target)); } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setHistoryError(reason instanceof TypeError
        ? 'Sin comunicación con el servidor. El listado mostrado puede estar desactualizado.'
        : reason instanceof Error ? reason.message : 'No se pudieron consultar las transferencias.');
    } finally { setLoading(false); }
  }
  useEffect(() => { if (visible) void loadHistory(); }, [visible, consulted, revision]);

  async function openDetail(id: string) {
    setDetailError(''); setActionError('');
    try {
      setSelected(await client.transfer(id));
      revealEditor('transfer-detail');
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setDetailError(reason instanceof Error ? reason.message : 'No se pudo consultar la transferencia.');
    }
  }

  function changeLine(key: string, patch: Partial<LineDraft>) {
    setDraft(previous => ({ ...previous, lines: previous.lines.map(line => line.key === key ? { ...line, ...patch } : line) }));
    setConfirmed('');
  }

  function openEditor() {
    setEditorOpen(true); setFormError(''); setConfirmed(''); setActionError('');
    revealEditor('transfer-editor');
  }

  function submitRequest(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const input = { origin: draft.origin, lines: draft.lines.map(line => ({
      product: line.product, unit: line.unit, quantity: apiDecimal(line.quantity) })),
      ...(draft.source ? { sourceDiscrepancy: draft.source.externalId } : {}) };
    setBusy(true); setFormError(''); setConfirmed('');
    client.requestTransfer(input, requestKey).then(transfer => {
      setConfirmed(`Transferencia ${transfer.code} solicitada a ${transfer.origin.name} con ${lineCount(transfer.lines.length)}`
        + (transfer.sourceDiscrepancy ? `, como reenvío del faltante de ${transfer.sourceDiscrepancy.transferCode}` : '')
        + '. Las existencias no cambian: el origen confirmará o ajustará las cantidades.');
      setRequestKey(newIdempotencyKey());
      setDraft(emptyRequest());
      setEditorOpen(false);
      setSelected(transfer);
      void loadHistory();
    }).catch(reason => {
      if (!keepsIdempotencyKey(reason)) setRequestKey(newIdempotencyKey());
      if (reason instanceof ApiError && reason.status === 401) { onError(reason); return; }
      setFormError(problem(reason, 'No se pudo solicitar la transferencia.'));
    }).finally(() => setBusy(false));
  }

  /**
   * Runs one transition with a key that survives a retry whose outcome is unknown. The key belongs to the exact body, so
   * editing the quantities or the reason afterwards sends a new request instead of an idempotency conflict.
   */
  async function act(transfer: Transfer, action: string, body: unknown, call: (key: string) => Promise<Transfer>,
                     done: (updated: Transfer) => string) {
    const slot = `${transfer.externalId}:${action}:${JSON.stringify(body)}`;
    const key = actionKeys[slot] ?? newIdempotencyKey();
    setActionKeys(previous => ({ ...previous, [slot]: key }));
    setBusy(true); setActionError(''); setConfirmed('');
    try {
      const updated = await call(key);
      setActionKeys(({ [slot]: _done, ...rest }) => rest);
      setSelected(updated);
      setConfirmed(done(updated));
      void loadHistory();
      return true;
    } catch (reason) {
      if (!keepsIdempotencyKey(reason)) setActionKeys(({ [slot]: _rejected, ...rest }) => rest);
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setActionError(problem(reason, 'No se pudo completar la operación.'));
      return false;
    } finally { setBusy(false); }
  }

  const prepare = (transfer: Transfer, lines: { lineNo: number; quantity: string }[]) =>
    act(transfer, 'prepare', lines, key => client.prepareTransfer(transfer.externalId, lines, key),
      updated => `Preparación de ${updated.code} guardada. El destino ya ve las cantidades a enviar; el gerente de origen debe aprobarla. `
        + 'No se reservaron existencias.');

  async function approve(transfer: Transfer) {
    const accepted = await confirm({
      title: `Aprobar ${transfer.code}`,
      description: <>Se aprobará el envío de las cantidades preparadas desde {transfer.origin.name} hacia {transfer.destination.name}.
        Las existencias no cambian hasta que el operador de origen registre el despacho, que volverá a validarlas.</>,
      confirmLabel: 'Aprobar transferencia',
    });
    if (!accepted) return;
    await act(transfer, 'approve', null, key => client.approveTransfer(transfer.externalId, key),
      updated => `${updated.code} quedó aprobada. Queda pendiente el despacho del origen.`);
  }

  const reject = (transfer: Transfer, reason: string) =>
    act(transfer, 'reject', reason, key => client.rejectTransfer(transfer.externalId, reason, key),
      updated => `${updated.code} quedó rechazada con su motivo. Las cantidades y existencias no cambiaron.`);

  const cancel = (transfer: Transfer, reason: string) =>
    act(transfer, 'cancel', reason, key => client.cancelTransfer(transfer.externalId, reason || null, key),
      updated => `${updated.code} quedó cancelada. Las existencias no cambiaron.`);

  const dispatch = (transfer: Transfer, input: TransferDispatchInput) =>
    act(transfer, 'dispatch', input, key => client.dispatchTransfer(transfer.externalId, input, key),
      updated => `${updated.code} despachada: las cantidades salieron de ${updated.origin.name} y quedan en tránsito hacia `
        + `${updated.destination.name} hasta su recepción.`);

  async function receive(transfer: Transfer, lines: TransferReceptionLine[], shortages: number) {
    const accepted = await confirm({
      title: `Confirmar la recepción de ${transfer.code}`,
      description: shortages > 0
        ? <>Faltan productos en {shortages === 1 ? '1 línea' : `${shortages} líneas`}. Lo recibido entrará a {transfer.destination.name};
          lo que falta quedará en tránsito, la transferencia quedará «Recibida con discrepancia» y se abrirá una alerta.
          No habrá una segunda recepción de este despacho.</>
        : <>Todo lo enviado entrará al inventario de {transfer.destination.name} y la transferencia quedará «Recibida completa».
          La recepción se confirma una sola vez.</>,
      confirmLabel: 'Confirmar recepción',
    });
    if (!accepted) return false;
    return act(transfer, 'receive', lines, key => client.receiveTransfer(transfer.externalId, lines, key),
      updated => updated.status === 'RECEIVED_COMPLETE'
        ? `${updated.code} recibida completa: las cantidades entraron al inventario de ${updated.destination.name}.`
        : `${updated.code} recibida con discrepancia: entró lo recibido y el faltante quedó en tránsito con una alerta para los gerentes.`);
  }

  const resolve = (transfer: Transfer, treatment: ShortageTreatment) =>
    act(transfer, 'resolve', treatment, key => client.resolveShortage(transfer.externalId, treatment, key),
      updated => `Alerta de faltante de ${updated.code} resuelta con tratamiento ${TREATMENT_LABEL[treatment].toLowerCase()}. `
        + 'El inventario, el tránsito y el estado de la transferencia no cambiaron.');

  async function writeOff(transfer: Transfer, input: TransitLossInput) {
    const accepted = await confirm({
      title: `Registrar la pérdida en tránsito de ${transfer.code}`,
      description: <>Se dará de baja todo lo que sigue en tránsito de este faltante, una sola vez y al costo congelado del despacho.
        No se puede deshacer y no cambia el inventario físico de ninguna sucursal.</>,
      confirmLabel: 'Registrar pérdida', destructive: true,
    });
    if (!accepted) return false;
    return act(transfer, 'transit-loss', input, key => client.writeOffTransit(transfer.externalId, input, key),
      updated => `Pérdida en tránsito de ${updated.code} registrada. La transferencia sigue «Recibida con discrepancia».`);
  }

  /** RF-0039-CA-02: a resend starts a new request from the same origin with the missing quantities, editable before sending. */
  function startResend(transfer: Transfer) {
    setDraft({ origin: transfer.origin.externalId, source: { externalId: transfer.discrepancy!.externalId, transferCode: transfer.code },
      lines: missingLines(transfer).map(line => ({ key: crypto.randomUUID(), product: line.productExternalId, unit: line.unit,
        quantity: inputDecimal(line.missingQuantity!) })) });
    setRequestKey(newIdempotencyKey());
    openEditor();
  }

  const requestable = catalog?.items ?? [];

  return <section aria-labelledby="transfers-title" aria-busy={loading || busy}>
    <h2 id="transfers-title" tabIndex={-1}>{admin ? 'Transferencias de la red' : 'Transferencias'}</h2>
    {dialog}
    <p>{operator
      ? 'Solicite productos a otra sucursal, prepare los envíos que le soliciten y registre el despacho de los aprobados. Ningún paso antes del despacho reserva ni mueve existencias.'
      : admin ? 'Consulte las transferencias de una sucursal o de toda la red. Las solicitan y preparan los operadores y las aprueba el gerente de origen.'
        : 'Apruebe o rechace las transferencias que su sucursal prepara para enviar y supervise las que recibe.'}</p>

    {operator && catalog && !catalog.branchActive && <p className="notice-warning">
      Sucursal inactiva. Se conserva su histórico, pero no admite nuevas transferencias.
    </p>}
    {operator && catalog?.branchActive && !editorOpen && <Button onClick={openEditor}><Plus aria-hidden="true" />Solicitar transferencia</Button>}
    {confirmed && <p role="status">{confirmed}</p>}

    {operator && editorOpen && <form id="transfer-editor" className="purchase-editor" onSubmit={submitRequest}
      aria-describedby={formError ? 'transfer-error' : undefined}>
      <fieldset disabled={busy}>
        <legend>{draft.source ? `Reenvío del faltante de ${draft.source.transferCode}` : 'Nueva solicitud'} hacia {catalog
          ? `${catalog.branchName} (${catalog.branchCode})` : 'su sucursal'}</legend>
        {draft.source && <p className="purchase-replacement-note">Esta solicitud quedará vinculada al faltante de {draft.source.transferCode}
          para su trazabilidad. Se pide desde el mismo origen; no cambia la transferencia original ni su tránsito.</p>}
        <label htmlFor="transfer-origin">Sucursal origen</label>
        <select id="transfer-origin" value={draft.origin} required disabled={!!draft.source}
          onChange={event => setDraft({ ...draft, origin: event.target.value })}>
          <option value="">Seleccione la sucursal que enviará</option>
          {origins.map(item => <option key={item.externalId} value={item.externalId}>{item.code} · {item.name}</option>)}
        </select>
        {origins.length === 0 && <p className="text-muted-foreground">No hay otras sucursales activas a las que solicitar.</p>}

        <fieldset className="purchase-lines">
          <legend>Productos solicitados</legend>
          {draft.lines.map((line, index) => {
            const item = requestable.find(product => product.productExternalId === line.product);
            const units = item?.units.filter(unit => unit.active) ?? [];
            const unit = units.find(candidate => candidate.name === line.unit);
            const number = index + 1;
            return <fieldset key={line.key} className="purchase-line transfer-line">
              <legend className="sr-only">Línea {number}</legend>
              <div>
                <label htmlFor={`transfer-product-${line.key}`}>Producto {number}</label>
                <select id={`transfer-product-${line.key}`} value={line.product} required onChange={event => {
                  const next = requestable.find(product => product.productExternalId === event.target.value);
                  changeLine(line.key, { product: event.target.value, unit: next?.units.find(candidate => candidate.base)?.name ?? '' });
                }}>
                  <option value="">Seleccione un producto habilitado</option>
                  {requestable.map(product => <option key={product.productExternalId} value={product.productExternalId}>
                    {product.sku} {product.name}</option>)}
                </select>
              </div>
              <div>
                <label htmlFor={`transfer-unit-${line.key}`}>Unidad</label>
                <select id={`transfer-unit-${line.key}`} value={line.unit} required disabled={!item}
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
              {draft.lines.length > 1 && <Button type="button" variant="ghost" size="sm" className="purchase-line-remove"
                onClick={() => setDraft(previous => ({ ...previous, lines: previous.lines.filter(other => other.key !== line.key) }))}>
                <Trash2 aria-hidden="true" />Quitar<span className="sr-only"> producto {number}</span>
              </Button>}
            </fieldset>;
          })}
          {catalog && requestable.length === 0 && <p className="text-muted-foreground">
            Su sucursal no tiene productos habilitados. El administrador los habilita desde el catálogo.
          </p>}
          <Button type="button" variant="outline" disabled={draft.lines.length >= MAX_LINES}
            onClick={() => setDraft(previous => ({ ...previous, lines: [...previous.lines, emptyLine()] }))}>
            <Plus aria-hidden="true" />Agregar producto
          </Button>
        </fieldset>
        <p className="text-muted-foreground">Cada producto debe estar habilitado también en el origen. Use una sola línea por
          producto y unidad. La solicitud no reserva existencias.</p>
        {formError && <p role="alert" id="transfer-error">{formError}</p>}
        <Button type="submit"><Send aria-hidden="true" />{busy ? 'Solicitando…' : 'Solicitar transferencia'}</Button>
        <Button variant="ghost" type="button" onClick={() => { setEditorOpen(false); setFormError(''); }}>
          <X aria-hidden="true" />Cerrar formulario
        </Button>
        {draft.source && <Button variant="ghost" type="button" onClick={() => { setDraft(emptyRequest()); setRequestKey(newIdempotencyKey()); }}>
          Descartar reenvío
        </Button>}
      </fieldset>
    </form>}

    <div className="list-heading">
      <div>
        <h3>{history?.branch ? `Transferencias de ${history.branch.name} (${history.branch.code})` : 'Transferencias de la red'}</h3>
        {history && <p>{consulted.discrepancy ? 'Alertas de faltante más recientes primero' : 'Más recientes primero'}.
          Lectura del servidor: {when(history.readAt)}</p>}
      </div>
      <Button variant="outline" disabled={loading} onClick={() => void loadHistory()}>
        <RefreshCw aria-hidden="true" />{loading ? 'Actualizando…' : 'Actualizar listado'}
      </Button>
    </div>
    <form className="purchase-filters" aria-label="Filtros de transferencias"
      onSubmit={event => { event.preventDefault(); setSelected(null); setConsulted({ ...filter }); }}>
      {admin && <div><label htmlFor="transfers-filter-branch">Sucursal</label>
        <select id="transfers-filter-branch" value={filter.branch ?? ''}
          onChange={event => setFilter({ ...filter, branch: event.target.value || undefined, direction: event.target.value ? filter.direction : undefined })}>
          <option value="">Toda la red</option>
          {branches.map(item => <option key={item.externalId} value={item.externalId}>
            {item.code} · {item.name}{item.active ? '' : ' (inactiva)'}</option>)}
        </select></div>}
      {(!admin || filter.branch) && <div><label htmlFor="transfers-filter-direction">Sentido</label>
        <select id="transfers-filter-direction" value={filter.direction ?? ''}
          onChange={event => setFilter({ ...filter, direction: (event.target.value || undefined) as TransferHistoryFilter['direction'] })}>
          <option value="">Enviadas y recibidas</option>
          <option value="INCOMING">Hacia {admin ? 'la sucursal' : 'mi sucursal'}</option>
          <option value="OUTGOING">Desde {admin ? 'la sucursal' : 'mi sucursal'}</option>
        </select></div>}
      <div><label htmlFor="transfers-filter-status">Estado</label>
        <select id="transfers-filter-status" value={filter.status ?? ''}
          onChange={event => setFilter({ ...filter, status: (event.target.value || undefined) as TransferStatus | undefined })}>
          {STATUS_FILTERS.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}
        </select></div>
      <div><label htmlFor="transfers-filter-shortage">Faltantes</label>
        <select id="transfers-filter-shortage" value={filter.discrepancy ?? ''}
          onChange={event => setFilter({ ...filter, discrepancy: (event.target.value || undefined) as TransferHistoryFilter['discrepancy'] })}>
          {SHORTAGE_FILTERS.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}
        </select></div>
      <Button type="submit" disabled={loading}><Search aria-hidden="true" />Consultar</Button>
    </form>

    {historyError && <p role="alert">{historyError}</p>}
    {loading && !history && <p role="status">Consultando transferencias…</p>}
    {history && (history.items.length === 0
      ? <p>No hay transferencias que cumplan estos filtros.{operator && catalog?.branchActive ? ' Use «Solicitar transferencia» para pedir productos a otra sucursal.' : ''}</p>
      : <HistoryTable history={history} selected={selected?.externalId} onOpen={id => void openDetail(id)} />)}

    {detailError && <p role="alert">{detailError}</p>}
    {selected && <TransferDetail key={selected.externalId} transfer={selected} user={user} busy={busy} error={actionError}
      onPrepare={lines => prepare(selected, lines)} onApprove={() => void approve(selected)}
      onReject={reason => reject(selected, reason)} onCancel={reason => cancel(selected, reason)}
      route={routes ? routeFor(routes, selected) : null} routesLoaded={routes !== null} routesError={routesError}
      onDispatch={input => dispatch(selected, input)}
      onReceive={(lines, shortages) => receive(selected, lines, shortages)}
      onResolve={treatment => resolve(selected, treatment)} onWriteOff={input => writeOff(selected, input)}
      onResend={() => startResend(selected)}
      onClose={() => setSelected(null)} />}
  </section>;
}

function HistoryTable({ history, selected, onOpen }: { history: TransferHistory; selected?: string; onOpen: (id: string) => void }) {
  return <div className="table-scroll" role="region" aria-label="Listado de transferencias" tabIndex={0}>
    <Table className="purchase-history">
      <TableCaption>Cantidades en la unidad solicitada. «A enviar» es lo que preparó el origen; «Enviado», lo despachado;
        «Recibido», lo que confirmó el destino.</TableCaption>
      <TableHeader><TableRow>
        <TableHead scope="col">Transferencia</TableHead><TableHead scope="col">Origen</TableHead>
        <TableHead scope="col">Destino</TableHead><TableHead scope="col">Estado</TableHead>
        <TableHead scope="col">Productos</TableHead><TableHead scope="col"><span className="sr-only">Acciones</span></TableHead>
      </TableRow></TableHeader>
      <TableBody>{history.items.map(transfer => <TableRow key={transfer.externalId}
        aria-current={transfer.externalId === selected ? 'true' : undefined}>
        <TableCell><span className="record-code">{transfer.code}</span><span className="cell-note">{when(transfer.requestedAt)}</span></TableCell>
        <TableCell>{transfer.origin.name}<span className="cell-note">{transfer.origin.code}</span></TableCell>
        <TableCell>{transfer.destination.name}<span className="cell-note">{transfer.destination.code}</span></TableCell>
        <TableCell><TransferStatusBadge status={transfer.status} />
          {transfer.discrepancy && <span className="cell-note">{transfer.discrepancy.status === 'OPEN' ? 'Alerta de faltante abierta'
            : `Faltante resuelto: ${TREATMENT_LABEL[transfer.discrepancy.treatment!].toLowerCase()}`}</span>}</TableCell>
        <TableCell className="cell-text">
          {transfer.lines.map(line => <span key={line.lineNo} className="block">
            {decimal(line.requestedQuantity)} {line.unit}: {line.sku} {line.productName}
            {line.receivedQuantity !== null ? <span className="cell-note">Enviado: {decimal(line.dispatchedQuantity!)},
              recibido: {decimal(line.receivedQuantity)} {line.unit}</span>
            : line.dispatchedQuantity !== null ? <span className="cell-note">Enviado: {decimal(line.dispatchedQuantity)} {line.unit}</span>
              : line.preparedQuantity !== null && <span className="cell-note">A enviar: {decimal(line.preparedQuantity)} {line.unit}</span>}
          </span>)}
        </TableCell>
        <TableCell><Button variant="outline" size="sm" onClick={() => onOpen(transfer.externalId)}>
          Ver detalle<span className="sr-only"> de {transfer.code}</span></Button></TableCell>
      </TableRow>)}</TableBody>
    </Table>
  </div>;
}

/** The transfer's place in its cycle; closed states say how and why the cycle ended. */
function TransferProgress({ transfer }: { transfer: Transfer }) {
  const closed = CLOSED_BEFORE_DISPATCH.includes(transfer.status);
  const reached = (transfer.events ?? []).map(event => event.toStatus);
  const current = TRANSFER_STEPS.indexOf(STEP_OF[transfer.status] ?? transfer.status);
  return <ol className="transfer-steps" aria-label="Avance de la transferencia">
    {TRANSFER_STEPS.map((step, index) => {
      const done = closed ? reached.includes(step) : current >= 0 && index <= current;
      const here = !closed && index === current;
      return <li key={step} data-state={here ? 'current' : done ? 'done' : 'pending'} aria-current={here ? 'step' : undefined}>
        {stepLabel(step)}<span className="sr-only">{here ? ' (estado actual)' : done ? ' (completado)' : ' (pendiente)'}</span>
      </li>;
    })}
  </ol>;
}

function TransferDetail({ transfer, user, busy, error, onPrepare, onApprove, onReject, onCancel, onClose, route, routesLoaded,
                          routesError, onDispatch, onReceive, onResolve, onWriteOff, onResend }: {
  transfer: Transfer; user: User; busy: boolean; error: string;
  onPrepare: (lines: { lineNo: number; quantity: string }[]) => Promise<boolean>; onApprove: () => void;
  onReject: (reason: string) => Promise<boolean>; onCancel: (reason: string) => Promise<boolean>; onClose: () => void;
  route: Route | null; routesLoaded: boolean; routesError: string; onDispatch: (input: TransferDispatchInput) => Promise<boolean>;
  onReceive: (lines: TransferReceptionLine[], shortages: number) => Promise<boolean>;
  onResolve: (treatment: ShortageTreatment) => Promise<boolean>; onWriteOff: (input: TransitLossInput) => Promise<boolean>;
  onResend: () => void;
}) {
  // Forms keep what was typed while the detail stays open (AGENTS.md §6).
  const [mode, setMode] = useState<'prepare' | 'reject' | 'cancel' | 'dispatch' | 'receive' | null>(null);
  // Seeded from what was sent when the form is first opened (the detail may have been open before the dispatch), then kept.
  const [received, setReceived] = useState<Record<number, string> | null>(null);
  const [quantities, setQuantities] = useState<Record<number, string>>({});
  const [reason, setReason] = useState('');
  const [dispatchDraft, setDispatchDraft] = useState<DispatchDraft>(emptyDispatchDraft);
  const [dispatchTouched, setDispatchTouched] = useState(false);
  const where = side(user, transfer);
  const showAvailability = transfer.lines.some(line => line.originAvailableQuantity !== null);
  const preparable = canPrepare(user, transfer);
  const decidable = canDecide(user, transfer);
  const cancellable = canCancel(user, transfer);
  const dispatchable = canDispatch(user, transfer);
  const dispatched = transfer.lines.some(line => line.dispatchedQuantity !== null);
  const receivable = canReceive(user, transfer);
  const arrived = transfer.arrival !== null;
  const reasonRequired = mode === 'reject' || (mode === 'cancel' && cancelReasonRequired(transfer));
  const closing = [...(transfer.events ?? [])].reverse().find(event => CLOSED_BEFORE_DISPATCH.includes(event.toStatus));

  function startPreparation() {
    setQuantities(Object.fromEntries(transfer.lines.map(line =>
      [line.lineNo, inputDecimal(line.preparedQuantity ?? line.requestedQuantity)])));
    setMode('prepare');
  }
  const checks = Object.fromEntries(transfer.lines.map(line => [line.lineNo,
    checkPreparation(apiDecimal(quantities[line.lineNo] ?? ''), line)]));
  const blocked = Object.values(checks).some(blocksPreparation);

  return <section id="transfer-detail" className="purchase-detail transfer-detail" aria-labelledby="transfer-detail-title" tabIndex={-1}>
    <header>
      <h3 id="transfer-detail-title">Transferencia {transfer.code}</h3>
      <TransferStatusBadge status={transfer.status} />
    </header>
    <p className="transfer-route">
      <span><span className="transfer-route-role">Origen{where === 'origin' ? ' (su sucursal)' : ''}</span>
        {transfer.origin.name} <span className="cell-note">{transfer.origin.code}</span></span>
      <ArrowRight aria-hidden="true" /><span className="sr-only">hacia</span>
      <span><span className="transfer-route-role">Destino{where === 'destination' ? ' (su sucursal)' : ''}</span>
        {transfer.destination.name} <span className="cell-note">{transfer.destination.code}</span></span>
    </p>
    <TransferProgress transfer={transfer} />
    {closing && <p className="notice-warning">{transferStatusLabel(closing.toStatus)} por {closing.actor.fullName} ({closing.actor.code})
      el {when(closing.occurredAt)}.{closing.reason ? ` Motivo: ${closing.reason}` : ''} Las existencias no cambiaron.</p>}
    <dl className="purchase-facts">
      <div><dt>Solicitada por</dt><dd>{transfer.requestedBy.fullName} ({transfer.requestedBy.code}), {when(transfer.requestedAt)}</dd></div>
      <div><dt>Aprobada por</dt><dd>{transfer.approval
        ? `${transfer.approval.actor.fullName} (${transfer.approval.actor.code}), ${when(transfer.approval.occurredAt)}`
        : 'Sin aprobar'}</dd></div>
      {transfer.dispatch && <>
        <div><dt>Despachada por</dt><dd>{transfer.dispatch.actor.fullName} ({transfer.dispatch.actor.code})</dd></div>
        <div><dt>Transportista</dt><dd>{transfer.dispatch.carrier}</dd></div>
        <div><dt>Salida real</dt><dd>{when(transfer.dispatch.departedAt)}</dd></div>
        <div><dt>Llegada estimada</dt><dd>{when(transfer.dispatch.estimatedArrivalAt)}</dd></div>
        <div><dt>Ruta</dt><dd><span className="record-code">{transfer.dispatch.routeCode}</span>{' '}
          <RoutePriorityBadge priority={transfer.dispatch.routePriority} />
          <span className="cell-note">Prioridad al momento del despacho.</span></dd></div>
      </>}
      {transfer.arrival && <div><dt>Recepción confirmada por</dt><dd>{transfer.arrival.actor.fullName}
        ({transfer.arrival.actor.code}), {when(transfer.arrival.occurredAt)}</dd></div>}
      {transfer.sourceDiscrepancy && <div><dt>Reenvío de</dt><dd>Faltante de <span className="record-code">
        {transfer.sourceDiscrepancy.transferCode}</span></dd></div>}
    </dl>

    <div className="table-scroll" role="region" aria-label={`Productos de ${transfer.code}`} tabIndex={0}>
      <Table className="purchase-lines-table">
        <TableCaption>Lo solicitado por el destino y lo que el origen enviará se conservan por separado.
          {showAvailability ? ' La disponibilidad es el físico actual del origen, sin reservas: el despacho la vuelve a validar.' : ''}</TableCaption>
        <TableHeader><TableRow>
          <TableHead scope="col">Producto</TableHead><TableHead className="quantity" scope="col">Solicitado</TableHead>
          <TableHead className="quantity" scope="col">A enviar</TableHead>
          {dispatched && <TableHead className="quantity" scope="col">Enviado</TableHead>}
          {arrived && <TableHead className="quantity" scope="col">Recibido</TableHead>}
          {arrived && <TableHead className="quantity" scope="col">Faltante</TableHead>}
          {showAvailability && <TableHead className="quantity" scope="col">Disponible en origen</TableHead>}
        </TableRow></TableHeader>
        <TableBody>{transfer.lines.map(line => {
          const lower = line.preparedQuantity !== null && compareDecimal(line.preparedQuantity, line.requestedQuantity)! < 0;
          return <TableRow key={line.lineNo}>
            <TableCell><span className="font-semibold">{line.sku}</span> {line.productName}</TableCell>
            <TableCell className="quantity">{decimal(line.requestedQuantity)} {line.unit}
              {line.unit !== line.baseUnitName && <span className="cell-note">{decimal(line.requestedBaseQuantity)} {line.baseUnitName}</span>}</TableCell>
            <TableCell className="quantity">{line.preparedQuantity === null
              ? <span className="text-muted-foreground">Sin preparar</span>
              : <>{decimal(line.preparedQuantity)} {line.unit}
                {lower && <span className="cell-note transfer-lower">Menos de lo solicitado</span>}</>}</TableCell>
            {dispatched && <TableCell className="quantity">{line.dispatchedQuantity === null ? '—'
              : `${decimal(line.dispatchedQuantity)} ${line.unit}`}</TableCell>}
            {arrived && <TableCell className="quantity">{decimal(line.receivedQuantity ?? '0')} {line.unit}</TableCell>}
            {arrived && <TableCell className="quantity">{compareDecimal(line.missingQuantity ?? '0', '0')! > 0
              ? <span className="transfer-lower">{decimal(line.missingQuantity!)} {line.unit}</span>
              : <span className="text-muted-foreground">Sin faltante</span>}</TableCell>}
            {showAvailability && <TableCell className="quantity">{line.originAvailableQuantity === null ? '—'
              : `${decimal(line.originAvailableQuantity)} ${line.baseUnitName}`}</TableCell>}
          </TableRow>;
        })}</TableBody>
      </Table>
    </div>

    {transfer.events && transfer.events.length > 0 && <>
      <h4>Historia de estados</h4>
      <ol className="purchase-timeline">{transfer.events.map((event, index) => <li key={index}>
        <strong>{event.fromStatus === 'PREPARING' && event.toStatus === 'PREPARING' ? 'Preparación ajustada'
          : transferStatusLabel(event.toStatus)}</strong> el {when(event.occurredAt)}, por {event.actor.fullName} ({event.actor.code})
        {event.preparation && <span className="cell-note">A enviar: {event.preparation.map(item => {
          const line = transfer.lines.find(candidate => candidate.lineNo === item.lineNo);
          return `${decimal(item.quantity)} ${line?.unit ?? ''} de ${line?.sku ?? `línea ${item.lineNo}`}`;
        }).join('; ')}</span>}
        {event.reason && <span className="cell-note">Motivo: {event.reason}</span>}
      </li>)}</ol>
    </>}
    {transfer.discrepancy && <TransferShortagePanel transfer={transfer} user={user} busy={busy} onResolve={onResolve}
      onWriteOff={onWriteOff} onResend={onResend} />}
    {error && <p role="alert">{error}</p>}

    {mode === 'receive' && received ? <TransferReceptionForm transfer={transfer} busy={busy} quantities={received} onChange={setReceived}
      onReceive={(lines, shortages) => onReceive(lines, shortages).then(done => { if (done) setMode(null); return done; })}
      onBack={() => setMode(null)} />
    : mode === 'dispatch' ? <TransferDispatchForm transfer={transfer} route={route} routesLoaded={routesLoaded}
      routesError={routesError} busy={busy} draft={dispatchDraft} onChange={setDispatchDraft} touched={dispatchTouched}
      onTouched={() => setDispatchTouched(true)}
      onDispatch={input => onDispatch(input).then(done => { if (done) setMode(null); return done; })}
      onBack={() => setMode(null)} />
    : mode === 'prepare' ? <form className="purchase-cancel transfer-prepare" aria-labelledby="transfer-prepare-title" onSubmit={event => {
      event.preventDefault();
      if (blocked) return;
      void onPrepare(transfer.lines.map(line => ({ lineNo: line.lineNo, quantity: apiDecimal(quantities[line.lineNo]) })))
        .then(done => { if (done) setMode(null); });
    }}>
      <fieldset disabled={busy}>
        <legend id="transfer-prepare-title">Preparar el envío de {transfer.code}</legend>
        <p>Confirme o reduzca la cantidad de cada producto; use 0 si no puede enviarlo. No puede enviar más de lo solicitado.
          Preparar no reserva ni descuenta existencias.</p>
        {transfer.lines.map(line => {
          const check = checks[line.lineNo];
          const blocking = blocksPreparation(check);
          const message = preparationMessage(check, line);
          const hint = `transfer-prepare-hint-${line.lineNo}`;
          return <div key={line.lineNo} className="transfer-prepare-line">
            <label htmlFor={`transfer-prepare-${line.lineNo}`}>{line.sku} {line.productName} ({line.unit})</label>
            <Input id={`transfer-prepare-${line.lineNo}`} value={quantities[line.lineNo] ?? ''} required 
              inputMode={line.unitDivisible ? 'decimal' : 'numeric'} pattern={quantityPattern(line.unitDivisible)}
              title={line.unitDivisible ? 'Hasta 3 decimales con coma, por ejemplo 2,5.' : 'Número entero.'}
              aria-describedby={hint} aria-invalid={blocking || undefined}
              onChange={event => setQuantities(previous => ({ ...previous, [line.lineNo]: event.target.value }))} />
            <p id={hint} className={blocking ? 'field-error' : message ? 'transfer-warning' : 'text-muted-foreground'}>
              {message ?? `Solicitado: ${decimal(line.requestedQuantity)} ${line.unit}.`
                    + (line.originAvailableQuantity !== null ? ` Disponible: ${decimal(line.originAvailableQuantity)} ${line.baseUnitName}.` : '')}
            </p>
          </div>;
        })}
        <Button type="submit" disabled={blocked}><ClipboardCheck aria-hidden="true" />{busy ? 'Guardando…' : 'Guardar preparación'}</Button>
        <Button type="button" variant="ghost" onClick={() => setMode(null)}>Volver sin guardar</Button>
      </fieldset>
    </form> : mode === 'reject' || mode === 'cancel' ? <form className="purchase-cancel" aria-labelledby="transfer-reason-title" onSubmit={event => {
      event.preventDefault();
      const action = mode === 'reject' ? onReject : onCancel;
      void action(reason.trim()).then(done => { if (done) { setMode(null); setReason(''); } });
    }}>
      <fieldset disabled={busy}>
        <legend id="transfer-reason-title">{mode === 'reject' ? 'Rechazar' : 'Cancelar'} {transfer.code}</legend>
        <p>{mode === 'reject'
          ? 'La transferencia quedará rechazada con su motivo y responsable. No se aprueba, no se despacha y sus cantidades no cambian.'
          : transfer.status === 'APPROVED'
            ? 'La transferencia aprobada quedará cancelada antes de su despacho. El motivo es obligatorio.'
            : 'La solicitud quedará cancelada y se conservará en el histórico.'} Las existencias no cambian y no se puede deshacer.</p>
        <label htmlFor="transfer-reason">Motivo{reasonRequired ? '' : ' (opcional)'}</label>
        <Textarea id="transfer-reason" value={reason} maxLength={MAX_REASON} rows={2} required={reasonRequired}
          aria-describedby="transfer-reason-hint" onChange={event => setReason(event.target.value)} />
        <p id="transfer-reason-hint" className="text-muted-foreground">Hasta {MAX_REASON} caracteres. Queda en la historia de estados.</p>
        <Button type="submit" variant="destructive">{mode === 'reject'
          ? <><XCircle aria-hidden="true" />{busy ? 'Rechazando…' : 'Confirmar rechazo'}</>
          : <><Ban aria-hidden="true" />{busy ? 'Cancelando…' : 'Confirmar cancelación'}</>}</Button>
        <Button type="button" variant="ghost" onClick={() => { setMode(null); setReason(''); }}>Volver</Button>
      </fieldset>
    </form> : <div className="purchase-actions">
      {preparable && <Button disabled={busy} onClick={startPreparation}><ClipboardCheck aria-hidden="true" />
        {transfer.status === 'PREPARING' ? 'Ajustar preparación' : 'Preparar envío'}</Button>}
      {dispatchable && <Button disabled={busy} onClick={() => setMode('dispatch')}><Truck aria-hidden="true" />Registrar despacho</Button>}
      {receivable && <Button disabled={busy} onClick={() => { setReceived(previous => previous ?? initialReception(transfer)); setMode('receive'); }}><PackageCheck aria-hidden="true" />Confirmar recepción</Button>}
      {decidable && <Button disabled={busy} onClick={onApprove}><CheckCircle2 aria-hidden="true" />Aprobar transferencia</Button>}
      {decidable && <Button variant="outline" disabled={busy} onClick={() => setMode('reject')}><XCircle aria-hidden="true" />Rechazar</Button>}
      {cancellable && <Button variant="outline" disabled={busy} onClick={() => setMode('cancel')}><Ban aria-hidden="true" />Cancelar transferencia</Button>}
      <Button variant="ghost" onClick={onClose}><X aria-hidden="true" />Cerrar detalle</Button>
    </div>}
    {where === 'destination' && transfer.status === 'REQUESTED' && <p className="text-muted-foreground">
      Esperando que {transfer.origin.name} revise su disponibilidad y prepare el envío.</p>}
    {where === 'origin' && user.role === 'OPERATOR' && transfer.status === 'PREPARING' && <p className="text-muted-foreground">
      Puede ajustar la preparación hasta que el gerente de la sucursal la apruebe o la rechace.</p>}
    {where === 'destination' && transfer.status === 'IN_TRANSIT' && <p className="text-muted-foreground">
      En tránsito: su inventario no cambia hasta que se confirme la recepción.</p>}
    {where === 'origin' && transfer.status === 'IN_TRANSIT' && <p className="text-muted-foreground">
      En tránsito hacia {transfer.destination.name}: el destino confirma la recepción.</p>}
  </section>;
}
