package com.optiplant.dashboard.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Rotation of one product in one branch (RF-0045): units sold / time-weighted average of the physical quantity over the
 * period. The quantity is rebuilt from the current balance and the ledger changes; nothing is extrapolated: a ledger that
 * starts after the period's start is not enough history.
 *
 * <p>With q(end) the quantity at the period's end and d<sub>i</sub> the changes at t<sub>i</sub> inside [start, end), the
 * integral of the quantity is q(end)·(end − start) − Σ d<sub>i</sub>·(t<sub>i</sub> − start). Both sums are aggregated by
 * the database, so the ledger is never loaded row by row.
 */
public final class RotationCalculator {
    public enum Status { CALCULATED, INSUFFICIENT_HISTORY, ZERO_AVERAGE, INCONSISTENT_HISTORY }

    /** A signed change of the physical quantity (entries positive, exits negative) at its instant. */
    public record Change(Instant at, BigDecimal delta) {}

    /**
     * The ledger of one product reduced to two sums: {@code afterEnd} is the net change at or after the period's end, and
     * {@code weightedInPeriod} the sum of each change inside the period times its microseconds since the start.
     */
    public record Ledger(BigDecimal afterEnd, BigDecimal weightedInPeriod) {
        /** Reduces individual changes; the database computes the same sums for the service. */
        public static Ledger of(List<Change> changes, Instant start, Instant end) {
            BigDecimal afterEnd = BigDecimal.ZERO;
            BigDecimal weighted = BigDecimal.ZERO;
            for (Change change : changes) {
                if (change.at().isBefore(start)) continue;
                if (!change.at().isBefore(end)) afterEnd = afterEnd.add(change.delta());
                else weighted = weighted.add(change.delta().multiply(BigDecimal.valueOf(ChronoUnit.MICROS.between(start, change.at()))));
            }
            return new Ledger(afterEnd, weighted);
        }
    }

    public record Result(Status status, BigDecimal average, BigDecimal rotation) {}

    private RotationCalculator() {}

    /** @param current the physical quantity now */
    public static Result calculate(BigDecimal current, Ledger ledger, Instant historyStartedAt, Instant start, Instant end,
                                   BigDecimal sold) {
        long duration = ChronoUnit.MICROS.between(start, end);
        if (historyStartedAt.isAfter(start) || duration <= 0) return new Result(Status.INSUFFICIENT_HISTORY, null, null);
        BigDecimal span = BigDecimal.valueOf(duration);
        BigDecimal atEnd = current.subtract(ledger.afterEnd());
        BigDecimal integral = atEnd.multiply(span).subtract(ledger.weightedInPeriod());
        // A physical quantity is never negative: a negative integral means a ledger that does not match the balance (data
        // changed outside the application), never a rotation to show.
        if (integral.signum() < 0) return new Result(Status.INCONSISTENT_HISTORY, null, null);
        BigDecimal average = integral.divide(span, 3, RoundingMode.HALF_UP);
        if (integral.signum() == 0) return new Result(Status.ZERO_AVERAGE, average, null);
        // sold / (integral / span), divided once so the rounding happens only at the end.
        return new Result(Status.CALCULATED, average, sold.multiply(span).divide(integral, 2, RoundingMode.HALF_UP));
    }
}
