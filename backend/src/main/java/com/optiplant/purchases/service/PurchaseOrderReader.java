package com.optiplant.purchases.service;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.purchases.dto.PurchaseOrderResponse;
import com.optiplant.purchases.dto.PurchaseOrderResponse.Event;
import com.optiplant.purchases.dto.PurchaseOrderResponse.Line;
import com.optiplant.purchases.dto.PurchaseOrderResponse.OrderReference;
import com.optiplant.purchases.dto.PurchaseOrderResponse.Person;
import com.optiplant.purchases.dto.PurchaseOrderResponse.Supplier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Builds purchase order responses from their stored snapshots; authorization is checked by the callers. The cost per
 * base unit fixed by a receipt is inventory valuation: it is returned only when {@code valuation} is true, never to
 * operators (INC-003/D04), like the movement history. Prices, discounts and nets are the document's own conditions.
 */
@Component
public class PurchaseOrderReader {
    private static final String ORDER = """
        SELECT o.id,o.external_id,o.code,o.status,o.payment_term_days,o.created_at,o.received_at,o.due_date,
            o.supplier_name_snapshot,s.external_id AS supplier,s.code AS supplier_code,
            b.external_id AS branch,b.code AS branch_code,b.name AS branch_name,b.active AS branch_active,
            c.code AS creator_code,c.full_name AS creator_name,r.code AS receiver_code,r.full_name AS receiver_name,
            (SELECT external_id FROM inventory_operation WHERE purchase_order_id=o.id) AS receipt,
            rp.external_id AS replaces,rp.code AS replaces_code,rb.external_id AS replaced_by,rb.code AS replaced_by_code
        FROM purchases_order o JOIN purchases_supplier s ON s.id=o.supplier_id JOIN identity_branch b ON b.id=o.branch_id
        JOIN identity_user_account c ON c.id=o.created_by LEFT JOIN identity_user_account r ON r.id=o.received_by
        LEFT JOIN purchases_order rp ON rp.id=o.replaces_order_id LEFT JOIN purchases_order rb ON rb.replaces_order_id=o.id
        """;

    private final JdbcTemplate jdbc;

    public PurchaseOrderReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The full order with its lines and state history. */
    public PurchaseOrderResponse detail(long orderId, boolean valuation) {
        PurchaseOrderResponse order = orders(ORDER + "WHERE o.id=?", List.of(orderId), false, valuation).getFirst();
        List<Event> events = jdbc.query("""
            SELECT e.from_status,e.to_status,e.occurred_at,u.code,u.full_name,e.reason FROM purchases_event e
            JOIN identity_user_account u ON u.id=e.actor_id WHERE e.order_id=? ORDER BY e.sequence_no""",
            (r, i) -> new Event(r.getString(1), r.getString(2), r.getObject(3, OffsetDateTime.class).toInstant(),
                new Person(r.getString(4), r.getString(5)), r.getString(6)), orderId);
        return new PurchaseOrderResponse(order.externalId(), order.code(), order.status(), order.branch(), order.supplier(),
            order.paymentTermDays(), order.createdAt(), order.createdBy(), order.receivedAt(), order.receivedBy(),
            order.dueDate(), order.receiptOperationExternalId(), order.replacesOrder(), order.replacedBy(), order.totalNet(),
            order.exactTotalNet(), order.lines(), events);
    }

    /** Orders selected by {@code where} (appended to the base query), each with its lines and without events. */
    public List<PurchaseOrderResponse> list(String where, List<Object> arguments, boolean valuation) {
        return orders(ORDER + where, arguments, true, valuation);
    }

    private record Row(long id, UUID externalId, String code, String status, int term, OffsetDateTime createdAt,
                       OffsetDateTime receivedAt, String dueDate, Supplier supplier, Branch branch, Person creator,
                       Person receiver, UUID receipt, OrderReference replaces, OrderReference replacedBy) {}

    private List<PurchaseOrderResponse> orders(String sql, List<Object> arguments, boolean listing, boolean valuation) {
        List<Row> rows = jdbc.query(sql, (r, i) -> new Row(r.getLong("id"), r.getObject("external_id", UUID.class),
            r.getString("code"), r.getString("status"), r.getInt("payment_term_days"),
            r.getObject("created_at", OffsetDateTime.class), r.getObject("received_at", OffsetDateTime.class),
            r.getString("due_date"),
            new Supplier(r.getObject("supplier", UUID.class), r.getString("supplier_code"), r.getString("supplier_name_snapshot")),
            new Branch(r.getObject("branch", UUID.class), r.getString("branch_code"), r.getString("branch_name"),
                r.getBoolean("branch_active")),
            new Person(r.getString("creator_code"), r.getString("creator_name")),
            r.getString("receiver_code") == null ? null : new Person(r.getString("receiver_code"), r.getString("receiver_name")),
            r.getObject("receipt", UUID.class), reference(r, "replaces"), reference(r, "replaced_by")), arguments.toArray());
        if (rows.isEmpty()) return List.of();
        Map<Long, List<Line>> lines = new LinkedHashMap<>();
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        rows.forEach(row -> { lines.put(row.id(), new ArrayList<>()); totals.put(row.id(), BigDecimal.ZERO); });
        String ids = String.join(",", Collections.nCopies(rows.size(), "?"));
        jdbc.query("""
            SELECT l.order_id,l.line_no,p.external_id,l.sku_snapshot,l.product_name_snapshot,l.unit_name_snapshot,
                l.factor_snapshot,l.commercial_qty,l.base_qty,p.base_unit_name,l.gross_unit_price,l.discount_pct,l.net_amount,
                l.historical_base_cost
            FROM purchases_line l JOIN catalog_product p ON p.id=l.product_id WHERE l.order_id IN (%s)
            ORDER BY l.order_id,l.line_no""".formatted(ids), (java.sql.ResultSet r) -> {
                long order = r.getLong("order_id");
                BigDecimal net = r.getBigDecimal("net_amount");
                BigDecimal cost = r.getBigDecimal("historical_base_cost");
                totals.merge(order, net, BigDecimal::add);
                lines.get(order).add(new Line(r.getInt("line_no"), r.getObject("external_id", UUID.class),
                    r.getString("sku_snapshot"), r.getString("product_name_snapshot"), r.getString("unit_name_snapshot"),
                    plain(r.getBigDecimal("factor_snapshot")), r.getBigDecimal("commercial_qty").setScale(3).toPlainString(),
                    r.getBigDecimal("base_qty").setScale(3).toPlainString(), r.getString("base_unit_name"),
                    plain(r.getBigDecimal("gross_unit_price")), plain(r.getBigDecimal("discount_pct")), money(net), plain(net),
                    cost == null || !valuation ? null : cost.setScale(6).toPlainString()));
            }, rows.stream().map(Row::id).toArray());
        return rows.stream().map(row -> new PurchaseOrderResponse(row.externalId(), row.code(), row.status(), row.branch(),
            row.supplier(), row.term(), row.createdAt().toInstant(), row.creator(),
            row.receivedAt() == null ? null : row.receivedAt().toInstant(), row.receiver(), row.dueDate(), row.receipt(),
            row.replaces(), row.replacedBy(), money(totals.get(row.id())), plain(totals.get(row.id())), List.copyOf(lines.get(row.id())),
            listing ? null : List.of())).toList();
    }

    private static OrderReference reference(java.sql.ResultSet r, String column) throws java.sql.SQLException {
        UUID id = r.getObject(column, UUID.class);
        return id == null ? null : new OrderReference(id, r.getString(column + "_code"));
    }

    /** Monetary display precision: 2 decimals, half up (INC-013/D03). */
    static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
