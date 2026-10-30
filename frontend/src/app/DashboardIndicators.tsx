import { Fragment, useState } from 'react';
import { RefreshCw, Search } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { ApiClient, BranchComparison, BranchPerformance, ComplianceMetrics, DemandItem, DemandRanking, DemandReport, TransferImpact } from '../lib/api/client.ts';
import { dateTime as when, decimal } from '../lib/format.ts';
import { day, monthName } from '../lib/dashboard/dashboard.ts';
import {
  barWidth, comparisonCoverage, comparisonPoints, currentMonthValue, demandCoverage, impactNote, monthError, quantity, rankingScale, restockNote,
} from '../lib/dashboard/indicators.ts';
import { useReading } from '../lib/dashboard/useReading.ts';
import { compliance, complianceBase, bogotaDateTime } from '../lib/logistics/compliance.ts';
import { TransferStatusBadge } from '../components/shared/TransferStatusBadge.tsx';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';

interface Section { client: ApiClient; onError: (reason: unknown) => void; revision: number; visible: boolean }
interface BranchSection extends Section { branch?: string; ready: boolean }

const prefersReducedMotion = () => typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/** The month consulted by a section: validated before asking, in progress by default. */
function useMonth() {
  const [month, setMonth] = useState(() => currentMonthValue(new Date()));
  const [consulted, setConsulted] = useState(month);
  const [error, setError] = useState('');
  const submit = (reload: () => void) => {
    const invalid = monthError(month, new Date());
    setError(invalid ?? '');
    if (invalid) return;
    if (month === consulted) reload(); else setConsulted(month);
  };
  return { month, setMonth, consulted, error, submit };
}

function MonthForm({ id, label, action, busy, state, onSubmit }: {
  id: string; label: string; action: string; busy: boolean; state: ReturnType<typeof useMonth>; onSubmit: () => void;
}) {
  return <>
    <form className="purchase-filters" aria-label={label} noValidate aria-describedby={state.error ? `${id}-error` : undefined}
      onSubmit={event => { event.preventDefault(); onSubmit(); }}>
      <div><label htmlFor={id}>Mes</label>
        <Input id={id} type="month" value={state.month} required aria-invalid={!!state.error}
          onChange={event => state.setMonth(event.target.value)} /></div>
      <Button type="submit" disabled={busy}><Search aria-hidden="true" />{action}</Button>
    </form>
    {state.error && <p role="alert" id={`${id}-error`}>{state.error}</p>}
  </>;
}

function Refresh({ busy, label, onClick }: { busy: boolean; label: string; onClick: () => void }) {
  return <Button variant="outline" disabled={busy} onClick={onClick}>
    <RefreshCw aria-hidden="true" />{busy ? 'Actualizando…' : label}
  </Button>;
}

// ---------------------------------------------------------------- RF-0046

export function DemandSection({ client, branch, ready, onError, revision, visible }: BranchSection) {
  const period = useMonth();
  const { value, loading, error, reload } = useReading({
    read: () => client.demand({ month: period.consulted, branch }), subject: `${branch ?? ''}|${period.consulted}`,
    active: visible && ready, revision, onError, fallback: 'No se pudo consultar la demanda.',
  });
  return <section className="compliance-section" aria-labelledby="dashboard-demand" aria-busy={loading}>
    <h3 id="dashboard-demand">Alta y baja demanda</h3>
    <p>Ranking relativo por unidades vendidas en ventas confirmadas del mes. No mide importes, no resta devoluciones y no
      predice la demanda futura.</p>
    <MonthForm id="demand-month" label="Mes de la demanda" action="Consultar demanda" busy={loading} state={period}
      onSubmit={() => period.submit(() => void reload())} />
    {error && <p role="alert">{error}</p>}
    {loading && !value && <p role="status">Consultando demanda…</p>}
    {value && <DemandView report={value} />}
  </section>;
}

function DemandView({ report }: { report: DemandReport }) {
  const month = monthName(report.month);
  return <>
    <p className="cell-note">{demandCoverage(report)}. Generado: {when(report.generatedAt)}</p>
    {!report.dataAvailable ? <p>No hay datos de {month}: el mes terminó antes de registrar la sucursal.</p>
      : report.rankings.length === 0 ? <p>Ningún producto tuvo ventas confirmadas en {month}.</p>
        : report.rankings.map(ranking => <RankingView key={ranking.baseUnitName} ranking={ranking} />)}
    {report.withoutSales.length > 0 && <>
      <h4>Sin ventas en {month}</h4>
      <div className="table-scroll" role="region" aria-label="Productos sin ventas en el mes" tabIndex={0}>
        <Table>
          <TableCaption>Productos habilitados en la sucursal. No forman parte de ningún ranking.</TableCaption>
          <TableHeader><TableRow>
            <TableHead scope="col">Producto</TableHead><TableHead scope="col">Unidad base</TableHead>
          </TableRow></TableHeader>
          <TableBody>{report.withoutSales.map(item => <TableRow key={item.productExternalId}>
            <TableCell><span className="font-semibold">{item.name}</span><span className="cell-note">{item.sku}</span></TableCell>
            <TableCell>{item.baseUnitName}</TableCell>
          </TableRow>)}</TableBody>
        </Table>
      </div>
    </>}
  </>;
}

function RankingView({ ranking }: { ranking: DemandRanking }) {
  const scale = rankingScale(ranking);
  const shown = ranking.high.length + ranking.low.length;
  return <div className="demand-ranking">
    <h4>Productos vendidos en {ranking.baseUnitName}</h4>
    <p className="cell-note">{ranking.productsWithSales === 1 ? '1 producto con ventas' : `${ranking.productsWithSales} productos con ventas`}
      {shown < ranking.productsWithSales ? `; se muestran ${shown}, los extremos del ranking` : ''}. Ambas listas usan la misma escala.</p>
    <div className="demand-groups">
      <DemandGroup title="Alta demanda" items={ranking.high} unit={ranking.baseUnitName} scale={scale} empty="" />
      <DemandGroup title="Baja demanda" items={ranking.low} unit={ranking.baseUnitName} scale={scale}
        empty="Con un solo producto vendido no hay grupo de baja demanda." />
    </div>
  </div>;
}

function DemandGroup({ title, items, unit, scale, empty }: { title: string; items: DemandItem[]; unit: string; scale: number; empty: string }) {
  return <section className="demand-group" aria-label={`${title} en ${unit}`}>
    <h5>{title}</h5>
    {items.length === 0 ? <p>{empty}</p> : <ol>
      {items.map(item => <li key={item.productExternalId}>
        <span><span className="font-semibold">{item.name}</span><span className="cell-note">{item.sku}</span></span>
        <span className="demand-quantity">{quantity(item.soldQuantity, unit)}</span>
        {/* The text beside it carries the exact figure; the bar only draws it. */}
        <span className="demand-bar" aria-hidden="true"><i style={{ width: `${barWidth(item.soldQuantity, scale)}%` }} /></span>
      </li>)}
    </ol>}
  </section>;
}

// ---------------------------------------------------------------- RF-0047

export function TransferImpactSection({ client, branch, ready, onError, revision, visible }: BranchSection) {
  const { value, loading, error, reload } = useReading({
    read: () => client.transferImpact(branch), subject: branch ?? '', active: visible && ready, revision, onError,
    fallback: 'No se pudieron consultar las transferencias.',
  });
  return <section className="compliance-section" aria-labelledby="dashboard-transfers" aria-busy={loading}>
    <div className="list-heading">
      <div>
        <h3 id="dashboard-transfers">Transferencias activas e impacto</h3>
        <p>Cada cantidad es un concepto distinto y no se suma con las demás: la previsión no reserva, el tránsito ya no está en
          el físico de origen y lo recibido ya está en el físico de destino.</p>
      </div>
      <Refresh busy={loading} label="Actualizar transferencias" onClick={() => void reload()} />
    </div>
    {error && <p role="alert">{error}</p>}
    {loading && !value && <p role="status">Consultando transferencias…</p>}
    {value && <TransferImpactView report={value} />}
  </section>;
}

function TransferImpactView({ report }: { report: TransferImpact }) {
  if (report.items.length === 0) return <p>Esta sucursal no tiene transferencias activas.</p>;
  return <>
    <p className="cell-note">Generado: {when(report.generatedAt)}</p>
    <div className="table-scroll" role="region" aria-label="Transferencias activas de la sucursal" tabIndex={0}>
      <Table className="transfer-impact">
        <TableCaption>Cantidades en la unidad base de cada producto. «No aplica» no equivale a cero.</TableCaption>
        <TableHeader><TableRow>
          <TableHead scope="col">Transferencia</TableHead>
          <TableHead scope="col">Producto</TableHead>
          <TableHead scope="col" className="text-right">Previsión</TableHead>
          <TableHead scope="col" className="text-right">En tránsito</TableHead>
          <TableHead scope="col" className="text-right">Recibido</TableHead>
          <TableHead scope="col" className="text-right">Faltante pendiente</TableHead>
          <TableHead scope="col" className="text-right">Pérdida tratada</TableHead>
        </TableRow></TableHeader>
        <TableBody>{report.items.map(item => <Fragment key={item.externalId}>
          {item.lines.map((line, index) => <TableRow key={`${item.externalId}-${line.sku}-${index}`}>
            {index === 0 && <TableCell rowSpan={item.lines.length} className="transfer-impact-head">
              <span className="font-semibold">{item.code}</span> <TransferStatusBadge status={item.status} />
              <span className="cell-note">{item.role === 'ORIGIN' ? 'Sale hacia' : 'Llega desde'} {item.counterpart.name} ({item.counterpart.code})</span>
              <span className="cell-note">{impactNote(item)}</span>
              {item.estimatedArrivalAt && <span className="cell-note">Llegada estimada: {bogotaDateTime(item.estimatedArrivalAt)}</span>}
            </TableCell>}
            <TableCell className="transfer-impact-product"><span className="font-semibold">{line.name}</span>
              <span className="cell-note">{line.sku}</span></TableCell>
            {[line.forecastQuantity, line.inTransitQuantity, line.receivedQuantity, line.pendingShortageQuantity, line.lostQuantity]
              .map((amount, cell) => <TableCell key={cell} className={`text-right tabular-nums${amount === null ? ' not-calculable' : ''}`}>
                {quantity(amount, line.baseUnitName)}</TableCell>)}
          </TableRow>)}
        </Fragment>)}</TableBody>
      </Table>
    </div>
  </>;
}

// ---------------------------------------------------------------- RF-0048

export function RestockSection({ client, branch, ready, onError, revision, visible }: BranchSection) {
  const { value, loading, error, reload } = useReading({
    read: () => client.restockIndicators(branch), subject: branch ?? '', active: visible && ready, revision, onError,
    fallback: 'No se pudieron consultar los productos próximos a agotarse.',
  });
  return <section className="compliance-section" aria-labelledby="dashboard-restock" aria-busy={loading}>
    <div className="list-heading">
      <div>
        <h3 id="dashboard-restock">Productos próximos a agotarse</h3>
        <p>Productos cuyo disponible es menor o igual a su stock mínimo vigente, la misma condición de las alertas de reabastecimiento.</p>
      </div>
      <Refresh busy={loading} label="Actualizar productos" onClick={() => void reload()} />
    </div>
    {error && <p role="alert">{error}</p>}
    {loading && !value && <p role="status">Consultando productos…</p>}
    {value && (value.items.length === 0 ? <p>Ningún producto de esta sucursal está en su mínimo o por debajo.</p> : <>
      <p className="cell-note">{value.items.length === 1 ? '1 producto' : `${value.items.length} productos`}. Generado: {when(value.generatedAt)}</p>
      <div className="table-scroll" role="region" aria-label="Productos próximos a agotarse" tabIndex={0}>
        <Table>
          <TableCaption>Cantidades en la unidad base de cada producto, ordenadas por SKU.</TableCaption>
          <TableHeader><TableRow>
            <TableHead scope="col">Producto</TableHead>
            <TableHead scope="col" className="text-right">Disponible</TableHead>
            <TableHead scope="col" className="text-right">Mínimo</TableHead>
            <TableHead scope="col">Situación</TableHead>
          </TableRow></TableHeader>
          <TableBody>{value.items.map(item => <TableRow key={item.productExternalId}>
            <TableCell><span className="font-semibold">{item.name}</span><span className="cell-note">{item.sku}</span></TableCell>
            <TableCell className="text-right tabular-nums">{quantity(item.availableQuantity, item.baseUnitName)}</TableCell>
            <TableCell className="text-right tabular-nums">{quantity(item.minimumQuantity, item.baseUnitName)}</TableCell>
            <TableCell>{restockNote(item)}</TableCell>
          </TableRow>)}</TableBody>
        </Table>
      </div>
    </>)}
  </section>;
}

// ---------------------------------------------------------------- RF-0049

export function BranchComparisonSection({ client, onError, revision, visible }: Section) {
  const period = useMonth();
  const { value, loading, error, reload } = useReading({
    read: () => client.branchComparison(period.consulted), subject: period.consulted, active: visible, revision, onError,
    fallback: 'No se pudo consultar la comparativa de sucursales.',
  });
  return <section className="compliance-section" aria-labelledby="dashboard-comparison" aria-busy={loading}>
    <h3 id="dashboard-comparison">Comparativa entre sucursales</h3>
    <p>Todas las sucursales en el mismo mes: ventas confirmadas netas de descuentos, número de ventas y cumplimiento logístico.
      Las ventas no indican rentabilidad. No depende de la sucursal elegida arriba.</p>
    <MonthForm id="comparison-month" label="Mes de la comparativa" action="Comparar sucursales" busy={loading} state={period}
      onSubmit={() => period.submit(() => void reload())} />
    {error && <p role="alert">{error}</p>}
    {loading && !value && <p role="status">Consultando comparativa…</p>}
    {value && <ComparisonView report={value} />}
  </section>;
}

function metricCell(metrics: ComplianceMetrics | null) {
  if (metrics === null) return <TableCell className="not-calculable">Sin datos</TableCell>;
  return <TableCell className={metrics.compliancePercent === null ? 'not-calculable' : undefined}>
    <span className="font-semibold tabular-nums">{compliance(metrics)}</span>
    <span className="cell-note">{complianceBase(metrics)}</span>
  </TableCell>;
}

const countLabel = (item: BranchPerformance) => item.salesCount === null ? 'Sin datos'
  : item.salesCount === 0 ? 'Sin ventas' : `${item.salesCount} ${item.salesCount === 1 ? 'venta' : 'ventas'}`;

function ComparisonView({ report }: { report: BranchComparison }) {
  const points = comparisonPoints(report.branches);
  const fill = report.inProgress ? 'url(#comparison-in-progress)' : 'var(--color-chart)';
  return <>
    <p className="cell-note">{monthName(report.month)}, del {day(report.from)} al {day(report.to)}{report.inProgress ? ' (mes en curso)' : ''}.
      Generado: {when(report.generatedAt)}</p>
    {/* The table below carries every figure; the chart is a visual summary of the same amounts. */}
    {!points.some(point => (point.amount ?? 0) > 0)
      ? <p>Ninguna sucursal tiene ventas confirmadas en este mes: no hay importes que graficar.</p>
      : <figure className="sales-chart" aria-hidden="true">
        <figcaption>Importe neto de ventas confirmadas</figcaption>
        <ResponsiveContainer width="100%" height={points.length * 48 + 32}>
          <BarChart data={points} layout="vertical" margin={{ top: 0, right: 16, bottom: 0, left: 0 }} accessibilityLayer={false}>
            <defs>
              <pattern id="comparison-in-progress" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
                <rect width="6" height="6" fill="var(--color-chart-soft)" />
                <line x1="0" y1="0" x2="0" y2="6" stroke="var(--color-chart)" strokeWidth="3" />
              </pattern>
            </defs>
            <CartesianGrid horizontal={false} stroke="var(--color-border)" />
            <XAxis type="number" tickLine={false} axisLine={false} tick={{ fill: 'var(--color-muted)', fontSize: 12 }}
              tickFormatter={(amount: number) => amount.toLocaleString('es-CO', { notation: 'compact', maximumFractionDigits: 1 })} />
            <YAxis type="category" dataKey="code" width={72} tickLine={false} axisLine={{ stroke: 'var(--color-border)' }}
              tick={{ fill: 'var(--color-muted)', fontSize: 12 }} />
            <Tooltip cursor={{ fill: 'var(--color-background)' }} content={({ active, payload }) => {
              const point = active ? payload?.[0]?.payload as (typeof points)[number] | undefined : undefined;
              return point ? <div className="chart-tooltip"><strong>{point.name} ({point.code})</strong><span>{point.exact}</span></div> : null;
            }} />
            <Bar dataKey="amount" radius={[0, 4, 4, 0]} maxBarSize={24} isAnimationActive={!prefersReducedMotion()}>
              {points.map(point => <Cell key={point.code} fill={fill} />)}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
        {(report.inProgress || points.some(point => point.amount === null)) && <ul className="compliance-legend">
          {report.inProgress && <li><i className="chart-in-progress" />Mes en curso, aún incompleto</li>}
          {points.some(point => point.amount === null) && <li><i className="chart-missing" />Sin barra: sin datos</li>}
        </ul>}
      </figure>}
    <div className="table-scroll" role="region" aria-label="Comparativa entre sucursales" tabIndex={0}>
      <Table>
        <TableCaption>Ordenadas por código. «Sin datos» no equivale a cero y «No calculable» indica que no hubo llegadas confirmadas.</TableCaption>
        <TableHeader><TableRow>
          <TableHead scope="col">Sucursal</TableHead>
          <TableHead scope="col">Ventas</TableHead>
          <TableHead scope="col" className="text-right">Importe neto</TableHead>
          <TableHead scope="col">Cumplimiento como origen</TableHead>
          <TableHead scope="col">Cumplimiento como destino</TableHead>
        </TableRow></TableHeader>
        <TableBody>{report.branches.map(item => <TableRow key={item.branch.externalId}>
          <TableCell><span className="font-semibold">{item.branch.name} ({item.branch.code})</span>
            <span className="cell-note">{comparisonCoverage(item, report)}{item.branch.active ? '' : '. Inactiva'}</span></TableCell>
          <TableCell className={item.dataAvailable ? undefined : 'not-calculable'}>{countLabel(item)}</TableCell>
          <TableCell className={`text-right tabular-nums${item.dataAvailable ? '' : ' not-calculable'}`}>
            {item.netAmount === null ? 'Sin datos' : `${decimal(item.netAmount)} COP`}</TableCell>
          {metricCell(item.asOrigin)}
          {metricCell(item.asDestination)}
        </TableRow>)}</TableBody>
      </Table>
    </div>
  </>;
}
