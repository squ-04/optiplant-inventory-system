package com.optiplant.catalog.dto;

import java.util.List;
import java.util.UUID;

/**
 * Catalog of the requesting user's own branch (RF-0012): products explicitly enabled there, even without stock.
 * Quantities are exact base-unit decimal strings. {@code available} is the operable physical stock (there are no
 * reservations, glossary §5.3); {@code inTransit} is dispatched towards the branch and is neither physical nor available.
 * {@code restockAlert} is the operational indicator of RF-0020: the combination has an active restock alert.
 */
public record LocalCatalogResponse(UUID branchExternalId, String branchCode, String branchName, boolean branchActive,
    List<Item> items) {

    public record Item(UUID productExternalId, String sku, String name, String description, String baseUnitName,
        boolean baseDivisible, String minimumQuantity, String physicalQuantity, String availableQuantity, String inTransitQuantity,
        boolean restockAlert, List<ProductResponse.Unit> units) {}
}
