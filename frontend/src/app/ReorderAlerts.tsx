import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { RefreshCw } from 'lucide-react';
import { ApiClient } from '../lib/api/client.ts';
import type { Branch, ReorderAlerts as Snapshot, User } from '../lib/api/client.ts';
import { Button } from '../components/ui/button';
import { RestockBadge } from '../components/shared/RestockBadge.tsx';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';
import {
  INVENTORY_NETWORK_MODE, INVENTORY_POLL_MS, INVENTORY_TIMEOUT_MS, connectionFailure, freshness,
} from '../lib/inventory/freshness.ts';
import { clockTime as time, dateTime as moment, decimal } from '../lib/format.ts';

/**
 * RF-0020: alerts are read with the same polling as inventory (ARQ PEN-003). The default scope (own branch for managers,
 * whole network for administrators, active alerts) shares its query with the shell notice, so it is polled only once.
 */
export function reorderAlertsQuery(client: ApiClient, branch: string, history: boolean, enabled: boolean) {
  return {
    queryKey: ['reorder-alerts', branch, history],
    queryFn: ({ signal }: { signal: AbortSignal }) => client.reorderAlerts({ branch: branch || undefined, history },
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

export function ReorderAlerts({ client, user, onError, revision, visible }: {
  client: ApiClient; user: User; onError: (reason: unknown) => void; revision: number; visible: boolean;
}) {
  const admin = user.role === 'ADMIN';
  const [branches, setBranches] = useState<Branch[]>([]);
  const [branch, setBranch] = useState('');
  const [history, setHistory] = useState(false);
  useEffect(() => {
    if (!admin) return;
    let active = true;
    client.branches().then(items => { if (active) setBranches(items); }).catch(reason => { if (active) onError(reason); });
    return () => { active = false; };
  }, [client, admin, revision]);

  const alerts = useQuery<Snapshot>(reorderAlertsQuery(client, branch, history, visible));
  useEffect(() => {
    if (alerts.error && !connectionFailure(alerts.error)) onError(alerts.error);
  }, [alerts.errorUpdatedAt]);
  const data = alerts.data;
  const state = freshness(data?.readAt, alerts.errorUpdatedAt || undefined, alerts.isError && connectionFailure(alerts.error));
  const network = admin && branch === '';
  const scope = data?.branch ? `${data.branch.name} (${data.branch.code})` : 'toda la red';

  return <section aria-labelledby="alerts-title" aria-busy={alerts.isFetching}>
    <h2 id="alerts-title" tabIndex={-1}>Alertas de reabastecimiento</h2>
    <p>Un producto requiere reabastecimiento cuando su disponible es igual o inferior al stock mínimo de la sucursal.
      La alerta se resuelve sola cuando el disponible supera el mínimo; no se envían correos ni se crean compras.</p>

    <div className="restock-filters">
      {admin && <div>
        <label htmlFor="alerts-branch">Sucursal</label>
        <select id="alerts-branch" value={branch} onChange={event => setBranch(event.target.value)}>
          <option value="">Todas las sucursales</option>
          {branches.map(item => <option key={item.externalId} value={item.externalId}>
            {item.code} · {item.name}{item.active ? '' : ' (inactiva)'}</option>)}
        </select>
      </div>}
      <fieldset className="choice-list choice-inline">
        <legend>Mostrar</legend>
        <label className="choice"><input type="radio" name="alerts-view" checked={!history} onChange={() => setHistory(false)} />
          <span><strong>Alertas activas</strong></span></label>
        <label className="choice"><input type="radio" name="alerts-view" checked={history} onChange={() => setHistory(true)} />
          <span><strong>Historial completo</strong></span></label>
      </fieldset>
    </div>

    <div className="inventory-live">
      <div className="inventory-live-header">
        <h3>{history ? 'Historial de alertas' : 'Alertas activas'} de {scope}</h3>
        {data && <RestockBadge active={data.activeCount > 0}
          activeLabel={`${data.activeCount} ${data.activeCount === 1 ? 'activa' : 'activas'}`} inactiveLabel="Sin alertas activas" />}
        <Button variant="outline" disabled={alerts.isFetching} onClick={() => void alerts.refetch()}>
          <RefreshCw aria-hidden="true" />Actualizar ahora
        </Button>
      </div>
      {state.kind === 'loading' && <p role="status">Consultando alertas…</p>}
      {state.kind === 'live' && <p role="status" aria-live="off">
        Se actualiza automáticamente cada {INVENTORY_POLL_MS / 1000} segundos mientras esta vista está abierta. Última lectura del servidor: {time(state.readAt)}
      </p>}
      {state.kind === 'stale' && <p role="alert" className="notice-warning">
        Sin comunicación con el servidor o servicio no disponible (último intento: {time(state.failedAt)}). Se muestran las alertas leídas a las {time(state.readAt)} y
        pueden estar desactualizadas; se reintentará automáticamente.
      </p>}
      {state.kind === 'unavailable' && <p role="alert">
        No se pudieron leer las alertas: sin comunicación con el servidor o servicio no disponible (último intento: {time(state.failedAt)}). Se reintentará automáticamente.
      </p>}
      {data && (data.items.length === 0
        ? <p>{history ? 'No hay alertas registradas en este ámbito.'
          : 'Ningún producto está en su stock mínimo o por debajo. Las alertas aparecerán aquí en cuanto ocurra.'}</p>
        : <div className="table-scroll" role="region" aria-label={`Alertas de reabastecimiento de ${scope}`} tabIndex={0}>
          <Table className="restock-table">
            <TableCaption>Cantidades en unidad base. «Al activarse» conserva el mínimo y el disponible del momento en que se abrió la alerta.</TableCaption>
            <TableHeader><TableRow>
              <TableHead scope="col">SKU</TableHead><TableHead scope="col">Producto</TableHead>
              {network && <TableHead scope="col">Sucursal</TableHead>}
              <TableHead scope="col" className="text-right">Disponible</TableHead>
              <TableHead scope="col" className="text-right">Stock mínimo vigente</TableHead>
              <TableHead scope="col">Al activarse</TableHead>
              <TableHead scope="col">Estado</TableHead>
            </TableRow></TableHeader>
            <TableBody>{data.items.map(item => <TableRow key={item.externalId}>
              <TableCell><span className="record-code whitespace-nowrap">{item.product.sku}</span></TableCell>
              <TableCell>{item.product.name}</TableCell>
              {network && <TableCell>{item.branch.name} ({item.branch.code})</TableCell>}
              <TableCell className="quantity">{decimal(item.currentQuantity)} {item.product.baseUnitName}</TableCell>
              <TableCell className="quantity">{item.currentMinimum === null ? 'No habilitado'
                : `${decimal(item.currentMinimum)} ${item.product.baseUnitName}`}</TableCell>
              <TableCell>{moment(item.openedAt)}
                <span className="cell-note">Mínimo {decimal(item.minimumAtOpening)}, disponible {decimal(item.quantityAtOpening)}</span></TableCell>
              <TableCell><RestockBadge active={item.status === 'ACTIVE'} activeLabel="Activa" inactiveLabel="Resuelta" />
                {item.resolvedAt && <span className="cell-note">{moment(item.resolvedAt)}</span>}</TableCell>
            </TableRow>)}</TableBody>
          </Table>
        </div>)}
    </div>
  </section>;
}
