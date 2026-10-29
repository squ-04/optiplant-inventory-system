package com.optiplant.transfers.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * The data of a dispatch (RF-0035, INC-015/D07): the carrier as free text, the actual departure and the estimated
 * arrival, all mandatory. Instants are ISO-8601 with an explicit offset (the client converts the America/Bogota entry).
 * The quantities are the prepared ones: a dispatch does not change them.
 */
public record TransferDispatchRequest(String carrier, Instant departedAt, Instant estimatedArrivalAt) {
    public static final int MAX_CARRIER = 160;
    private static final Set<String> FIELDS = Set.of("carrier", "departedAt", "estimatedArrivalAt");

    public static TransferDispatchRequest from(JsonNode body) {
        if (body == null || !body.isObject()) throw TransferRequest.invalid("La solicitud no es válida.");
        body.propertyNames().forEach(name -> {
            if (!FIELDS.contains(name)) {
                throw TransferRequest.invalid("Campo no admitido: " + name + ". El despacho indica el transportista, la salida "
                    + "real y la llegada estimada; envía las cantidades preparadas.");
            }
        });
        JsonNode carrier = body.path("carrier");
        if (!carrier.isTextual() || carrier.asText().isBlank()) {
            throw TransferRequest.invalid("Indique el transportista: nombre o identificador.");
        }
        String text = carrier.asText().strip();
        if (text.length() > MAX_CARRIER) {
            throw TransferRequest.invalid("El transportista admite hasta " + MAX_CARRIER + " caracteres.");
        }
        Instant departed = instant(body.path("departedAt"), "Indique la fecha y hora de salida real.");
        Instant estimated = instant(body.path("estimatedArrivalAt"), "Indique la fecha y hora estimada de llegada.");
        if (estimated.isBefore(departed)) {
            throw TransferRequest.invalid("La llegada estimada no puede ser anterior a la salida real.");
        }
        return new TransferDispatchRequest(text, departed, estimated);
    }

    private static Instant instant(JsonNode value, String detail) {
        if (!value.isTextual()) throw TransferRequest.invalid(detail);
        try {
            return OffsetDateTime.parse(value.asText()).toInstant();
        } catch (DateTimeParseException error) {
            throw TransferRequest.invalid(detail + " Use una fecha y hora válidas con zona horaria.");
        }
    }
}
