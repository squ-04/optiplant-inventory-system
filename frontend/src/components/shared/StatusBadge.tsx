import { Badge } from '../ui/badge';

export function StatusBadge({ active, activeLabel = 'Activo', inactiveLabel = 'Inactivo' }: {
  active: boolean;
  activeLabel?: string;
  inactiveLabel?: string;
}) {
  return <Badge variant="secondary" className={active ? 'bg-success-muted text-success' : undefined}>
    {active ? activeLabel : inactiveLabel}
  </Badge>;
}
