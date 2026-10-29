import { Badge } from '../ui/badge';
import type { TransferStatus } from '../../lib/api/client.ts';
import { transferStatusLabel } from '../../lib/transfers/transfers.ts';

// The label always names the state; the tint only reinforces it (states never depend on color alone).
const TONES: Record<TransferStatus, string | undefined> = {
  REQUESTED: undefined,
  PREPARING: 'bg-warning-muted text-warning',
  APPROVED: 'bg-success-muted text-success',
  IN_TRANSIT: 'bg-warning-muted text-warning',
  RECEIVED_COMPLETE: 'bg-success-muted text-success',
  RECEIVED_DISCREPANCY: 'bg-warning-muted text-warning',
  CANCELLED: 'line-through decoration-1',
  REJECTED: 'line-through decoration-1',
};

export function TransferStatusBadge({ status }: { status: TransferStatus }) {
  return <Badge variant={status === 'CANCELLED' || status === 'REJECTED' ? 'outline' : 'secondary'} className={TONES[status]}>
    {transferStatusLabel(status)}
  </Badge>;
}
