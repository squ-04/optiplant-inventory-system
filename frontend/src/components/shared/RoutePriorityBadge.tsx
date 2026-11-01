/** Badge with the manual priority of a route, from its single definition in `lib/logistics/routes.ts`. */
import { Badge } from '../ui/badge';
import { ROUTE_PRIORITY } from '../../lib/logistics/routes.ts';
import type { RoutePriority } from '../../lib/logistics/routes.ts';

// The label always names the priority; the tint only reinforces it.
export function RoutePriorityBadge({ priority }: { priority: RoutePriority }) {
  return <Badge variant={ROUTE_PRIORITY[priority].variant} className={ROUTE_PRIORITY[priority].tone}>
    {ROUTE_PRIORITY[priority].label}
  </Badge>;
}
