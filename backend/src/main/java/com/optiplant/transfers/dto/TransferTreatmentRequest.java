package com.optiplant.transfers.dto;

import com.optiplant.common.exception.ApiException;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * The treatment selected when a manager marks a shortage alert as resolved (RF-0038-CA-02, RF-0039-CA-01): resend,
 * adjustment or claim. Selecting it only documents the decision: nothing is moved, settled or reopened.
 */
public record TransferTreatmentRequest(String treatment) {
    public static final Set<String> TREATMENTS = Set.of("RESEND", "ADJUSTMENT", "CLAIM");

    public static TransferTreatmentRequest from(JsonNode body) {
        if (body != null && !body.isNull() && !body.isObject()) throw TransferRequest.invalid("La solicitud no es válida.");
        if (body != null && body.isObject()) {
            body.propertyNames().forEach(name -> {
                if (!name.equals("treatment")) {
                    throw TransferRequest.invalid("Campo no admitido: " + name + ". Solo se selecciona el tratamiento del faltante.");
                }
            });
        }
        JsonNode treatment = body == null ? null : body.path("treatment");
        if (treatment == null || treatment.isMissingNode() || treatment.isNull()
            || (treatment.isTextual() && treatment.asText().isBlank())) {
            throw new ApiException(400, "TREATMENT_REQUIRED", "Seleccione el tratamiento del faltante: reenvío, ajuste o "
                + "reclamación. No se resolvió la alerta.");
        }
        if (!treatment.isTextual() || !TREATMENTS.contains(treatment.asText())) {
            throw TransferRequest.invalid("Tratamiento no admitido: use RESEND (reenvío), ADJUSTMENT (ajuste) o CLAIM "
                + "(reclamación). No se resolvió la alerta.");
        }
        return new TransferTreatmentRequest(treatment.asText());
    }
}
