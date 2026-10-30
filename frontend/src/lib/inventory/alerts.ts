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

/**
 * Accessible name of the navigation entry, so the count is not conveyed by the badge alone. The badge adds the open
 * shortage alerts of transfers (RF-0038), named apart from the restock ones.
 */
export function alertsNavigationLabel(activeCount: number, openShortages = 0): string {
  if (openShortages <= 0) {
    if (activeCount <= 0) return 'Alertas de reabastecimiento, sin alertas activas';
    return `Alertas de reabastecimiento, ${activeCount} ${activeCount === 1 ? 'activa' : 'activas'}`;
  }
  const restock = activeCount <= 0 ? 'sin reabastecimiento activo'
    : `${activeCount} de reabastecimiento ${activeCount === 1 ? 'activa' : 'activas'}`;
  const shortages = openShortages === 1 ? '1 faltante de transferencia abierto' : `${openShortages} faltantes de transferencias abiertos`;
  return `Alertas: ${restock} y ${shortages}`;
}
