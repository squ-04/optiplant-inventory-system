import type { RotationItem, SalesMonth } from '../api/client.ts';
import { decimal } from '../format.ts';
import { periodError } from '../logistics/compliance.ts';
import { bogotaLocal } from '../logistics/routes.ts';

/** `YYYY-MM` as «octubre de 2026», without time-zone shifts. */
export function monthName(month: string): string {
  const [year, number] = month.split('-').map(Number);
  return new Date(Date.UTC(year, number - 1, 1)).toLocaleDateString('es-CO', { month: 'long', year: 'numeric', timeZone: 'UTC' });
}

/** A `YYYY-MM-DD` date as dd/mm/aaaa. */
export const day = (value: string) => value.split('-').reverse().join('/');

/**
 * RF-0044 (INC-018/D02,D07): what period a month covers, so the month in progress and the month the branch was registered
 * are never read as complete months, and a month without data is never read as zero sales.
 */
export function monthCoverage(month: SalesMonth): string {
  if (!month.dataAvailable) return 'Sin datos: la sucursal aún no estaba registrada';
  if (month.inProgress) return `Mes en curso, hasta el ${day(month.to)}${month.partialHistory ? ', desde el registro de la sucursal' : ''}`;
  if (month.partialHistory) return 'Mes incompleto: desde el registro de la sucursal';
  return 'Mes completo';
}

/** The net amount in COP, or «Sin datos» (never 0) when the branch did not exist yet. */
export const netAmount = (month: SalesMonth) => month.netAmount === null ? 'Sin datos' : `${decimal(month.netAmount)} COP`;

/** The number of sales, distinguishing a real zero from missing data. */
export function salesCount(month: SalesMonth): string {
  if (month.salesCount === null) return 'Sin datos';
  return month.salesCount === 0 ? 'Sin ventas' : `${month.salesCount} ${month.salesCount === 1 ? 'venta' : 'ventas'}`;
}

export interface ProductRow {
  productExternalId: string; sku: string; name: string; baseUnitName: string;
  /** One cell per month, in the order of the months: null when that month has no data. */
  cells: ({ sold: string; returned: string } | null)[];
}

/**
 * Units by product and month in the product's base unit: one row per product, never summed across products with
 * different units. A month with data and no movement of the product shows zero.
 */
export function productRows(months: SalesMonth[]): ProductRow[] {
  const rows = new Map<string, ProductRow>();
  months.forEach(month => month.products.forEach(product => {
    if (!rows.has(product.productExternalId)) {
      rows.set(product.productExternalId, { productExternalId: product.productExternalId, sku: product.sku, name: product.name,
        baseUnitName: product.baseUnitName, cells: [] });
    }
  }));
  for (const row of rows.values()) {
    row.cells = months.map(month => {
      if (!month.dataAvailable) return null;
      const found = month.products.find(product => product.productExternalId === row.productExternalId);
      return { sold: found?.soldQuantity ?? '0.000', returned: found?.returnedQuantity ?? '0.000' };
    });
  }
  return [...rows.values()].sort((left, right) => left.sku.localeCompare(right.sku));
}

/** The months as chart points; the amount stays a string in the tooltip so no precision is lost when shown. */
export function chartPoints(months: SalesMonth[]) {
  return months.map(month => ({
    month: month.month, label: monthName(month.month), amount: month.netAmount === null ? null : Number(month.netAmount),
    exact: netAmount(month), coverage: monthCoverage(month), inProgress: month.inProgress, available: month.dataAvailable,
  }));
}

/** RF-0045: the rotation with two decimals, or «No calculable». */
export const rotation = (item: RotationItem) => item.rotation === null ? 'No calculable' : decimal(item.rotation);

/** Why the rotation is not calculable, or what a zero means. */
export function rotationNote(item: RotationItem): string {
  if (item.status === 'INSUFFICIENT_HISTORY') {
    return `Historia insuficiente: el inventario de la sucursal se registra desde el ${day(bogotaLocal(new Date(item.historyStartedAt)).slice(0, 10))}`;
  }
  if (item.status === 'ZERO_AVERAGE') return 'Sin existencias físicas durante el período';
  // Decided by the units sold, never by the rounded rotation: 1 unit over an average of 1000 also shows 0,00.
  return /^0(\.0+)?$/.test(item.soldQuantity) ? 'Sin ventas en el período, con existencias' : '';
}

/** A rotation period is valid like a report period and cannot end after today (Bogotá). */
export function rotationPeriodError(from: string, to: string, now: Date): string | null {
  const invalid = periodError(from, to);
  if (invalid) return invalid;
  return to > bogotaLocal(now).slice(0, 10)
    ? 'La fecha final del período no puede ser posterior a hoy: los días futuros no tienen inventario registrado.' : null;
}
