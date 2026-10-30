package com.optiplant.inventory.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.inventory.dto.MovementExportRow;
import com.optiplant.inventory.dto.MovementHistoryResponse;
import com.optiplant.inventory.dto.MovementHistoryResponse.Entry;
import com.optiplant.inventory.dto.MovementHistoryResponse.Responsible;
import com.optiplant.inventory.dto.MovementHistoryResponse.Valuation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-0018: auditable history of a branch's movements. Managers and operators read their own branch, administrators any
 * branch (RF-0007 scope); the balance never replaces the movement records.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class MovementHistoryService {
    public static final int DEFAULT_LIMIT = 200;
    public static final int MAX_LIMIT = 500;
    private final JdbcTemplate jdbc;
    private final ScopePolicy policy;

    public MovementHistoryService(JdbcTemplate jdbc, ScopePolicy policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    public MovementHistoryResponse history(SessionPrincipal principal, UUID branchId, UUID product, Integer limit) {
        policy.requirePublicReference(branchId);
        if (product != null) policy.requirePublicReference(product);
        int rows = limit == null ? DEFAULT_LIMIT : limit;
        if (rows < 1 || rows > MAX_LIMIT) {
            throw new ApiException(400, "VALIDATION_FAILED", "El límite debe estar entre 1 y " + MAX_LIMIT + ".");
        }
        policy.requireHistoryRead(principal, branchId);
        Branch branch = jdbc.query("SELECT external_id,code,name,active FROM identity_branch WHERE external_id=?",
                (r, i) -> new Branch(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getBoolean(4)), branchId)
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal."));
        boolean valuation = !principal.role().equals("OPERATOR");
        List<Object> arguments = new ArrayList<>(List.of(branchId));
        if (product != null) arguments.add(product);
        arguments.add(rows);
        List<Entry> items = jdbc.query("""
            SELECT m.external_id,o.external_id AS operation,o.occurred_at,o.kind,m.direction,p.external_id AS product,
                m.sku_snapshot,m.product_name_snapshot,m.unit_name_snapshot,m.commercial_qty,m.base_qty,p.base_unit_name,
                o.reason,u.code,u.full_name,r.external_id AS reference,m.historical_base_cost,
                (SELECT amount FROM inventory_valuation_component WHERE movement_id=m.id AND component_kind='BASE') AS base_amount,
                d.component_kind AS difference_kind,d.amount AS difference_amount,
                CASE WHEN (o.kind='SALE' AND m.direction='OUT') OR (o.kind IN ('PURCHASE_RECEIPT','MANUAL_ENTRY','SALE_RETURN')
                        AND m.direction='IN')
                    THEN %s END AS pending
            FROM inventory_movement m JOIN inventory_operation o ON o.id=m.operation_id
            JOIN identity_branch b ON b.id=m.branch_id JOIN catalog_product p ON p.id=m.product_id
            JOIN identity_user_account u ON u.id=o.actor_id
            LEFT JOIN inventory_movement r ON r.id=m.original_movement_id
            LEFT JOIN inventory_valuation_component d ON d.movement_id=m.id AND d.component_kind<>'BASE'
            WHERE b.external_id=?""".formatted(ManualMovementService.PENDING) + (product == null ? "" : " AND p.external_id=?")
            + " ORDER BY o.occurred_at DESC,m.id DESC LIMIT ?", (r, i) -> {
                BigDecimal pending = r.getBigDecimal("pending");
                Valuation value = null;
                if (valuation) {
                    BigDecimal base = r.getBigDecimal("base_amount");
                    BigDecimal difference = r.getBigDecimal("difference_amount");
                    BigDecimal total = difference == null ? base : base.add(difference);
                    value = new Valuation(r.getBigDecimal("historical_base_cost").setScale(6).toPlainString(), money(base),
                        r.getString("difference_kind"), difference == null ? null : money(difference),
                        difference == null ? null : difference.stripTrailingZeros().toPlainString(), money(total));
                }
                return new Entry(r.getObject("external_id", UUID.class), r.getObject("operation", UUID.class),
                    r.getObject("occurred_at", OffsetDateTime.class).toInstant(), r.getString("kind"), r.getString("direction"),
                    r.getObject("product", UUID.class), r.getString("sku_snapshot"), r.getString("product_name_snapshot"),
                    r.getString("unit_name_snapshot"), r.getBigDecimal("commercial_qty").setScale(3).toPlainString(),
                    r.getBigDecimal("base_qty").setScale(3).toPlainString(), r.getString("base_unit_name"), r.getString("reason"),
                    new Responsible(r.getString("code"), r.getString("full_name")), r.getObject("reference", UUID.class),
                    pending == null ? null : pending.setScale(3).toPlainString(), value);
            }, arguments.toArray());
        OffsetDateTime readAt = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class);
        return new MovementHistoryResponse(branch, readAt.toInstant(), items);
    }

    /**
     * RF-0052: movements registered in [start, end) of one branch, or of the network when {@code branch} is null, newest
     * first as in the history. Authorization is checked by the caller; costs are never read.
     */
    public List<MovementExportRow> exportRows(UUID branch, Instant start, Instant end, int limit) {
        List<Object> arguments = new ArrayList<>(List.of(Timestamp.from(start), Timestamp.from(end)));
        if (branch != null) arguments.add(branch);
        arguments.add(limit);
        return jdbc.query("""
            SELECT m.external_id,o.occurred_at,b.code AS branch_code,b.name AS branch_name,m.sku_snapshot,m.product_name_snapshot,
                o.kind,o.reason,m.direction,m.commercial_qty,m.unit_name_snapshot,u.code AS actor_code,u.full_name,
                coalesce('Compra '||po.code,'Venta '||s.code,'Transferencia '||t.code,'Movimiento '||r.external_id) AS source
            FROM inventory_movement m JOIN inventory_operation o ON o.id=m.operation_id
            JOIN identity_branch b ON b.id=m.branch_id JOIN identity_user_account u ON u.id=o.actor_id
            LEFT JOIN purchases_order po ON po.id=o.purchase_order_id
            LEFT JOIN sales_line sl ON sl.id=m.sale_line_id LEFT JOIN sales_sale s ON s.id=coalesce(o.sale_id,sl.sale_id)
            LEFT JOIN logistics_arrival la ON la.id=o.arrival_id LEFT JOIN logistics_discrepancy x ON x.id=o.discrepancy_id
            LEFT JOIN logistics_arrival xa ON xa.id=x.arrival_id
            LEFT JOIN logistics_dispatch ld ON ld.id=coalesce(o.dispatch_id,la.dispatch_id,xa.dispatch_id)
            LEFT JOIN transfers_transfer t ON t.id=ld.transfer_id LEFT JOIN inventory_movement r ON r.id=m.original_movement_id
            WHERE o.occurred_at>=? AND o.occurred_at<?""" + (branch == null ? "" : " AND b.external_id=?")
            + " ORDER BY o.occurred_at DESC,m.id DESC LIMIT ?", (r, i) -> new MovementExportRow(r.getObject("external_id", UUID.class),
                r.getObject("occurred_at", OffsetDateTime.class).toInstant(), r.getString("branch_code"), r.getString("branch_name"),
                r.getString("sku_snapshot"), r.getString("product_name_snapshot"), r.getString("kind"), r.getString("reason"),
                r.getString("direction"), r.getBigDecimal("commercial_qty"), r.getString("unit_name_snapshot"),
                r.getString("actor_code"), r.getString("full_name"), r.getString("source")), arguments.toArray());
    }

    /** Monetary display precision: 2 decimals, half up (INC-013/D03). */
    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
