import { useEffect, useState } from 'react';
import { Download } from 'lucide-react';
import { ApiClient, ApiError } from '../lib/api/client.ts';
import type { Branch, ReportCategory, User } from '../lib/api/client.ts';
import { ZONE_NOTE, currentMonth } from '../lib/logistics/compliance.ts';
import { REPORT_CATEGORIES, downloadSummary, reportPeriodError, saveFile } from '../lib/reports/reports.ts';
import { TRANSFER_STATUS, TRANSFER_STATUSES } from '../lib/transfers/statuses.ts';
import type { TransferStatus } from '../lib/transfers/statuses.ts';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';

interface Props { client: ApiClient; user: User; onError: (reason: unknown) => void; visible: boolean }

/**
 * RF-0050–RF-0055 (UC-020): direct download of an Excel report. Every rule (permissions, limits, columns) belongs to the
 * server; this view collects the filters and delivers the file. Operators never reach it (§5.2).
 */
export function Reports({ client, user, onError, visible }: Props) {
  const admin = user.role === 'ADMIN';
  const [branches, setBranches] = useState<Branch[]>([]);
  const [category, setCategory] = useState<ReportCategory>('movements');
  const [period, setPeriod] = useState(() => currentMonth(new Date()));
  const [branch, setBranch] = useState('');
  const [status, setStatus] = useState<TransferStatus | ''>('');
  const [generating, setGenerating] = useState(false);
  const [filterError, setFilterError] = useState('');
  const [error, setError] = useState('');
  const [result, setResult] = useState<{ message: string; empty: boolean } | null>(null);

  useEffect(() => {
    if (admin && visible) client.visibleBranches().then(setBranches).catch(onError);
  }, [client, admin, visible]);

  // A result describes the filters that produced it: changing them must not leave it on screen.
  useEffect(() => { setResult(null); setError(''); }, [category, period, branch, status]);

  // The filters stay locked while generating, so the result always belongs to the ones on screen.
  const selected = REPORT_CATEGORIES.find(item => item.id === category)!;

  async function submit() {
    const invalid = reportPeriodError(period.from, period.to);
    setFilterError(invalid ?? '');
    setResult(null);
    setError('');
    if (invalid) return;
    setGenerating(true);
    try {
      const download = await client.downloadReport(category, { ...period, branch: branch || undefined,
        status: category === 'transfers' && status ? status : undefined });
      saveFile(download);
      setResult(downloadSummary(download));
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setError(reason instanceof TypeError ? 'Sin comunicación con el servidor. No se generó ningún archivo.'
        : reason instanceof Error ? reason.message : 'No se pudo generar el reporte.');
    } finally {
      setGenerating(false);
    }
  }

  return <section aria-labelledby="reports-title" aria-busy={generating}>
    <h2 id="reports-title" tabIndex={-1}>Reportes</h2>
    <p>Descargue un libro de Excel para trabajar los datos fuera de la aplicación. Cada reporte admite hasta 12 meses y
      10.000 filas; exportar no cambia ningún registro.</p>

    <form aria-label="Filtros del reporte" noValidate aria-describedby={filterError ? 'reports-filter-error' : undefined}
      onSubmit={event => { event.preventDefault(); void submit(); }}>
      <fieldset className="choice-list">
        <legend>Reporte</legend>
        {REPORT_CATEGORIES.map(item => <label key={item.id} className="choice">
          <input type="radio" name="report-category" value={item.id} checked={category === item.id} disabled={generating}
            onChange={() => setCategory(item.id)} />
          <span><strong>{item.label}</strong><span>{item.description}</span></span>
        </label>)}
      </fieldset>

      <div className="purchase-filters reports-filters">
        <div><label htmlFor="reports-from">Desde</label>
          <Input id="reports-from" type="date" value={period.from} required aria-invalid={!!filterError} disabled={generating}
            onChange={event => setPeriod({ ...period, from: event.target.value })} /></div>
        <div><label htmlFor="reports-to">Hasta</label>
          <Input id="reports-to" type="date" value={period.to} required aria-invalid={!!filterError} disabled={generating}
            onChange={event => setPeriod({ ...period, to: event.target.value })} /></div>
        {admin && <div><label htmlFor="reports-branch">Sucursal</label>
          <select id="reports-branch" value={branch} disabled={generating} onChange={event => setBranch(event.target.value)}>
            <option value="">Toda la red</option>
            {branches.map(item => <option key={item.externalId} value={item.externalId}>
              {item.code} · {item.name}{item.active ? '' : ' (inactiva)'}</option>)}
          </select></div>}
        {category === 'transfers' && <div><label htmlFor="reports-status">Estado</label>
          <select id="reports-status" value={status} disabled={generating} onChange={event => setStatus(event.target.value as TransferStatus | '')}>
            <option value="">Todos</option>
            {TRANSFER_STATUSES.map(item => <option key={item} value={item}>{TRANSFER_STATUS[item].filterLabel}</option>)}
          </select></div>}
      </div>
      <p className="cell-note">{selected.dateNote} Ambas fechas se incluyen completas. {ZONE_NOTE}.
        {!admin && ' El reporte incluye solo su sucursal.'}</p>
      {filterError && <p role="alert" id="reports-filter-error">{filterError}</p>}

      <Button type="submit" disabled={generating}><Download aria-hidden="true" />
        {generating ? 'Generando archivo…' : 'Descargar Excel'}</Button>
    </form>

    {generating && <p role="status">Generando el archivo. La descarga empezará al terminar.</p>}
    {error && <p role="alert">{error}</p>}
    {result && <p role="status" className={result.empty ? 'notice-warning' : undefined}>{result.message}</p>}
  </section>;
}
