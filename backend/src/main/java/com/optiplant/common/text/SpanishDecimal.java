package com.optiplant.common.text;

import java.math.BigDecimal;

/**
 * Decimals inside Spanish user messages: comma for decimals and dot for thousands, keeping the exact scale. JSON values
 * keep the API format with a dot; only human-readable text uses this one.
 */
public final class SpanishDecimal {
    private SpanishDecimal() {}

    public static String format(BigDecimal value) {
        String plain = value.abs().toPlainString();
        int point = plain.indexOf('.');
        String integer = point < 0 ? plain : plain.substring(0, point);
        StringBuilder grouped = new StringBuilder(integer);
        for (int index = integer.length() - 3; index > 0; index -= 3) grouped.insert(index, '.');
        String fraction = point < 0 ? "" : "," + plain.substring(point + 1);
        return (value.signum() < 0 ? "−" : "") + grouped + fraction;
    }
}
