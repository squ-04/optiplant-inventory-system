package com.optiplant.common.web;

import com.optiplant.common.exception.ApiException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** A required `AAAA-MM-DD` limit of a consulted period, shared by the reports and the dashboard. */
public final class PeriodDate {
    public static final int MIN_YEAR = 1900;
    public static final int MAX_YEAR = 9999;

    private PeriodDate() {}

    /** Parses the limit named by {@code which} ("inicial" or "final"), answering 400 when absent or invalid. */
    public static LocalDate parse(String value, String which) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, "VALIDATION_FAILED", "Indique la fecha " + which + " del período (AAAA-MM-DD).");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new ApiException(400, "VALIDATION_FAILED", "La fecha " + which + " del período no es válida: use AAAA-MM-DD.");
        }
        // Bounded so that the period's limits always exist as PostgreSQL instants: an extreme year answers 400, not 500.
        if (date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR) {
            throw new ApiException(400, "VALIDATION_FAILED", "La fecha " + which + " del período debe estar entre los años "
                + MIN_YEAR + " y " + MAX_YEAR + ".");
        }
        return date;
    }
}
