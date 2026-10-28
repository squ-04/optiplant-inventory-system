import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { RefreshCw, Search } from 'lucide-react';
import { ApiClient } from '../lib/api/client.ts';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { SectionTabs } from '../components/shared/SectionTabs.tsx';
import { StatusBadge } from '../components/shared/StatusBadge.tsx';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';
import {
  INVENTORY_NETWORK_MODE, INVENTORY_POLL_MS, INVENTORY_TIMEOUT_MS, connectionFailure, freshness,
} from '../lib/inventory/freshness.ts';
import type { Branch, BranchInventory, Movement, OperationalDocument, User, Visibility as Snapshot } from '../lib/api/client.ts';
import { kindLabel } from '../lib/inventory/movements.ts';
import { clockTime as time, dateTime, decimal, directionLabel, documentStatusLabel } from '../lib/format.ts';

export function Visibility({ client, user, onError, revision, visible }: {
  client: ApiClient; user: User; onError: (reason: unknown) => void; revision: number; visible: boolean;
}) {
  const [branches, setBranches] = useState<Branch[]>([]);
  const [selected, setSelected] = useState(user.branchExternalId ?? '');
  const [consulted, setConsulted] = useState('');
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    setSnapshot(null);
    client.visibleBranches().then(items => { if (active) setBranches(items); })
      .catch(reason => { if (active) onError(reason); });
    return () => { active = false; };
  }, [client, revision]);

  // RF-0015: confirmed changes reach this view by REST polling (ARQ PEN-003) while it is on screen; the query also
  // refreshes on focus, reconnection and when the view becomes visible again. A failed poll keeps the last read.
  const inventory = useQuery<BranchInventory>({
    queryKey: ['branch-inventory', consulted],
    queryFn: ({ signal }) => client.branchInventory(consulted, AbortSignal.any([signal, AbortSignal.timeout(INVENTORY_TIMEOUT_MS)])),
    enabled: consulted !== '' && visible,
    // A client rejection (session, permission, unknown branch) stops polling and focus refetches; connection loss
    // and server outages keep retrying.
    refetchInterval: query => visible && (query.state.error === null || connectionFailure(query.state.error)) ? INVENTORY_POLL_MS : false,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: query => query.state.error === null || connectionFailure(query.state.error) ? 'always' : false,
    refetchOnReconnect: 'always',
    networkMode: INVENTORY_NETWORK_MODE,
    retry: false,
  });
  // Only communication loss is shown inline; session, permission and validation rejections go to the shell.
  useEffect(() => {
    if (inventory.error && !connectionFailure(inventory.error)) onError(inventory.error);
  }, [inventory.errorUpdatedAt]);

  async function load() {
    setBusy(true); setSnapshot(null);
    try {
      if (consulted === selected) await inventory.refetch();
      setConsulted(selected);
      if (user.role === 'ADMIN' || selected === user.branchExternalId) setSnapshot(await client.visibility(selected));
    } catch (reason) { onError(reason); }
    finally { setBusy(false); }
  }
  const branch = branches.find(item => item.externalId === selected);
  const data = consulted === '' ? undefined : inventory.data;
  const state = freshness(data?.readAt, inventory.errorUpdatedAt || undefined, inventory.isError && connectionFailure(inventory.error));
  const stopped = inventory.isError && !connectionFailure(inventory.error);
  const foreign = data && user.branchExternalId !== null && data.branch.externalId !== user.branchExternalId;
  return <section aria-labelledby="visibility-title" aria-busy={busy || inventory.isFetching}>
    <h2 id="visibility-title" tabIndex={-1}>{user.role === 'ADMIN' ? 'Visibilidad de la red' : 'Consulta de sucursal'}</h2>
    <p>Consulte las existencias de cualquier sucursal. Consultar otra sucursal no cambia su ámbito de operación.</p>
    <label htmlFor="visibility-branch">Sucursal</label>
    <select id="visibility-branch" value={selected} disabled={busy} onChange={e => {
      setSelected(e.target.value); setConsulted(''); setSnapshot(null);
    }}>
      <option value="">Seleccione una sucursal</option>
      {branches.map(item => <option key={item.externalId} value={item.externalId}>
        {item.code} · {item.name}{item.active ? '' : ' (inactiva: solo consultas)'}
      </option>)}
    </select>
    <Button disabled={busy || !selected} onClick={() => void load()}><Search aria-hidden="true" />{busy ? 'Consultando…' : 'Consultar sucursal'}</Button>
    {branch && !branch.active && <p className="notice-warning">Sucursal inactiva. Se conserva el historial; no admite nuevas operaciones.</p>}

    {consulted && <div className="inventory-live">
      <div className="inventory-live-header">
        <h3>Inventario de {data ? `${data.branch.name} (${data.branch.code})` : 'la sucursal seleccionada'}</h3>
        {data && <StatusBadge active={data.branch.active} activeLabel="Sucursal activa" inactiveLabel="Sucursal inactiva" />}
        <Button variant="outline" disabled={inventory.isFetching} onClick={() => void inventory.refetch()}>
          <RefreshCw aria-hidden="true" />Actualizar ahora
        </Button>
      </div>
      {foreign && <p>Consulta de solo lectura: estas existencias pertenecen a otra sucursal y no se modifican desde aquí.</p>}
      {state.kind === 'loading' && <p role="status">Consultando inventario…</p>}
      {stopped && <p className="notice-warning">
        La actualización automática se detuvo porque el servidor rechazó la consulta. Los datos mostrados pueden estar desactualizados.
      </p>}
      {state.kind === 'live' && !stopped && <p role="status" aria-live="off">
        Se actualiza automáticamente cada {INVENTORY_POLL_MS / 1000} segundos mientras esta vista está abierta. Última lectura del servidor: {time(state.readAt)}
      </p>}
      {state.kind === 'stale' && <p role="alert" className="notice-warning">
        Sin comunicación con el servidor o servicio no disponible (último intento: {time(state.failedAt)}). Se muestran los datos leídos a las {time(state.readAt)} y pueden
        estar desactualizados. Esta consulta no confirma ninguna operación; se reintentará automáticamente.
      </p>}
      {state.kind === 'unavailable' && <p role="alert">
        No se pudo leer el inventario: sin comunicación con el servidor o servicio no disponible (último intento: {time(state.failedAt)}). Se reintentará automáticamente; también puede actualizar ahora.
      </p>}
      {data && (data.items.length === 0
        ? <p>Esta sucursal no tiene productos habilitados, saldos ni tránsito registrados.</p>
        : <div className="table-scroll" role="region" aria-label={`Existencias de ${data.branch.name}`} tabIndex={0}>
          <Table>
            <TableCaption>Cantidades en unidad base de cada producto. El tránsito ya salió del origen y no forma parte del físico ni del disponible.</TableCaption>
            <TableHeader><TableRow>
              <TableHead scope="col">SKU</TableHead><TableHead scope="col">Producto</TableHead>
              <TableHead className="quantity" scope="col">Físico</TableHead>
              <TableHead className="quantity" scope="col">Disponible</TableHead>
              <TableHead className="quantity" scope="col">En tránsito hacia la sucursal</TableHead>
            </TableRow></TableHeader>
            <TableBody>{data.items.map(item => <TableRow key={item.productExternalId}>
              <TableCell className="font-semibold">{item.sku}</TableCell><TableCell>{item.name}</TableCell>
              <TableCell className="quantity">{decimal(item.physicalQuantity)} {item.baseUnitName}</TableCell>
              <TableCell className="quantity">{decimal(item.availableQuantity)} {item.baseUnitName}</TableCell>
              <TableCell className="quantity">{decimal(item.inTransitQuantity)} {item.baseUnitName}</TableCell>
            </TableRow>)}</TableBody>
          </Table>
        </div>)}
    </div>}

    {snapshot && <div className="mt-8">
      <h3>Historial operativo de {snapshot.branch.name} ({snapshot.branch.code})</h3>
      <p>El historial operativo no se actualiza automáticamente; vuelva a consultar la sucursal para recuperar cambios posteriores.</p>
      <SectionTabs label={`Historial operativo de ${snapshot.branch.name}`} items={[
        { label: `Compras (${snapshot.purchases.length})`, content: <Documents title="Compras" branch={snapshot.branch.name} items={snapshot.purchases} /> },
        { label: `Ventas (${snapshot.sales.length})`, content: <Documents title="Ventas" branch={snapshot.branch.name} items={snapshot.sales} /> },
        { label: `Transferencias (${snapshot.transfers.length})`, content:
          <Documents title="Transferencias de origen o destino" branch={snapshot.branch.name} items={snapshot.transfers} /> },
        { label: `Movimientos (${snapshot.movements.length})`, content: <Movements branch={snapshot.branch.name} items={snapshot.movements} /> },
      ]} />
    </div>}
    <p>Los ingresos y retiros se registran en «Movimientos». Compras, ventas y transferencias se incorporan en sus entregas funcionales; no se simulan desde esta pantalla.</p>
  </section>;
}

function Documents({ title, branch, items }: { title: string; branch: string; items: OperationalDocument[] }) {
  if (items.length === 0) return <p className="text-muted-foreground">Sin registros de {title.toLowerCase()}.</p>;
  return <div className="table-scroll" role="region" aria-label={`${title} de ${branch}`} tabIndex={0}>
    <Table>
      <TableCaption>{title} persistidas en la sucursal, más recientes primero.</TableCaption>
      <TableHeader><TableRow>
        <TableHead scope="col">Código</TableHead><TableHead scope="col">Estado</TableHead>
        <TableHead scope="col">Fecha y hora</TableHead><TableHead scope="col">Referencia</TableHead>
      </TableRow></TableHeader>
      <TableBody>{items.map(item => <TableRow key={item.externalId}>
        <TableCell className="font-semibold">{item.code}</TableCell>
        <TableCell><Badge variant="secondary">{documentStatusLabel(item.status)}</Badge></TableCell>
        <TableCell className="whitespace-nowrap">{dateTime(item.occurredAt)}</TableCell>
        <TableCell><span className="cell-note">{item.externalId}</span></TableCell>
      </TableRow>)}</TableBody>
    </Table>
  </div>;
}

function Movements({ branch, items }: { branch: string; items: Movement[] }) {
  if (items.length === 0) return <p className="text-muted-foreground">Sin movimientos registrados.</p>;
  return <div className="table-scroll" role="region" aria-label={`Movimientos de ${branch}`} tabIndex={0}>
    <Table>
      <TableCaption>Movimientos persistidos en la sucursal, más recientes primero. El detalle con motivo y valoración está en «Movimientos».</TableCaption>
      <TableHeader><TableRow>
        <TableHead scope="col">Fecha y hora</TableHead><TableHead scope="col">Tipo</TableHead>
        <TableHead scope="col">Dirección</TableHead><TableHead scope="col">Producto</TableHead>
        <TableHead className="quantity" scope="col">Cantidad</TableHead>
        <TableHead scope="col">Responsable</TableHead><TableHead scope="col">Referencia</TableHead>
      </TableRow></TableHeader>
      <TableBody>{items.map(item => <TableRow key={item.externalId}>
        <TableCell className="whitespace-nowrap">{dateTime(item.occurredAt)}</TableCell>
        <TableCell>{kindLabel(item.kind)}</TableCell>
        <TableCell><Badge variant="secondary" className={item.direction === 'IN' ? 'bg-success-muted text-success' : undefined}>
          {directionLabel(item.direction)}
        </Badge></TableCell>
        <TableCell><span className="font-semibold">{item.sku}</span> {item.productName}</TableCell>
        <TableCell className="quantity">{decimal(item.quantity)} {item.unit}</TableCell>
        <TableCell>{item.actorCode}</TableCell>
        <TableCell><span className="cell-note">{item.externalId}</span></TableCell>
      </TableRow>)}</TableBody>
    </Table>
  </div>;
}
