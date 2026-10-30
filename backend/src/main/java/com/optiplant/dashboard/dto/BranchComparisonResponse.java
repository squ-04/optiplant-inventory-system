package com.optiplant.dashboard.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.logistics.dto.ComplianceReportResponse.Metrics;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Performance of every branch in the same month (RF-0049, INC-018/D06–D07), exclusively for the general administrator:
 * confirmed sales net of discounts, number of sales and logistics compliance of RF-0043. Sales are not profit.
 */
public record BranchComparisonResponse(String timeZone, String month, LocalDate from, LocalDate to, boolean inProgress,
                                       Instant generatedAt, List<Item> branches) {
    /**
     * {@code dataAvailable} is false, with every figure null, for a month that ended before the branch was registered;
     * {@code partialHistory} marks the month of its registration. {@code netAmount} has two decimals (HALF_UP). Compliance
     * is reported as origin and as destination apart, as in RF-0043; its percent is null («No calculable») without arrivals.
     */
    public record Item(Branch branch, boolean dataAvailable, boolean partialHistory, Integer salesCount, String netAmount,
                       Metrics asOrigin, Metrics asDestination) {}
}
