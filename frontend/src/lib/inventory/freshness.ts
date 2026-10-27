/** Initial polling interval approved in ARQ PEN-003 for inventory reads that need freshness (RF-0015). */
export const INVENTORY_POLL_MS = 2000;

export type Freshness =
  | { kind: 'loading' }
  | { kind: 'live'; readAt: string }
  | { kind: 'stale'; readAt: string; failedAt: string }
  | { kind: 'unavailable'; failedAt: string };

/**
 * Describes how current the shown stock is. A failed refresh never discards the last confirmed read: it is kept and
 * flagged as potentially outdated (RF-0015-CA-02), and nothing is presented as confirmed by the failure.
 */
export function freshness(readAt: string | undefined, failedAt: number | undefined, failing: boolean): Freshness {
  if (failing && failedAt !== undefined) {
    const failed = new Date(failedAt).toISOString();
    return readAt ? { kind: 'stale', readAt, failedAt: failed } : { kind: 'unavailable', failedAt: failed };
  }
  return readAt ? { kind: 'live', readAt } : { kind: 'loading' };
}
