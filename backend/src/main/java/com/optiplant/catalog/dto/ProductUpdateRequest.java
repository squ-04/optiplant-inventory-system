package com.optiplant.catalog.dto;

import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.dto.BranchCreateRequest;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Editable product attributes. The public identity is never part of a request body. */
public record ProductUpdateRequest(String sku, String name, String description, String baseUnitName, boolean baseDivisible) {
    static final Set<String> FIELDS = Set.of("sku", "name", "description", "baseUnitName", "baseDivisible");

    public static ProductUpdateRequest from(JsonNode body) {
        BranchCreateRequest.validateFields(body, FIELDS);
        return attributes(body);
    }

    static ProductUpdateRequest attributes(JsonNode body) {
        String sku = text(body.path("sku"), 64);
        if (!sku.equals(sku.strip())) throw invalid();
        JsonNode description = body.path("description");
        if (!description.isNull() && !description.isTextual()) throw invalid();
        String descriptionValue = description.isNull() || description.asText().isBlank() ? null : description.asText();
        if (!body.path("baseDivisible").isBoolean()) throw invalid();
        return new ProductUpdateRequest(sku, text(body.path("name"), 160), descriptionValue,
            text(body.path("baseUnitName"), 64), body.path("baseDivisible").asBoolean());
    }

    private static String text(JsonNode value, int maxLength) {
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > maxLength) throw invalid();
        return value.asText();
    }

    static ApiException invalid() {
        return new ApiException(400, "VALIDATION_FAILED", "La solicitud no es válida.");
    }
}
