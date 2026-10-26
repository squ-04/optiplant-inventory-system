package com.optiplant.catalog.dto;

import com.optiplant.identity.dto.BranchCreateRequest;
import java.math.BigDecimal;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** New explicit minimum stock for one product-branch combination, in base units (RF-0001). */
public record ProductMinimumRequest(BigDecimal minimumQuantity) {
    public static ProductMinimumRequest from(JsonNode body) {
        BranchCreateRequest.validateFields(body, Set.of("minimumQuantity"));
        return new ProductMinimumRequest(ProductEnablementRequest.quantity(body.path("minimumQuantity")));
    }
}
