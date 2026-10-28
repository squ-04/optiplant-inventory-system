import type { PurchaseOrder, PurchaseStatus } from '../api/client.ts';

/**
 * Presentation rules for purchases; the server validates conditions, computes the stored net and decides every effect
 * (RF-0021, RF-0023–RF-0025). Decimal strings are never converted to JavaScript numbers.
 */

interface Scaled { units: bigint; scale: number }

const scaled = (value: string): Scaled | null => {
  if (!/^\d+(\.\d+)?$/.test(value)) return null;
  const [integer, fraction = ''] = value.split('.');
  return { units: BigInt(integer + fraction), scale: fraction.length };
};

const plain = ({ units, scale }: Scaled): string => {
  const digits = units.toString().padStart(scale + 1, '0');
  const integer = digits.slice(0, digits.length - scale);
  const fraction = digits.slice(digits.length - scale).replace(/0+$/, '');
  return fraction ? `${integer}.${fraction}` : integer;
};

/**
 * Exact line net, `quantity × price × (1 − discount/100)`, the same formula the server stores (data model §5.1), for a
 * preview while typing. Returns null while an operand is incomplete or the discount exceeds 100.
 */
export function lineNet(quantity: string, unitPrice: string, discountPercent: string): string | null {
  const q = scaled(quantity), p = scaled(unitPrice), d = scaled(discountPercent);
  if (!q || !p || !d) return null;
  const hundred = 100n * 10n ** BigInt(d.scale);
  if (d.units > hundred) return null;
  return plain({ units: q.units * p.units * (hundred - d.units), scale: q.scale + p.scale + d.scale + 2 });
}

/** Exact sum of decimal strings. */
export function sum(values: string[]): string {
  const parts = values.map(scaled).filter((value): value is Scaled => value !== null);
  const scale = Math.max(0, ...parts.map(part => part.scale));
  return plain({ units: parts.reduce((total, part) => total + part.units * 10n ** BigInt(scale - part.scale), 0n), scale });
}

/** Monetary display: 2 decimals rounded half up (INC-013/D03), from an exact decimal string. */
export function money(value: string): string {
  const exact = scaled(value);
  if (!exact) return value;
  const shift = 10n ** BigInt(Math.abs(exact.scale - 2));
  const cents = exact.scale <= 2 ? exact.units * shift : (exact.units + shift / 2n) / shift;
  const digits = cents.toString().padStart(3, '0');
  return `${digits.slice(0, -2)}.${digits.slice(-2)}`;
}

const STATUS_LABELS: Record<PurchaseStatus, string> = {
  DRAFT: 'Borrador', PENDING: 'Pendiente de recepción', RECEIVED: 'Recibida', CANCELLED: 'Cancelada',
};
export const purchaseStatusLabel = (status: PurchaseStatus) => STATUS_LABELS[status] ?? status;

export type PurchaseAction = 'submit' | 'receive';

/** Only the operator of the order's branch executes transitions (§5.2); managers and administrators consult. */
export function actionFor(role: string, order: Pick<PurchaseOrder, 'status'>): PurchaseAction | null {
  if (role !== 'OPERATOR') return null;
  return order.status === 'DRAFT' ? 'submit' : order.status === 'PENDING' ? 'receive' : null;
}

/** Payment term as typed: a whole number of days, 0 allowed (INC-012/D04). */
export const MAX_PAYMENT_TERM_DAYS = 36500;
export function paymentTerm(value: string): number | null {
  const trimmed = value.trim();
  if (!/^\d{1,5}$/.test(trimmed)) return null;
  const days = Number(trimmed);
  return days <= MAX_PAYMENT_TERM_DAYS ? days : null;
}

/** A due date (yyyy-mm-dd, Bogotá calendar date) shown as dd/mm/yyyy without time-zone conversion. */
export const dueDate = (value: string) => value.split('-').reverse().join('/');
