package com.optiplant.logistics.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Logistics compliance of a period (RF-0043, H v0.15, INC-017/D04/D07). A transfer belongs to the period by the instant of
 * its single confirmed arrival, in {@code timeZone}. {@code branch} is the consulted branch, null for the whole network.
 * {@code summary} covers every transfer of the scope once; {@code branches} reports each branch as origin and as
 * destination apart; {@code routes} reports each directed route by the route recorded at dispatch. {@code overdueInTransit}
 * lists the transfers still in transit whose estimated arrival fell in the period and has passed: they are shown apart
 * and never counted in the denominator.
 */
public record ComplianceReportResponse(String timeZone, LocalDate from, LocalDate to, Instant generatedAt, Branch branch,
                                       Metrics summary, List<BranchCompliance> branches, List<RouteCompliance> routes,
                                       List<Overdue> overdueInTransit) {
    /**
     * {@code arrivals} are the transfers with a confirmed arrival in the period; {@code onTimeComplete} the complete
     * receptions not later than the estimated arrival; {@code lateComplete} the complete ones after it;
     * {@code discrepancy} the ones received with discrepancy, counted as not compliant whatever their alert state;
     * {@code inconsistentTiming} the complete ones whose recorded arrival precedes the departure (data corrected outside
     * the application): confirmed arrivals, so in the denominator, but never compliant. The four counts add up to
     * arrivals. {@code compliancePercent} is onTimeComplete / arrivals with two decimals, null («No calculable») without
     * arrivals.
     */
    public record Metrics(int arrivals, int onTimeComplete, int lateComplete, int discrepancy, int inconsistentTiming,
                          String compliancePercent) {}

    public record BranchCompliance(Branch branch, Metrics asOrigin, Metrics asDestination) {}

    /** {@code priority} is the route's current classification (RF-0041). */
    public record RouteCompliance(UUID externalId, String code, Branch origin, Branch destination, String priority,
                                  boolean active, Metrics metrics) {}

    public record Overdue(UUID externalId, String code, Branch origin, Branch destination, String routeCode, String carrier,
                          Instant departedAt, Instant estimatedArrivalAt) {}
}
