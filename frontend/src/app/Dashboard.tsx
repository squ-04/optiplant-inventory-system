import { useEffect, useRef, useState } from 'react';
import { RefreshCw, Search } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { ApiClient } from '../lib/api/client.ts';
import type { Branch, RotationFilter, RotationReport, SalesComparison, User } from '../lib/api/client.ts';
import { dateTime as when, decimal } from '../lib/format.ts';
import {
  chartPoints, day, monthCoverage, monthName, netAmount, productRows, rotation, rotationNote, rotationPeriodError, salesCount,
} from '../lib/dashboard/dashboard.ts';
import { ZONE_NOTE, bogotaDateTime, currentMonth } from '../lib/logistics/compliance.ts';
import { failure } from '../lib/dashboard/useReading.ts';
import { discardPending, loadLatest, requestGate } from '../lib/latestRequest.ts';
import { SectionTabs } from '../components/shared/SectionTabs.tsx';
import { BranchComparisonSection, DemandSection, RestockSection, TransferImpactSection } from './DashboardIndicators.tsx';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';

interface Props { client: ApiClient; user: User; onError: (reason: unknown) => void; revision: number; visible: boolean }

/**
 * UC-019 branch dashboard: monthly sales of the current month against the three previous ones (RF-0044), inventory rotation
 * by product (RF-0045), high and low demand (RF-0046), active transfers (RF-0047) and products about to run out (RF-0048).
 * Every profile reads its own branch; the administrator chooses one and also compares the network (RF-0049). Missing data,
 * a real zero and «No calculable» are shown as different things.
 */
export function Dashboard({ client, user, onError, revision, visible }: Props) {
  const admin = user.role === 'ADMIN';
  const [branches, setBranches] = useState<Branch[]>([]);
  const [branch, setBranch] = useState('');
  const scope = admin ? branch || undefined : undefined;
  const ready = !admin || !!branch;
  const reading = { client, branch: scope, ready, onError, revision, visible };

  useEffect(() => {
    if (admin && visible) client.visibleBranches().then(setBranches).catch(onError);
  }, [client, admin, visible, revision]);

  // ---------------------------------------------------------------- RF-0044
  const [sales, setSales] = useState<SalesComparison | null>(null);
  const [salesLoading, setSalesLoading] = useState(false);
  const [salesError, setSalesError] = useState('');
  const salesGate = useRef(requestGate());
  const loadSales = () => loadLatest(salesGate.current, () => client.salesComparison(scope), {
    start: () => { setSalesLoading(true); setSalesError(''); },
    value: setSales,
    error: failure(onError, setSalesError, 'No se pudo consultar la comparación de ventas.'),
    done: () => setSalesLoading(false),
  });

  // ---------------------------------------------------------------- RF-0045
  const [period, setPeriod] = useState(() => currentMonth(new Date()));
  const [consulted, setConsulted] = useState(period);
  const [periodError, setPeriodError] = useState('');
  const [turnover, setTurnover] = useState<RotationReport | null>(null);
  const [turnoverLoading, setTurnoverLoading] = useState(false);
  const [turnoverError, setTurnoverError] = useState('');
  const turnoverGate = useRef(requestGate());
  const loadTurnover = (target: RotationFilter = { ...consulted, branch: scope }) =>
    loadLatest(turnoverGate.current, () => client.rotation(target), {
      start: () => { setTurnoverLoading(true); setTurnoverError(''); },
      value: setTurnover,
      error: failure(onError, setTurnoverError, 'No se pudo calcular la rotación.'),
      done: () => setTurnoverLoading(false),
    });

  useEffect(() => {
    if (!visible) return;
    if (!ready) { setSales(null); setTurnover(null); return; }
    void loadSales();
  }, [visible, revision, branch]);
  useEffect(() => { if (visible && ready) void loadTurnover(); }, [visible, revision, branch, consulted]);

  function submitPeriod() {
    const invalid = rotationPeriodError(period.from, period.to, new Date());
    setPeriodError(invalid ?? '');
    if (invalid) return;
    if (period.from === consulted.from && period.to === consulted.to) void loadTurnover(); else setConsulted({ ...period });
  }

  const shown = sales?.branch ?? turnover?.branch;
  return <section aria-labelledby="dashboard-title">
    <h2 id="dashboard-title" tabIndex={-1}>Dashboard</h2>
    <p>Indicadores de {shown ? `${shown.name} (${shown.code})` : admin ? 'la sucursal que elija' : 'su sucursal'}, calculados con
      las ventas confirmadas y el libro de inventario. {ZONE_NOTE}.</p>

    {admin && <form className="purchase-filters" aria-label="Sucursal del dashboard" onSubmit={event => event.preventDefault()}>
      <div><label htmlFor="dashboard-branch">Sucursal</label>
        <select id="dashboard-branch" value={branch} onChange={event => {
          // The figures of the previous branch never stay next to the new selection, nor next to its error, and a late
          // answer for it is discarded even when no new branch is consulted.
          discardPending(salesGate.current); discardPending(turnoverGate.current);
          setSales(null); setTurnover(null); setSalesError(''); setTurnoverError('');
          setSalesLoading(false); setTurnoverLoading(false);
          setBranch(event.target.value);
        }}>
          <option value="">Seleccione una sucursal</option>
          {branches.map(item => <option key={item.externalId} value={item.externalId}>
            {item.code} · {item.name}{item.active ? '' : ' (inactiva)'}</option>)}
        </select></div>
    </form>}

    <SectionTabs label="Indicadores del dashboard" items={[
      { label: 'Ventas y rotación', content: <>
    {!ready && <p>Seleccione una sucursal para ver sus ventas mensuales y la rotación de su inventario.</p>}

    {ready && <>
      <section className="compliance-section" aria-labelledby="dashboard-sales" aria-busy={salesLoading}>
        <div className="list-heading">
          <div>
            <h3 id="dashboard-sales">Ventas del mes y de los tres meses anteriores</h3>
            <p>Importe de las ventas confirmadas, neto de descuentos. Las devoluciones se muestran aparte y no se restan.</p>
          </div>
          <Button variant="outline" disabled={salesLoading} onClick={() => void loadSales()}>
            <RefreshCw aria-hidden="true" />{salesLoading ? 'Actualizando…' : 'Actualizar ventas'}
          </Button>
        </div>
        {salesError && <p role="alert">{salesError}</p>}
        {salesLoading && !sales && <p role="status">Consultando ventas…</p>}
        {sales && <SalesView sales={sales} />}
      </section>

      <section className="compliance-section" aria-labelledby="dashboard-rotation" aria-busy={turnoverLoading}>
        <h3 id="dashboard-rotation">Rotación del inventario</h3>
        <p>Unidades vendidas en el período divididas entre el inventario físico promedio del mismo período, ponderado por el
          tiempo que cada saldo se mantuvo. Sin historia que cubra todo el período, o con promedio cero, no es calculable.</p>
        <form className="purchase-filters" aria-label="Período de la rotación" noValidate
          aria-describedby={periodError ? 'rotation-period-error' : undefined}
          onSubmit={event => { event.preventDefault(); submitPeriod(); }}>
          <div><label htmlFor="rotation-from">Desde</label>
            <Input id="rotation-from" type="date" value={period.from} required aria-invalid={!!periodError}
              onChange={event => setPeriod({ ...period, from: event.target.value })} /></div>
          <div><label htmlFor="rotation-to">Hasta</label>
            <Input id="rotation-to" type="date" value={period.to} required aria-invalid={!!periodError}
              onChange={event => setPeriod({ ...period, to: event.target.value })} /></div>
          <Button type="submit" disabled={turnoverLoading}><Search aria-hidden="true" />Calcular rotación</Button>
        </form>
        {periodError && <p role="alert" id="rotation-period-error">{periodError}</p>}
        {turnoverError && <p role="alert">{turnoverError}</p>}
        {turnoverLoading && !turnover && <p role="status">Calculando rotación…</p>}
        {turnover && <RotationView report={turnover} />}
      </section>
    </>}
      </> },
      { label: 'Demanda', content: <>
        {!ready && <p>Seleccione una sucursal para ver sus productos de alta y baja demanda.</p>}
        {ready && <DemandSection {...reading} />}
      </> },
      { label: 'Transferencias', content: <>
        {!ready && <p>Seleccione una sucursal para ver sus transferencias activas.</p>}
        {ready && <TransferImpactSection {...reading} />}
      </> },
      { label: 'Próximos a agotarse', content: <>
        {!ready && <p>Seleccione una sucursal para ver sus productos próximos a agotarse.</p>}
        {ready && <RestockSection {...reading} />}
      </> },
      ...(admin ? [{ label: 'Comparativa', content: <BranchComparisonSection {...{ client, onError, revision, visible }} /> }] : []),
    ]} />
  </section>;
}

const prefersReducedMotion = () => typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

function SalesView({ sales }: { sales: SalesComparison }) {
  const points = chartPoints(sales.months);
  const rows = productRows(sales.months);
  const months = sales.months;
  return <>
    <p className="cell-note">Sucursal registrada el {day(sales.recordedFrom)}. Generado: {when(sales.generatedAt)}</p>
    {/* The table below carries every figure; the chart is a visual summary of the same amounts. */}
    {!points.some(point => (point.amount ?? 0) > 0)
      ? <p>Sin ventas confirmadas en estos meses: no hay importes que graficar.</p>
      : <figure className="sales-chart" aria-hidden="true">
      <ResponsiveContainer width="100%" height={240}>
        <BarChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: 8 }} accessibilityLayer={false}>
          <defs>
            <pattern id="sales-in-progress" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
              <rect width="6" height="6" fill="var(--color-chart-soft)" />
              <line x1="0" y1="0" x2="0" y2="6" stroke="var(--color-chart)" strokeWidth="3" />
            </pattern>
          </defs>
          <CartesianGrid vertical={false} stroke="var(--color-border)" />
          <XAxis dataKey="label" tickLine={false} axisLine={{ stroke: 'var(--color-border)' }}
            tick={{ fill: 'var(--color-muted)', fontSize: 12 }} />
          <YAxis width={72} tickLine={false} axisLine={false} tick={{ fill: 'var(--color-muted)', fontSize: 12 }}
            tickFormatter={(value: number) => value.toLocaleString('es-CO', { notation: 'compact', maximumFractionDigits: 1 })} />
          <Tooltip cursor={{ fill: 'var(--color-background)' }} content={({ active, payload }) => {
            const point = active ? payload?.[0]?.payload as (typeof points)[number] | undefined : undefined;
            return point ? <div className="chart-tooltip"><strong>{point.label}</strong><span>{point.exact}</span>
              <small>{point.coverage}</small></div> : null;
          }} />
          <Bar dataKey="amount" radius={[4, 4, 0, 0]} maxBarSize={56} isAnimationActive={!prefersReducedMotion()}>
            {points.map(point => <Cell key={point.month}
              fill={point.inProgress ? 'url(#sales-in-progress)' : 'var(--color-chart)'} />)}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
      <ul className="compliance-legend">
        <li><i className="chart-complete" />Mes cerrado</li>
        <li><i className="chart-in-progress" />Mes en curso, aún incompleto</li>
        {points.some(point => !point.available) && <li><i className="chart-missing" />Sin barra: sin datos</li>}
      </ul>
    </figure>}

    <div className="table-scroll" role="region" aria-label="Ventas por mes" tabIndex={0}>
      <Table>
        <TableCaption>Del mes más antiguo al mes en curso. «Sin datos» no equivale a cero ventas.</TableCaption>
        <TableHeader><TableRow>
          <TableHead scope="col">Mes</TableHead><TableHead scope="col">Ventas</TableHead>
          <TableHead scope="col" className="text-right">Importe neto</TableHead><TableHead scope="col">Devoluciones</TableHead>
        </TableRow></TableHeader>
        <TableBody>{months.map(month => <TableRow key={month.month} className={month.inProgress ? 'month-in-progress' : undefined}>
          <TableCell><span className="font-semibold">{monthName(month.month)}</span>
            <span className="cell-note">{monthCoverage(month)}</span></TableCell>
          <TableCell className={month.dataAvailable ? undefined : 'not-calculable'}>{salesCount(month)}</TableCell>
          <TableCell className={`text-right tabular-nums${month.dataAvailable ? '' : ' not-calculable'}`}>{netAmount(month)}</TableCell>
          <TableCell className={month.dataAvailable ? undefined : 'not-calculable'}>{month.returnsCount === null ? 'Sin datos'
            : month.returnsCount === 0 ? 'Ninguna' : `${month.returnsCount} ${month.returnsCount === 1 ? 'devolución' : 'devoluciones'}`}
          </TableCell>
        </TableRow>)}</TableBody>
      </Table>
    </div>

    <h4>Unidades vendidas por producto</h4>
    {rows.length === 0
      ? <p>No hay ventas ni devoluciones en estos meses.</p>
      : <div className="table-scroll" role="region" aria-label="Unidades vendidas por producto y mes" tabIndex={0}>
        <Table>
          <TableCaption>Cantidades en la unidad base de cada producto; no se suman productos con unidades distintas.</TableCaption>
          <TableHeader><TableRow>
            <TableHead scope="col">Producto</TableHead>
            {months.map(month => <TableHead key={month.month} scope="col" className="text-right">{monthName(month.month)}</TableHead>)}
          </TableRow></TableHeader>
          <TableBody>{rows.map(row => <TableRow key={row.productExternalId}>
            <TableCell><span className="font-semibold">{row.name}</span>
              <span className="cell-note">{row.sku} · {row.baseUnitName}</span></TableCell>
            {row.cells.map((cell, index) => <TableCell key={months[index].month}
              className={`text-right tabular-nums${cell ? '' : ' not-calculable'}`}>
              {cell ? <>{decimal(cell.sold)}{!/^0(\.0+)?$/.test(cell.returned)
                && <span className="cell-note">Devuelto: {decimal(cell.returned)}</span>}</> : 'Sin datos'}
            </TableCell>)}
          </TableRow>)}</TableBody>
        </Table>
      </div>}
  </>;
}

function RotationView({ report }: { report: RotationReport }) {
  return <>
    <p className="cell-note">Del {day(report.from)} al {day(report.to)}, medido hasta el {bogotaDateTime(report.measuredUntil)}
      (generado el {when(report.generatedAt)})</p>
    {report.items.length === 0
      ? <p>La sucursal no tiene inventario registrado en este período. Elija un período posterior a su primer movimiento.</p>
      : <div className="table-scroll" role="region" aria-label="Rotación por producto" tabIndex={0}>
        <Table>
          <TableCaption>Cantidades en la unidad base de cada producto, ordenadas por SKU.</TableCaption>
          <TableHeader><TableRow>
            <TableHead scope="col">Producto</TableHead>
            <TableHead scope="col" className="text-right">Vendidas</TableHead>
            <TableHead scope="col" className="text-right">Inventario promedio</TableHead>
            <TableHead scope="col" className="text-right">Rotación</TableHead>
          </TableRow></TableHeader>
          <TableBody>{report.items.map(item => <TableRow key={item.productExternalId}>
            <TableCell><span className="font-semibold">{item.name}</span>
              <span className="cell-note">{item.sku} · {item.baseUnitName}</span></TableCell>
            <TableCell className="text-right tabular-nums">{decimal(item.soldQuantity)}</TableCell>
            <TableCell className={`text-right tabular-nums${item.averageQuantity === null ? ' not-calculable' : ''}`}>
              {item.averageQuantity === null ? 'Sin historia' : decimal(item.averageQuantity)}</TableCell>
            <TableCell className={`text-right${item.rotation === null ? ' not-calculable' : ''}`}>
              <span className="font-semibold tabular-nums">{rotation(item)}</span>
              {rotationNote(item) && <span className="cell-note">{rotationNote(item)}</span>}</TableCell>
          </TableRow>)}</TableBody>
        </Table>
      </div>}
  </>;
}
