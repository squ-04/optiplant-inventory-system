package com.optiplant.transfers.dto;

import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * The authorized write-off of a shortage's pending transit (RF-0039-CA-02): a reason and the evidence that supports the
 * loss, both mandatory. The quantities are the pending ones: the whole pending transit is written off once.
 */
public record TransitLossRequest(String reason, String evidence) {
    public static final int MAX_REASON = 500;
    public static final int MAX_EVIDENCE = 1000;
    private static final Set<String> FIELDS = Set.of("reason", "evidence");

    public static TransitLossRequest from(JsonNode body) {
        if (body == null || !body.isObject()) throw TransferRequest.invalid("La solicitud no es válida.");
        body.propertyNames().forEach(name -> {
            if (!FIELDS.contains(name)) {
                throw TransferRequest.invalid("Campo no admitido: " + name + ". La pérdida indica su motivo y su evidencia.");
            }
        });
        String reason = text(body.path("reason"), "Indique el motivo de la pérdida.", MAX_REASON);
        String evidence = text(body.path("evidence"), "Indique la evidencia de la pérdida (acta, guía, reporte del transportista).",
            MAX_EVIDENCE);
        return new TransitLossRequest(reason, evidence);
    }

    private static String text(JsonNode value, String required, int max) {
        if (!value.isTextual() || value.asText().isBlank()) throw TransferRequest.invalid(required + " No se registró la pérdida.");
        String text = value.asText().strip();
        if (text.length() > max) throw TransferRequest.invalid("El texto admite hasta " + max + " caracteres. No se registró la pérdida.");
        return text;
    }
}
