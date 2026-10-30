package com.optiplant.dashboard.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Rotation of one product in one branch (RF-0045): units sold / time-weighted average of the physical quantity over the
 * period. The quantity at every instant is rebuilt backwards from the current balance and the ledger changes; nothing is
 * extrapolated: a ledger that starts after the period's start is not enough history.
 */
public final class RotationCalculator {
    public enum Status { CALCULATED, INSUFFICIENT_HISTORY, ZERO_AVERAGE }

    /** A signed change of the physical quantity (entries positive, exits negative) at its instant. */
    public record Change(Instant at, BigDecimal delta) {}

    public record Result(Status status, BigDecimal average, BigDecimal rotation) {}

    private RotationCalculator() {}

    /**
     * @param current the physical quantity now
     * @param changes every change at or after {@code start} (later ones included: they are undone to reach the period)
     */
    public static Result calculate(BigDecimal current, List<Change> changes, Instant historyStartedAt, Instant start,
                                   Instant end, BigDecimal sold) {
        long duration = ChronoUnit.MICROS.between(start, end);
        if (historyStartedAt.isAfter(start) || duration <= 0) return new Result(Status.INSUFFICIENT_HISTORY, null, null);
        List<Change> descending = changes.stream().sorted(Comparator.comparing(Change::at).reversed()).toList();
        BigDecimal quantity = current;
        BigDecimal integral = BigDecimal.ZERO;
        Instant cursor = end;
        for (Change change : descending) {
            if (change.at().isBefore(start)) continue;
            if (!change.at().isBefore(end)) {
                quantity = quantity.subtract(change.delta());
                continue;
            }
            integral = integral.add(quantity.multiply(BigDecimal.valueOf(ChronoUnit.MICROS.between(change.at(), cursor))));
            quantity = quantity.subtract(change.delta());
            cursor = change.at();
        }
        integral = integral.add(quantity.multiply(BigDecimal.valueOf(ChronoUnit.MICROS.between(start, cursor))));
        BigDecimal span = BigDecimal.valueOf(duration);
        BigDecimal average = integral.divide(span, 3, RoundingMode.HALF_UP);
        if (integral.signum() == 0) return new Result(Status.ZERO_AVERAGE, average, null);
        // sold / (integral / span), divided once so the rounding happens only at the end.
        return new Result(Status.CALCULATED, average, sold.multiply(span).divide(integral, 2, RoundingMode.HALF_UP));
    }
}
