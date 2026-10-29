import type { Branch, Transfer, User } from '../api/client.ts';

/**
 * The only definition of route priorities in the frontend (RF-0041, INC-017/D02): manual priority high, medium or low,
 * in this order. The codes must match the backend `RoutePriority` enum and the database CHECK constraint.
 */
export const ROUTE_PRIORITY = {
  HIGH: { label: 'Alta', group: 'Prioridad alta', variant: 'secondary', tone: 'bg-accent text-accent-foreground' },
  MEDIUM: { label: 'Media', group: 'Prioridad media', variant: 'secondary', tone: 'bg-secondary text-secondary-foreground' },
  LOW: { label: 'Baja', group: 'Prioridad baja', variant: 'outline', tone: undefined },
} as const satisfies Record<string, { label: string; group: string; variant: 'secondary' | 'outline'; tone: string | undefined }>;

export type RoutePriority = keyof typeof ROUTE_PRIORITY;

/** Every priority, in classification order. */
export const ROUTE_PRIORITIES = Object.keys(ROUTE_PRIORITY) as RoutePriority[];

export const isRoutePriority = (value: unknown): value is RoutePriority =>
  typeof value === 'string' && Object.hasOwn(ROUTE_PRIORITY, value);

export const routePriorityLabel = (priority: RoutePriority) => ROUTE_PRIORITY[priority]?.label ?? priority;

interface Directed { origin: Pick<Branch, 'externalId'>; destination: Pick<Branch, 'externalId'>; active: boolean }

/** The active route a transfer will be dispatched through, as the server chooses it: same origin and destination. */
export function routeFor<T extends Directed>(routes: T[], transfer: Pick<Transfer, 'origin' | 'destination'>): T | null {
  return routes.find(route => route.active && route.origin.externalId === transfer.origin.externalId
    && route.destination.externalId === transfer.destination.externalId) ?? null;
}

/** RF-0035: only the origin operator dispatches, and only an approved transfer. */
export const canDispatch = (user: Pick<User, 'role' | 'branchExternalId'>, transfer: Pick<Transfer, 'status' | 'origin'>) =>
  user.role === 'OPERATOR' && user.branchExternalId === transfer.origin.externalId && transfer.status === 'APPROVED';

/**
 * Business time zone (INC-017/D06). America/Bogota has no daylight saving time: its offset is always -05:00, so a local
 * entry converts to an instant exactly.
 */
export const BUSINESS_ZONE = 'America/Bogota';
/** The single definition of the business offset; the ISO suffix and the millisecond shift derive from it. */
const OFFSET_HOURS = -5;
const OFFSET = `${OFFSET_HOURS < 0 ? '-' : '+'}${String(Math.abs(OFFSET_HOURS)).padStart(2, '0')}:00`;
const OFFSET_MS = OFFSET_HOURS * 60 * 60 * 1000;
/** Mirrors `TransferService.DEPARTURE_CLOCK_TOLERANCE`: a device clock slightly ahead of the server is accepted. */
export const DEPARTURE_CLOCK_TOLERANCE_MS = 5 * 60 * 1000;
const LOCAL = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/;

/** A `datetime-local` value (minutes) read as Bogotá time, as an ISO-8601 instant with offset; null when invalid. */
export function bogotaInstant(local: string): string | null {
  const parts = LOCAL.exec(local);
  if (!parts) return null;
  const [, year, month, day, hour, minute] = parts.map(Number);
  const check = new Date(Date.UTC(year, month - 1, day, hour, minute));
  if (check.getUTCFullYear() !== year || check.getUTCMonth() !== month - 1 || check.getUTCDate() !== day
      || check.getUTCHours() !== hour) return null;
  return `${local}:00${OFFSET}`;
}

/** The Bogotá wall-clock minute of an instant, in `datetime-local` format. */
export function bogotaLocal(instant: Date): string {
  return new Date(instant.getTime() + OFFSET_MS).toISOString().slice(0, 16);
}

export interface DispatchDraft { carrier: string; departedAt: string; estimatedArrivalAt: string }
export const MAX_CARRIER = 160;

/**
 * The reason the server would reject the dispatch data, worded as the server words it; null when acceptable. Mirrors
 * `TransferDispatchRequest` and the time checks of `TransferService.dispatch`: the departure is not later than now (beyond
 * the clock tolerance) nor earlier than the approval's minute, and the estimated arrival is not earlier than the departure.
 */
export function dispatchProblem(draft: DispatchDraft, now: Date, approvedAt: string | null): string | null {
  const carrier = draft.carrier.trim();
  if (!carrier) return 'Indique el transportista: nombre o identificador.';
  if (carrier.length > MAX_CARRIER) return `El transportista admite hasta ${MAX_CARRIER} caracteres.`;
  const departed = bogotaInstant(draft.departedAt);
  if (!departed) return 'Indique la fecha y hora de salida real.';
  const estimated = bogotaInstant(draft.estimatedArrivalAt);
  if (!estimated) return 'Indique la fecha y hora estimada de llegada.';
  if (Date.parse(estimated) < Date.parse(departed)) return 'La llegada estimada no puede ser anterior a la salida real.';
  if (Date.parse(departed) > now.getTime() + DEPARTURE_CLOCK_TOLERANCE_MS) {
    return 'La salida real no puede ser posterior al momento actual: registre el despacho cuando la mercancía haya salido.';
  }
  if (approvedAt !== null && Date.parse(departed) < Math.floor(Date.parse(approvedAt) / 60000) * 60000) {
    return 'La salida real no puede ser anterior a la aprobación de la transferencia.';
  }
  return null;
}
