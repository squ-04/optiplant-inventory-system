package com.optiplant.transfers.dto;

import com.optiplant.common.exception.ApiException;
import tools.jackson.databind.JsonNode;

/**
 * The reason of a rejection or cancellation (RF-0034). The service decides whether it is mandatory: always for a
 * rejection and for cancelling an approved transfer, optional when the destination cancels before approval.
 */
public record TransferReasonRequest(String reason) {
    public static final int MAX_REASON = 500;

    public static TransferReasonRequest from(JsonNode body) {
        if (body == null || body.isNull()) return new TransferReasonRequest(null);
        if (!body.isObject()) throw TransferRequest.invalid("La solicitud no es válida.");
        body.propertyNames().forEach(name -> {
            if (!name.equals("reason")) throw TransferRequest.invalid("Campo no admitido: " + name + ". Solo se admite un motivo.");
        });
        JsonNode reason = body.path("reason");
        if (reason.isMissingNode() || reason.isNull()) return new TransferReasonRequest(null);
        if (!reason.isTextual()) throw TransferRequest.invalid("El motivo debe ser un texto.");
        String text = reason.asText().strip();
        if (text.length() > MAX_REASON) throw TransferRequest.invalid("El motivo admite hasta " + MAX_REASON + " caracteres.");
        return new TransferReasonRequest(text.isEmpty() ? null : text);
    }

    /** Rejects a missing reason where the rule requires one. */
    public String required(String detail) {
        if (reason == null) throw new ApiException(400, "REASON_REQUIRED", detail);
        return reason;
    }
}
