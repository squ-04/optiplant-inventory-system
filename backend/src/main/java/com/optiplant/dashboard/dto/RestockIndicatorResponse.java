package com.optiplant.dashboard.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Products about to run out in one branch (RF-0048): the operable combinations whose available stock is lower than or
 * equal to their current minimum, the same condition as the restock alerts (RF-0020). There is no second threshold.
 * Quantities are in the product's base unit.
 */
public record RestockIndicatorResponse(Instant generatedAt, Branch branch, List<Item> items) {
    public record Item(UUID productExternalId, String sku, String name, String baseUnitName, String availableQuantity,
                       String minimumQuantity) {}
}
