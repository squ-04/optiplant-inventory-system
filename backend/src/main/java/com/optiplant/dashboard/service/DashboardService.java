package com.optiplant.dashboard.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.dashboard.dto.RotationResponse;
import com.optiplant.dashboard.dto.SalesComparisonResponse;
import com.optiplant.dashboard.dto.SalesComparisonResponse.Month;
import com.optiplant.dashboard.dto.SalesComparisonResponse.ProductSales;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.dashboard.service.RotationCalculator.Ledger;
import com.optiplant.dashboard.service.RotationCalculator.Result;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Branch dashboard indicators (RF-0044, RF-0045; INC-018/D01–D03,D07). Administrators choose the branch; managers and
 * operators read only their own (§5.2, UC-019). Read-only: every figure is computed from the confirmed sales and the
 * inventory ledger, without dashboard tables, and nothing missing is filled with invented zeros.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DashboardService {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");
    static final int PREVIOUS_MONTHS = 3;

    private final JdbcTemplate jdbc;
    private final ScopePolicy policy;
    private final Clock clock;

    public DashboardService(JdbcTemplate jdbc, ScopePolicy policy, Clock clock) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.clock = clock;
    }

    private record Scope(long id, Branch branch, Instant registeredAt) {}

    public SalesComparisonResponse sales(SessionPrincipal principal, UUID branch) {
        Scope scope = scope(principal, branch);
        Instant now = clock.instant();
        YearMonth current = YearMonth.from(now.atZone(BUSINESS_ZONE));
        List<Month> months = new ArrayList<>();
        for (int back = PREVIOUS_MONTHS; back >= 0; back--) {
            YearMonth month = current.minusMonths(back);
            Instant start = month.atDay(1).atStartOfDay(BUSINESS_ZONE).toInstant();
            boolean inProgress = back == 0;
            Instant end = inProgress ? now : month.plusMonths(1).atDay(1).atStartOfDay(BUSINESS_ZONE).toInstant();
            LocalDate to = inProgress ? LocalDate.ofInstant(now, BUSINESS_ZONE) : month.atEndOfMonth();
            boolean available = scope.registeredAt().isBefore(end);
            if (!available) {
                months.add(new Month(month.toString(), month.atDay(1), to, inProgress, false, false, null, null, null, List.of()));
                continue;
            }
            months.add(month(scope.id(), month, to, inProgress, scope.registeredAt().isAfter(start), start, end));
        }
        return new SalesComparisonResponse(BUSINESS_ZONE.getId(), now, scope.branch(),
            LocalDate.ofInstant(scope.registeredAt(), BUSINESS_ZONE), months);
    }

    private Month month(long branch, YearMonth month, LocalDate to, boolean inProgress, boolean partial, Instant start,
                        Instant end) {
        Timestamp from = Timestamp.from(start);
        Timestamp until = Timestamp.from(end);
        Integer count = jdbc.queryForObject("SELECT count(*) FROM sales_sale WHERE branch_id=? AND confirmed_at>=? AND confirmed_at<?",
            Integer.class, branch, from, until);
        BigDecimal net = jdbc.queryForObject("SELECT coalesce(sum(l.net_amount),0) FROM sales_line l JOIN sales_sale s ON s.id=l.sale_id "
            + "WHERE s.branch_id=? AND s.confirmed_at>=? AND s.confirmed_at<?", BigDecimal.class, branch, from, until);
        Integer returns = jdbc.queryForObject("SELECT count(*) FROM inventory_operation o WHERE o.kind='SALE_RETURN' AND o.occurred_at>=? "
            + "AND o.occurred_at<? AND EXISTS (SELECT 1 FROM inventory_movement m WHERE m.operation_id=o.id AND m.branch_id=?)",
            Integer.class, from, until, branch);
        List<ProductSales> products = jdbc.query("""
            SELECT p.external_id,p.sku,p.name,p.base_unit_name,sum(x.sold) AS sold,sum(x.returned) AS returned FROM (
              SELECT l.product_id,l.base_qty AS sold,0 AS returned FROM sales_line l JOIN sales_sale s ON s.id=l.sale_id
              WHERE s.branch_id=? AND s.confirmed_at>=? AND s.confirmed_at<?
              UNION ALL
              SELECT m.product_id,0,m.base_qty FROM inventory_movement m JOIN inventory_operation o ON o.id=m.operation_id
              WHERE o.kind='SALE_RETURN' AND m.branch_id=? AND o.occurred_at>=? AND o.occurred_at<?
            ) x JOIN catalog_product p ON p.id=x.product_id
            GROUP BY p.id,p.external_id,p.sku,p.name,p.base_unit_name ORDER BY p.sku""",
            (r, i) -> new ProductSales(r.getObject("external_id", UUID.class), r.getString("sku"), r.getString("name"),
                r.getString("base_unit_name"), quantity(r.getBigDecimal("sold")), quantity(r.getBigDecimal("returned"))),
            branch, from, until, branch, from, until);
        return new Month(month.toString(), month.atDay(1), to, inProgress, true, partial, count,
            net.setScale(2, RoundingMode.HALF_UP).toPlainString(), returns, products);
    }

    public RotationResponse rotation(SessionPrincipal principal, UUID branch, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ApiException(400, "VALIDATION_FAILED", "La fecha final del período no puede ser anterior a la inicial.");
        }
        Instant now = clock.instant();
        if (to.isAfter(LocalDate.ofInstant(now, BUSINESS_ZONE))) {
            throw new ApiException(400, "VALIDATION_FAILED",
                "La fecha final del período no puede ser posterior a hoy: los días futuros no tienen inventario registrado.");
        }
        Scope scope = scope(principal, branch);
        Instant start = from.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant dayAfter = to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant end = dayAfter.isBefore(now) ? dayAfter : now;

        // Two sums per product (see RotationCalculator): bounded by the number of products, not by the ledger's length.
        // EXTRACT(EPOCH ...) is an exact numeric; timestamps have microsecond precision.
        Map<Long, Ledger> ledgers = new HashMap<>();
        Timestamp periodStart = Timestamp.from(start);
        Timestamp periodEnd = Timestamp.from(end);
        jdbc.query("""
            SELECT m.product_id,
              coalesce(sum(CASE WHEN o.occurred_at>=? THEN x.delta END),0) AS after_end,
              coalesce(sum(CASE WHEN o.occurred_at<? THEN x.delta*EXTRACT(EPOCH FROM o.occurred_at-?::timestamptz)*1000000 END),0)
                AS weighted
            FROM inventory_movement m JOIN inventory_operation o ON o.id=m.operation_id
            CROSS JOIN LATERAL (SELECT CASE m.direction WHEN 'IN' THEN m.base_qty ELSE -m.base_qty END AS delta) x
            WHERE m.branch_id=? AND o.occurred_at>=? GROUP BY m.product_id""",
            (java.sql.ResultSet r) -> {
                ledgers.put(r.getLong("product_id"), new Ledger(r.getBigDecimal("after_end"), r.getBigDecimal("weighted")));
            }, periodEnd, periodEnd, periodStart, scope.id(), periodStart);
        Map<Long, BigDecimal> sold = new HashMap<>();
        jdbc.query("SELECT l.product_id,sum(l.base_qty) AS sold FROM sales_line l JOIN sales_sale s ON s.id=l.sale_id "
                + "WHERE s.branch_id=? AND s.confirmed_at>=? AND s.confirmed_at<? GROUP BY l.product_id",
            (java.sql.ResultSet r) -> { sold.put(r.getLong("product_id"), r.getBigDecimal("sold")); },
            scope.id(), Timestamp.from(start), Timestamp.from(end));

        List<RotationResponse.Item> items = jdbc.query("""
            SELECT b.product_id,b.physical_qty,b.history_started_at,p.external_id,p.sku,p.name,p.base_unit_name
            FROM inventory_balance b JOIN catalog_product p ON p.id=b.product_id
            WHERE b.branch_id=? AND b.history_started_at<? ORDER BY p.sku""", (r, i) -> {
            long product = r.getLong("product_id");
            Instant history = r.getObject("history_started_at", OffsetDateTime.class).toInstant();
            BigDecimal units = sold.getOrDefault(product, BigDecimal.ZERO);
            Result result = RotationCalculator.calculate(r.getBigDecimal("physical_qty"),
                ledgers.getOrDefault(product, new Ledger(BigDecimal.ZERO, BigDecimal.ZERO)),
                history, start, end, units);
            return new RotationResponse.Item(r.getObject("external_id", UUID.class), r.getString("sku"), r.getString("name"),
                r.getString("base_unit_name"), history, quantity(units),
                result.average() == null ? null : result.average().toPlainString(),
                result.rotation() == null ? null : result.rotation().toPlainString(), result.status().name());
        }, scope.id(), Timestamp.from(end));
        return new RotationResponse(BUSINESS_ZONE.getId(), from, to, end, now, scope.branch(), items);
    }

    /** The consulted branch: the one chosen by the administrator, the assigned one for everybody else. */
    private Scope scope(SessionPrincipal principal, UUID branch) {
        UUID target = branch != null ? branch : principal.branchExternalId();
        if (target == null) throw new ApiException(400, "VALIDATION_FAILED", "Seleccione la sucursal del dashboard.");
        policy.requireHistoryRead(principal, target);
        return jdbc.query("SELECT id,external_id,code,name,active,created_at FROM identity_branch WHERE external_id=?", (r, i) ->
                new Scope(r.getLong("id"), new Branch(r.getObject("external_id", UUID.class), r.getString("code"), r.getString("name"),
                    r.getBoolean("active")), r.getObject("created_at", OffsetDateTime.class).toInstant()), target)
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal."));
    }

    private static String quantity(BigDecimal value) {
        return value.setScale(3, RoundingMode.UNNECESSARY).toPlainString();
    }
}
