package com.optiplant.inventory.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.inventory.dto.ReorderAlertResponse;
import com.optiplant.inventory.dto.ReorderAlertResponse.Item;
import com.optiplant.inventory.dto.ReorderAlertResponse.Product;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Minimum stock control (RF-0019) and restock alerts (RF-0020). One condition is used everywhere: an operable
 * product-branch combination with an explicit minimum needs restocking when its available stock (= physical, glossary
 * §5.3) is lower than or equal to that minimum, so a zero minimum alerts at zero stock (INC-010/D03).
 *
 * <p>Each combination has at most one active episode (partial unique index), opened with snapshots of the minimum and
 * the stock, and resolved automatically once the stock rises strictly above the minimum (INC-010/D05). Resolved
 * episodes are kept as history. Every relevant change re-evaluates the combination in the caller's transaction: stock
 * (posted by {@link InventoryLedger}), minimum and enablement. Disabling a product or a branch closes its episodes because
 * the combination stops being operable, not because stock was replenished. There is no manual resolution, e-mail, SMS or
 * automatic purchase.
 */
@Service
public class ReorderAlertService {
    public static final int DEFAULT_LIMIT = 200;
    public static final int MAX_LIMIT = 500;

    private final JdbcTemplate jdbc;
    private final ScopePolicy policy;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public ReorderAlertService(JdbcTemplate jdbc, ScopePolicy policy, Clock clock, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.clock = clock;
        this.transactions = transactions;
    }

    /** The single restock condition of RF-0019, shared by alerts and the "about to run out" indicator (RF-0048). */
    public static boolean requiresRestock(BigDecimal available, BigDecimal minimum) {
        return minimum != null && available.compareTo(minimum) <= 0;
    }

    /**
     * Opens, keeps or resolves the episode of one product in one branch. The combination row is locked, so concurrent
     * stock and minimum changes evaluate one after the other and never open two episodes.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void evaluate(long branchId, long productId) {
        // Lock first, then read in a separate statement: its fresh snapshot sees every change committed while waiting,
        // including a branch or product deactivation that did not modify this row.
        if (jdbc.queryForList("SELECT id FROM catalog_branch_product WHERE branch_id=? AND product_id=? FOR UPDATE",
            Long.class, branchId, productId).isEmpty()) return;
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT bp.id,bp.minimum_qty,(bp.enabled AND p.active AND b.active) AS operable,
                coalesce(ib.physical_qty,0) AS quantity
            FROM catalog_branch_product bp JOIN catalog_product p ON p.id=bp.product_id
            JOIN identity_branch b ON b.id=bp.branch_id
            LEFT JOIN inventory_balance ib ON ib.branch_id=bp.branch_id AND ib.product_id=bp.product_id
            WHERE bp.branch_id=? AND bp.product_id=?""", branchId, productId);
        Map<String, Object> row = rows.getFirst();
        long combination = ((Number) row.get("id")).longValue();
        BigDecimal minimum = (BigDecimal) row.get("minimum_qty");
        BigDecimal quantity = (BigDecimal) row.get("quantity");
        boolean restock = Boolean.TRUE.equals(row.get("operable")) && requiresRestock(quantity, minimum);
        boolean active = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM inventory_reorder_alert "
            + "WHERE branch_product_id=? AND resolved_at IS NULL)", Boolean.class, combination));
        Timestamp now = Timestamp.from(clock.instant());
        if (restock && !active) {
            jdbc.update("INSERT INTO inventory_reorder_alert(external_id,branch_product_id,opened_at,minimum_snapshot,"
                + "quantity_snapshot,created_at,updated_at) VALUES (?,?,?,?,?,?,?)", UUID.randomUUID(), combination, now,
                minimum, quantity, now, now);
        } else if (!restock && active) {
            jdbc.update("UPDATE inventory_reorder_alert SET resolved_at=GREATEST(?,opened_at),updated_at=? "
                + "WHERE branch_product_id=? AND resolved_at IS NULL", now, now, combination);
        }
    }

    /** Closes the active episodes of a product being deactivated; its rows stay as history. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeProduct(long productId) {
        close("product_id=? ORDER BY branch_id", productId);
    }

    /** Closes the active episodes of a branch being deactivated; its rows stay as history. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeBranch(long branchId) {
        close("branch_id=? ORDER BY product_id", branchId);
    }

    /**
     * Locks the combinations before their episodes, the same order {@link #evaluate} follows inside a stock operation
     * (combination, then episode; products ascending within a branch), so a concurrent operation cannot deadlock.
     */
    private void close(String combinations, long id) {
        jdbc.queryForList("SELECT id FROM catalog_branch_product WHERE " + combinations + " FOR UPDATE", Long.class, id);
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("UPDATE inventory_reorder_alert SET resolved_at=GREATEST(?,opened_at),updated_at=? WHERE resolved_at IS NULL "
            + "AND branch_product_id IN (SELECT id FROM catalog_branch_product WHERE " + combinations.substring(0,
                combinations.indexOf(" ORDER BY")) + ")", now, now, id);
    }

    /**
     * Aligns stored episodes with current data at start-up, for combinations enabled or loaded before alerts were
     * evaluated (for instance the demonstration seed). Idempotent: an already consistent database is left unchanged.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        // One short transaction per combination: live requests never queue behind the whole catalog.
        jdbc.query("SELECT branch_id,product_id FROM catalog_branch_product ORDER BY branch_id,product_id",
                (row, index) -> new long[] {row.getLong(1), row.getLong(2)})
            .forEach(combination -> transactions.executeWithoutResult(status -> evaluate(combination[0], combination[1])));
    }

    /**
     * Persistent alerts for the affected manager (own branch) and the administrator (any branch, or all). Operators see
     * the operational indicator of their local catalog instead (INC-010/D04).
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReorderAlertResponse list(SessionPrincipal principal, UUID branchId, String status, Integer limit) {
        if (principal.role().equals("OPERATOR")) {
            throw new ApiException(403, "ROLE_FORBIDDEN", "Las alertas de reabastecimiento corresponden a gerentes y "
                + "administradores; el indicador de reabastecimiento se muestra en su catálogo local.");
        }
        boolean all = status != null && status.equals("ALL");
        if (status != null && !all && !status.equals("ACTIVE")) {
            throw new ApiException(400, "VALIDATION_FAILED", "El estado debe ser ACTIVE o ALL.");
        }
        int rows = limit == null ? DEFAULT_LIMIT : limit;
        if (rows < 1 || rows > MAX_LIMIT) {
            throw new ApiException(400, "VALIDATION_FAILED", "El límite debe estar entre 1 y " + MAX_LIMIT + ".");
        }
        UUID scope = branchId;
        if (principal.role().equals("MANAGER") && scope == null) scope = principal.branchExternalId();
        Branch branch = null;
        if (scope != null) {
            policy.requirePublicReference(scope);
            policy.requireHistoryRead(principal, scope);
            branch = jdbc.query("SELECT external_id,code,name,active FROM identity_branch WHERE external_id=?",
                    (r, i) -> new Branch(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getBoolean(4)), scope)
                .stream().findFirst().orElseThrow(() -> new ApiException(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal."));
        }
        String where = (scope == null ? "TRUE" : "b.external_id=?");
        List<Object> arguments = new ArrayList<>();
        if (scope != null) arguments.add(scope);
        Integer active = jdbc.queryForObject("SELECT count(*) FROM inventory_reorder_alert a "
            + "JOIN catalog_branch_product bp ON bp.id=a.branch_product_id JOIN identity_branch b ON b.id=bp.branch_id "
            + "WHERE a.resolved_at IS NULL AND " + where, Integer.class, arguments.toArray());
        arguments.add(rows);
        List<Item> items = jdbc.query("""
            SELECT a.external_id,a.opened_at,a.resolved_at,a.minimum_snapshot,a.quantity_snapshot,
                b.external_id AS branch,b.code,b.name AS branch_name,b.active AS branch_active,
                p.external_id AS product,p.sku,p.name,p.base_unit_name,bp.minimum_qty,coalesce(ib.physical_qty,0) AS quantity
            FROM inventory_reorder_alert a JOIN catalog_branch_product bp ON bp.id=a.branch_product_id
            JOIN identity_branch b ON b.id=bp.branch_id JOIN catalog_product p ON p.id=bp.product_id
            LEFT JOIN inventory_balance ib ON ib.branch_id=bp.branch_id AND ib.product_id=bp.product_id
            WHERE""" + " " + where + (all ? "" : " AND a.resolved_at IS NULL")
            + " ORDER BY a.resolved_at IS NOT NULL,a.opened_at DESC,a.id DESC LIMIT ?", (r, i) -> {
                Timestamp resolved = r.getTimestamp("resolved_at");
                BigDecimal minimum = r.getBigDecimal("minimum_qty");
                return new Item(r.getObject("external_id", UUID.class), resolved == null ? "ACTIVE" : "RESOLVED",
                    new Branch(r.getObject("branch", UUID.class), r.getString("code"), r.getString("branch_name"),
                        r.getBoolean("branch_active")),
                    new Product(r.getObject("product", UUID.class), r.getString("sku"), r.getString("name"),
                        r.getString("base_unit_name")),
                    r.getTimestamp("opened_at").toInstant(), resolved == null ? null : resolved.toInstant(),
                    quantity(r.getBigDecimal("minimum_snapshot")), quantity(r.getBigDecimal("quantity_snapshot")),
                    minimum == null ? null : quantity(minimum), quantity(r.getBigDecimal("quantity")));
            }, arguments.toArray());
        return new ReorderAlertResponse(Instant.now(clock), branch, active == null ? 0 : active, items);
    }

    /** Plain decimal string with at least three decimals; never rounds a stored value. */
    private static String quantity(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.scale() < 3 ? stripped.setScale(3) : stripped).toPlainString();
    }
}
