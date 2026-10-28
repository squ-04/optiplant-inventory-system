import { Badge } from '../ui/badge';
import type { PurchaseStatus } from '../../lib/api/client.ts';
import { purchaseStatusLabel } from '../../lib/purchases/purchases.ts';

// The label always names the state; the tint only reinforces it (states never depend on color alone).
const TONES: Record<PurchaseStatus, string | undefined> = {
  DRAFT: undefined,
  PENDING: 'bg-warning-muted text-warning',
  RECEIVED: 'bg-success-muted text-success',
  CANCELLED: 'line-through decoration-1',
};

export function PurchaseStatusBadge({ status }: { status: PurchaseStatus }) {
  return <Badge variant={status === 'CANCELLED' ? 'outline' : 'secondary'} className={TONES[status]}>
    {purchaseStatusLabel(status)}
  </Badge>;
}
