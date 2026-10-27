package com.optiplant.inventory.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Restock alerts (RF-0020). {@code branch} is the consulted branch, or {@code null} when the administrator reads the
 * whole network. {@code activeCount} counts every active episode in that scope, regardless of {@code limit}. Each item
 * keeps the minimum and stock captured when the episode opened and shows the current values next to them; quantities are
 * exact base-unit decimal strings and {@code currentMinimum} is {@code null} once the combination is no longer enabled.
 */
public record ReorderAlertResponse(Instant readAt, Branch branch, int activeCount, List<Item> items) {

    public record Product(UUID externalId, String sku, String name, String baseUnitName) {}

    public record Item(UUID externalId, String status, Branch branch, Product product, Instant openedAt, Instant resolvedAt,
        String minimumAtOpening, String quantityAtOpening, String currentMinimum, String currentQuantity) {}
}
