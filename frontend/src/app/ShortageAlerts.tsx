import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, RefreshCw, X } from 'lucide-react';
import { ApiClient, ApiError } from '../lib/api/client.ts';
import type { ShortageTreatment, Transfer, TransferHistory, TransferHistoryFilter, User } from '../lib/api/client.ts';
import { TREATMENT_LABEL, canResolve } from '../lib/transfers/transfers.ts';
import { keepsIdempotencyKey, newIdempotencyKey } from '../lib/inventory/movements.ts';
import { dateTime as when, sentence } from '../lib/format.ts';
import {
  INVENTORY_NETWORK_MODE, INVENTORY_POLL_MS, INVENTORY_TIMEOUT_MS, connectionFailure,
} from '../lib/inventory/freshness.ts';
import { ShortageBadge, ShortageLines, ShortageResolveForm } from './TransferShortagePanel.tsx';
import { TransferStatusBadge } from '../components/shared/TransferStatusBadge.tsx';
import { Button } from '../components/ui/button';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';

type View = NonNullable<TransferHistoryFilter['discrepancy']>;
const VIEWS: { value: View; label: string }[] = [
  { value: 'OPEN', label: 'Abiertas' }, { value: 'RESOLVED', label: 'Resueltas' }, { value: 'ALL', label: 'Todas' },
];

/**
 * RF-0038: shortage alerts are read with the same polling as the restock alerts (ARQ PEN-003). The open ones of the
 * default scope share their query with the shell's notice and counter, so they are polled only once.
 */
export function shortageAlertsQuery(client: ApiClient, view: View, enabled: boolean) {
  return {
    queryKey: ['shortage-alerts', view],
    queryFn: ({ signal }: { signal: AbortSignal }) => client.transfers({ discrepancy: view },
      AbortSignal.any([signal, AbortSignal.timeout(INVENTORY_TIMEOUT_MS)])),
    enabled,
    refetchInterval: (query: { state: { error: unknown } }) =>
      enabled && (query.state.error === null || connectionFailure(query.state.error)) ? INVENTORY_POLL_MS : false,
    refetchIntervalInBackground: false,
    refetchOnReconnect: 'always' as const,
    networkMode: INVENTORY_NETWORK_MODE,
    retry: false,
  };
}

/**
 * RF-0038: the shortage alerts of transfers, apart from the restock alerts (they never resolve by threshold). Managers of
 * the origin or destination mark them «Resuelto» with a treatment; administrators supervise the network. The transit loss
 * and the resend are registered from the transfer detail.
 */
export function ShortageAlerts({ client, user, onError, revision, visible }: {
  client: ApiClient; user: User; onError: (reason: unknown) => void; revision: number; visible: boolean;
}) {
  const [view, setView] = useState<View>('OPEN');
  const queries = useQueryClient();
  const query = useQuery<TransferHistory>(shortageAlertsQuery(client, view, visible));
  const alerts = query.data ?? null;
  const loading = query.isFetching;
  const [resolving, setResolving] = useState<string | null>(null);
  const [keys, setKeys] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState('');
  const [confirmed, setConfirmed] = useState('');

  useEffect(() => {
    if (query.error instanceof ApiError && query.error.status === 401) onError(query.error);
  }, [query.errorUpdatedAt]);
  const error = !query.isError || (query.error instanceof ApiError && query.error.status === 401) ? ''
    : connectionFailure(query.error) && !(query.error instanceof ApiError)
      ? 'Sin comunicación con el servidor. El listado mostrado puede estar desactualizado.'
      : `${query.error instanceof Error ? query.error.message : 'No se pudieron consultar las alertas de faltante.'}`
        + (alerts ? ' El listado mostrado puede estar desactualizado.' : '');
  // Every shortage listing (this view and the shell's notices) is read again after a local change.
  const load = () => queries.invalidateQueries({ queryKey: ['shortage-alerts'] });
  useEffect(() => { if (visible && revision > 0) void load(); }, [revision]);
  useEffect(() => { if (!visible) setConfirmed(''); }, [visible]);

  async function resolve(transfer: Transfer, treatment: ShortageTreatment) {
    const slot = `${transfer.externalId}:${treatment}`;
    const key = keys[slot] ?? newIdempotencyKey();
    setKeys(previous => ({ ...previous, [slot]: key }));
    setBusy(true); setActionError(''); setConfirmed('');
    try {
      const updated = await client.resolveShortage(transfer.externalId, treatment, key);
      setKeys(({ [slot]: _done, ...rest }) => rest);
      setResolving(null);
      setConfirmed(`Alerta de faltante de ${updated.code} resuelta con tratamiento ${TREATMENT_LABEL[treatment].toLowerCase()}. `
        + 'El inventario, el tránsito y el estado de la transferencia no cambiaron.');
      void load();
      return true;
    } catch (reason) {
      if (!keepsIdempotencyKey(reason)) setKeys(({ [slot]: _rejected, ...rest }) => rest);
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setActionError(reason instanceof TypeError
        ? 'Sin comunicación con el servidor. No se sabe si la alerta quedó resuelta: repita sin cambios y no se duplicará.'
        : reason instanceof Error ? reason.message : 'No se pudo resolver la alerta.');
      return false;
    } finally { setBusy(false); }
  }

  const target = alerts?.items.find(item => item.externalId === resolving) ?? null;
  const network = alerts !== null && alerts.branch === null;

  return <section className="shortage-alerts" aria-labelledby="shortage-alerts-title" aria-busy={loading || busy}>
    <h2 id="shortage-alerts-title" tabIndex={-1}>Faltantes de transferencias</h2>
    <p>Se abre una alerta cuando una recepción llega con menos de lo enviado. Los gerentes de origen o destino la marcan
      «Resuelto» con su tratamiento; eso no mueve inventario ni cambia la transferencia. La pérdida en tránsito y el reenvío
      se registran desde el detalle de la transferencia.</p>
    <div className="list-heading">
      <fieldset className="choice-list choice-inline">
        <legend>Mostrar</legend>
        {VIEWS.map(item => <label key={item.value} className="choice">
          <input type="radio" name="shortage-view" checked={view === item.value} onChange={() => { setView(item.value); setResolving(null); }} />
          <span><strong>{item.label}</strong></span></label>)}
      </fieldset>
      <Button variant="outline" disabled={loading} onClick={() => void load()}>
        <RefreshCw aria-hidden="true" />{loading ? 'Actualizando…' : 'Actualizar listado'}
      </Button>
    </div>
    {confirmed && <p role="status">{confirmed}</p>}
    {error && <p role="alert">{error}</p>}
    {loading && !alerts && <p role="status">Consultando alertas de faltante…</p>}
    {alerts && (alerts.items.length === 0
      ? <p>{view === 'OPEN' ? 'No hay faltantes pendientes de resolver.' : view === 'RESOLVED' ? 'No hay alertas de faltante resueltas.'
        : 'No se han registrado faltantes en recepciones.'}</p>
      : <div className="table-scroll" role="region" aria-label="Alertas de faltante" tabIndex={0}>
        <Table className="purchase-history">
          <TableCaption>Más recientes primero. {sentence(`Lectura del servidor: ${when(alerts.readAt)}`)}</TableCaption>
          <TableHeader><TableRow>
            <TableHead scope="col">Transferencia</TableHead><TableHead scope="col">Origen y destino</TableHead>
            <TableHead scope="col">Faltante</TableHead><TableHead scope="col">Alerta</TableHead>
            <TableHead scope="col"><span className="sr-only">Acciones</span></TableHead>
          </TableRow></TableHeader>
          <TableBody>{alerts.items.map(transfer => <TableRow key={transfer.externalId}
            aria-current={transfer.externalId === resolving ? 'true' : undefined}>
            <TableCell><span className="record-code">{transfer.code}</span>
              <span className="cell-note">Abierta {when(transfer.discrepancy!.openedAt)}</span>
              <TransferStatusBadge status={transfer.status} /></TableCell>
            <TableCell>{transfer.origin.name} hacia {transfer.destination.name}
              {network && <span className="cell-note">{transfer.origin.code} hacia {transfer.destination.code}</span>}</TableCell>
            <TableCell className="cell-text"><ShortageLines transfer={transfer} /></TableCell>
            <TableCell><ShortageBadge transfer={transfer} />
              {transfer.discrepancy!.status === 'RESOLVED' && <span className="cell-note">
                {TREATMENT_LABEL[transfer.discrepancy!.treatment!]}: {transfer.discrepancy!.resolvedBy!.fullName},
                {' '}{when(transfer.discrepancy!.resolvedAt!)}</span>}
              {transfer.discrepancy!.transitLoss && <span className="cell-note">Pérdida en tránsito registrada</span>}</TableCell>
            <TableCell>{canResolve(user, transfer) && <Button variant="outline" size="sm" disabled={busy}
              onClick={() => { setResolving(transfer.externalId); setActionError(''); }}>
              <CheckCircle2 aria-hidden="true" />Resolver<span className="sr-only"> la alerta de {transfer.code}</span></Button>}</TableCell>
          </TableRow>)}</TableBody>
        </Table>
      </div>)}
    {target && <div className="transfer-shortage">
      <h3>Resolver la alerta de {target.code}</h3>
      <ShortageLines transfer={target} />
      <ShortageResolveForm key={target.externalId} transfer={target} busy={busy} onResolve={treatment => resolve(target, treatment)}
        idPrefix="alerts" />
      {actionError && <p role="alert">{actionError}</p>}
      <Button variant="ghost" onClick={() => setResolving(null)}><X aria-hidden="true" />Cerrar sin resolver</Button>
    </div>}
  </section>;
}
