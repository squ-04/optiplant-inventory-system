package com.optiplant.dashboard.service;

import static com.optiplant.dashboard.service.DashboardService.BUSINESS_ZONE;

import com.optiplant.common.exception.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * One calendar month in America/Bogota, the single calendar of the dashboard (RF-0044, RF-0046, RF-0049). The month in
 * progress ends now, not at its last day; {@code to} is the last day covered.
 */
record MonthPeriod(YearMonth month, Instant start, Instant end, LocalDate to, boolean inProgress) {
    static MonthPeriod of(YearMonth month, Instant now) {
        boolean inProgress = month.equals(YearMonth.from(now.atZone(BUSINESS_ZONE)));
        return new MonthPeriod(month, month.atDay(1).atStartOfDay(BUSINESS_ZONE).toInstant(),
            inProgress ? now : month.plusMonths(1).atDay(1).atStartOfDay(BUSINESS_ZONE).toInstant(),
            inProgress ? LocalDate.ofInstant(now, BUSINESS_ZONE) : month.atEndOfMonth(), inProgress);
    }

    /** The month asked for, the current one by default; a month that has not started has nothing to consult. */
    static MonthPeriod consulted(YearMonth month, Instant now) {
        YearMonth current = YearMonth.from(now.atZone(BUSINESS_ZONE));
        if (month != null && month.isAfter(current)) {
            throw new ApiException(400, "VALIDATION_FAILED", "El mes consultado no puede ser posterior al mes en curso.");
        }
        return of(month == null ? current : month, now);
    }
}
