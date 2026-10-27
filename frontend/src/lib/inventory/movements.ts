import { ApiError } from '../api/client.ts';
import type { MovementDirection, MovementHistoryEntry, MovementKind } from '../api/client.ts';

/** Presentation rules for manual movements; the server validates and decides every effect (RF-0016–RF-0018). */
export interface KindOption { kind: MovementKind; label: string; description: string; direction: MovementDirection | null }

const OPERATOR_KINDS: KindOption[] = [
  { kind: 'MANUAL_ENTRY', label: 'Ingreso ordinario', description: 'Suma existencias que no llegan por una compra ni una transferencia.', direction: 'IN' },
  { kind: 'SALE_RETURN', label: 'Devolución de cliente', description: 'Reingresa unidades devueltas, con o sin la venta de referencia.', direction: 'IN' },
  { kind: 'MANUAL_EXIT', label: 'Retiro o merma', description: 'Descuenta unidades dañadas, vencidas o retiradas.', direction: 'OUT' },
];
const MANAGER_KINDS: KindOption[] = [
  { kind: 'COUNT_ADJUSTMENT', label: 'Ajuste por conteo', description: 'Iguala el sistema con el conteo físico, al costo promedio vigente.', direction: null },
  // A correction only removes units registered in excess; missing units enter as a new entry or a count adjustment.
  { kind: 'ENTRY_CORRECTION', label: 'Corrección de una entrada', description: 'Descuenta unidades registradas de más en una entrada, a su costo histórico.', direction: 'OUT' },
];

/** INC-009/D03: operators register ordinary movements, managers adjustments and corrections; administrators none. */
export function kindsFor(role: string): KindOption[] {
  return role === 'OPERATOR' ? OPERATOR_KINDS : role === 'MANAGER' ? MANAGER_KINDS : [];
}

const LABELS: Record<string, string> = {
  PURCHASE_RECEIPT: 'Recepción de compra', SALE: 'Venta', DISPATCH: 'Despacho de transferencia',
  ARRIVAL: 'Recepción de transferencia', MANUAL_ENTRY: 'Ingreso ordinario', MANUAL_EXIT: 'Retiro o merma',
  SALE_RETURN: 'Devolución de cliente', COUNT_ADJUSTMENT: 'Ajuste por conteo', ENTRY_CORRECTION: 'Corrección de entrada',
  TRANSIT_LOSS: 'Pérdida en tránsito',
};
export const kindLabel = (kind: string) => LABELS[kind] ?? kind;

export const DIFFERENCE_LABELS: Record<string, string> = {
  FINAL_ROUNDING: 'Diferencia de redondeo al agotar existencias',
  HISTORICAL_CORRECTION: 'Diferencia de valoración de la corrección',
};

/** Quantity with the sign of its effect on stock; the decimal string itself is never converted to a number. */
export const signedQuantity = (entry: { direction: MovementDirection; commercialQuantity: string }) =>
  `${entry.direction === 'IN' ? '+' : '−'}${entry.commercialQuantity}`;

const CORRECTABLE = ['PURCHASE_RECEIPT', 'MANUAL_ENTRY', 'SALE_RETURN'];
const positive = (value: string | null) => value !== null && /[1-9]/.test(value);

/** Movements a compensation may reference: sale exits with units still returnable, or entries with units to correct. */
export function referenceCandidates(history: MovementHistoryEntry[], kind: MovementKind) {
  if (kind === 'SALE_RETURN') return history.filter(item => item.kind === 'SALE' && item.direction === 'OUT' && positive(item.pendingQuantity));
  if (kind === 'ENTRY_CORRECTION') return history.filter(item => CORRECTABLE.includes(item.kind) && item.direction === 'IN'
    && positive(item.pendingQuantity));
  return [];
}

/**
 * An entry without reference is valued at the current average when the branch has stock; with no stock it needs a
 * justified unit cost (INC-013/D06). The server re-checks under lock, so this only decides whether to show the field.
 */
export function asksUnitCost(kind: MovementKind, direction: MovementDirection, referenced: boolean, physicalQuantity: string | undefined) {
  const entryWithoutReference = kind === 'MANUAL_ENTRY' || (kind === 'SALE_RETURN' && !referenced)
    || (kind === 'COUNT_ADJUSTMENT' && direction === 'IN');
  return entryWithoutReference && physicalQuantity !== undefined && !positive(physicalQuantity);
}

/**
 * A request whose outcome is unknown (no response, or a server or gateway failure) keeps its idempotency key so a retry
 * cannot duplicate it; a rejection (4xx) or a success is final for that key, so the next submission gets a new one.
 */
export const keepsIdempotencyKey = (reason: unknown) => !(reason instanceof ApiError) || reason.status >= 500;

export const newIdempotencyKey = () => crypto.randomUUID();
