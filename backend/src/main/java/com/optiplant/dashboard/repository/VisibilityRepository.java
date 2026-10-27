package com.optiplant.dashboard.repository;

import com.optiplant.common.exception.ApiException;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.dashboard.dto.VisibilityResponse.Document;
import com.optiplant.dashboard.dto.VisibilityResponse.InventoryItem;
import com.optiplant.dashboard.dto.VisibilityResponse.Movement;
import com.optiplant.dashboard.dto.VisibilityResponse.StockItem;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VisibilityRepository {
    private final JdbcTemplate jdbc;
    public VisibilityRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Branch> branches() {
        return jdbc.query("SELECT external_id,code,name,active FROM identity_branch ORDER BY code", (r, i) -> branch(r));
    }
    public Branch branch(UUID id) {
        return jdbc.query("SELECT external_id,code,name,active FROM identity_branch WHERE external_id=?",
            (r, i) -> branch(r), id).stream().findFirst().orElseThrow(() ->
                new ApiException(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal."));
    }
    private Branch branch(ResultSet r) throws SQLException {
        return new Branch(r.getObject("external_id", UUID.class), r.getString("code"), r.getString("name"), r.getBoolean("active"));
    }
    public List<InventoryItem> inventory(UUID branch) {
        return jdbc.query("""
            SELECT p.external_id,p.sku,p.name,ib.physical_qty FROM inventory_balance ib
            JOIN identity_branch b ON b.id=ib.branch_id JOIN catalog_product p ON p.id=ib.product_id
            WHERE b.external_id=? ORDER BY p.sku
            """, (r, i) -> new InventoryItem(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getBigDecimal(4).toPlainString()), branch);
    }
    /**
     * Products with a balance, enabled in the branch catalog or with pending transit towards the branch. Transit is
     * reported apart and never added to physical or available stock. Costs and valuation are not selected.
     */
    public List<StockItem> stock(UUID branch) {
        return jdbc.query("""
            WITH b AS (SELECT id FROM identity_branch WHERE external_id=?),
            transit AS (
                SELECT l.product_id,sum(i.pending_base_qty) AS qty FROM inventory_transit i
                JOIN logistics_dispatch_line dl ON dl.id=i.dispatch_line_id JOIN transfers_line l ON l.id=dl.transfer_line_id
                JOIN transfers_transfer t ON t.id=l.transfer_id JOIN b ON b.id=t.destination_branch_id
                WHERE i.pending_base_qty>0 GROUP BY l.product_id)
            SELECT p.external_id,p.sku,p.name,p.base_unit_name,coalesce(ib.physical_qty,0::numeric(18,3)) AS physical,
                coalesce(tr.qty,0::numeric(18,3)) AS transit
            FROM catalog_product p CROSS JOIN b
            LEFT JOIN inventory_balance ib ON ib.branch_id=b.id AND ib.product_id=p.id
            LEFT JOIN catalog_branch_product bp ON bp.branch_id=b.id AND bp.product_id=p.id
            LEFT JOIN transit tr ON tr.product_id=p.id
            WHERE ib.id IS NOT NULL OR tr.qty IS NOT NULL OR (bp.enabled AND p.active)
            ORDER BY p.sku
            """, (r, i) -> {
                // No reservations exist (glossary §5.3): available equals the operable physical stock.
                String physical = r.getBigDecimal("physical").setScale(3).toPlainString();
                return new StockItem(r.getObject("external_id", UUID.class), r.getString("sku"), r.getString("name"),
                    r.getString("base_unit_name"), physical, physical, r.getBigDecimal("transit").setScale(3).toPlainString());
            }, branch);
    }
    /** Transaction start time: under the service's repeatable-read transaction it dates the consulted snapshot. */
    public Instant snapshotTime() {
        return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class).toInstant();
    }
    public List<Document> purchases(UUID branch) {
        return jdbc.query("""
            SELECT o.external_id,o.code,o.status,o.created_at FROM purchases_order o
            JOIN identity_branch b ON b.id=o.branch_id WHERE b.external_id=? ORDER BY o.created_at,o.code
            """, (r, i) -> document(r), branch);
    }
    public List<Document> sales(UUID branch) {
        return jdbc.query("""
            SELECT s.external_id,s.code,'CONFIRMED',s.confirmed_at FROM sales_sale s
            JOIN identity_branch b ON b.id=s.branch_id WHERE b.external_id=? ORDER BY s.confirmed_at,s.code
            """, (r, i) -> document(r), branch);
    }
    public List<Document> transfers(UUID branch) {
        return jdbc.query("""
            SELECT t.external_id,t.code,t.status,t.requested_at FROM transfers_transfer t
            JOIN identity_branch b ON b.id=t.origin_branch_id OR b.id=t.destination_branch_id
            WHERE b.external_id=? ORDER BY t.requested_at,t.code
            """, (r, i) -> document(r), branch);
    }
    private Document document(ResultSet r) throws SQLException {
        return new Document(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getObject(4, OffsetDateTime.class).toInstant());
    }
    public List<Movement> movements(UUID branch) {
        return jdbc.query("""
            SELECT m.external_id,o.occurred_at,o.kind,m.direction,m.sku_snapshot,m.product_name_snapshot,
                m.commercial_qty,m.unit_name_snapshot,u.code FROM inventory_movement m
            JOIN inventory_operation o ON o.id=m.operation_id JOIN identity_user_account u ON u.id=o.actor_id
            JOIN identity_branch b ON b.id=m.branch_id WHERE b.external_id=? ORDER BY o.occurred_at,m.external_id
            """, (r, i) -> new Movement(r.getObject(1, UUID.class), r.getObject(2, OffsetDateTime.class).toInstant(),
                r.getString(3), r.getString(4), r.getString(5), r.getString(6), r.getBigDecimal(7).toPlainString(), r.getString(8), r.getString(9)), branch);
    }
}
