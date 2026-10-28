package com.optiplant.catalog.service;

import com.optiplant.common.exception.ApiException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves the commercial unit of a product that a branch may operate (RF-0011-CA-03, RF-0012): the product must be
 * active and enabled in the branch catalog and the unit active. Manual movements and purchase orders share this rule.
 */
@Component
public class OperableUnits {
    public record Unit(long id, long productId, String unitName, BigDecimal factor, boolean divisible, boolean baseDivisible) {}

    private final JdbcTemplate jdbc;

    public OperableUnits(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The active unit {@code unitName} of the public product {@code product}, operable in the branch. */
    public Unit find(long branchId, UUID product, String unitName) {
        List<Map<String, Object>> found = jdbc.queryForList("""
            SELECT p.id AS product_id,p.base_divisible,(p.active AND coalesce(bp.enabled,false)) AS operable
            FROM catalog_product p LEFT JOIN catalog_branch_product bp ON bp.product_id=p.id AND bp.branch_id=?
            WHERE p.external_id=?""", branchId, product);
        if (found.isEmpty()) throw new ApiException(404, "PRODUCT_NOT_FOUND", "El producto no existe.");
        long productId = requireOperable(found.getFirst());
        boolean baseDivisible = (Boolean) found.getFirst().get("base_divisible");
        return jdbc.query("SELECT id,name,factor,divisible FROM catalog_product_unit WHERE product_id=? AND name=? AND active",
                (row, index) -> new Unit(row.getLong(1), productId, row.getString(2), row.getBigDecimal(3), row.getBoolean(4),
                    baseDivisible), productId, unitName).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "PRODUCT_UNIT_NOT_FOUND", "La unidad no existe o no está activa para este producto."));
    }

    /** Re-checks by internal identifiers that a unit chosen earlier is still active and operable in the branch. */
    public Unit find(long branchId, long productId, long unitId) {
        List<Map<String, Object>> found = jdbc.queryForList("""
            SELECT p.id AS product_id,p.base_divisible,(p.active AND coalesce(bp.enabled,false)) AS operable
            FROM catalog_product p LEFT JOIN catalog_branch_product bp ON bp.product_id=p.id AND bp.branch_id=?
            WHERE p.id=?""", branchId, productId);
        requireOperable(found.getFirst());
        boolean baseDivisible = (Boolean) found.getFirst().get("base_divisible");
        return jdbc.query("SELECT id,name,factor,divisible FROM catalog_product_unit WHERE id=? AND product_id=? AND active",
                (row, index) -> new Unit(row.getLong(1), productId, row.getString(2), row.getBigDecimal(3), row.getBoolean(4),
                    baseDivisible), unitId, productId).stream().findFirst()
            .orElseThrow(() -> new ApiException(409, "PRODUCT_UNIT_NOT_FOUND", "Una unidad de la orden ya no está activa para su producto."));
    }

    /** Rejects a product that is inactive or not enabled in the branch. */
    public void requireOperable(long branchId, long productId) {
        requireOperable(jdbc.queryForList("""
            SELECT p.id AS product_id,(p.active AND coalesce(bp.enabled,false)) AS operable FROM catalog_product p
            LEFT JOIN catalog_branch_product bp ON bp.product_id=p.id AND bp.branch_id=? WHERE p.id=?""", branchId, productId)
            .getFirst());
    }

    private static long requireOperable(Map<String, Object> product) {
        if (!(Boolean) product.get("operable")) {
            throw new ApiException(409, "PRODUCT_NOT_OPERABLE", "El producto no está activo y habilitado en el catálogo de la sucursal.");
        }
        return ((Number) product.get("product_id")).longValue();
    }
}
