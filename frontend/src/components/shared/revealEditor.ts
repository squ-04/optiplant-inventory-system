/**
 * Brings an opened editor into view after React commits it and focuses its first editable field, so the
 * button that opened it always has a visible result. Respects the reduced-motion preference.
 */
export function revealEditor(id: string) {
  requestAnimationFrame(() => {
    const element = document.getElementById(id);
    if (!element) return;
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    element.scrollIntoView({ block: 'start', behavior: reduce ? 'auto' : 'smooth' });
    element.querySelector<HTMLElement>('input:not([readonly]):not([type="checkbox"]), textarea, select')
      ?.focus({ preventScroll: true });
  });
}
