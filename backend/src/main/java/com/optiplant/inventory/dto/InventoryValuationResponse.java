package com.optiplant.inventory.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Current weighted-average valuation of one branch (RF-0026), for its manager and administrators only. Each item is the
 * recorded balance of a product with physical stock: the average cost per base unit at 6 decimals and the recorded
 * value, which keeps its rounding differences instead of being rebuilt from the rounded average. {@code value} and
 * {@code totalValue} are rounded half up to 2 decimals for display (§5.4); the {@code exact} fields are not rounded.
 */
public record InventoryValuationResponse(Branch branch, Instant readAt, String totalValue, String exactTotalValue,
                                         List<Item> items) {
    public record Item(UUID productExternalId, String sku, String productName, String baseUnitName,
                       String physicalQuantity, String averageCost, String value, String exactValue) {}
}
