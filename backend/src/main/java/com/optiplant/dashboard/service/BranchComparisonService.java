package com.optiplant.dashboard.service;

import com.optiplant.dashboard.dto.BranchComparisonResponse;
import com.optiplant.dashboard.dto.BranchComparisonResponse.Item;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.logistics.dto.ComplianceReportResponse.BranchCompliance;
import com.optiplant.logistics.service.ComplianceService;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Network comparison (RF-0049). The controller restricts it to the general administrator; the logistics figures come
 * from the RF-0043 report, so both views always agree. Read-only.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class BranchComparisonService {
    private final JdbcTemplate jdbc;
    private final ComplianceService compliance;
    private final Clock clock;

    public BranchComparisonService(JdbcTemplate jdbc, ComplianceService compliance, Clock clock) {
        this.jdbc = jdbc;
        this.compliance = compliance;
        this.clock = clock;
    }

    public BranchComparisonResponse compare(SessionPrincipal principal, YearMonth month) {
        Instant now = clock.instant();
        MonthPeriod period = MonthPeriod.consulted(month, now);
        Map<UUID, BranchCompliance> logistics = compliance.report(principal, period.month().atDay(1), period.to(), null).branches()
            .stream().collect(Collectors.toMap(item -> item.branch().externalId(), Function.identity()));
        List<Item> branches = jdbc.query("""
            SELECT b.external_id,b.code,b.name,b.active,b.created_at,count(DISTINCT s.id) AS sales,coalesce(sum(l.net_amount),0) AS net
            FROM identity_branch b LEFT JOIN sales_sale s ON s.branch_id=b.id AND s.confirmed_at>=? AND s.confirmed_at<?
            LEFT JOIN sales_line l ON l.sale_id=s.id
            GROUP BY b.id,b.external_id,b.code,b.name,b.active,b.created_at ORDER BY b.code""", (r, i) -> {
            Branch branch = new Branch(r.getObject("external_id", UUID.class), r.getString("code"), r.getString("name"),
                r.getBoolean("active"));
            Instant registered = r.getObject("created_at", OffsetDateTime.class).toInstant();
            if (!registered.isBefore(period.end())) return new Item(branch, false, false, null, null, null, null);
            BranchCompliance metrics = logistics.get(branch.externalId());
            return new Item(branch, true, registered.isAfter(period.start()), r.getInt("sales"),
                r.getBigDecimal("net").setScale(2, RoundingMode.HALF_UP).toPlainString(), metrics.asOrigin(), metrics.asDestination());
        }, Timestamp.from(period.start()), Timestamp.from(period.end()));
        return new BranchComparisonResponse(DashboardService.BUSINESS_ZONE.getId(), period.month().toString(),
            period.month().atDay(1), period.to(), period.inProgress(), now, branches);
    }
}
