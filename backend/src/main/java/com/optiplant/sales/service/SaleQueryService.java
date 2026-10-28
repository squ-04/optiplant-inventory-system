package com.optiplant.sales.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.sales.dto.SaleHistoryResponse;
import com.optiplant.sales.dto.SaleResponse;
import com.optiplant.sales.dto.SaleResponse.Line;
import com.optiplant.sales.dto.SaleResponse.Person;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-0031: confirmed sale records, built only from the snapshots stored at confirmation. Operators and managers read
 * their own branch, administrators one branch or the whole network (§5.2). The valuation cost of each exit is never
 * returned (INC-003/D04).
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SaleQueryService {
    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 500;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String SALE = """
        SELECT s.id,s.external_id,s.code,s.confirmed_at,b.external_id AS branch,b.code AS branch_code,b.name AS branch_name,
            b.active AS branch_active,u.code AS actor_code,u.full_name AS actor_name,
            (SELECT external_id FROM inventory_operation WHERE sale_id=s.id) AS operation
        FROM sales_sale s JOIN identity_branch b ON b.id=s.branch_id JOIN identity_user_account u ON u.id=s.confirmed_by
        """;

    private final JdbcTemplate jdbc;
    private final ScopePolicy policy;

    public SaleQueryService(JdbcTemplate jdbc, ScopePolicy policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    public SaleHistoryResponse history(SessionPrincipal principal, UUID branch, Integer limit) {
        if (branch != null) policy.requirePublicReference(branch);
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
        String where = scope == null ? "WHERE TRUE" : "WHERE b.external_id=?";
        List<Object> arguments = new ArrayList<>();
        if (scope != null) arguments.add(scope);
        int total = jdbc.queryForObject("SELECT count(*) FROM sales_sale s JOIN identity_branch b ON b.id=s.branch_id " + where,
            Integer.class, arguments.toArray());
        arguments.add(rows);
        List<SaleResponse> items = sales(SALE + where + " ORDER BY s.confirmed_at DESC,s.id DESC LIMIT ?", arguments);
        OffsetDateTime readAt = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class);
        return new SaleHistoryResponse(readAt.toInstant(), consulted, total, items);
    }

    /** One sale record, within the reader's scope; another branch's sale is forbidden to local profiles. */
    public SaleResponse detail(SessionPrincipal principal, UUID sale) {
        policy.requirePublicReference(sale);
        Map<String, Object> found = jdbc.queryForList("SELECT s.id,b.external_id AS branch FROM sales_sale s "
                + "JOIN identity_branch b ON b.id=s.branch_id WHERE s.external_id=?", sale).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "SALE_NOT_FOUND", "No se encontró la venta."));
        policy.requireHistoryRead(principal, (UUID) found.get("branch"));
        return record(((Number) found.get("id")).longValue());
    }

    /** The record of one sale by internal identifier; authorization is checked by the caller. */
    SaleResponse record(long saleId) {
        return sales(SALE + "WHERE s.id=?", List.of(saleId)).getFirst();
    }

    private record Row(long id, UUID externalId, String code, OffsetDateTime confirmedAt, Branch branch, Person actor,
                       UUID operation) {}

    private record Amounts(BigDecimal gross, BigDecimal net) {}

    private List<SaleResponse> sales(String sql, List<Object> arguments) {
        List<Row> rows = jdbc.query(sql, (r, i) -> new Row(r.getLong("id"), r.getObject("external_id", UUID.class),
            r.getString("code"), r.getObject("confirmed_at", OffsetDateTime.class),
            new Branch(r.getObject("branch", UUID.class), r.getString("branch_code"), r.getString("branch_name"),
                r.getBoolean("branch_active")),
            new Person(r.getString("actor_code"), r.getString("actor_name")), r.getObject("operation", UUID.class)),
            arguments.toArray());
        if (rows.isEmpty()) return List.of();
        Map<Long, List<Line>> lines = new LinkedHashMap<>();
        Map<Long, Amounts> totals = new LinkedHashMap<>();
        rows.forEach(row -> { lines.put(row.id(), new ArrayList<>()); totals.put(row.id(), new Amounts(BigDecimal.ZERO, BigDecimal.ZERO)); });
        String ids = String.join(",", Collections.nCopies(rows.size(), "?"));
        // A return also links its entry to the sale line; the exit of the sale itself is the movement of the SALE operation.
        jdbc.query("""
            SELECT l.sale_id,l.line_no,p.external_id,l.sku_snapshot,l.product_name_snapshot,l.unit_name_snapshot,
                l.factor_snapshot,l.commercial_qty,l.base_qty,p.base_unit_name,pl.external_id AS list,l.list_name_snapshot,
                l.gross_unit_price,l.discount_pct,l.net_amount,
                (SELECT m.external_id FROM inventory_movement m JOIN inventory_operation o ON o.id=m.operation_id
                 WHERE m.sale_line_id=l.id AND o.kind='SALE') AS movement
            FROM sales_line l JOIN catalog_product p ON p.id=l.product_id JOIN catalog_price_list pl ON pl.id=l.price_list_id
            WHERE l.sale_id IN (%s) ORDER BY l.sale_id,l.line_no""".formatted(ids), (java.sql.ResultSet r) -> {
                long sale = r.getLong("sale_id");
                BigDecimal quantity = r.getBigDecimal("commercial_qty");
                BigDecimal price = r.getBigDecimal("gross_unit_price");
                BigDecimal discount = r.getBigDecimal("discount_pct");
                BigDecimal net = r.getBigDecimal("net_amount");
                totals.merge(sale, new Amounts(quantity.multiply(price), net),
                    (left, right) -> new Amounts(left.gross().add(right.gross()), left.net().add(right.net())));
                lines.get(sale).add(new Line(r.getInt("line_no"), r.getObject("external_id", UUID.class),
                    r.getString("sku_snapshot"), r.getString("product_name_snapshot"), r.getString("unit_name_snapshot"),
                    plain(r.getBigDecimal("factor_snapshot")), quantity.setScale(3).toPlainString(),
                    r.getBigDecimal("base_qty").setScale(3).toPlainString(), r.getString("base_unit_name"),
                    r.getObject("list", UUID.class), r.getString("list_name_snapshot"), plain(price), plain(discount),
                    plain(netUnitPrice(price, discount)), money(net), plain(net), r.getObject("movement", UUID.class)));
            }, rows.stream().map(Row::id).toArray());
        return rows.stream().map(row -> {
            Amounts amounts = totals.get(row.id());
            BigDecimal gross = rounded(amounts.gross());
            BigDecimal net = rounded(amounts.net());
            // The shown discount is derived from the shown amounts, so subtotal − discount = net always holds on the record.
            return new SaleResponse(row.externalId(), row.code(), row.branch(), row.confirmedAt().toInstant(), row.actor(),
                row.operation(), gross.toPlainString(), gross.subtract(net).toPlainString(), net.toPlainString(),
                plain(amounts.net()), List.copyOf(lines.get(row.id())));
        }).toList();
    }

    /** Exact unit price after the line discount: price × (1 − discount/100) (RF-0029-CA-02). */
    static BigDecimal netUnitPrice(BigDecimal price, BigDecimal discount) {
        return price.multiply(HUNDRED.subtract(discount)).movePointLeft(2);
    }

    /** Monetary display precision: 2 decimals, half up (INC-013/D03). */
    private static BigDecimal rounded(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String money(BigDecimal amount) {
        return rounded(amount).toPlainString();
    }

    private static String plain(BigDecimal value) {
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }
}
