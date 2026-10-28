import { ApiError } from '../api/client.ts';
import type { ListPrice } from '../api/client.ts';
import { keepsIdempotencyKey } from '../inventory/movements.ts';
import { listPriceOf } from '../prices/prices.ts';
import { lineNet, money, sum } from '../purchases/purchases.ts';

/** Lines per sale; mirrors the server limit `SaleRequest.MAX_LINES`. */
export const MAX_SALE_LINES = 100;

/**
 * Presentation rules for sales (RF-0027–RF-0029). The server takes the price from the chosen list, validates stock when
 * confirming and decides every effect; these helpers only preview while typing. Decimal strings are never converted to
 * JavaScript numbers.
 */

/**
 * Exact unit price after the line discount (RF-0029-CA-02: 100 with 10% → 90), or null while incomplete. It is the
 * line net of one unit: the same stored formula, `quantity × price × (1 − discount/100)`, with quantity 1.
 */
export const netUnitPrice = (price: string, discountPercent: string) => lineNet('1', price, discountPercent);

/** Exact base quantity of a commercial quantity, `quantity × factor` (a line net without discount), or null while incomplete. */
export const baseQuantity = (quantity: string, factor: string) => lineNet(quantity, factor, '0');

/** Compares two non-negative decimal strings: negative, zero or positive like a comparator. */
export function compareDecimal(left: string, right: string): number {
  const [li, lf = ''] = left.split('.');
  const [ri, rf = ''] = right.split('.');
  const scale = Math.max(lf.length, rf.length);
  const a = BigInt(li + lf.padEnd(scale, '0'));
  const b = BigInt(ri + rf.padEnd(scale, '0'));
  return a === b ? 0 : a > b ? 1 : -1;
}

export interface Requirement { product: string; quantity: string; factor: string }

/**
 * Products whose lines together ask for more base units than the branch shows as available (RF-0028). It is a
 * warning on possibly stale data: the server validates again, atomically, when confirming.
 */
export function shortages(lines: Requirement[], available: Record<string, string>): Set<string> {
  const required = new Map<string, string[]>();
  for (const line of lines) {
    const base = baseQuantity(line.quantity, line.factor);
    if (base === null) continue;
    required.set(line.product, [...(required.get(line.product) ?? []), base]);
  }
  const short = new Set<string>();
  required.forEach((bases, product) => {
    const stock = available[product];
    if (stock !== undefined && compareDecimal(sum(bases), stock) > 0) short.add(product);
  });
  return short;
}

/** Exact difference of two non-negative decimal strings where `left ≥ right`, such as list subtotal minus net total. */
export function subtract(left: string, right: string): string {
  const scale = Math.max((left.split('.')[1] ?? '').length, (right.split('.')[1] ?? '').length);
  const units = (value: string) => { const [integer, fraction = ''] = value.split('.'); return BigInt(integer + fraction.padEnd(scale, '0')); };
  const digits = (units(left) - units(right)).toString().padStart(scale + 1, '0');
  return scale === 0 ? digits : `${digits.slice(0, -scale)}.${digits.slice(-scale)}`;
}

/**
 * How the form reacts to a failed confirmation (ARQ-007, RF-0027-CA-02). An unknown outcome keeps the key, also if the
 * draft is edited, so a retry never sells twice. `IDEMPOTENCY_CONFLICT` means that key already confirmed a sale with
 * other data: the earlier attempt did withdraw stock, so the records are reloaded and a new key is used from then on.
 */
export function confirmationFailure(reason: unknown): { message: string; keepKey: boolean; alreadyConfirmed: boolean } {
  if (reason instanceof ApiError && reason.problem.code === 'IDEMPOTENCY_CONFLICT') {
    return { keepKey: false, alreadyConfirmed: true, message: 'La confirmación anterior sí quedó registrada antes de editar el '
      + 'borrador y ya descontó existencias. Revise el comprobante más reciente antes de confirmar otra venta; el borrador se conserva.' };
  }
  const message = reason instanceof TypeError
    ? 'Sin comunicación con el servidor. No se sabe si la venta quedó confirmada: repita la confirmación y no se duplicará.'
    : reason instanceof ApiError && reason.status >= 500
      ? 'El servidor no pudo completar la venta. Repita la confirmación; no se duplicará.'
      : reason instanceof Error ? reason.message : 'No se pudo confirmar la venta.';
  return { message, keepKey: keepsIdempotencyKey(reason), alreadyConfirmed: false };
}

export interface ShownPrice { product: string; unit: string; price: string | null }

/**
 * Line numbers (1-based) whose price shown in the form no longer matches the re-read list: changed, removed or no
 * longer usable. The server always applies the current list price (RF-0030), so the operator must not accept a total
 * built from an older one.
 */
export function priceChanges(lines: ShownPrice[], prices: ListPrice[]): number[] {
  return lines.flatMap((line, index) => {
    const current = listPriceOf(prices, line.product, line.unit);
    const usable = current && current.productActive && current.unitActive ? current.price : null;
    const same = usable !== null && line.price !== null && compareDecimal(usable, line.price) === 0;
    return same ? [] : [index + 1];
  });
}

/** The chosen price list was deactivated: local profiles no longer read it (404) and a sale with it is rejected (409). */
export const listUnavailable = (reason: unknown) => reason instanceof ApiError
  && (reason.problem.code === 'PRICE_LIST_NOT_FOUND' || reason.problem.code === 'PRICE_LIST_INACTIVE');

/** Shown discount of a sale: shown list subtotal minus shown net total, so the displayed amounts always add up. */
export const shownDiscount = (gross: string, net: string) => subtract(money(gross), money(net));
