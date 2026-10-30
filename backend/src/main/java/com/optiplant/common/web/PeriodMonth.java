package com.optiplant.common.web;

import com.optiplant.common.exception.ApiException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** An optional `AAAA-MM` month of the dashboard. Absent means the month in progress, resolved by the caller. */
public final class PeriodMonth {
    private PeriodMonth() {}

    /** Parses the month, answering 400 when invalid; null when it was not given. */
    public static YearMonth parse(String value) {
        if (value == null || value.isBlank()) return null;
        YearMonth month;
        try {
            month = YearMonth.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new ApiException(400, "VALIDATION_FAILED", "El mes consultado no es válido: use AAAA-MM.");
        }
        if (month.getYear() < PeriodDate.MIN_YEAR || month.getYear() > PeriodDate.MAX_YEAR) {
            throw new ApiException(400, "VALIDATION_FAILED", "El mes consultado debe estar entre los años "
                + PeriodDate.MIN_YEAR + " y " + PeriodDate.MAX_YEAR + ".");
        }
        return month;
    }
}
