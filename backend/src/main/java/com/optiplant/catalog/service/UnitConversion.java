package com.optiplant.catalog.service;

import com.optiplant.common.exception.ApiException;
import java.math.BigDecimal;

/**
 * Exact conversion between a commercial unit and the product's base unit (RF-0013, INC-007/D01–D03). Nothing is
 * rounded: quantities and factors that would need more than three base decimals, or fractions of indivisible units,
 * are rejected.
 */
public final class UnitConversion {
    static final int BASE_SCALE = 3;
    private static final BigDecimal MAX_BASE = new BigDecimal("999999999999999.999");

    private UnitConversion() {}

    /** Validates a factor for a unit of a product whose base unit has the given divisibility. */
    public static void requireValidFactor(BigDecimal factor, boolean unitDivisible, boolean baseDivisible) {
        if (factor.signum() <= 0 || factor.stripTrailingZeros().scale() > BASE_SCALE) {
            throw invalid("El factor debe ser positivo y tener como máximo 3 decimales en la unidad base.");
        }
        if (!baseDivisible && unitDivisible) {
            throw invalid("La unidad base no admite fracciones: la unidad comercial tampoco puede admitirlas.");
        }
        if (!baseDivisible && !isWhole(factor)) {
            throw invalid("La unidad base no admite fracciones: el factor debe ser un número entero.");
        }
    }

    /** Returns the exact base quantity of {@code quantity} commercial units, with three decimals. */
    public static BigDecimal toBase(BigDecimal quantity, BigDecimal factor, boolean unitDivisible, boolean baseDivisible) {
        if (quantity.signum() <= 0) throw invalid("La cantidad debe ser mayor que cero.");
        if (!unitDivisible && !isWhole(quantity)) throw invalid("La unidad no admite fracciones: la cantidad debe ser entera.");
        BigDecimal base = quantity.multiply(factor);
        if (base.stripTrailingZeros().scale() > BASE_SCALE) {
            throw invalid("El equivalente en unidad base excede 3 decimales; no se redondea.");
        }
        if (!baseDivisible && !isWhole(base)) throw invalid("El equivalente en unidad base debe ser entero.");
        if (base.compareTo(MAX_BASE) > 0) throw invalid("El equivalente en unidad base excede el máximo admitido.");
        return base.setScale(BASE_SCALE);
    }

    private static boolean isWhole(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= 0;
    }

    private static ApiException invalid(String detail) {
        return new ApiException(400, "VALIDATION_FAILED", detail);
    }
}
