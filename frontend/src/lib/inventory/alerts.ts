/**
 * RF-0020 presentation helpers. The restock condition itself (available <= minimum) is evaluated only by the server;
 * the interface shows the alerts it stores and never recomputes them from quantities.
 */

/** Text of the persistent notice for managers and administrators; null when nothing needs restocking. */
export function restockNotice(activeCount: number, network: boolean): string | null {
  if (activeCount <= 0) return null;
  const scope = network ? 'en la red' : 'en su sucursal';
  return activeCount === 1
    ? `1 producto ${scope} está en su stock mínimo o por debajo.`
    : `${activeCount} productos ${scope} están en su stock mínimo o por debajo.`;
}

/** Accessible name of the navigation entry, so the count is not conveyed by the badge alone. */
export function alertsNavigationLabel(activeCount: number): string {
  if (activeCount <= 0) return 'Alertas de reabastecimiento, sin alertas activas';
  return `Alertas de reabastecimiento, ${activeCount} ${activeCount === 1 ? 'activa' : 'activas'}`;
}
