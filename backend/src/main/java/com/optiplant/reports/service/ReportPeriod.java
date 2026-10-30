package com.optiplant.reports.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.common.web.PeriodDate;
import com.optiplant.logistics.service.ComplianceService;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The mandatory dates of an export (RF-0055): inclusive business days in America/Bogota, resolved to the half-open
 * interval [start, end) so that the next day is never included. The exclusive end may reach the start plus 12 calendar
 * months (RNF-CAP-01): 2026-01-01 to 2026-12-31 is allowed, 2026-01-01 to 2027-01-01 is not.
 */
public record ReportPeriod(LocalDate from, LocalDate to) {
    public static final int MAX_MONTHS = 12;

    public static ReportPeriod parse(String from, String to) {
        LocalDate start = PeriodDate.parse(from, "inicial");
        LocalDate end = PeriodDate.parse(to, "final");
        if (end.isBefore(start)) {
            throw new ApiException(400, "VALIDATION_FAILED", "La fecha final del período no puede ser anterior a la inicial.");
        }
        if (end.plusDays(1).isAfter(start.plusMonths(MAX_MONTHS))) {
            throw new ApiException(400, "VALIDATION_FAILED", "El período de un reporte no puede superar " + MAX_MONTHS
                + " meses. Reduzca el rango de fechas.");
        }
        return new ReportPeriod(start, end);
    }

    public Instant start() {
        return from.atStartOfDay(ComplianceService.BUSINESS_ZONE).toInstant();
    }

    public Instant end() {
        return to.plusDays(1).atStartOfDay(ComplianceService.BUSINESS_ZONE).toInstant();
    }
}
