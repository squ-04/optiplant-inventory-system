package com.optiplant.catalog.dto;

import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.dto.BranchCreateRequest;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Code and name of a global price list; {@code active} is changed only by an update (RF-0030). */
public record PriceListRequest(String code, String name, boolean active) {
    private static final Set<String> CREATE_FIELDS = Set.of("code", "name");
    private static final Set<String> UPDATE_FIELDS = Set.of("code", "name", "active");

    public static PriceListRequest create(JsonNode body) {
        return parse(body, CREATE_FIELDS);
    }

    public static PriceListRequest update(JsonNode body) {
        return parse(body, UPDATE_FIELDS);
    }

    private static PriceListRequest parse(JsonNode body, Set<String> fields) {
        if (!body.isObject()) throw invalid("La solicitud no es válida.");
        body.propertyNames().forEach(name -> {
            if (!fields.contains(name)) throw invalid("Campo no admitido: " + name + ".");
        });
        BranchCreateRequest.validateFields(body, fields);
        String code = text(body.path("code"), 64, "El código es obligatorio y admite hasta 64 caracteres.");
        if (!code.equals(code.strip())) throw invalid("El código no admite espacios al inicio ni al final.");
        String name = text(body.path("name"), 160, "El nombre es obligatorio y admite hasta 160 caracteres.");
        boolean active = true;
        if (fields.contains("active")) {
            if (!body.path("active").isBoolean()) throw invalid("El estado de la lista debe ser verdadero o falso.");
            active = body.path("active").asBoolean();
        }
        return new PriceListRequest(code, name.strip(), active);
    }

    private static String text(JsonNode value, int maxLength, String detail) {
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > maxLength) throw invalid(detail);
        return value.asText();
    }

    private static ApiException invalid(String detail) {
        return new ApiException(400, "VALIDATION_FAILED", detail);
    }
}
