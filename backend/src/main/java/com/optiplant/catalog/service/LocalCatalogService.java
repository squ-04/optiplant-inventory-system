package com.optiplant.catalog.service;

import com.optiplant.catalog.dto.LocalCatalogResponse;
import com.optiplant.catalog.dto.ProductResponse;
import com.optiplant.common.exception.ApiException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local catalog of the requesting branch (RF-0012): products enabled explicitly by the administrator, including those
 * without stock. Products that are not enabled are never presented as operable, and other branches are not included.
 */
@Service
public class LocalCatalogService {
    // Pending transit towards the branch, per product; it is neither physical nor available stock at destination.
    private static final String TRANSIT = "SELECT l.product_id,sum(i.pending_base_qty) AS qty FROM inventory_transit i "
        + "JOIN logistics_dispatch_line dl ON dl.id=i.dispatch_line_id JOIN transfers_line l ON l.id=dl.transfer_line_id "
        + "JOIN transfers_transfer t ON t.id=l.transfer_id WHERE t.destination_branch_id=? AND i.pending_base_qty>0 "
        + "GROUP BY l.product_id";

    private final JdbcTemplate jdbc;

    public LocalCatalogService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public LocalCatalogResponse find(UUID branchExternalId) {
        List<Map<String, Object>> branch = jdbc.queryForList("SELECT id,code,name,active FROM identity_branch WHERE external_id=?",
            branchExternalId);
        if (branch.isEmpty()) throw new ApiException(404, "BRANCH_NOT_FOUND", "La sucursal no existe.");
        long branchId = ((Number) branch.getFirst().get("id")).longValue();

        Map<Long, BigDecimal> transit = new HashMap<>();
        jdbc.query(TRANSIT, row -> { transit.put(row.getLong("product_id"), row.getBigDecimal("qty")); }, branchId);
        Map<Long, List<ProductResponse.Unit>> units = new HashMap<>();
        jdbc.query("SELECT u.product_id,u.name,u.factor,u.divisible,u.is_base,u.active FROM catalog_product_unit u "
            + "JOIN catalog_branch_product bp ON bp.product_id=u.product_id WHERE bp.branch_id=? AND bp.enabled AND u.active "
            + "ORDER BY u.is_base DESC,u.name", row -> {
                units.computeIfAbsent(row.getLong("product_id"), key -> new ArrayList<>()).add(ProductService.unit(row));
            }, branchId);

        List<LocalCatalogResponse.Item> items = jdbc.query("SELECT p.id,p.external_id,p.sku,p.name,p.description,p.base_unit_name,"
            + "p.base_divisible,bp.minimum_qty,coalesce(ib.physical_qty,0) AS physical,"
            + "EXISTS (SELECT 1 FROM inventory_reorder_alert a WHERE a.branch_product_id=bp.id AND a.resolved_at IS NULL) AS restock "
            + "FROM catalog_branch_product bp "
            + "JOIN catalog_product p ON p.id=bp.product_id LEFT JOIN inventory_balance ib ON ib.branch_id=bp.branch_id "
            + "AND ib.product_id=bp.product_id WHERE bp.branch_id=? AND bp.enabled AND p.active ORDER BY p.sku", (row, index) -> {
                long productId = row.getLong("id");
                // No reservations exist (glossary §5.3): available equals the operable physical stock.
                String physical = ProductService.exact(row.getBigDecimal("physical"));
                return new LocalCatalogResponse.Item(row.getObject("external_id", UUID.class), row.getString("sku"),
                    row.getString("name"), row.getString("description"), row.getString("base_unit_name"),
                    row.getBoolean("base_divisible"), ProductService.exact(row.getBigDecimal("minimum_qty")), physical, physical,
                    ProductService.exact(transit.getOrDefault(productId, BigDecimal.ZERO)), row.getBoolean("restock"), units.getOrDefault(productId, List.of()));
            }, branchId);
        return new LocalCatalogResponse(branchExternalId, (String) branch.getFirst().get("code"),
            (String) branch.getFirst().get("name"), (Boolean) branch.getFirst().get("active"), items);
    }
}
