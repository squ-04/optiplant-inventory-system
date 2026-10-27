import { Badge } from '../ui/badge';

/** Restock state shown as text, never by color alone (RF-0020). */
export function RestockBadge({ active, activeLabel = 'Requiere reabastecimiento', inactiveLabel = 'Sobre el mínimo' }: {
  active: boolean;
  activeLabel?: string;
  inactiveLabel?: string;
}) {
  return <Badge variant="secondary" className={active ? 'bg-warning-muted text-warning' : undefined}>
    {active ? activeLabel : inactiveLabel}
  </Badge>;
}
