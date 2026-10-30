package com.optiplant.dashboard.service;

import com.optiplant.dashboard.dto.TransferImpactResponse.Item;
import com.optiplant.dashboard.dto.TransferImpactResponse.Line;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.transfers.service.TransferStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the active transfers of a branch with their lines in one query (RF-0047). Open states follow the same criteria as
 * the branch deactivation (§5.1): a resolved alert keeps a transfer active while its transit is pending, and an open
 * alert keeps it active after the loss was written off.
 */
@Component
class TransferImpactReader {
    private static final String BRANCH_COLUMNS = "%1$s.external_id AS %1$s_id,%1$s.code AS %1$s_code,%1$s.name AS %1$s_name,"
        + "%1$s.active AS %1$s_active";
    private static final String QUERY = "SELECT t.id,t.external_id,t.code,t.status,t.requested_at,t.origin_branch_id,"
        + BRANCH_COLUMNS.formatted("o") + "," + BRANCH_COLUMNS.formatted("d") + """
        ,ld.estimated_arrival_at,x.status AS alert_status,x.treatment,
          p.external_id AS product,l.sku_snapshot,l.product_name_snapshot,p.base_unit_name,l.requested_base_qty,l.prepared_base_qty,
          al.received_base_qty,it.pending_base_qty,
          (SELECT -sum(e.qty_delta) FROM inventory_transit_entry e WHERE e.transit_id=it.id AND e.entry_kind='LOSS') AS lost_qty
        FROM transfers_transfer t JOIN transfers_line l ON l.transfer_id=t.id JOIN catalog_product p ON p.id=l.product_id
        JOIN identity_branch o ON o.id=t.origin_branch_id JOIN identity_branch d ON d.id=t.destination_branch_id
        LEFT JOIN logistics_dispatch ld ON ld.transfer_id=t.id
        LEFT JOIN logistics_dispatch_line dl ON dl.transfer_line_id=l.id
        LEFT JOIN logistics_arrival la ON la.dispatch_id=ld.id
        LEFT JOIN logistics_arrival_line al ON al.dispatch_line_id=dl.id
        LEFT JOIN inventory_transit it ON it.dispatch_line_id=dl.id
        LEFT JOIN logistics_discrepancy x ON x.arrival_id=la.id
        WHERE (t.origin_branch_id=? OR t.destination_branch_id=?)
          AND (t.status IN ('REQUESTED','PREPARING','APPROVED','IN_TRANSIT')
            OR (t.status='RECEIVED_DISCREPANCY' AND (x.status='OPEN' OR EXISTS (
              SELECT 1 FROM logistics_dispatch_line pdl JOIN inventory_transit pit ON pit.dispatch_line_id=pdl.id
              WHERE pdl.dispatch_id=ld.id
                AND (pit.pending_base_qty<>0 OR pit.pending_base_value<>0 OR pit.pending_rounding_value<>0)))))
        ORDER BY t.requested_at DESC,t.id DESC,l.line_no""";

    private final JdbcTemplate jdbc;

    TransferImpactReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<Item> read(long branch) {
        Map<Long, Item> items = new LinkedHashMap<>();
        jdbc.query(QUERY, (ResultSet r) -> {
            items.computeIfAbsent(r.getLong("id"), id -> item(r, branch)).lines().add(line(r));
        }, branch, branch);
        return List.copyOf(items.values());
    }

    private static Item item(ResultSet r, long branch) {
        try {
            boolean origin = r.getLong("origin_branch_id") == branch;
            OffsetDateTime estimated = r.getObject("estimated_arrival_at", OffsetDateTime.class);
            return new Item(r.getObject("external_id", UUID.class), r.getString("code"), r.getString("status"),
                origin ? "ORIGIN" : "DESTINATION", branch(r, origin ? "d" : "o"),
                r.getObject("requested_at", OffsetDateTime.class).toInstant(), instant(estimated), r.getString("alert_status"),
                r.getString("treatment"), new ArrayList<>());
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Line line(ResultSet r) throws SQLException {
        TransferStatus status = TransferStatus.valueOf(r.getString("status"));
        BigDecimal prepared = r.getBigDecimal("prepared_base_qty");
        BigDecimal pending = r.getBigDecimal("pending_base_qty");
        pending = pending == null ? BigDecimal.ZERO : pending;
        boolean received = status == TransferStatus.RECEIVED_DISCREPANCY;
        BigDecimal lost = r.getBigDecimal("lost_qty");
        BigDecimal arrived = r.getBigDecimal("received_base_qty");
        return new Line(r.getObject("product", UUID.class), r.getString("sku_snapshot"), r.getString("product_name_snapshot"),
            r.getString("base_unit_name"),
            status == TransferStatus.IN_TRANSIT || received ? null
                : DashboardService.quantity(prepared == null ? r.getBigDecimal("requested_base_qty") : prepared),
            status == TransferStatus.IN_TRANSIT ? DashboardService.quantity(pending) : null,
            received ? DashboardService.quantity(arrived == null ? BigDecimal.ZERO : arrived) : null,
            received ? DashboardService.quantity(pending) : null,
            received ? DashboardService.quantity(lost == null ? BigDecimal.ZERO : lost) : null);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static Branch branch(ResultSet r, String alias) throws SQLException {
        return new Branch(r.getObject(alias + "_id", UUID.class), r.getString(alias + "_code"), r.getString(alias + "_name"),
            r.getBoolean(alias + "_active"));
    }
}
