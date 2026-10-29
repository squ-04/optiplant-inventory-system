package com.optiplant.transfers.dto;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Shared parsing of transfer request fields. */
final class TransferText {
    private TransferText() {}

    /** A canonical lowercase UUIDv4 public reference. */
    static UUID uuid(JsonNode value, String detail) {
        if (!value.isTextual()) throw TransferRequest.invalid(detail);
        try {
            UUID reference = UUID.fromString(value.asText());
            if (reference.version() == 4 && reference.variant() == 2 && reference.toString().equalsIgnoreCase(value.asText())) {
                return reference;
            }
        } catch (IllegalArgumentException ignored) { /* reported below */ }
        throw TransferRequest.invalid(detail);
    }
}
