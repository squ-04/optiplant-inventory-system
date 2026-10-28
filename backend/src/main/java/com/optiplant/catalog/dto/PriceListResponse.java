package com.optiplant.catalog.dto;

import java.util.List;
import java.util.UUID;

/**
 * A global price list. {@code prices} is returned by the detail query and is null in the list. Each price belongs to
 * one commercial unit of a product; {@code price} is the exact gross price in COP per commercial unit.
 */
public record PriceListResponse(UUID externalId, String code, String name, boolean active, List<Price> prices) {
    public record Price(UUID productExternalId, String sku, String productName, boolean productActive, String unitName,
        boolean unitActive, String price) {}
}
