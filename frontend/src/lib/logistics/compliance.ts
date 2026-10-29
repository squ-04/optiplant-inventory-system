import type { ComplianceMetrics, DeliveryTiming } from '../api/client.ts';
import { decimal } from '../format.ts';
import { BUSINESS_ZONE, bogotaLocal } from './routes.ts';

/** RF-0040 (INC-017/D06): instants of delivery shown on the business clock, with the zone visible next to them. */
export const bogotaDateTime = (value: string) => new Date(value).toLocaleString('es-CO', {
  timeZone: BUSINESS_ZONE, day: '2-digit', month: '2-digit', year: 'numeric', hour: 'numeric', minute: '2-digit', hour12: true,
});
export const ZONE_NOTE = `Hora de Bogotá (${BUSINESS_ZONE})`;

/** A duration in seconds as hours and minutes; under a minute it says so instead of rounding to zero. */
export function duration(seconds: number): string {
  const absolute = Math.abs(seconds);
  if (absolute < 60) return absolute === 0 ? '0 min' : 'menos de 1 min';
  const hours = Math.floor(absolute / 3600);
  const minutes = Math.floor((absolute % 3600) / 60);
  if (hours === 0) return `${minutes} min`;
  return minutes === 0 ? `${hours} h` : `${hours} h ${minutes} min`;
}

/** How the real arrival compares with the estimate; null while nothing has arrived. */
export function deviation(timing: DeliveryTiming): string | null {
  if (timing.deviationSeconds === null || !timing.consistent) return null;
  if (timing.deviationSeconds === 0) return 'A la hora estimada';
  return `${duration(timing.deviationSeconds)} ${timing.deviationSeconds > 0 ? 'después' : 'antes'} de lo estimado`;
}

/** An arrival recorded before its departure only comes from data corrected outside the application. */
export const INCONSISTENT_TIMING = 'La llegada registrada es anterior a la salida; revise los datos del despacho. No cuenta como cumplida';

/** RF-0040: a partial arrival is never presented as a complete delivery. */
export function deliveryOutcome(timing: DeliveryTiming): string {
  if (!timing.consistent) return INCONSISTENT_TIMING;
  if (timing.delivery === null) return timing.overdue ? 'En tránsito, llegada estimada vencida' : 'En tránsito, sin llegada confirmada';
  if (timing.delivery === 'DISCREPANCY') return 'Recibida con discrepancia: entrega parcial, no cumple';
  return timing.onTime ? 'Recibida completa a tiempo: cumple' : 'Recibida completa con retraso: no cumple';
}

/** RF-0043: the KPI as a percentage, or «No calculable» without confirmed arrivals (never an invented 0 %). */
export const compliance = (metrics: ComplianceMetrics) =>
  metrics.compliancePercent === null ? 'No calculable' : `${decimal(metrics.compliancePercent)} %`;

/** The fraction behind the KPI, so the percentage always reads with its base. */
export const complianceBase = (metrics: ComplianceMetrics) => metrics.arrivals === 0
  ? 'Sin llegadas confirmadas en el período'
  : `${metrics.onTimeComplete} de ${metrics.arrivals} ${metrics.arrivals === 1 ? 'llegada' : 'llegadas'} completas a tiempo`;

/** From the first day of the current Bogotá month to today, as `YYYY-MM-DD` dates. */
export function currentMonth(now: Date): { from: string; to: string } {
  const today = bogotaLocal(now).slice(0, 10);
  return { from: `${today.slice(0, 8)}01`, to: today };
}

/** A period is valid when both dates exist and the end is not before the start. */
export function periodError(from: string, to: string): string | null {
  const valid = (value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(`${value}T00:00:00Z`));
  if (!valid(from) || !valid(to)) return 'Indique la fecha inicial y la fecha final del período.';
  return to < from ? 'La fecha final del período no puede ser anterior a la inicial.' : null;
}
