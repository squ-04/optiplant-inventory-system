/**
 * Holds at most one pending confirmation. Each request settles exactly once: the first answer wins, a newer request
 * or a cancellation resolves the pending one as declined, so no caller is left waiting forever.
 */
export function confirmationSlot() {
  let pending: ((accepted: boolean) => void) | null = null;
  const settle = (accepted: boolean) => {
    const resolve = pending;
    pending = null;
    resolve?.(accepted);
  };
  return {
    open: () => {
      settle(false);
      return new Promise<boolean>(resolve => { pending = resolve; });
    },
    settle,
    cancel: () => settle(false),
  };
}
