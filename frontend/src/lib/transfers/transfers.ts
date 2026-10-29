import type { ShortageTreatment, Transfer, TransferLine, User } from '../api/client.ts';
import { decimal } from '../format.ts';
import { STEP_LABEL_OVERRIDES, TRANSFER_STATUS, TRANSFER_STEPS } from './statuses.ts';
import type { TransferStatus } from './statuses.ts';

export { TRANSFER_STEPS };

/**
 * Presentation rules for transfers (RF-0032–RF-0039). The server validates every quantity and decides who may act under
 * a lock; these rules only choose which actions to offer. Decimal strings are never converted to JavaScript numbers.
 */

export const transferStatusLabel = (status: TransferStatus) => TRANSFER_STATUS[status]?.label ?? status;
export const stepLabel = (status: TransferStatus) => STEP_LABEL_OVERRIDES[status] ?? transferStatusLabel(status);

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
/** The same value without insignificant trailing zeros, as `stripTrailingZeros` on the server. */
const stripped = ({ units, scale }: Scaled): Scaled => {
  while (scale > 0 && units % 10n === 0n) { units /= 10n; scale -= 1; }
  return { units, scale };
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

export type PreparationCheck = 'ok' | 'invalid' | 'decimals' | 'fraction' | 'baseDecimals' | 'baseFraction' | 'exceeds'
  | 'exceedsAvailable';
type PreparedLine = Pick<TransferLine, 'requestedQuantity' | 'factor' | 'originAvailableQuantity' | 'unitDivisible'
  | 'baseDivisible' | 'unit' | 'baseUnitName'>;
const MAX_DECIMALS = 3;

/**
 * Checks a typed preparation quantity (API decimal) with the server's rules and in the server's order: a decimal of 0
 * or more with up to 15 integer and 3 decimal digits; a positive quantity is converted exactly (`UnitConversion.toBase`): no fractions of an
 * indivisible unit, and a base equivalent with at most 3 decimals, whole when the base unit is indivisible; finally,
 * never more than requested (INC-015/D03). Exceeding the origin's availability is only a warning, because preparation
 * does not reserve and the dispatch revalidates stock (INC-015/D05).
 */
export function checkPreparation(quantity: string, line: PreparedLine): PreparationCheck {
  const typed = scaled(quantity);
  if (!typed) return 'invalid';
  // ProductEnablementRequest.QUANTITY: up to 15 integer digits and up to 3 decimal digits, as typed.
  if (typed.scale > MAX_DECIMALS || quantity.split('.')[0].length > 15) return 'decimals';
  if (typed.units !== 0n) {
    if (!line.unitDivisible && stripped(typed).scale > 0) return 'fraction';
    const base = scaled(multiply(quantity, line.factor)!)!;
    if (base.scale > MAX_DECIMALS) return 'baseDecimals';
    if (!line.baseDivisible && base.scale > 0) return 'baseFraction';
  }
  if (compareDecimal(quantity, line.requestedQuantity)! > 0) return 'exceeds';
  const base = multiply(quantity, line.factor);
  if (line.originAvailableQuantity !== null && base !== null && compareDecimal(base, line.originAvailableQuantity)! > 0) {
    return 'exceedsAvailable';
  }
  return 'ok';
}

/** The reason a preparation is rejected or warned, worded as the server words it; null when it is acceptable. */
export function preparationMessage(check: PreparationCheck, line: PreparedLine): string | null {
  switch (check) {
    case 'ok': return null;
    case 'invalid': return 'Indique una cantidad de 0 o más, con coma decimal; use 0 si no puede enviarlo.';
    case 'decimals': return 'La cantidad preparada debe ser un decimal de 0 o más con hasta 3 decimales.';
    case 'fraction': return 'La unidad no admite fracciones: la cantidad debe ser entera.';
    case 'baseDecimals': return 'El equivalente en unidad base excede 3 decimales; no se redondea.';
    case 'baseFraction': return 'El equivalente en unidad base debe ser entero.';
    case 'exceeds': return `No puede superar lo solicitado: ${decimal(line.requestedQuantity)} ${line.unit}.`;
    case 'exceedsAvailable': return `Supera la disponibilidad actual del origen (${decimal(line.originAvailableQuantity ?? '0')} `
      + `${line.baseUnitName}); el despacho no podrá confirmarse sin existencias suficientes.`;
  }
}

/** Blocking checks stop the save, as the server would reject them; the availability warning does not. */
export const blocksPreparation = (check: PreparationCheck) => check !== 'ok' && check !== 'exceedsAvailable';

/** RF-0036/RF-0037: only the destination operator confirms the arrival, once, while the transfer is in transit. */
export const canReceive = (user: Scope, transfer: Sides) => user.role === 'OPERATOR' && atDestination(user, transfer)
  && transfer.status === 'IN_TRANSIT';

type WithShortage = Sides & Pick<Transfer, 'discrepancy'>;
/** RF-0038-CA-02: managers of the origin or the destination mark an open shortage alert «Resuelto». */
export const canResolve = (user: Scope, transfer: WithShortage) => user.role === 'MANAGER'
  && (atOrigin(user, transfer) || atDestination(user, transfer)) && transfer.discrepancy?.status === 'OPEN';

/** Whether something of the shortage is still pending in transit. */
export const pendingInTransit = (transfer: Pick<Transfer, 'lines'>) => transfer.lines.some(line =>
  line.inTransitBaseQuantity !== null && compareDecimal(line.inTransitBaseQuantity, '0') !== 0);

/** RF-0039-CA-02: the origin manager authorizes the loss of what is still in transit, once. */
export const canWriteOff = (user: Scope, transfer: WithShortage & Pick<Transfer, 'lines'>) => user.role === 'MANAGER'
  && atOrigin(user, transfer) && transfer.discrepancy !== null && transfer.discrepancy.transitLoss === null
  && pendingInTransit(transfer);

/** RF-0039-CA-02: a resend is a new request of the destination operator from the same origin. */
export const canResend = (user: Scope, transfer: WithShortage) => user.role === 'OPERATOR' && atDestination(user, transfer)
  && transfer.discrepancy !== null;

/** The selection offered by «Resuelto»; it documents the decision and moves nothing. */
export const TREATMENT_LABEL: Record<ShortageTreatment, string> = {
  RESEND: 'Reenvío', ADJUSTMENT: 'Ajuste', CLAIM: 'Reclamación',
};
export const TREATMENT_HINT: Record<ShortageTreatment, string> = {
  RESEND: 'La reposición se pide con una nueva transferencia que referencia este faltante.',
  ADJUSTMENT: 'Los movimientos que correspondan se registran aparte, con su motivo.',
  CLAIM: 'Solo documenta la reclamación: no ingresa mercancía ni da de baja el tránsito.',
};

/** Lines with a shortage, in the requested unit. */
export const missingLines = (transfer: Pick<Transfer, 'lines'>) => transfer.lines.filter(line =>
  line.missingQuantity !== null && compareDecimal(line.missingQuantity, '0')! > 0);

export type ReceptionCheck = PreparationCheck;
type ReceivedLine = Pick<TransferLine, 'dispatchedQuantity' | 'factor' | 'unitDivisible' | 'baseDivisible' | 'unit' | 'baseUnitName'>;

/**
 * Checks a typed received quantity with the server's rules: the same decimal and unit rules as the preparation, and
 * never more than was dispatched (surpluses are rejected before confirming, RF-0037).
 */
export function checkReception(quantity: string, line: ReceivedLine): ReceptionCheck {
  return checkPreparation(quantity, { ...line, requestedQuantity: line.dispatchedQuantity ?? '0', originAvailableQuantity: null });
}

export function receptionMessage(check: ReceptionCheck, line: ReceivedLine): string | null {
  if (check === 'exceeds') return `No puede superar lo enviado: ${decimal(line.dispatchedQuantity ?? '0')} ${line.unit}. `
    + 'Un sobrante no se recibe en esta transferencia.';
  if (check === 'invalid') return 'Indique la cantidad recibida, con coma decimal; use 0 si no llegó nada.';
  if (check === 'decimals') return 'La cantidad recibida debe ser un decimal de 0 o más con hasta 3 decimales.';
  return preparationMessage(check, { ...line, requestedQuantity: line.dispatchedQuantity ?? '0', originAvailableQuantity: null });
}
