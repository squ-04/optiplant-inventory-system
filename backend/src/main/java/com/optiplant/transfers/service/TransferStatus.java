package com.optiplant.transfers.service;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The only definition of transfer states in the backend (INC-015/D04), with the label used in messages. The codes must
 * match the {@code transfers_transfer.status} and {@code transfers_event} CHECK constraints (V3) and the frontend's
 * {@code lib/transfers/statuses.ts}; adding or renaming a code needs a migration of those constraints.
 */
public enum TransferStatus {
    REQUESTED("solicitada"),
    PREPARING("en preparación"),
    APPROVED("aprobada"),
    IN_TRANSIT("en tránsito"),
    RECEIVED_COMPLETE("recibida completa"),
    RECEIVED_DISCREPANCY("recibida con faltantes"),
    CANCELLED("cancelada"),
    REJECTED("rechazada");

    private final String label;

    TransferStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static boolean isCode(String code) {
        return Arrays.stream(values()).anyMatch(status -> status.name().equals(code));
    }

    /** The label of a stored code; an unknown code is shown as is. */
    public static String labelOf(String code) {
        return isCode(code) ? valueOf(code).label : code;
    }

    /** Every code in alphabetical order, as the history filter has always listed them. */
    public static String codes() {
        return Arrays.stream(values()).map(Enum::name).sorted().collect(Collectors.joining(", "));
    }
}
