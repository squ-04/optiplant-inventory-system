package com.optiplant.inventory.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.inventory.dto.InventoryValuationResponse;
import com.optiplant.inventory.dto.InventoryValuationResponse.Item;
import com.optiplant.inventory.dto.InventoryValuationResponse.TransitItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-0026: consultation of the valuation the {@link InventoryLedger} keeps per product and branch. Managers read their
 * own branch and administrators any branch; operators never read valuation (INC-003/D04). The query only reads the
 * recorded balances: nothing is recalculated.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class InventoryValuationService {
    private final JdbcTemplate jdbc;
    private final ScopePolicy policy;

    public InventoryValuationService(JdbcTemplate jdbc, ScopePolicy policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    public InventoryValuationResponse valuation(SessionPrincipal principal, UUID branchId) {
        policy.requirePublicReference(branchId);
        if (principal.role().equals("OPERATOR")) {
            throw new ApiException(403, "VALUATION_FORBIDDEN", "La valoración del inventario la consultan el gerente de la "
                + "sucursal y el administrador.");
        }
        policy.requireHistoryRead(principal, branchId);
        Branch branch = jdbc.query("SELECT external_id,code,name,active FROM identity_branch WHERE external_id=?",
                (r, i) -> new Branch(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getBoolean(4)), branchId)
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal."));
        List<Row> rows = jdbc.query("""
            SELECT p.external_id,p.sku,p.name,p.base_unit_name,ib.physical_qty,ib.average_base_cost,ib.inventory_value
            FROM inventory_balance ib JOIN identity_branch b ON b.id=ib.branch_id JOIN catalog_product p ON p.id=ib.product_id
            WHERE b.external_id=? AND ib.physical_qty>0 ORDER BY p.sku""", (r, i) -> new Row(r.getObject(1, UUID.class),
                r.getString(2), r.getString(3), r.getString(4), r.getBigDecimal(5), r.getBigDecimal(6), r.getBigDecimal(7)),
            branchId);
        BigDecimal total = rows.stream().map(Row::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<TransitRow> transit = jdbc.query("""
            SELECT tr.external_id,tr.code,CASE WHEN origin.external_id=? THEN 'OUTGOING' ELSE 'INCOMING' END AS direction,
                   counterpart.external_id,counterpart.code,counterpart.name,counterpart.active,
                   p.external_id,p.sku,p.name,p.base_unit_name,t.pending_base_qty,dl.frozen_base_cost,
                   t.pending_base_value+t.pending_rounding_value AS pending_value
            FROM inventory_transit t JOIN logistics_dispatch_line dl ON dl.id=t.dispatch_line_id
            JOIN transfers_line l ON l.id=dl.transfer_line_id JOIN transfers_transfer tr ON tr.id=l.transfer_id
            JOIN catalog_product p ON p.id=l.product_id JOIN identity_branch origin ON origin.id=tr.origin_branch_id
            JOIN identity_branch destination ON destination.id=tr.destination_branch_id
            JOIN identity_branch counterpart ON counterpart.id=
                CASE WHEN origin.external_id=? THEN tr.destination_branch_id ELSE tr.origin_branch_id END
            WHERE t.pending_base_qty>0 AND ? IN (origin.external_id,destination.external_id)
            ORDER BY tr.code,l.line_no""", (r, i) -> new TransitRow(r.getObject(1, UUID.class), r.getString(2), r.getString(3),
                new Branch(r.getObject(4, UUID.class), r.getString(5), r.getString(6), r.getBoolean(7)),
                r.getObject(8, UUID.class), r.getString(9), r.getString(10), r.getString(11), r.getBigDecimal(12),
                r.getBigDecimal(13), r.getBigDecimal(14)), branchId, branchId, branchId);
        OffsetDateTime readAt = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class);
        return new InventoryValuationResponse(branch, readAt.toInstant(), money(total), exact(total),
            rows.stream().map(row -> new Item(row.product(), row.sku(), row.name(), row.baseUnit(),
                row.quantity().setScale(3).toPlainString(), row.average().setScale(InventoryLedger.AVERAGE_SCALE).toPlainString(),
                money(row.value()), exact(row.value()))).toList(),
            money(transitValue(transit, "OUTGOING")), money(transitValue(transit, "INCOMING")),
            transit.stream().map(row -> new TransitItem(row.transfer(), row.code(), row.direction(), row.counterpart(),
                row.product(), row.sku(), row.name(), row.baseUnit(), row.quantity().setScale(3).toPlainString(),
                row.frozenCost().setScale(InventoryLedger.AVERAGE_SCALE).toPlainString(), money(row.value()),
                exact(row.value()))).toList());
    }

    private static BigDecimal transitValue(List<TransitRow> rows, String direction) {
        return rows.stream().filter(row -> row.direction().equals(direction)).map(TransitRow::value)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private record Row(UUID product, String sku, String name, String baseUnit, BigDecimal quantity, BigDecimal average,
                       BigDecimal value) {}

    private record TransitRow(UUID transfer, String code, String direction, Branch counterpart, UUID product, String sku,
                              String name, String baseUnit, BigDecimal quantity, BigDecimal frozenCost, BigDecimal value) {}

    /** Monetary display precision: 2 decimals, half up (INC-013/D03). */
    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String exact(BigDecimal amount) {
        return amount.signum() == 0 ? "0" : amount.stripTrailingZeros().toPlainString();
    }
}
