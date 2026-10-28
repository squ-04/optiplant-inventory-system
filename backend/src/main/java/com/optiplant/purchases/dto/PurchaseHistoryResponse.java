package com.optiplant.purchases.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import java.time.Instant;
import java.util.List;

/**
 * RF-0025: purchase history by supplier and by product. {@code branch} is null for the whole network.
 * {@code summary} counts every matching order by status, so orders are never confused with received purchases;
 * {@code items} holds the newest matching orders up to the limit.
 */
public record PurchaseHistoryResponse(Instant readAt, Branch branch, Summary summary, List<PurchaseOrderResponse> items) {
    public record Summary(int orders, int drafts, int pending, int received, int cancelled) {}
}
