package com.optiplant.logistics.dto;

import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.dto.BranchCreateRequest;
import com.optiplant.logistics.service.RoutePriority;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * A directed route and its manual priority (RF-0041, INC-017/D01–D02). Origin and destination are fixed at creation:
 * an update changes only the code, the priority and the active state, so dispatches keep the trajectory they used.
 */
public record RouteRequest(String code, UUID origin, UUID destination, String priority, boolean active) {
    private static final Set<String> CREATE_FIELDS = Set.of("code", "origin", "destination", "priority");
    private static final Set<String> UPDATE_FIELDS = Set.of("code", "priority", "active");

    public static RouteRequest create(JsonNode body) {
        validate(body, CREATE_FIELDS, "El origen y el destino de una ruta no se modifican: registre otra ruta.");
        return new RouteRequest(code(body), branch(body.path("origin"), "origen"), branch(body.path("destination"), "destino"),
            priority(body), true);
    }

    public static RouteRequest update(JsonNode body) {
        validate(body, UPDATE_FIELDS, "El origen y el destino de una ruta no se modifican: registre otra ruta.");
        if (!body.path("active").isBoolean()) throw invalid("El estado de la ruta debe ser verdadero o falso.");
        return new RouteRequest(code(body), null, null, priority(body), body.path("active").asBoolean());
    }

    private static void validate(JsonNode body, Set<String> fields, String fixedTrajectory) {
        if (body == null || !body.isObject()) throw invalid("La solicitud no es válida.");
        body.propertyNames().forEach(name -> {
            if ((name.equals("origin") || name.equals("destination")) && !fields.contains(name)) throw invalid(fixedTrajectory);
            if (!fields.contains(name)) throw invalid("Campo no admitido: " + name + ".");
        });
        BranchCreateRequest.validateFields(body, fields);
    }

    private static String code(JsonNode body) {
        JsonNode value = body.path("code");
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 64) {
            throw invalid("El código es obligatorio y admite hasta 64 caracteres.");
        }
        if (!value.asText().equals(value.asText().strip())) throw invalid("El código no admite espacios al inicio ni al final.");
        return value.asText();
    }

    private static UUID branch(JsonNode value, String role) {
        try {
            return UUID.fromString(value.asText());
        } catch (IllegalArgumentException error) {
            throw invalid("Seleccione la sucursal " + role + " de la ruta.");
        }
    }

    private static String priority(JsonNode body) {
        String priority = body.path("priority").asText();
        if (!body.path("priority").isTextual() || !RoutePriority.isCode(priority)) {
            throw invalid("Prioridad no admitida: use " + RoutePriority.codes() + ".");
        }
        return priority;
    }

    private static ApiException invalid(String detail) {
        return new ApiException(400, "VALIDATION_FAILED", detail);
    }
}
