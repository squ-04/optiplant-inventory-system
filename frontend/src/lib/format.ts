/** Presentation of server values shared by every view, so dates and codes read the same everywhere. */
const LOCALE = 'es-CO';

export const dateTime = (value: string) => new Date(value).toLocaleString(LOCALE, { dateStyle: 'short', timeStyle: 'medium' });
export const clockTime = (value: string) => new Date(value).toLocaleTimeString(LOCALE, { timeStyle: 'medium' });

const ROLES: Record<string, string> = { ADMIN: 'Administrador', MANAGER: 'Gerente', OPERATOR: 'Operador' };
export const roleLabel = (role: string) => ROLES[role] ?? role;

const DIRECTIONS: Record<string, string> = { IN: 'Entrada', OUT: 'Salida' };
export const directionLabel = (direction: string) => DIRECTIONS[direction] ?? direction;

// Statuses of purchases, sales and transfers (data model V2/V3); unknown codes are shown as received.
const DOCUMENT_STATUSES: Record<string, string> = {
  DRAFT: 'Borrador', PENDING: 'Pendiente', RECEIVED: 'Recibida', CANCELLED: 'Cancelada', CONFIRMED: 'Confirmada',
  REQUESTED: 'Solicitada', PREPARING: 'En preparación', APPROVED: 'Aprobada', IN_TRANSIT: 'En tránsito',
  RECEIVED_COMPLETE: 'Recibida completa', RECEIVED_DISCREPANCY: 'Recibida con diferencias',
};
export const documentStatusLabel = (status: string) => DOCUMENT_STATUSES[status] ?? status;

/**
 * Decimal strings from the API (dot separator) shown with a comma for decimals and a dot for thousands. The string is
 * never converted to a number, so the exact scale sent by the server is kept.
 */
export function decimal(value: string): string {
  const negative = value.startsWith('-');
  const [integer, fraction] = (negative ? value.slice(1) : value).split('.');
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return `${negative ? '−' : ''}${grouped}${fraction === undefined ? '' : `,${fraction}`}`;
}

/** Input pattern for decimals typed with a comma; a dot is rejected because it reads as a thousands separator. */
export const decimalInputPattern = (decimals: number, integerDigits = 15) =>
  decimals === 0 ? `\\d{1,${integerDigits}}` : `\\d{1,${integerDigits}}(,\\d{1,${decimals}})?`;

/** A typed decimal in the API format. */
export const apiDecimal = (value: string) => value.trim().replace(',', '.');

/** An API decimal to prefill an input, without insignificant zeros. */
export function inputDecimal(value: string): string {
  const [integer, fraction = ''] = value.split('.');
  const significant = fraction.replace(/0+$/, '');
  return significant ? `${integer},${significant}` : integer;
}
