package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.catalog.service.UnitConversion;
import com.optiplant.common.exception.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class UnitConversionTest {
    @Test
    void boxOfTwelveConvertsToTwelveBaseUnits() {
        assertThat(UnitConversion.toBase(new BigDecimal("1"), new BigDecimal("12"), false, false)).isEqualByComparingTo("12");
        assertThat(UnitConversion.toBase(new BigDecimal("3"), new BigDecimal("12"), false, false).toPlainString()).isEqualTo("36.000");
    }

    @Test
    void divisibleUnitsKeepExactlyThreeBaseDecimals() {
        assertThat(UnitConversion.toBase(new BigDecimal("0.5"), new BigDecimal("25.5"), true, true).toPlainString()).isEqualTo("12.750");
        assertThat(UnitConversion.toBase(new BigDecimal("250"), new BigDecimal("0.001"), true, true).toPlainString()).isEqualTo("0.250");
    }

    @Test
    void rejectsFractionsOfIndivisibleUnitsAndResultsBeyondThreeDecimalsWithoutRounding() {
        assertRejected(() -> UnitConversion.toBase(new BigDecimal("1.5"), new BigDecimal("12"), false, false));
        assertRejected(() -> UnitConversion.toBase(new BigDecimal("0.5"), new BigDecimal("0.001"), true, true));
        assertRejected(() -> UnitConversion.toBase(new BigDecimal("0.5"), new BigDecimal("3"), true, false));
        assertRejected(() -> UnitConversion.toBase(BigDecimal.ZERO, new BigDecimal("12"), false, false));
    }

    @Test
    void factorsMatchTheDivisibilityOfTheBaseUnit() {
        UnitConversion.requireValidFactor(new BigDecimal("12"), false, false);
        UnitConversion.requireValidFactor(new BigDecimal("0.001"), true, true);
        assertRejected(() -> UnitConversion.requireValidFactor(new BigDecimal("0.5"), false, false));
        assertRejected(() -> UnitConversion.requireValidFactor(new BigDecimal("2"), true, false));
        assertRejected(() -> UnitConversion.requireValidFactor(new BigDecimal("0.0001"), true, true));
        assertRejected(() -> UnitConversion.requireValidFactor(BigDecimal.ZERO, false, true));
    }

    private static void assertRejected(Runnable conversion) {
        assertThatThrownBy(conversion::run).isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo("VALIDATION_FAILED"));
    }
}
