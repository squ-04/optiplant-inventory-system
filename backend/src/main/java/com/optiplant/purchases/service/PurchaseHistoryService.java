package com.optiplant.purchases.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.purchases.dto.PurchaseHistoryResponse;
import com.optiplant.purchases.dto.PurchaseHistoryResponse.Summary;
import com.optiplant.purchases.dto.PurchaseOrderResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-0025: purchase history by supplier and by product, including drafts, pending, cancelled and received orders.
 * Managers and operators read their own branch, administrators one branch or the whole network (§5.2, INC-011/D06).
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class PurchaseHistoryService {
    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 500;
    private static final Set<String> STATUSES = Set.of("DRAFT", "PENDING", "RECEIVED", "CANCELLED");

    private final JdbcTemplate jdbc;
    private final ScopePolicy policy;
    private final PurchaseOrderReader reader;

    public PurchaseHistoryService(JdbcTemplate jdbc, ScopePolicy policy, PurchaseOrderReader reader) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.reader = reader;
    }

    public PurchaseHistoryResponse history(SessionPrincipal principal, UUID branch, UUID supplier, UUID product,
                                           String status, Integer limit) {
        for (UUID reference : new UUID[] {branch, supplier, product}) {
            if (reference != null) policy.requirePublicReference(reference);
        }
        if (status != null && !STATUSES.contains(status)) {
            throw new ApiException(400, "VALIDATION_FAILED", "Estado no admitido: use DRAFT, PENDING, RECEIVED o CANCELLED.");
        }
        int rows = limit == null ? DEFAULT_LIMIT : limit;
        if (rows < 1 || rows > MAX_LIMIT) {
            throw new ApiException(400, "VALIDATION_FAILED", "El límite debe estar entre 1 y " + MAX_LIMIT + ".");
        }
        // Local profiles always read their own branch; only the administrator may omit it to read the network.
        UUID scope = branch != null ? branch : "ADMIN".equals(principal.role()) ? null : principal.branchExternalId();
        if (scope == null && !"ADMIN".equals(principal.role())) {
            throw new ApiException(403, "BRANCH_FORBIDDEN", "La consulta está limitada a su sucursal asignada.");
        }
        if (scope != null) policy.requireHistoryRead(principal, scope);
        Branch consulted = scope == null ? null : jdbc.query("SELECT external_id,code,name,active FROM identity_branch WHERE external_id=?",
                (r, i) -> new Branch(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getBoolean(4)), scope)
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal."));
        StringBuilder where = new StringBuilder("WHERE TRUE");
        List<Object> arguments = new ArrayList<>();
        if (scope != null) { where.append(" AND b.external_id=?"); arguments.add(scope); }
        if (supplier != null) { where.append(" AND s.external_id=?"); arguments.add(supplier); }
        if (product != null) {
            where.append(" AND EXISTS (SELECT 1 FROM purchases_line pl JOIN catalog_product pp ON pp.id=pl.product_id "
                + "WHERE pl.order_id=o.id AND pp.external_id=?)");
            arguments.add(product);
        }
        Map<String, Object> counts = jdbc.queryForMap("""
            SELECT count(*) AS orders,count(*) FILTER (WHERE o.status='DRAFT') AS drafts,
                count(*) FILTER (WHERE o.status='PENDING') AS pending,count(*) FILTER (WHERE o.status='RECEIVED') AS received,
                count(*) FILTER (WHERE o.status='CANCELLED') AS cancelled
            FROM purchases_order o JOIN purchases_supplier s ON s.id=o.supplier_id JOIN identity_branch b ON b.id=o.branch_id
            """ + where, arguments.toArray());
        if (status != null) { where.append(" AND o.status=?"); arguments.add(status); }
        arguments.add(rows);
        List<PurchaseOrderResponse> items = reader.list(where + " ORDER BY o.created_at DESC,o.id DESC LIMIT ?", arguments);
        OffsetDateTime readAt = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class);
        return new PurchaseHistoryResponse(readAt.toInstant(), consulted, new Summary(count(counts, "orders"),
            count(counts, "drafts"), count(counts, "pending"), count(counts, "received"), count(counts, "cancelled")), items);
    }

    /** One order with its lines and state history, within the reader's scope. */
    public PurchaseOrderResponse detail(SessionPrincipal principal, UUID order) {
        policy.requirePublicReference(order);
        Map<String, Object> found = jdbc.queryForList("SELECT o.id,b.external_id AS branch FROM purchases_order o "
                + "JOIN identity_branch b ON b.id=o.branch_id WHERE o.external_id=?", order).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "PURCHASE_NOT_FOUND", "No se encontró la orden de compra."));
        policy.requireHistoryRead(principal, (UUID) found.get("branch"));
        return reader.detail(((Number) found.get("id")).longValue());
    }

    private static int count(Map<String, Object> counts, String key) {
        return ((Number) counts.get(key)).intValue();
    }
}
