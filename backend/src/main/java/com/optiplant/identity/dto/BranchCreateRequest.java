package com.optiplant.identity.dto;

import com.optiplant.common.exception.ApiException;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public record BranchCreateRequest(String code, String name) {
    public static BranchCreateRequest from(JsonNode body) {
        validateFields(body, Set.of("code", "name"));
        return new BranchCreateRequest(code(body), name(body));
    }

    static String code(JsonNode body) {
        String value = requiredText(body.path("code"));
        if (value.length() > 64 || !value.equals(value.strip())) throw invalid();
        return value;
    }

    static String name(JsonNode body) {
        String value = requiredText(body.path("name"));
        if (value.length() > 160) throw invalid();
        return value;
    }

    static void validateFields(JsonNode body, Set<String> fields) {
        if (!body.isObject() || body.size() != fields.size() || !fields.stream().allMatch(body::has)) throw invalid();
    }

    private static String requiredText(JsonNode value) {
        if (!value.isTextual() || value.asText().isBlank()) throw invalid();
        return value.asText();
    }

    private static ApiException invalid() {
        return new ApiException(400, "VALIDATION_FAILED", "La solicitud no es válida.");
    }
}
