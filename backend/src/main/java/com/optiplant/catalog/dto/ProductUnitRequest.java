package com.optiplant.catalog.dto;

import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.dto.BranchCreateRequest;
import java.math.BigDecimal;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Commercial unit of one product (RF-0013). {@code factor} is the exact number of base units in one commercial unit;
 * at most three decimals, because the base equivalent of a quantity keeps three decimals (INC-007/D03).
 */
public record ProductUnitRequest(String name, BigDecimal factor, boolean divisible, boolean active) {
    private static final Set<String> CREATE = Set.of("name", "factor", "divisible");
    private static final Set<String> UPDATE = Set.of("name", "factor", "active");

    public static ProductUnitRequest create(JsonNode body) {
        BranchCreateRequest.validateFields(body, CREATE);
        if (!body.path("divisible").isBoolean()) throw ProductUpdateRequest.invalid();
        return new ProductUnitRequest(name(body), factor(body), body.path("divisible").asBoolean(), true);
    }

    /** The name identifies the unit inside its product; divisibility is fixed when the unit is created. */
    public static ProductUnitRequest update(JsonNode body) {
        BranchCreateRequest.validateFields(body, UPDATE);
        if (!body.path("active").isBoolean()) throw ProductUpdateRequest.invalid();
        return new ProductUnitRequest(name(body), factor(body), false, body.path("active").asBoolean());
    }

    private static String name(JsonNode body) {
        JsonNode value = body.path("name");
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 64
            || !value.asText().equals(value.asText().strip())) throw ProductUpdateRequest.invalid();
        return value.asText();
    }

    private static BigDecimal factor(JsonNode body) {
        BigDecimal factor = ProductEnablementRequest.quantity(body.path("factor"));
        if (factor.signum() <= 0) {
            // Same wording as UnitConversion.requireValidFactor, which validates the factor against the product.
            throw new ApiException(400, "VALIDATION_FAILED",
                "El factor debe ser positivo y tener como máximo 3 decimales en la unidad base.");
        }
        return factor;
    }
}
