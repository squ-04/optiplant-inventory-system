import type { ListPrice } from '../api/client.ts';

/** The price a list already defines for a product unit, or undefined while the unit has no price in it. */
export const listPriceOf = (prices: ListPrice[], product: string, unit: string) =>
  prices.find(price => price.productExternalId === product && price.unitName === unit);

/** Why a price of the list cannot be used in a new sale, or null when it can. */
export function unavailableReason(price: ListPrice): string | null {
  if (!price.productActive) return 'Producto inactivo';
  if (!price.unitActive) return 'Unidad inactiva';
  return null;
}
