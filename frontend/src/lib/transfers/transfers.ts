import type { Transfer, TransferStatus, User } from '../api/client.ts';

/**
 * Presentation rules for transfers (RF-0032–RF-0034). The server validates every quantity and decides who may act under
 * a lock; these rules only choose which actions to offer. Decimal strings are never converted to JavaScript numbers.
 */

const STATUS_LABELS: Record<TransferStatus, string> = {
  REQUESTED: 'Solicitada', PREPARING: 'En preparación', APPROVED: 'Aprobada', IN_TRANSIT: 'En tránsito',
  RECEIVED_COMPLETE: 'Recibida completa', RECEIVED_DISCREPANCY: 'Recibida con faltantes', CANCELLED: 'Cancelada',
  REJECTED: 'Rechazada',
};
export const transferStatusLabel = (status: TransferStatus) => STATUS_LABELS[status] ?? status;

/** The steps of this cycle in order; cancellation and rejection close it before dispatch. */
export const TRANSFER_STEPS: TransferStatus[] = ['REQUESTED', 'PREPARING', 'APPROVED', 'IN_TRANSIT'];

type Scope = Pick<User, 'role' | 'branchExternalId'>;
type Sides = Pick<Transfer, 'status' | 'origin' | 'destination'>;
const atOrigin = (user: Scope, transfer: Sides) => user.branchExternalId === transfer.origin.externalId;
const atDestination = (user: Scope, transfer: Sides) => user.branchExternalId === transfer.destination.externalId;

/** RF-0033: the origin operator prepares while the transfer is requested or still in preparation. */
export const canPrepare = (user: Scope, transfer: Sides) => user.role === 'OPERATOR' && atOrigin(user, transfer)
  && (transfer.status === 'REQUESTED' || transfer.status === 'PREPARING');

/** RF-0034: only the origin manager approves or rejects, only while in preparation. */
export const canDecide = (user: Scope, transfer: Sides) => user.role === 'MANAGER' && atOrigin(user, transfer)
  && transfer.status === 'PREPARING';

/**
 * RF-0034-CA-02: before approval the destination operator cancels; once approved and before dispatch, only the origin
 * manager, with a reason. Nothing is cancelled in transit.
 */
export function canCancel(user: Scope, transfer: Sides): boolean {
  if (transfer.status === 'REQUESTED' || transfer.status === 'PREPARING') return user.role === 'OPERATOR' && atDestination(user, transfer);
  return transfer.status === 'APPROVED' && user.role === 'MANAGER' && atOrigin(user, transfer);
}
export const cancelReasonRequired = (transfer: Pick<Transfer, 'status'>) => transfer.status === 'APPROVED';

/** Whether the user sees the transfer as the receiving branch, the sending branch, or as an administrator. */
export function side(user: Scope, transfer: Sides): 'origin' | 'destination' | 'network' {
  return atOrigin(user, transfer) ? 'origin' : atDestination(user, transfer) ? 'destination' : 'network';
}

interface Scaled { units: bigint; scale: number }
const scaled = (value: string): Scaled | null => {
  if (!/^\d+(\.\d+)?$/.test(value)) return null;
  const [integer, fraction = ''] = value.split('.');
  return { units: BigInt(integer + fraction), scale: fraction.length };
};

/** Exact comparison of two decimal strings: negative, zero or positive; null when one is not a decimal. */
export function compareDecimal(left: string, right: string): number | null {
  const a = scaled(left), b = scaled(right);
  if (!a || !b) return null;
  const scale = Math.max(a.scale, b.scale);
  const x = a.units * 10n ** BigInt(scale - a.scale), y = b.units * 10n ** BigInt(scale - b.scale);
  return x === y ? 0 : x < y ? -1 : 1;
}

/** Exact product of two decimal strings, without trailing zeros (base equivalent = quantity × factor). */
export function multiply(left: string, right: string): string | null {
  const a = scaled(left), b = scaled(right);
  if (!a || !b) return null;
  const scale = a.scale + b.scale;
  const digits = (a.units * b.units).toString().padStart(scale + 1, '0');
  const integer = digits.slice(0, digits.length - scale);
  const fraction = digits.slice(digits.length - scale).replace(/0+$/, '');
  return fraction ? `${integer}.${fraction}` : integer;
}

export type PreparationCheck = 'ok' | 'invalid' | 'exceeds' | 'exceedsAvailable';

/**
 * Checks a typed preparation quantity (API decimal) against the request and, when known, the origin's availability in
 * base units. Exceeding the request is blocked (INC-015/D03); exceeding availability is only a warning, because
 * preparation does not reserve and the dispatch revalidates stock (INC-015/D05).
 */
export function checkPreparation(quantity: string, requested: string, factor: string, available: string | null): PreparationCheck {
  const relation = compareDecimal(quantity, requested);
  if (relation === null) return 'invalid';
  if (relation > 0) return 'exceeds';
  const base = multiply(quantity, factor);
  if (available !== null && base !== null && compareDecimal(base, available)! > 0) return 'exceedsAvailable';
  return 'ok';
}
