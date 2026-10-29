package com.optiplant.logistics.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.logistics.dto.ComplianceReportResponse;
import com.optiplant.logistics.dto.ComplianceReportResponse.BranchCompliance;
import com.optiplant.logistics.dto.ComplianceReportResponse.Metrics;
import com.optiplant.logistics.dto.ComplianceReportResponse.Overdue;
import com.optiplant.logistics.dto.ComplianceReportResponse.RouteCompliance;
import com.optiplant.logistics.dto.DeliveryTiming;
import com.optiplant.transfers.service.TransferStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logistics compliance report (RF-0043, H v0.15). KPI = complete receptions not later than the estimated arrival fixed at
 * dispatch / transfers whose single arrival was confirmed in the period (America/Bogota). Receptions with discrepancy
 * count as not compliant whatever their alert state, so resolving an alert never changes the KPI. Transfers still in
 * transit are never in the denominator; the overdue ones are listed apart. Administrators read one branch or the network;
 * managers only their own branch (§5.2). Read-only: it moves nothing.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ComplianceService {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");
    private static final String BRANCH_COLUMNS = "%1$s.external_id AS %1$s_id,%1$s.code AS %1$s_code,%1$s.name AS %1$s_name,"
        + "%1$s.active AS %1$s_active";

    private final JdbcTemplate jdbc;
    private final ScopePolicy policy;

    public ComplianceService(JdbcTemplate jdbc, ScopePolicy policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    /** A confirmed arrival of the period with the branches and the route of its transfer. */
    private record Arrival(long origin, long destination, Long route, boolean onTime, boolean complete) {}

    public ComplianceReportResponse report(SessionPrincipal principal, LocalDate from, LocalDate to, UUID branch) {
        if (to.isBefore(from)) {
            throw new ApiException(400, "VALIDATION_FAILED", "La fecha final del período no puede ser anterior a la inicial.");
        }
        boolean admin = "ADMIN".equals(principal.role());
        UUID scope = branch != null ? branch : admin ? null : principal.branchExternalId();
        if (scope != null) policy.requireHistoryRead(principal, scope);
        Long scopeId = scope == null ? null : jdbc.queryForList("SELECT id FROM identity_branch WHERE external_id=?", Long.class, scope)
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal."));
        OffsetDateTime start = from.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        String inScope = scopeId == null ? "" : " AND (t.origin_branch_id=" + scopeId + " OR t.destination_branch_id=" + scopeId + ")";

        List<Arrival> arrivals = jdbc.query("""
            SELECT t.status,t.origin_branch_id,t.destination_branch_id,ld.route_id,ld.dispatched_at,ld.estimated_arrival_at,la.arrived_at
            FROM logistics_arrival la JOIN logistics_dispatch ld ON ld.id=la.dispatch_id JOIN transfers_transfer t ON t.id=ld.transfer_id
            WHERE la.arrived_at>=? AND la.arrived_at<?""" + inScope, (r, i) -> {
            boolean complete = TransferStatus.RECEIVED_COMPLETE.name().equals(r.getString("status"));
            Instant arrived = r.getObject("arrived_at", OffsetDateTime.class).toInstant();
            // The single definition of punctuality is the transfer's delivery timing (RF-0040); with an arrival the
            // reading time plays no part.
            DeliveryTiming timing = DeliveryTiming.of(r.getObject("dispatched_at", OffsetDateTime.class).toInstant(),
                r.getObject("estimated_arrival_at", OffsetDateTime.class).toInstant(), arrived, complete, arrived);
            return new Arrival(r.getLong("origin_branch_id"), r.getLong("destination_branch_id"),
                r.getObject("route_id", Long.class), Boolean.TRUE.equals(timing.onTime()), complete);
        }, start, end);

        List<BranchCompliance> branches = new ArrayList<>();
        jdbc.query("SELECT b.id," + BRANCH_COLUMNS.formatted("b") + " FROM identity_branch b"
            + (scopeId == null ? "" : " WHERE b.id=" + scopeId) + " ORDER BY b.code", (ResultSet r) -> {
            long id = r.getLong("id");
            branches.add(new BranchCompliance(branch(r, "b"), metrics(arrivals, a -> a.origin() == id),
                metrics(arrivals, a -> a.destination() == id)));
        });

        List<RouteCompliance> routes = jdbc.query("SELECT r.id,r.external_id,r.code,r.priority,r.active," + BRANCH_COLUMNS.formatted("o")
            + "," + BRANCH_COLUMNS.formatted("d") + " FROM logistics_route r JOIN identity_branch o ON o.id=r.origin_branch_id "
            + "JOIN identity_branch d ON d.id=r.destination_branch_id"
            + (scopeId == null ? "" : " WHERE r.origin_branch_id=" + scopeId + " OR r.destination_branch_id=" + scopeId)
            + " ORDER BY r.code", (r, i) -> {
            long id = r.getLong("id");
            return new RouteCompliance(r.getObject("external_id", UUID.class), r.getString("code"), branch(r, "o"), branch(r, "d"),
                r.getString("priority"), r.getBoolean("active"), metrics(arrivals, a -> a.route() != null && a.route() == id));
        });

        List<Overdue> overdue = jdbc.query("SELECT t.external_id,t.code,lr.code AS route_code,ld.carrier_text,ld.dispatched_at,"
            + "ld.estimated_arrival_at," + BRANCH_COLUMNS.formatted("o") + "," + BRANCH_COLUMNS.formatted("d") + """
             FROM logistics_dispatch ld JOIN transfers_transfer t ON t.id=ld.transfer_id
            JOIN identity_branch o ON o.id=t.origin_branch_id JOIN identity_branch d ON d.id=t.destination_branch_id
            LEFT JOIN logistics_route lr ON lr.id=ld.route_id LEFT JOIN logistics_arrival la ON la.dispatch_id=ld.id
            WHERE la.id IS NULL AND t.status='IN_TRANSIT' AND ld.estimated_arrival_at>=? AND ld.estimated_arrival_at<?
            AND ld.estimated_arrival_at<CURRENT_TIMESTAMP""" + inScope + " ORDER BY ld.estimated_arrival_at,t.id",
            (r, i) -> new Overdue(r.getObject("external_id", UUID.class), r.getString("code"), branch(r, "o"), branch(r, "d"),
                r.getString("route_code"), r.getString("carrier_text"), r.getObject("dispatched_at", OffsetDateTime.class).toInstant(),
                r.getObject("estimated_arrival_at", OffsetDateTime.class).toInstant()), start, end);

        Branch consulted = scopeId == null ? null : branches.getFirst().branch();
        OffsetDateTime generatedAt = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class);
        return new ComplianceReportResponse(BUSINESS_ZONE.getId(), from, to, generatedAt.toInstant(), consulted,
            metrics(arrivals, a -> true), List.copyOf(branches), routes, overdue);
    }

    private static Metrics metrics(List<Arrival> arrivals, Predicate<Arrival> filter) {
        List<Arrival> selected = arrivals.stream().filter(filter).toList();
        int onTime = (int) selected.stream().filter(Arrival::onTime).count();
        int complete = (int) selected.stream().filter(Arrival::complete).count();
        String percent = selected.isEmpty() ? null : BigDecimal.valueOf(onTime * 100L)
            .divide(BigDecimal.valueOf(selected.size()), 2, RoundingMode.HALF_UP).toPlainString();
        return new Metrics(selected.size(), onTime, complete - onTime, selected.size() - complete, percent);
    }

    private static Branch branch(ResultSet r, String alias) throws SQLException {
        return new Branch(r.getObject(alias + "_id", UUID.class), r.getString(alias + "_code"), r.getString(alias + "_name"),
            r.getBoolean(alias + "_active"));
    }
}
