import type { ReportCategory, ReportDownload } from '../api/client.ts';
import { periodError } from '../logistics/compliance.ts';

/** The exportable categories (INC-020/D02) and the date each one filters by (D04). */
export const REPORT_CATEGORIES: { id: ReportCategory; label: string; description: string; dateNote: string }[] = [
  { id: 'movements', label: 'Movimientos de inventario', description: 'Una fila por movimiento, con su clase, motivo y documento origen. No incluye costos.',
    dateNote: 'Las fechas se aplican a la fecha de registro de cada movimiento.' },
  { id: 'sales', label: 'Ventas', description: 'Una fila por línea de venta, con precio, descuento e importe neto.',
    dateNote: 'Las fechas se aplican a la fecha de confirmación de cada venta.' },
  { id: 'transfers', label: 'Transferencias', description: 'Una fila por línea, con cantidades solicitada, despachada y recibida, diferencia y tratamiento.',
    dateNote: 'Las fechas se aplican a la fecha de solicitud de cada transferencia; despacho y recepción se muestran cuando existen.' },
];

const MAX_MONTHS = 12;

/** The first day out of range: the start plus 12 calendar months, on the last day of a shorter month (`ReportPeriod`). */
function firstDayOutOfRange(from: string): string {
  const year = Number(from.slice(0, 4)) + Math.floor(MAX_MONTHS / 12);
  const month = from.slice(5, 7);
  const last = new Date(Date.UTC(year, Number(month), 0)).getUTCDate();
  return `${year}-${month}-${String(Math.min(Number(from.slice(8, 10)), last)).padStart(2, '0')}`;
}

/** RF-0055: mirrors the server so the person corrects the dates before waiting; the server still decides. */
export function reportPeriodError(from: string, to: string): string | null {
  const invalid = periodError(from, to);
  if (invalid) return invalid;
  return to >= firstDayOutOfRange(from) ? `El período de un reporte no puede superar ${MAX_MONTHS} meses. Reduzca el rango de fechas.` : null;
}

/** What the person is told after the download; an empty file is a valid result that must not read as data. */
export function downloadSummary(download: ReportDownload): { message: string; empty: boolean } {
  if (download.rows === 0) {
    return { empty: true, message: `Se descargó ${download.fileName} sin filas: no hay registros para esos filtros.` };
  }
  const rows = download.rows === null ? '' : download.rows === 1 ? ' con 1 fila' : ` con ${download.rows.toLocaleString('es-CO')} filas`;
  return { empty: false, message: `Se descargó ${download.fileName}${rows}.` };
}

/** Hands the generated file to the browser; nothing is kept in the application. */
export function saveFile(download: ReportDownload): void {
  const url = URL.createObjectURL(download.blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = download.fileName;
  link.hidden = true;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
