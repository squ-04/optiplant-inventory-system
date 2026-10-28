import { lineNet, sum } from '../purchases/purchases.ts';

/**
 * Presentation rules for sales (RF-0027–RF-0029). The server takes the price from the chosen list, validates stock when
 * confirming and decides every effect; these helpers only preview while typing. Decimal strings are never converted to
 * JavaScript numbers.
 */

/** Exact unit price after the line discount (RF-0029-CA-02: 100 with 10% → 90), or null while incomplete. */
export const netUnitPrice = (price: string, discountPercent: string) => lineNet('1', price, discountPercent);

/** Exact base quantity of a commercial quantity, `quantity × factor`, or null while incomplete. */
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
