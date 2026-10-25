import { useEffect, useState } from 'react';
import { ApiClient } from '../lib/api/client.ts';
import { Button } from '../components/ui/button';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';
import type { Branch, InventoryItem, OperationalDocument, User, Visibility as Snapshot } from '../lib/api/client.ts';

export function Visibility({ client, user, onError, revision }: {
  client: ApiClient; user: User; onError: (reason: unknown) => void; revision: number;
}) {
  const [branches, setBranches] = useState<Branch[]>([]);
  const [selected, setSelected] = useState(user.branchExternalId ?? '');
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [inventory, setInventory] = useState<InventoryItem[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [readAt, setReadAt] = useState('');
  useEffect(() => {
    let active = true;
    setSnapshot(null); setInventory(null); setReadAt('');
    client.visibleBranches().then(items => { if (active) setBranches(items); })
      .catch(reason => { if (active) onError(reason); });
    return () => { active = false; };
  }, [client, revision]);

  async function load() {
    setBusy(true); setSnapshot(null); setInventory(null); setReadAt('');
    try {
      if (user.role === 'ADMIN' || selected === user.branchExternalId) {
        const result = await client.visibility(selected); setSnapshot(result); setInventory(result.inventory);
      } else setInventory(await client.inventory(selected));
      setReadAt(new Date().toLocaleString());
    } catch (reason) { onError(reason); }
    finally { setBusy(false); }
  }
  const branch = branches.find(item => item.externalId === selected);
  return <section aria-labelledby="visibility-title" aria-busy={busy}>
    <h2 id="visibility-title" tabIndex={-1}>{user.role === 'ADMIN' ? 'Visibilidad de la red' : 'Consulta de sucursal'}</h2>
    <p>Consultar otra sucursal no cambia su ámbito de escritura. Esta vista es de solo lectura.</p>
    <label htmlFor="visibility-branch">Sucursal</label>
    <select id="visibility-branch" value={selected} disabled={busy} onChange={e => {
      setSelected(e.target.value); setSnapshot(null); setInventory(null); setReadAt('');
    }}>
      <option value="">Seleccione una sucursal</option>
      {branches.map(item => <option key={item.externalId} value={item.externalId}>
        {item.code} · {item.name}{item.active ? '' : ' (inactiva: solo consultas)'}
      </option>)}
    </select>
    <Button disabled={busy || !selected} onClick={() => void load()}>{busy ? 'Consultando…' : 'Consultar datos actuales'}</Button>
    {readAt && <p role="status">Consulta recibida: {readAt}. Actualice para recuperar cambios posteriores.</p>}
    {branch && !branch.active && <p className="notice-warning">Sucursal inactiva. Se conserva el historial; no admite nuevas operaciones.</p>}
    {inventory && <><h3>Existencias físicas</h3>{inventory.length === 0 ? <p>Sin saldos registrados.</p> :
      <div className="table-scroll" role="region" aria-label="Existencias por producto" tabIndex={0}>
        <Table><TableCaption>Existencias por producto; las cantidades de productos diferentes no se suman.</TableCaption>
          <TableHeader><TableRow><TableHead scope="col">SKU</TableHead><TableHead scope="col">Producto</TableHead><TableHead className="text-right" scope="col">Cantidad base</TableHead></TableRow></TableHeader>
          <TableBody>{inventory.map(item => <TableRow key={item.productExternalId}><TableCell>{item.sku}</TableCell><TableCell>{item.name}</TableCell><TableCell className="text-right whitespace-nowrap">{item.physicalQuantity}</TableCell></TableRow>)}</TableBody>
        </Table>
      </div>}</>}
    {snapshot && <>
      <Documents title="Compras" items={snapshot.purchases} />
      <Documents title="Ventas" items={snapshot.sales} />
      <Documents title="Transferencias de origen o destino" items={snapshot.transfers} />
      <h3>Movimientos registrados</h3>
      {snapshot.movements.length === 0 ? <p>Sin movimientos registrados.</p> : <ul>{snapshot.movements.map(item =>
        <li key={item.externalId}>{item.occurredAt} · {item.kind} · {item.direction} · {item.sku} · {item.quantity} {item.unit} · Responsable: {item.actorCode} · Ref.: {item.externalId}</li>)}</ul>}
    </>}
    <p>Los comandos de inventario, compras, ventas y transferencias se incorporan en sus entregas funcionales; no se simulan desde esta pantalla.</p>
  </section>;
}

function Documents({ title, items }: { title: string; items: OperationalDocument[] }) {
  return <><h3>{title}</h3>{items.length === 0 ? <p>Sin registros.</p> : <ul>{items.map(item =>
    <li key={item.externalId}>{item.code} · {item.status} · {item.occurredAt} · Ref.: {item.externalId}</li>)}</ul>}</>;
}
