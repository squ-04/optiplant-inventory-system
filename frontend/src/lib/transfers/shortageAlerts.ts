import type { Transfer, TransferHistory } from '../api/client.ts';
import { dateTime as when, sentence } from '../format.ts';
import { TREATMENT_LABEL } from './transfers.ts';

/**
 * RF-0038 presentation helpers for the shell. Shortage alerts are counted next to the restock alerts but worded apart:
 * they never resolve by threshold, only when a manager marks them «Resuelto».
 */

/** Text of the persistent notice for managers and administrators; null when no shortage alert is open. */
export function shortageNotice(openCount: number, network: boolean): string | null {
  if (openCount <= 0) return null;
  const scope = network ? 'en la red' : 'en su sucursal';
  return openCount === 1
    ? `1 faltante de transferencia ${scope} está pendiente de resolver.`
    : `${openCount} faltantes de transferencias ${scope} están pendientes de resolver.`;
}

/**
 * The counts behind the navigation badge: active restock alerts plus every open shortage. Shortages are counted by
 * the listing's `total`, never by its rows, because the listing stops at its limit.
 */
export function alertCounts(allowed: boolean, restock: { activeCount: number } | undefined,
                            shortages: { total: number } | undefined) {
  const active = allowed ? restock?.activeCount ?? 0 : 0;
  const open = allowed ? shortages?.total ?? 0 : 0;
  return { restock: active, shortages: open, badge: active + open };
}

/** How long a resolution by the other manager is announced, measured from the server reading. */
export const RESOLUTION_NOTICE_WINDOW_MS = 24 * 60 * 60 * 1000;

/**
 * Shortage alerts of the reader's scope resolved by someone else within the window and not dismissed by this reader.
 * The window is measured against the server's reading time, never the device clock.
 */
export function recentResolutions(history: TransferHistory | undefined, reader: { code: string },
                                  dismissed: ReadonlySet<string>, windowMs = RESOLUTION_NOTICE_WINDOW_MS): Transfer[] {
  if (!history) return [];
  const since = Date.parse(history.readAt) - windowMs;
  return history.items.filter(transfer => {
    const shortage = transfer.discrepancy;
    return shortage !== null && shortage.status === 'RESOLVED' && shortage.resolvedAt !== null && shortage.resolvedBy !== null
      && shortage.resolvedBy.code !== reader.code && Date.parse(shortage.resolvedAt) >= since && !dismissed.has(shortage.externalId);
  });
}

export function resolutionNotice(transfer: Transfer): string {
  const shortage = transfer.discrepancy!;
  return sentence(`La alerta de faltante de ${transfer.code} (${transfer.origin.code} hacia ${transfer.destination.code}) fue resuelta `
    + `por ${shortage.resolvedBy!.fullName} (${shortage.resolvedBy!.code}) con tratamiento `
    + `${TREATMENT_LABEL[shortage.treatment!].toLowerCase()} el ${when(shortage.resolvedAt!)}`);
}

/** Per-viewer convenience: a dismissed resolution is not announced again in this browser. */
type KeyValueStorage = Pick<Storage, 'getItem' | 'setItem'>;
const dismissalKey = (user: string) => `optiplant.dismissed-shortage-resolutions.${user}`;

export function dismissedResolutions(storage: KeyValueStorage | null, user: string): Set<string> {
  try {
    const value: unknown = JSON.parse(storage?.getItem(dismissalKey(user)) ?? '[]');
    return new Set(Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []);
  } catch { return new Set(); }
}

export function dismissResolution(storage: KeyValueStorage | null, user: string, shortage: string): Set<string> {
  const next = dismissedResolutions(storage, user).add(shortage);
  try { storage?.setItem(dismissalKey(user), JSON.stringify([...next])); } catch { /* Storage blocked: dismissed for this view only. */ }
  return next;
}
