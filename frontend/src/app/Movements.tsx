import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Plus, RefreshCw, Save, Search, X } from 'lucide-react';
import { ApiClient, ApiError } from '../lib/api/client.ts';
import type {
  Branch, LocalCatalog, ManualMovement, MovementDirection, MovementHistory, MovementHistoryEntry, MovementKind, User,
} from '../lib/api/client.ts';
import {
  DIFFERENCE_LABELS, asksUnitCost, keepsIdempotencyKey, kindLabel, kindsFor, newIdempotencyKey, referenceCandidates, referenceLabel,
  signedQuantity,
} from '../lib/inventory/movements.ts';
import { revealEditor } from '../components/shared/revealEditor.ts';
import { apiDecimal, dateTime as when, decimal, decimalInputPattern } from '../lib/format.ts';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Textarea } from '../components/ui/textarea';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';

// Indivisible units admit whole numbers only; quantities keep at most three decimals (RF-0013). Decimals are typed
// with a comma and sent to the API with a dot.
const quantityPattern = (divisible: boolean) => decimalInputPattern(divisible ? 3 : 0);
const costPattern = decimalInputPattern(6, 27);

interface Draft {
  kind: MovementKind | ''; direction: MovementDirection; product: string; unit: string; reference: string;
  quantity: string; unitCost: string; reason: string;
}
const emptyDraft: Draft = { kind: '', direction: 'OUT', product: '', unit: '', reference: '', quantity: '', unitCost: '', reason: '' };

interface Props { client: ApiClient; user: User; onError: (reason: unknown) => void; revision: number; visible: boolean }

export function Movements({ client, user, onError, revision, visible }: Props) {
  const kinds = kindsFor(user.role);
  const admin = user.role === 'ADMIN';
  const [branches, setBranches] = useState<Branch[]>([]);
  const [branch, setBranch] = useState(user.branchExternalId ?? '');
  const [consulted, setConsulted] = useState(user.branchExternalId ?? '');
  const [history, setHistory] = useState<MovementHistory | null>(null);
  const [catalog, setCatalog] = useState<LocalCatalog | null>(null);
  const [loading, setLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');
  const [editorOpen, setEditorOpen] = useState(false);
  const [draft, setDraft] = useState<Draft>(emptyDraft);
  // One key per submission attempt; it survives a retry whose outcome is unknown (ARQ-007).
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState('');
  const [confirmed, setConfirmed] = useState<ManualMovement | null>(null);

  // A success notice belongs to the moment it happened: it is not shown again when the user comes back.
  useEffect(() => { if (!visible) setConfirmed(null); }, [visible]);

  useEffect(() => {
    if (admin) client.visibleBranches().then(setBranches).catch(onError);
  }, [client, admin, revision]);

  async function loadHistory(target = consulted) {
    if (!target) return;
    setLoading(true); setHistoryError('');
    try {
      const [movements, local] = await Promise.all([
        client.movementHistory(target), kinds.length > 0 ? client.localCatalog() : Promise.resolve(null),
      ]);
      setHistory(movements);
      setCatalog(local);
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setHistoryError(reason instanceof TypeError
        ? 'Sin comunicación con el servidor. El historial mostrado puede estar desactualizado.'
        : reason instanceof Error ? reason.message : 'No se pudo consultar el historial.');
    } finally { setLoading(false); }
  }
  useEffect(() => { if (visible && consulted) void loadHistory(); }, [visible, consulted, revision]);

  const option = kinds.find(item => item.kind === draft.kind);
  const direction: MovementDirection = option?.direction ?? draft.direction;
  const candidates = draft.kind ? referenceCandidates(history?.items ?? [], draft.kind) : [];
  const reference = candidates.find(item => item.externalId === draft.reference);
  const referenced = draft.kind === 'ENTRY_CORRECTION' || (draft.kind === 'SALE_RETURN' && draft.reference !== '');
  const product = catalog?.items.find(item => item.productExternalId === draft.product);
  const units = product?.units.filter(unit => unit.active) ?? [];
  const unit = units.find(item => item.name === draft.unit);
  const showCost = draft.kind !== '' && asksUnitCost(draft.kind, direction, referenced, product?.physicalQuantity);
  // A compensation uses the unit of its original movement; the server applies that unit's divisibility.
  const quantityDivisible = referenced ? true : unit?.divisible ?? false;

  function change(patch: Partial<Draft>) {
    setDraft(previous => ({ ...previous, ...patch }));
    setConfirmed(null);
  }

  function open() {
    setEditorOpen(true); setFormError(''); setConfirmed(null);
    if (!draft.kind && kinds.length > 0) change({ kind: kinds[0].kind });
    revealEditor('movement-editor');
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!draft.kind) return;
    const input = {
      kind: draft.kind, quantity: apiDecimal(draft.quantity), reason: draft.reason.trim(),
      ...(option?.direction === null ? { direction } : {}),
      ...(referenced ? { reference: draft.reference } : { product: draft.product, unit: draft.unit }),
      ...(showCost && draft.unitCost.trim() ? { unitCost: apiDecimal(draft.unitCost) } : {}),
    };
    setBusy(true); setFormError(''); setConfirmed(null);
    client.registerMovement(input, idempotencyKey).then(movement => {
      setConfirmed(movement);
      setIdempotencyKey(newIdempotencyKey());
      setDraft(previous => ({ ...emptyDraft, kind: previous.kind, direction: previous.direction }));
      void loadHistory();
    }).catch(reason => {
      if (!keepsIdempotencyKey(reason)) setIdempotencyKey(newIdempotencyKey());
      if (reason instanceof ApiError && reason.status === 401) { onError(reason); return; }
      setFormError(reason instanceof TypeError
        ? 'Sin comunicación con el servidor. No se sabe si el movimiento quedó registrado: envíelo de nuevo sin cambios '
          + 'y no se duplicará. Si cambia los datos y el envío anterior sí se registró, el servidor lo rechazará: '
          + 'actualice el historial antes de registrar otro movimiento.'
        : reason instanceof ApiError && reason.status >= 500
          ? 'El servidor no pudo completar la solicitud. Envíela de nuevo sin cambios; no se duplicará.'
          : reason instanceof Error ? reason.message : 'No se pudo registrar el movimiento.');
    }).finally(() => setBusy(false));
  }

  const heading = admin ? 'Movimientos de inventario de la red' : 'Movimientos de inventario';
  return <section aria-labelledby="movements-title" aria-busy={loading || busy}>
    <h2 id="movements-title" tabIndex={-1}>{heading}</h2>
    <p>{admin
      ? 'Consulte el historial auditable de cualquier sucursal. Los movimientos los registran el operador y el gerente de cada sucursal.'
      : 'Cada ingreso o retiro queda registrado con fecha, responsable, motivo y cantidad. Los movimientos confirmados no se editan: un error se corrige con un movimiento vinculado.'}</p>

    {admin && <div>
      <label htmlFor="movements-branch">Sucursal</label>
      <select id="movements-branch" value={branch} disabled={loading} onChange={event => setBranch(event.target.value)}>
        <option value="">Seleccione una sucursal</option>
        {branches.map(item => <option key={item.externalId} value={item.externalId}>
          {item.code} · {item.name}{item.active ? '' : ' (inactiva: solo consultas)'}
        </option>)}
      </select>
      <Button disabled={loading || !branch} onClick={() => {
        if (branch === consulted) void loadHistory(); else setConsulted(branch);
      }}><Search aria-hidden="true" />Consultar historial</Button>
    </div>}

    {kinds.length > 0 && catalog && !catalog.branchActive && <p className="notice-warning">
      Sucursal inactiva. Se conserva su historial, pero no admite nuevos movimientos.
    </p>}
    {kinds.length > 0 && catalog?.branchActive && !editorOpen &&
      <Button onClick={open}><Plus aria-hidden="true" />Nuevo movimiento</Button>}
    {confirmed && <p role="status">
      Movimiento registrado: {kindLabel(confirmed.kind)} de {signedQuantity(confirmed)} {confirmed.unit} de {confirmed.sku}.
      Existencias resultantes: {decimal(confirmed.resultingPhysicalQuantity)} {confirmed.baseUnitName}.
    </p>}

    {editorOpen && kinds.length > 0 && <form id="movement-editor" onSubmit={submit} aria-describedby={formError ? 'movement-error' : undefined}>
      <fieldset disabled={busy}>
        <legend>Registrar movimiento en {catalog ? `${catalog.branchName} (${catalog.branchCode})` : 'su sucursal'}</legend>
        <fieldset className="choice-list">
          <legend>Tipo de movimiento</legend>
          {kinds.map(item => <label key={item.kind} className="choice">
            <input type="radio" name="kind" value={item.kind} checked={draft.kind === item.kind}
              onChange={() => change({ kind: item.kind, reference: '', unitCost: '' })} />
            <span><strong>{item.label}</strong><span>{item.description}</span></span>
          </label>)}
        </fieldset>

        {option && option.direction === null && <fieldset className="choice-list choice-inline">
          <legend>Efecto en las existencias</legend>
          {(['IN', 'OUT'] as const).map(value => <label key={value} className="choice">
            <input type="radio" name="direction" value={value} checked={draft.direction === value}
              onChange={() => change({ direction: value, reference: '', unitCost: '' })} />
            <span><strong>{value === 'IN' ? 'Incrementa' : 'Disminuye'}</strong></span>
          </label>)}
        </fieldset>}

        {(draft.kind === 'ENTRY_CORRECTION' || draft.kind === 'SALE_RETURN') && <>
          <label htmlFor="movement-reference">
            {draft.kind === 'ENTRY_CORRECTION' ? 'Entrada que corrige' : 'Venta de referencia'}
          </label>
          <select id="movement-reference" value={draft.reference} required={draft.kind === 'ENTRY_CORRECTION'}
            onChange={event => change({ reference: event.target.value, unitCost: '' })}>
            <option value="">{draft.kind === 'ENTRY_CORRECTION' ? 'Seleccione la entrada' : 'Sin venta de referencia'}</option>
            {candidates.map(item => <option key={item.externalId} value={item.externalId}>{referenceLabel(item)}</option>)}
          </select>
          {candidates.length === 0 && <p className="text-muted-foreground">
            {draft.kind === 'ENTRY_CORRECTION'
              ? 'No hay entradas con unidades por corregir en el historial consultado. Si faltaron unidades, regístrelas con un ajuste por conteo o pida al operador un nuevo ingreso.'
              : 'No hay ventas con unidades por devolver. Registre la devolución sin referencia, con su motivo.'}
          </p>}
        </>}

        {referenced ? reference && <p>
          Producto: <strong>{reference.sku}</strong> {reference.productName}. La cantidad se expresa en {reference.unit}, la unidad
          del movimiento original, y se valora a su costo histórico.
        </p> : <>
          <label htmlFor="movement-product">Producto</label>
          <select id="movement-product" value={draft.product} required
            onChange={event => {
              const next = catalog?.items.find(item => item.productExternalId === event.target.value);
              change({ product: event.target.value, unit: next?.units.find(item => item.base)?.name ?? '', unitCost: '' });
            }}>
            <option value="">Seleccione un producto habilitado</option>
            {catalog?.items.map(item => <option key={item.productExternalId} value={item.productExternalId}>
              {item.sku} {item.name}: {decimal(item.physicalQuantity)} {item.baseUnitName} en existencia
            </option>)}
          </select>
          {catalog && catalog.items.length === 0 && <p className="text-muted-foreground">
            La sucursal no tiene productos habilitados. El administrador los habilita desde el catálogo.
          </p>}
          {product && <>
            <label htmlFor="movement-unit">Unidad</label>
            <select id="movement-unit" value={draft.unit} required onChange={event => change({ unit: event.target.value })}>
              {units.map(item => <option key={item.name} value={item.name}>
                {item.base ? `${item.name} (unidad base)` : `${item.name} (${decimal(item.factor)} ${product.baseUnitName})`}
              </option>)}
            </select>
          </>}
        </>}

        <label>Cantidad{unit && !referenced ? ` en ${unit.name}` : reference ? ` en ${reference.unit}` : ''}
          <Input value={draft.quantity} onChange={event => change({ quantity: event.target.value })} required
            inputMode={quantityDivisible ? 'decimal' : 'numeric'} pattern={quantityPattern(quantityDivisible)}
            title={quantityDivisible ? 'Hasta 3 decimales con coma, por ejemplo 2,5.' : 'Número entero.'} /></label>
        {draft.kind === 'ENTRY_CORRECTION' && reference && <p className="text-muted-foreground">
          Puede descontar hasta {reference.pendingQuantity === null ? '' : decimal(reference.pendingQuantity)} {reference.baseUnitName} de esta entrada.
        </p>}

        {showCost && product && <>
          <label>Costo unitario justificado por {product.baseUnitName}
            <Input value={draft.unitCost} onChange={event => change({ unitCost: event.target.value })} required
              inputMode="decimal" pattern={costPattern} title="Decimal no negativo con hasta 6 decimales y coma, por ejemplo 1500,25." /></label>
          <p className="text-muted-foreground">
            La sucursal no tiene existencias de este producto, así que no hay costo promedio que aplicar. Indique el costo que
            justifica el ingreso; con existencias se usa el promedio vigente.
          </p>
        </>}

        <label htmlFor="movement-reason">Motivo</label>
        <Textarea id="movement-reason" value={draft.reason} onChange={event => change({ reason: event.target.value })}
          required maxLength={500} rows={3} />

        {formError && <p role="alert" id="movement-error">{formError}</p>}
        <Button type="submit" disabled={!draft.kind}><Save aria-hidden="true" />{busy ? 'Registrando…' : 'Registrar movimiento'}</Button>
        <Button variant="ghost" type="button" onClick={() => { setEditorOpen(false); setFormError(''); }}>
          <X aria-hidden="true" />Cerrar formulario
        </Button>
      </fieldset>
    </form>}

    {consulted && <div className="list-heading">
      <div>
        <h3>Historial de movimientos{history ? ` de ${history.branch.name} (${history.branch.code})` : ''}</h3>
        {history && <p>Más recientes primero. Lectura del servidor: {when(history.readAt)}</p>}
      </div>
      <Button variant="outline" disabled={loading} onClick={() => void loadHistory()}>
        <RefreshCw aria-hidden="true" />{loading ? 'Actualizando…' : 'Actualizar historial'}
      </Button>
    </div>}
    {historyError && <p role="alert">{historyError}</p>}
    {consulted && loading && !history && <p role="status">Consultando historial…</p>}
    {history && (history.items.length === 0
      ? <p>Todavía no hay movimientos registrados en esta sucursal.{kinds.length > 0 ? ' Use «Nuevo movimiento» para registrar el primero.' : ''}</p>
      : <HistoryTable history={history} />)}
  </section>;
}

function HistoryTable({ history }: { history: MovementHistory }) {
  const valued = history.items.some(item => item.valuation !== null);
  const byId = new Map(history.items.map(item => [item.externalId, item]));
  const referenceText = (item: MovementHistoryEntry) => {
    if (!item.referenceExternalId) return '';
    const original = byId.get(item.referenceExternalId);
    return original ? `${kindLabel(original.kind)} del ${when(original.occurredAt)}` : `Movimiento ${item.referenceExternalId}`;
  };
  return <div className="table-scroll" role="region" aria-label={`Historial de movimientos de ${history.branch.name}`} tabIndex={0}>
    <Table className="movement-history">
      <TableCaption>
        Cantidades con signo: + incrementa y − disminuye las existencias. El equivalente se expresa en la unidad base.
        {valued ? ' Los importes son el efecto en el valor del inventario, redondeados a 2 decimales.' : ''}
      </TableCaption>
      <TableHeader><TableRow>
        <TableHead scope="col">Fecha y hora</TableHead><TableHead scope="col">Tipo</TableHead>
        <TableHead scope="col">Producto</TableHead><TableHead className="quantity" scope="col">Cantidad</TableHead>
        <TableHead scope="col">Motivo</TableHead><TableHead scope="col">Responsable</TableHead>
        <TableHead scope="col">Vínculo</TableHead>
        {valued && <><TableHead className="quantity" scope="col">Costo unitario</TableHead>
          <TableHead className="quantity" scope="col">Efecto en valor</TableHead></>}
      </TableRow></TableHeader>
      <TableBody>{history.items.map(item => <TableRow key={item.externalId}>
        <TableCell className="whitespace-nowrap">{when(item.occurredAt)}</TableCell>
        <TableCell>{kindLabel(item.kind)}</TableCell>
        <TableCell><span className="font-semibold">{item.sku}</span> {item.productName}</TableCell>
        <TableCell className="quantity">
          {signedQuantity(item)} {item.unit}
          {item.commercialQuantity !== item.baseQuantity || item.unit !== item.baseUnitName
            ? <span className="cell-note">{decimal(item.baseQuantity)} {item.baseUnitName}</span> : null}
        </TableCell>
        <TableCell className="cell-text">{item.reason}</TableCell>
        <TableCell>{item.responsible.fullName}<span className="cell-note">{item.responsible.code}</span></TableCell>
        <TableCell className="cell-text">
          {referenceText(item)}
          {item.pendingQuantity !== null && <span className="cell-note">Pendiente de compensar: {decimal(item.pendingQuantity)} {item.baseUnitName}</span>}
        </TableCell>
        {valued && <>
          <TableCell className="quantity">{item.valuation && decimal(item.valuation.unitCost)}</TableCell>
          <TableCell className="quantity">
            {item.valuation && decimal(item.valuation.totalAmount)}
            {item.valuation?.differenceKind && <span className="cell-note">
              Incluye {DIFFERENCE_LABELS[item.valuation.differenceKind] ?? item.valuation.differenceKind}: {item.valuation.exactDifferenceAmount && decimal(item.valuation.exactDifferenceAmount)}
              {' '}(al costo aplicado: {decimal(item.valuation.baseAmount)})
            </span>}
          </TableCell>
        </>}
      </TableRow>)}</TableBody>
    </Table>
  </div>;
}
