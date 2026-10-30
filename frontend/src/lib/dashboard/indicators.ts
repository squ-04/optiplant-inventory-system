import type { BranchComparison, BranchPerformance, DemandRanking, DemandReport, RestockItem, TransferImpactItem } from '../api/client.ts';
import { decimal } from '../format.ts';
import { bogotaLocal } from '../logistics/routes.ts';
import { TREATMENT_LABEL } from '../transfers/transfers.ts';
import { day } from './dashboard.ts';

const isZero = (value: string) => /^0+(\.0+)?$/.test(value);

/** The `YYYY-MM` month in progress in Bogotá. */
export const currentMonthValue = (now: Date) => bogotaLocal(now).slice(0, 7);

/** Mirrors the validation of the month in the server (`PeriodMonth`, `MonthPeriod`). */
export function monthError(value: string, now: Date): string | null {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(value)) return 'Indique el mes con el formato AAAA-MM.';
  if (Number(value.slice(0, 4)) < 1900) return 'El mes consultado debe estar entre los años 1900 y 9999.';
  return value > currentMonthValue(now) ? 'El mes consultado no puede ser posterior al mes en curso.' : null;
}

/** What a consulted month covers, so an incomplete month is never read as a complete one (INC-018/D07). */
function coverage(available: boolean, inProgress: boolean, partial: boolean, to: string): string {
  if (!available) return 'Sin datos: la sucursal aún no estaba registrada';
  if (inProgress) return `Mes en curso, hasta el ${day(to)}${partial ? ', desde el registro de la sucursal' : ''}`;
  return partial ? 'Mes incompleto: desde el registro de la sucursal' : 'Mes completo';
}

export const demandCoverage = (report: DemandReport) =>
  coverage(report.dataAvailable, report.inProgress, report.partialHistory, report.to);

export const comparisonCoverage = (item: BranchPerformance, report: Pick<BranchComparison, 'inProgress' | 'to'>) =>
  coverage(item.dataAvailable, report.inProgress, item.partialHistory, report.to);

/** The largest quantity of a ranking: both groups use it, so their bars can be compared. */
export const rankingScale = (ranking: DemandRanking) =>
  Math.max(...[...ranking.high, ...ranking.low].map(item => Number(item.soldQuantity)));

/** The width of a bar in percent; only the drawing uses `Number`, the figure shown stays the server's text. */
export const barWidth = (value: string, scale: number) => Math.max(2, Number(value) / scale * 100);

/** A quantity with its unit; null means it does not apply in this state, which is different from zero. */
export const quantity = (value: string | null, unit: string) => value === null ? 'No aplica' : `${decimal(value)} ${unit}`;

/** RF-0047: what the state means for the stock of the consulted branch. The alert never changes a quantity. */
export function impactNote(item: TransferImpactItem): string {
  const origin = item.role === 'ORIGIN';
  if (item.status === 'IN_TRANSIT') return origin ? 'Ya salió del físico de esta sucursal' : 'Aún no entra al físico de esta sucursal';
  if (item.status !== 'RECEIVED_DISCREPANCY') return 'Previsión: aún no reserva ni mueve existencias';
  const nothing = item.lines.every(line => line.receivedQuantity !== null && isZero(line.receivedQuantity));
  const received = nothing ? `No llegó mercancía a ${origin ? 'destino' : 'esta sucursal'}`
    : `Lo recibido ya está en el físico de ${origin ? 'destino' : 'esta sucursal'}`;
  if (item.alertStatus === 'OPEN') return `${received}. Alerta de faltante abierta`;
  if (item.alertStatus === 'RESOLVED') {
    const treatment = item.treatment ? ` con ${TREATMENT_LABEL[item.treatment].toLowerCase()}` : '';
    return `${received}. Alerta resuelta${treatment}; no cambia las cantidades`;
  }
  return received;
}

/** RF-0047: the estimate only informs while the goods are on the way; after the arrival it would read as still pending. */
export const estimatedArrival = (item: TransferImpactItem) => item.status === 'IN_TRANSIT' ? item.estimatedArrivalAt : null;

/** RF-0048: how the available stock stands against its minimum. */
export function restockNote(item: RestockItem): string {
  if (isZero(item.availableQuantity)) return 'Sin existencias';
  return Number(item.availableQuantity) < Number(item.minimumQuantity) ? 'Por debajo de su mínimo' : 'En su mínimo';
}

/** RF-0049: room for the longest branch code on the chart axis, so that no code is cut; very long ones are the limit. */
export const codeAxisWidth = (codes: string[]) => Math.min(160, Math.max(72, Math.max(0, ...codes.map(code => code.length)) * 8 + 16));

/** RF-0049: the branches as chart points; the amount stays a string in the tooltip so no precision is lost when shown. */
export const comparisonPoints = (items: BranchPerformance[]) => items.map(item => ({
  code: item.branch.code, name: item.branch.name, amount: item.netAmount === null ? null : Number(item.netAmount),
  exact: item.netAmount === null ? 'Sin datos' : `${decimal(item.netAmount)} COP`,
}));
