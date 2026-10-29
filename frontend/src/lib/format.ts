import { TRANSFER_STATUS } from './transfers/statuses.ts';

/** Presentation of server values shared by every view, so dates and codes read the same everywhere. */
const LOCALE = 'es-CO';

// dd/mm/aaaa with a four-digit year, as in Colombian business documents, and a 12-hour clock with seconds for audit.
export const dateTime = (value: string) => new Date(value).toLocaleString(LOCALE, {
  day: '2-digit', month: '2-digit', year: 'numeric', hour: 'numeric', minute: '2-digit', second: '2-digit', hour12: true,
});
/** Ends a sentence with a period, unless it already ends with one (a time in «p. m.» or «a. m.»). */
export const sentence = (text: string) => text.endsWith('.') ? text : `${text}.`;
export const clockTime = (value: string) => new Date(value).toLocaleTimeString(LOCALE, { timeStyle: 'medium' });

const ROLES: Record<string, string> = { ADMIN: 'Administrador', MANAGER: 'Gerente', OPERATOR: 'Operador' };
export const roleLabel = (role: string) => ROLES[role] ?? role;

const DIRECTIONS: Record<string, string> = { IN: 'Entrada', OUT: 'Salida' };
export const directionLabel = (direction: string) => DIRECTIONS[direction] ?? direction;

// Statuses of purchases, sales and transfers (data model V2/V3); unknown codes are shown as received. Transfer states
// come from their single definition (RF-0042: «Recibida con discrepancia»).
const DOCUMENT_STATUSES: Record<string, string> = {
  ...Object.fromEntries(Object.entries(TRANSFER_STATUS).map(([code, status]) => [code, status.label])),
  DRAFT: 'Borrador', PENDING: 'Pendiente', RECEIVED: 'Recibida', CANCELLED: 'Cancelada', CONFIRMED: 'Confirmada',
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

/**
 * A unit price in COP: at least 2 decimals, like every amount, and up to the 6 a price list keeps (§5.4), without
 * insignificant zeros beyond the second.
 */
export function price(value: string): string {
  const [integer, fraction = ''] = value.split('.');
  const significant = fraction.replace(/0+$/, '').padEnd(2, '0');
  return decimal(`${integer}.${significant}`);
}

/** Input pattern for decimals typed with a comma; a dot is rejected because it reads as a thousands separator. */
export const decimalInputPattern = (decimals: number, integerDigits = 15) =>
  decimals === 0 ? `\\d{1,${integerDigits}}` : `\\d{1,${integerDigits}}(,\\d{1,${decimals}})?`;

/** Input pattern for a line discount: a percentage from 0 to 100 with up to 6 decimals, typed with a comma. */
export const percentInputPattern = '100(,0{1,6})?|\\d{1,2}(,\\d{1,6})?';

/** A typed decimal in the API format. */
export const apiDecimal = (value: string) => value.trim().replace(',', '.');

/** An API decimal to prefill an input, without insignificant zeros. */
export function inputDecimal(value: string): string {
  const [integer, fraction = ''] = value.split('.');
  const significant = fraction.replace(/0+$/, '');
  return significant ? `${integer},${significant}` : integer;
}
