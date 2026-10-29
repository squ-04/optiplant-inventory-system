import { useEffect, useState } from 'react';
import { ArrowRight, RefreshCw, Search } from 'lucide-react';
import { ApiClient, ApiError } from '../lib/api/client.ts';
import type { Branch, ComplianceFilter, ComplianceMetrics, ComplianceReport, User } from '../lib/api/client.ts';
import { dateTime as when } from '../lib/format.ts';
import { ZONE_NOTE, bogotaDateTime, compliance, complianceBase, currentMonth, periodError } from '../lib/logistics/compliance.ts';
import { RoutePriorityBadge } from '../components/shared/RoutePriorityBadge.tsx';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';

interface Props { client: ApiClient; user: User; onError: (reason: unknown) => void; revision: number; visible: boolean }

/**
 * RF-0043: logistics compliance of a period by branch (as origin and as destination) and by route, with the overdue
 * transfers still in transit apart. Managers read their own branch; the administrator one branch or the network.
 * Operators never reach this view (§5.2): they consult the times of each transfer in Transferencias.
 */
export function LogisticsCompliance({ client, user, onError, revision, visible }: Props) {
  const admin = user.role === 'ADMIN';
  const [branches, setBranches] = useState<Branch[]>([]);
  const [filter, setFilter] = useState<ComplianceFilter>(() => currentMonth(new Date()));
  const [consulted, setConsulted] = useState<ComplianceFilter>(filter);
  const [report, setReport] = useState<ComplianceReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [filterError, setFilterError] = useState('');

  useEffect(() => {
    if (admin && visible) client.visibleBranches().then(setBranches).catch(onError);
  }, [client, admin, visible, revision]);

  async function load(target = consulted) {
    setLoading(true); setError('');
    try { setReport(await client.complianceReport(target)); } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setError(reason instanceof TypeError
        ? 'Sin comunicación con el servidor. El reporte mostrado puede estar desactualizado.'
        : reason instanceof Error ? reason.message : 'No se pudo generar el reporte de cumplimiento.');
    } finally { setLoading(false); }
  }
  useEffect(() => { if (visible) void load(); }, [visible, consulted, revision]);

  function submit() {
    const invalid = periodError(filter.from, filter.to);
    setFilterError(invalid ?? '');
    if (invalid) return;
    if (JSON.stringify(filter) === JSON.stringify(consulted)) void load(); else setConsulted({ ...filter });
  }

  const scope = report?.branch ? `${report.branch.name} (${report.branch.code})` : 'Toda la red';
  return <section aria-labelledby="compliance-title" aria-busy={loading}>
    <h2 id="compliance-title" tabIndex={-1}>Cumplimiento logístico</h2>
    <p>Porcentaje de transferencias recibidas completas sin superar la llegada estimada al despachar, sobre las que
      confirmaron su llegada en el período. Las recibidas con discrepancia cuentan como no cumplidas aunque su alerta esté
      resuelta; las que siguen en tránsito no cuentan hasta que lleguen.</p>

    <form className="purchase-filters" aria-label="Período del reporte" noValidate
      aria-describedby={filterError ? 'compliance-filter-error' : undefined}
      onSubmit={event => { event.preventDefault(); submit(); }}>
      <div><label htmlFor="compliance-from">Desde</label>
        <Input id="compliance-from" type="date" value={filter.from} required aria-invalid={!!filterError}
          onChange={event => setFilter({ ...filter, from: event.target.value })} /></div>
      <div><label htmlFor="compliance-to">Hasta</label>
        <Input id="compliance-to" type="date" value={filter.to} required aria-invalid={!!filterError}
          onChange={event => setFilter({ ...filter, to: event.target.value })} /></div>
      {admin && <div><label htmlFor="compliance-branch">Sucursal</label>
        <select id="compliance-branch" value={filter.branch ?? ''}
          onChange={event => setFilter({ ...filter, branch: event.target.value || undefined })}>
          <option value="">Toda la red</option>
          {branches.map(item => <option key={item.externalId} value={item.externalId}>
            {item.code} · {item.name}{item.active ? '' : ' (inactiva)'}</option>)}
        </select></div>}
      <Button type="submit" disabled={loading}><Search aria-hidden="true" />Generar reporte</Button>
    </form>
    <p className="cell-note">Fechas incluidas completas, según la fecha y hora de confirmación de la llegada. {ZONE_NOTE}.</p>
    {filterError && <p role="alert" id="compliance-filter-error">{filterError}</p>}

    {error && <p role="alert">{error}</p>}
    {loading && !report && <p role="status">Generando reporte de cumplimiento…</p>}
    {report && <>
      <div className="list-heading">
        <div>
          <h3>{scope}: del {day(report.from)} al {day(report.to)}</h3>
          <p>Generado: {when(report.generatedAt)}</p>
        </div>
        <Button variant="outline" disabled={loading} onClick={() => void load()}>
          <RefreshCw aria-hidden="true" />{loading ? 'Actualizando…' : 'Actualizar reporte'}
        </Button>
      </div>

      <dl className="purchase-summary compliance-kpi" aria-label="Resumen del cumplimiento">
        <div className={report.summary.compliancePercent === null ? undefined : 'purchase-summary-received'}>
          <dt>Cumplimiento</dt><dd>{compliance(report.summary)}<small>{complianceBase(report.summary)}</small></dd></div>
        <div><dt>Completas con retraso</dt><dd>{report.summary.lateComplete}</dd></div>
        <div><dt>Recibidas con discrepancia</dt><dd>{report.summary.discrepancy}</dd></div>
        <div><dt>Vencidas en tránsito</dt><dd>{report.overdueInTransit.length}<small>No cuentan en el porcentaje</small></dd></div>
      </dl>
      <Outcomes metrics={report.summary} />

      <section className="compliance-section" aria-labelledby="compliance-branches">
        <h3 id="compliance-branches">Por sucursal</h3>
        <div className="table-scroll" role="region" aria-label="Cumplimiento por sucursal" tabIndex={0}>
          <Table>
            <TableCaption>Cada sucursal como origen (transferencias que envió) y como destino (las que recibió), por separado.</TableCaption>
            <TableHeader><TableRow>
              <TableHead scope="col">Sucursal</TableHead>
              <TableHead scope="col">Como origen</TableHead><TableHead scope="col">Como destino</TableHead>
            </TableRow></TableHeader>
            <TableBody>{report.branches.map(item => <TableRow key={item.branch.externalId}>
              <TableCell><span className="font-semibold">{item.branch.name}</span>
                <span className="cell-note">{item.branch.code}{item.branch.active ? '' : ' · inactiva'}</span></TableCell>
              <MetricsCell metrics={item.asOrigin} />
              <MetricsCell metrics={item.asDestination} />
            </TableRow>)}</TableBody>
          </Table>
        </div>
      </section>

      <section className="compliance-section" aria-labelledby="compliance-routes">
        <h3 id="compliance-routes">Por ruta</h3>
        {report.routes.length === 0
          ? <p>No hay rutas registradas{report.branch ? ' para esta sucursal' : ''}. El administrador las registra en Rutas.</p>
          : <div className="table-scroll" role="region" aria-label="Cumplimiento por ruta" tabIndex={0}>
            <Table>
              <TableCaption>Ruta dirigida registrada en el despacho. La prioridad es la clasificación vigente.</TableCaption>
              <TableHeader><TableRow>
                <TableHead scope="col">Ruta</TableHead><TableHead scope="col">Origen y destino</TableHead>
                <TableHead scope="col">Cumplimiento</TableHead>
              </TableRow></TableHeader>
              <TableBody>{report.routes.map(item => <TableRow key={item.externalId}>
                <TableCell><span className="record-code">{item.code}</span> <RoutePriorityBadge priority={item.priority} />
                  {!item.active && <span className="cell-note">Inactiva</span>}</TableCell>
                <TableCell>{item.origin.code} <ArrowRight aria-hidden="true" className="inline size-4" />
                  <span className="sr-only">hacia</span> {item.destination.code}</TableCell>
                <MetricsCell metrics={item.metrics} />
              </TableRow>)}</TableBody>
            </Table>
          </div>}
      </section>

      <section className="compliance-section" aria-labelledby="compliance-overdue">
        <h3 id="compliance-overdue">Vencidas aún en tránsito</h3>
        {report.overdueInTransit.length === 0
          ? <p>Ninguna transferencia con llegada estimada en el período sigue en tránsito después de su fecha estimada.</p>
          : <div className="table-scroll" role="region" aria-label="Transferencias vencidas en tránsito" tabIndex={0}>
            <Table>
              <TableCaption>Se muestran aparte: entrarán al cálculo cuando se confirme su llegada.</TableCaption>
              <TableHeader><TableRow>
                <TableHead scope="col">Transferencia</TableHead><TableHead scope="col">Origen y destino</TableHead>
                <TableHead scope="col">Salida real</TableHead><TableHead scope="col">Llegada estimada</TableHead>
                <TableHead scope="col">Transportista</TableHead>
              </TableRow></TableHeader>
              <TableBody>{report.overdueInTransit.map(item => <TableRow key={item.externalId}>
                <TableCell><span className="record-code">{item.code}</span>
                  {item.routeCode && <span className="cell-note">Ruta {item.routeCode}</span>}</TableCell>
                <TableCell>{item.origin.code} <ArrowRight aria-hidden="true" className="inline size-4" />
                  <span className="sr-only">hacia</span> {item.destination.code}</TableCell>
                <TableCell className="whitespace-nowrap">{bogotaDateTime(item.departedAt)}</TableCell>
                <TableCell className="whitespace-nowrap">{bogotaDateTime(item.estimatedArrivalAt)}</TableCell>
                <TableCell>{item.carrier}</TableCell>
              </TableRow>)}</TableBody>
            </Table>
          </div>}
      </section>
    </>}
  </section>;
}

function MetricsCell({ metrics }: { metrics: ComplianceMetrics }) {
  return <TableCell className={metrics.compliancePercent === null ? 'not-calculable' : undefined}>
    <span className="font-semibold">{compliance(metrics)}</span>
    <span className="cell-note">{complianceBase(metrics)}{metrics.arrivals > 0
      ? `; ${metrics.lateComplete} con retraso, ${metrics.discrepancy} con discrepancia` : ''}</span>
  </TableCell>;
}

/** How the confirmed arrivals split; hidden from assistive technology because the tiles and the legend say it in text. */
function Outcomes({ metrics }: { metrics: ComplianceMetrics }) {
  if (metrics.arrivals === 0) return null;
  const share = (count: number) => `${(count * 100) / metrics.arrivals}%`;
  return <figure aria-label="Distribución de las llegadas confirmadas">
    <div className="compliance-outcomes" aria-hidden="true">
      <span className="on-time" style={{ width: share(metrics.onTimeComplete) }} />
      <span className="late" style={{ width: share(metrics.lateComplete) }} />
      <span className="discrepancy" style={{ width: share(metrics.discrepancy) }} />
    </div>
    <ul className="compliance-legend">
      <li><i className="on-time" aria-hidden="true" />Completas a tiempo: {metrics.onTimeComplete}</li>
      <li><i className="late" aria-hidden="true" />Completas con retraso: {metrics.lateComplete}</li>
      <li><i className="discrepancy" aria-hidden="true" />Con discrepancia: {metrics.discrepancy}</li>
    </ul>
  </figure>;
}

/** A `YYYY-MM-DD` date as dd/mm/aaaa, without time-zone shifts. */
const day = (value: string) => value.split('-').reverse().join('/');
