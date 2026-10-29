package com.optiplant.logistics.service;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The only route classification criterion (RF-0041, INC-017/D02): manual priority high, medium or low, in this order.
 * The codes must match the {@code logistics_route.priority} and {@code logistics_dispatch.route_priority_snapshot}
 * CHECK constraints (V3) and the frontend's {@code lib/logistics/routes.ts}.
 */
public enum RoutePriority {
    HIGH("alta"),
    MEDIUM("media"),
    LOW("baja");

    private final String label;

    RoutePriority(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static boolean isCode(String code) {
        return Arrays.stream(values()).anyMatch(priority -> priority.name().equals(code));
    }

    /** Every code in classification order, for validation messages. */
    public static String codes() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
