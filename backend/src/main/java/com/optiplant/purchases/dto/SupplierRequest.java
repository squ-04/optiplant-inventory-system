package com.optiplant.purchases.dto;

import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.dto.BranchCreateRequest;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Code and name of a minimum supplier; no fiscal or contact data is accepted (INC-012/D02). */
public record SupplierRequest(String code, String name) {
    private static final Set<String> FIELDS = Set.of("code", "name");

    public static SupplierRequest from(JsonNode body) {
        if (!body.isObject()) throw invalid("La solicitud no es válida.");
        body.propertyNames().forEach(name -> {
            if (!FIELDS.contains(name)) throw invalid("Campo no admitido: " + name + ". El proveedor solo registra código y nombre.");
        });
        BranchCreateRequest.validateFields(body, FIELDS);
        String code = text(body.path("code"), 64, "El código es obligatorio y admite hasta 64 caracteres.");
        if (!code.equals(code.strip())) throw invalid("El código no admite espacios al inicio ni al final.");
        String name = text(body.path("name"), 160, "El nombre es obligatorio y admite hasta 160 caracteres.");
        return new SupplierRequest(code, name.strip());
    }

    private static String text(JsonNode value, int maxLength, String detail) {
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > maxLength) throw invalid(detail);
        return value.asText();
    }

    private static ApiException invalid(String detail) {
        return new ApiException(400, "VALIDATION_FAILED", detail);
    }
}
