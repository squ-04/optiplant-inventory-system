import { Badge } from '../ui/badge';
import { CLOSED_BEFORE_DISPATCH, TRANSFER_STATUS } from '../../lib/transfers/statuses.ts';
import type { TransferStatus } from '../../lib/transfers/statuses.ts';

// The label always names the state; the tint only reinforces it (states never depend on color alone).
export function TransferStatusBadge({ status }: { status: TransferStatus }) {
  return <Badge variant={CLOSED_BEFORE_DISPATCH.includes(status) ? 'outline' : 'secondary'} className={TRANSFER_STATUS[status].tone}>
    {TRANSFER_STATUS[status].label}
  </Badge>;
}
