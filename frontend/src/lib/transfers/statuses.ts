/**
 * The only definition of transfer states in the frontend (RF-0032–RF-0039, INC-015/D04). The type, the response
 * validator, the status badge, the listing filter and the progress steps all derive from it: to rename a state or change
 * its tone, edit it here. The codes must match the backend `TransferStatus` enum and the database CHECK constraint.
 */
export const TRANSFER_STATUS = {
  REQUESTED: { label: 'Solicitada', filterLabel: 'Solicitadas', tone: undefined },
  PREPARING: { label: 'En preparación', filterLabel: 'En preparación', tone: 'bg-warning-muted text-warning' },
  APPROVED: { label: 'Aprobada', filterLabel: 'Aprobadas', tone: 'bg-success-muted text-success' },
  IN_TRANSIT: { label: 'En tránsito', filterLabel: 'En tránsito', tone: 'bg-warning-muted text-warning' },
  RECEIVED_COMPLETE: { label: 'Recibida completa', filterLabel: 'Recibidas completas', tone: 'bg-success-muted text-success' },
  RECEIVED_DISCREPANCY: { label: 'Recibida con discrepancia', filterLabel: 'Recibidas con discrepancia', tone: 'bg-warning-muted text-warning' },
  CANCELLED: { label: 'Cancelada', filterLabel: 'Canceladas', tone: 'line-through decoration-1' },
  REJECTED: { label: 'Rechazada', filterLabel: 'Rechazadas', tone: 'line-through decoration-1' },
} as const satisfies Record<string, { label: string; filterLabel: string; tone: string | undefined }>;

export type TransferStatus = keyof typeof TRANSFER_STATUS;

/** Every state code, in the order of the cycle. */
export const TRANSFER_STATUSES = Object.keys(TRANSFER_STATUS) as TransferStatus[];

export const isTransferStatus = (value: unknown): value is TransferStatus =>
  typeof value === 'string' && Object.hasOwn(TRANSFER_STATUS, value);

/** States that close the cycle before dispatch; they are shown struck through and explained with their reason. */
export const CLOSED_BEFORE_DISPATCH: TransferStatus[] = ['CANCELLED', 'REJECTED'];

/** The steps of this cycle in order; cancellation and rejection close it before dispatch. */
export const TRANSFER_STEPS: TransferStatus[] = ['REQUESTED', 'PREPARING', 'APPROVED', 'IN_TRANSIT'];

/** The progress step names the dispatch (RF-0035) instead of the state it leads to. */
export const STEP_LABEL_OVERRIDES: Partial<Record<TransferStatus, string>> = { IN_TRANSIT: 'Despacho' };
