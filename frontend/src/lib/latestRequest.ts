/** Counts the requests of one view so that only the one started last may update it. */
export interface RequestGate { latest: number }

export const requestGate = (): RequestGate => ({ latest: 0 });

/** What a load does with its outcome; each callback runs only while the request is still the latest one. */
export interface LatestHandlers<T> {
  start: () => void;
  value: (value: T) => void;
  error: (reason: unknown) => void;
  done: () => void;
}

/**
 * Runs `read` and applies its outcome only if no later request started through the same gate: a slower earlier
 * response (another period, a repeated refresh) is discarded, together with its error and its end of loading.
 */
export async function loadLatest<T>(gate: RequestGate, read: () => Promise<T>, handlers: LatestHandlers<T>): Promise<void> {
  const request = ++gate.latest;
  const current = () => request === gate.latest;
  handlers.start();
  try {
    const value = await read();
    if (current()) handlers.value(value);
  } catch (reason) {
    if (current()) handlers.error(reason);
  } finally {
    if (current()) handlers.done();
  }
}
