package com.optiplant.catalog.dto;

import java.util.List;
import java.util.UUID;

/**
 * Catalog data only: no stock, cost or valuation. {@code branches} lists local enablement for administrators
 * and is null for local profiles, whose maintenance rights it would otherwise suggest. {@code units} is returned
 * by the detail query and is null in the list.
 */
public record ProductResponse(UUID externalId, String sku, String name, String description, String baseUnitName,
    boolean baseDivisible, boolean active, boolean inUse, List<Branch> branches, List<Unit> units) {

    /** {@code minimumQuantity} is an exact decimal string, or null while the product is not enabled locally. */
    public record Branch(UUID branchExternalId, String branchCode, String branchName, boolean branchActive, boolean enabled,
        String minimumQuantity) {}

    /** {@code factor} is the exact base-unit equivalent of one unit; the base unit always has factor 1. */
    public record Unit(String name, String factor, boolean divisible, boolean base, boolean active) {}
}
