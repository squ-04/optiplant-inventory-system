import { useEffect, useState } from 'react';
import { RefreshCw, Search } from 'lucide-react';
import { ApiClient, ApiError } from '../lib/api/client.ts';
import type { Branch, InventoryValuation, User } from '../lib/api/client.ts';
import { dateTime as when, decimal } from '../lib/format.ts';
import { Button } from '../components/ui/button';
import { Table, TableBody, TableCaption, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '../components/ui/table';

interface Props { client: ApiClient; user: User; onError: (reason: unknown) => void; revision: number; visible: boolean }

/**
 * RF-0026: weighted-average valuation recorded per product for one branch. Managers read their own branch; the
 * administrator chooses any branch. Operators never reach this view (INC-003/D04).
 */
export function Valuation({ client, user, onError, revision, visible }: Props) {
  const admin = user.role === 'ADMIN';
  const [branches, setBranches] = useState<Branch[]>([]);
  const [branch, setBranch] = useState(admin ? '' : user.branchExternalId ?? '');
  const [consulted, setConsulted] = useState(admin ? '' : user.branchExternalId ?? '');
  const [valuation, setValuation] = useState<InventoryValuation | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (admin && visible) client.visibleBranches().then(setBranches).catch(onError);
  }, [client, admin, visible, revision]);

  async function load(target = consulted) {
    if (!target) return;
    setLoading(true); setError('');
    try { setValuation(await client.inventoryValuation(target)); } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setError(reason instanceof TypeError
        ? 'Sin comunicación con el servidor. La valoración mostrada puede estar desactualizada.'
        : reason instanceof Error ? reason.message : 'No se pudo consultar la valoración.');
    } finally { setLoading(false); }
  }
  useEffect(() => { if (visible) void load(); }, [visible, consulted, revision]);

  return <section aria-labelledby="valuation-title" aria-busy={loading}>
    <h2 id="valuation-title" tabIndex={-1}>{admin ? 'Valoración del inventario de la red' : 'Valoración del inventario'}</h2>
    <p>Costo promedio ponderado por producto y valor registrado de las existencias. Cada ingreso valorado recalcula el
      promedio; las salidas lo consumen sin cambiarlo.</p>

    {admin && <form className="purchase-filters" aria-label="Sucursal a valorar" onSubmit={event => {
      event.preventDefault();
      if (branch === consulted) void load(); else setConsulted(branch);
    }}>
      <div><label htmlFor="valuation-branch">Sucursal</label>
        <select id="valuation-branch" value={branch} disabled={loading} onChange={event => setBranch(event.target.value)}>
          <option value="">Seleccione una sucursal</option>
          {branches.map(item => <option key={item.externalId} value={item.externalId}>
            {item.code} · {item.name}{item.active ? '' : ' (inactiva)'}</option>)}
        </select></div>
      <Button type="submit" disabled={loading || !branch}><Search aria-hidden="true" />Consultar valoración</Button>
    </form>}
    {admin && !consulted && <p className="text-muted-foreground">Seleccione una sucursal para ver su valoración.</p>}

    {consulted && <div className="list-heading">
      <div>
        <h3>{valuation ? `${valuation.branch.name} (${valuation.branch.code})` : 'Valoración'}</h3>
        {valuation && <p>Lectura del servidor: {when(valuation.readAt)}</p>}
      </div>
      <Button variant="outline" disabled={loading} onClick={() => void load()}>
        <RefreshCw aria-hidden="true" />{loading ? 'Actualizando…' : 'Actualizar valoración'}
      </Button>
    </div>}
    {error && <p role="alert">{error}</p>}
    {loading && !valuation && <p role="status">Consultando valoración…</p>}
    {valuation && consulted === valuation.branch.externalId && <>
      <dl className="purchase-summary" aria-label="Resumen de la valoración">
        <div className="purchase-summary-received"><dt>Valor del inventario</dt><dd>{decimal(valuation.totalValue)} COP</dd></div>
        <div><dt>Productos con existencias</dt><dd>{valuation.items.length}</dd></div>
      </dl>
      {valuation.items.length === 0
        ? <p>La sucursal no tiene existencias valoradas. El valor aparece al recibir compras o registrar ingresos.</p>
        : <div className="table-scroll" role="region" aria-label={`Valoración de ${valuation.branch.name}`} tabIndex={0}>
          <Table className="valuation-table">
            <TableCaption>Costo promedio por unidad base con 6 decimales. Los valores en COP se muestran redondeados a 2
              decimales; el total se suma con los valores exactos.</TableCaption>
            <TableHeader><TableRow>
              <TableHead scope="col">Producto</TableHead><TableHead className="quantity" scope="col">Existencias</TableHead>
              <TableHead className="quantity" scope="col">Costo promedio</TableHead>
              <TableHead className="quantity" scope="col">Valor</TableHead>
            </TableRow></TableHeader>
            <TableBody>{valuation.items.map(item => <TableRow key={item.productExternalId}>
              <TableCell><span className="font-semibold">{item.sku}</span> {item.productName}</TableCell>
              <TableCell className="quantity">{decimal(item.physicalQuantity)} {item.baseUnitName}</TableCell>
              <TableCell className="quantity">{decimal(item.averageCost)}<span className="cell-note">por {item.baseUnitName}</span></TableCell>
              <TableCell className="quantity">{decimal(item.value)}{exactDiffers(item.exactValue) &&
                <span className="cell-note">exacto {decimal(item.exactValue)}</span>}</TableCell>
            </TableRow>)}</TableBody>
            <TableFooter><TableRow>
              <TableCell colSpan={3}>Total</TableCell>
              <TableCell className="quantity">{decimal(valuation.totalValue)} COP</TableCell>
            </TableRow></TableFooter>
          </Table>
        </div>}
    </>}
  </section>;
}

/** The exact value is worth showing when it carries more than the 2 displayed decimals. */
const exactDiffers = (exact: string) => (exact.split('.')[1] ?? '').length > 2;
