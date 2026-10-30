package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.dashboard.service.RotationCalculator;
import com.optiplant.dashboard.service.RotationCalculator.Change;
import com.optiplant.dashboard.service.RotationCalculator.Result;
import com.optiplant.dashboard.service.RotationCalculator.Status;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** RF-0045: rotation = units sold / time-weighted daily average of the physical quantity, never extrapolated. */
class RotationCalculatorTest {
    static final Instant START = Instant.parse("2025-03-01T05:00:00Z");
    static final Instant END = START.plus(Duration.ofDays(10));
    static final Instant BEFORE = START.minus(Duration.ofDays(30));

    static BigDecimal d(String value) { return new BigDecimal(value); }

    @Test void twentySoldOverAnAverageOfTenRotateTwice() {
        // RF-0045-CA-02: a constant 10 during the whole period, 20 sold.
        Result result = RotationCalculator.calculate(d("10.000"), List.of(), BEFORE, START, END, d("20.000"));
        assertThat(result.status()).isEqualTo(Status.CALCULATED);
        assertThat(result.average()).isEqualByComparingTo("10");
        assertThat(result.rotation()).isEqualTo(d("2.00"));
    }

    @Test void theAverageIsWeightedByTimeAndLaterChangesAreUndone() {
        Instant middle = START.plus(Duration.ofDays(5));
        // 0 during the first half, 20 during the second, then 7 more entered after the period: the current 27 is not used.
        Result result = RotationCalculator.calculate(d("27"), List.of(new Change(middle, d("20")),
            new Change(END.plus(Duration.ofHours(1)), d("7"))), BEFORE, START, END, d("5"));
        assertThat(result.average()).isEqualByComparingTo("10");
        assertThat(result.rotation()).isEqualTo(d("0.50"));
    }

    @Test void zeroSalesWithEnoughHistoryRotateZeroTimes() {
        // RF-0045-CA-03: a real zero, not missing data.
        Result result = RotationCalculator.calculate(d("10"), List.of(), BEFORE, START, END, BigDecimal.ZERO);
        assertThat(result.status()).isEqualTo(Status.CALCULATED);
        assertThat(result.rotation()).isEqualTo(d("0.00"));
    }

    @Test void aZeroAverageIsNotCalculable() {
        Result result = RotationCalculator.calculate(BigDecimal.ZERO, List.of(), BEFORE, START, END, BigDecimal.ZERO);
        assertThat(result.status()).isEqualTo(Status.ZERO_AVERAGE);
        assertThat(result.rotation()).isNull();
    }

    @Test void aLedgerStartedInsideThePeriodIsNotEnoughHistory() {
        // RF-0045-CA-03: the missing days are never completed with zeros.
        Instant started = START.plus(Duration.ofDays(2));
        Result result = RotationCalculator.calculate(d("10"), List.of(new Change(started, d("10"))), started, START, END, d("20"));
        assertThat(result.status()).isEqualTo(Status.INSUFFICIENT_HISTORY);
        assertThat(result.average()).isNull();
        assertThat(result.rotation()).isNull();
    }

    @Test void aLedgerStartedExactlyAtThePeriodStartIsEnough() {
        Result result = RotationCalculator.calculate(d("10"), List.of(), START, START, END, d("20"));
        assertThat(result.rotation()).isEqualTo(d("2.00"));
    }
}
