package com.optiplant.catalog.dto;

import com.optiplant.identity.dto.BranchCreateRequest;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/** Explicit local enablement: a branch and its minimum stock in base units, including zero. */
public record ProductEnablementRequest(UUID branchExternalId, BigDecimal minimumQuantity) {
    // NUMERIC(18,3): decimal strings only, so precision is never rounded silently.
    private static final Pattern QUANTITY = Pattern.compile("^\\d{1,15}(\\.\\d{1,3})?$");

    public static ProductEnablementRequest from(JsonNode body) {
        BranchCreateRequest.validateFields(body, Set.of("branchExternalId", "minimumQuantity"));
        JsonNode branch = body.path("branchExternalId");
        JsonNode minimum = body.path("minimumQuantity");
        if (!branch.isTextual()) throw ProductUpdateRequest.invalid();
        BigDecimal minimumQuantity = quantity(minimum);
        UUID reference;
        try {
            reference = UUID.fromString(branch.asText());
        } catch (IllegalArgumentException error) {
            throw ProductUpdateRequest.invalid();
        }
        if (reference.version() != 4 || reference.variant() != 2 || !reference.toString().equalsIgnoreCase(branch.asText())) {
            throw ProductUpdateRequest.invalid();
        }
        return new ProductEnablementRequest(reference, minimumQuantity);
    }

    /** Parses an exact base-unit quantity string: zero or positive, at most three decimals. */
    public static BigDecimal quantity(JsonNode value) {
        if (!value.isTextual()) throw ProductUpdateRequest.invalid();
        return quantity(value.asText());
    }

    public static BigDecimal quantity(String value) {
        if (value == null || !QUANTITY.matcher(value).matches()) throw ProductUpdateRequest.invalid();
        return new BigDecimal(value);
    }

    /** Indivisible base units only admit whole minimums. */
    public boolean admits(boolean baseDivisible) {
        return admits(minimumQuantity, baseDivisible);
    }

    public static boolean admits(BigDecimal quantity, boolean divisible) {
        return divisible || quantity.stripTrailingZeros().scale() <= 0;
    }
}
