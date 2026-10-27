import { ApiError } from '../api/client.ts';

/** Initial polling interval approved in ARQ PEN-003 for inventory reads that need freshness (RF-0015). */
export const INVENTORY_POLL_MS = 2000;
/** A read slower than the approved five-second freshness target (RNF-REN-01) is aborted and treated as communication loss. */
export const INVENTORY_TIMEOUT_MS = 5000;

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

/**
 * Communication loss keeps polling and shows stale data: network errors, malformed payloads and server-side outages
 * (5xx). Client rejections (session, permission, unknown branch) stop polling and are reported by the shell.
 */
export function connectionFailure(error: unknown): boolean {
  return !(error instanceof ApiError) || error.status >= 500;
}
