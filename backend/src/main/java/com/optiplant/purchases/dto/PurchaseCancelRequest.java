package com.optiplant.purchases.dto;

import com.optiplant.common.exception.ApiException;
import tools.jackson.databind.JsonNode;

/**
 * Cancellation of a draft or pending order (RF-0022). The reason is optional: the data model keeps it "if applicable"
 * and imposes no new functional datum (§6.3, {@code purchases_event.reason}).
 */
public record PurchaseCancelRequest(String reason) {
    public static final int MAX_REASON = 500;

    public static PurchaseCancelRequest from(JsonNode body) {
        if (body == null || body.isNull()) return new PurchaseCancelRequest(null);
        if (!body.isObject()) throw invalid("La solicitud no es válida.");
        body.propertyNames().forEach(name -> {
            if (!name.equals("reason")) throw invalid("Campo no admitido: " + name + ". La cancelación solo admite un motivo.");
        });
        JsonNode reason = body.path("reason");
        if (reason.isMissingNode() || reason.isNull()) return new PurchaseCancelRequest(null);
        if (!reason.isTextual()) throw invalid("El motivo de la cancelación debe ser un texto.");
        String text = reason.asText().strip();
        if (text.length() > MAX_REASON) {
            throw invalid("El motivo de la cancelación admite hasta " + MAX_REASON + " caracteres.");
        }
        return new PurchaseCancelRequest(text.isEmpty() ? null : text);
    }

    private static ApiException invalid(String detail) {
        return new ApiException(400, "VALIDATION_FAILED", detail);
    }
}
