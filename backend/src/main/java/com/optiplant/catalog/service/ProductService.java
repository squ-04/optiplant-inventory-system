package com.optiplant.catalog.service;

import com.optiplant.catalog.dto.ProductCreateRequest;
import com.optiplant.catalog.dto.ProductEnablementRequest;
import com.optiplant.catalog.dto.ProductMinimumRequest;
import com.optiplant.catalog.dto.ProductResponse;
import com.optiplant.catalog.dto.ProductUpdateRequest;
import com.optiplant.catalog.entity.Product;
import com.optiplant.catalog.repository.ProductRepository;
import com.optiplant.common.exception.ApiException;
import com.optiplant.inventory.service.ReorderAlertService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product maintenance (RF-0008–RF-0011) and minimum stock configuration (RF-0001); minimum and enablement changes
 * re-evaluate restock alerts (RF-0019/RF-0020). Products are never physically deleted (H v1.3/01): removal is
 * exclusively {@code active=false}, and the runtime database role has no DELETE privilege on the table.
 */
@Service
public class ProductService {
    // Documents, movements, balances and prices that protect SKU and base unit once a product is used.
    private static final String USAGE = "SELECT product_id FROM inventory_balance UNION SELECT product_id FROM inventory_movement "
        + "UNION SELECT product_id FROM purchases_line UNION SELECT product_id FROM sales_line "
        + "UNION SELECT product_id FROM transfers_line UNION SELECT u.product_id FROM catalog_price_entry e "
        + "JOIN catalog_product_unit u ON u.id=e.product_unit_id";

    private final ProductRepository products;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ReorderAlertService alerts;

    public ProductService(ProductRepository products, JdbcTemplate jdbc, Clock clock, ReorderAlertService alerts) {
        this.products = products;
        this.jdbc = jdbc;
        this.clock = clock;
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        Set<Long> used = new HashSet<>(jdbc.queryForList(USAGE, Long.class));
        return products.findAllByOrderBySkuAsc().stream()
            .map(product -> response(product, used.contains(product.getId()), null, null)).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse find(UUID externalId, boolean withBranches) {
        return response(products.findByExternalId(externalId).orElseThrow(ProductService::notFound), withBranches);
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        ProductUpdateRequest attributes = request.attributes();
        if (products.existsBySkuIgnoreCase(attributes.sku())) throw skuExists();
        List<Long> branches = request.branches().stream().map(branch -> activeBranch(branch.branchExternalId())).toList();
        Product product;
        try {
            product = products.saveAndFlush(Product.create(attributes.sku(), attributes.name(), attributes.description(),
                attributes.baseUnitName(), attributes.baseDivisible(), clock.instant()));
        } catch (DataIntegrityViolationException error) {
            throw skuExists();
        }
        Timestamp now = now();
        jdbc.update("INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base,created_at,updated_at) "
            + "VALUES (?,?,1,?,TRUE,?,?)", product.getId(), attributes.baseUnitName(), attributes.baseDivisible(), now, now);
        // Enablement and its explicit minimum are written together; no stock balance is created.
        for (int index = 0; index < branches.size(); index++) {
            jdbc.update("INSERT INTO catalog_branch_product(branch_id,product_id,enabled,minimum_qty,created_at,updated_at) "
                + "VALUES (?,?,TRUE,?,?,?)", branches.get(index), product.getId(), request.branches().get(index).minimumQuantity(), now, now);
            alerts.evaluate(branches.get(index), product.getId());
        }
        return response(product, true);
    }

    @Transactional
    public ProductResponse update(UUID externalId, ProductUpdateRequest request) {
        Product product = locked(externalId);
        boolean identityChanged = !product.getSku().equals(request.sku());
        boolean baseChanged = !product.getBaseUnitName().equals(request.baseUnitName())
            || product.isBaseDivisible() != request.baseDivisible();
        if ((identityChanged || baseChanged) && inUse(product.getId())) {
            throw new ApiException(409, "PRODUCT_IDENTITY_PROTECTED",
                "El producto ya fue utilizado: su SKU y su unidad base no pueden modificarse.");
        }
        if (identityChanged && products.existsBySkuIgnoreCaseAndIdNot(request.sku(), product.getId())) throw skuExists();
        if (baseChanged) {
            if (!request.baseDivisible() && exists("SELECT EXISTS (SELECT 1 FROM catalog_branch_product WHERE product_id=? "
                + "AND minimum_qty IS NOT NULL AND minimum_qty<>trunc(minimum_qty))", product.getId())) {
                throw new ApiException(409, "PRODUCT_MINIMUM_FRACTIONAL",
                    "Hay mínimos fraccionarios configurados: la unidad base debe seguir siendo divisible.");
            }
            if (!request.baseDivisible() && exists("SELECT EXISTS (SELECT 1 FROM catalog_product_unit WHERE product_id=? "
                + "AND NOT is_base AND (divisible OR factor<>trunc(factor)))", product.getId())) {
                throw new ApiException(409, "PRODUCT_UNITS_INCOMPATIBLE",
                    "Hay unidades comerciales fraccionables o con factor decimal: la unidad base debe seguir siendo divisible.");
            }
            try {
                jdbc.update("UPDATE catalog_product_unit SET name=?,divisible=?,updated_at=? WHERE product_id=? AND is_base",
                    request.baseUnitName(), request.baseDivisible(), now(), product.getId());
            } catch (DataIntegrityViolationException error) {
                throw new ApiException(409, "PRODUCT_UNIT_EXISTS", "El producto ya tiene una unidad con ese nombre.");
            }
        }
        // Updating attributes never reactivates the product nor restores local enablement.
        product.update(request.sku(), request.name(), request.description(), request.baseUnitName(), request.baseDivisible(),
            clock.instant());
        try {
            products.flush();
        } catch (DataIntegrityViolationException error) {
            throw skuExists();
        }
        return response(product, true);
    }

    @Transactional
    public ProductResponse enable(UUID externalId, ProductEnablementRequest request) {
        Product product = locked(externalId);
        if (!product.isActive()) {
            throw new ApiException(409, "PRODUCT_INACTIVE", "El producto está inactivo y no admite nuevas habilitaciones.");
        }
        if (!request.admits(product.isBaseDivisible())) {
            throw new ApiException(400, "VALIDATION_FAILED", "La unidad base no es divisible: el mínimo debe ser entero.");
        }
        long branch = activeBranch(request.branchExternalId());
        List<Boolean> current = jdbc.queryForList("SELECT enabled FROM catalog_branch_product WHERE branch_id=? AND product_id=? "
            + "FOR UPDATE", Boolean.class, branch, product.getId());
        if (current.contains(Boolean.TRUE)) {
            throw new ApiException(409, "PRODUCT_ALREADY_ENABLED", "El producto ya está habilitado en la sucursal.");
        }
        Timestamp now = now();
        if (current.isEmpty()) {
            jdbc.update("INSERT INTO catalog_branch_product(branch_id,product_id,enabled,minimum_qty,created_at,updated_at) "
                + "VALUES (?,?,TRUE,?,?,?)", branch, product.getId(), request.minimumQuantity(), now, now);
        } else {
            jdbc.update("UPDATE catalog_branch_product SET enabled=TRUE,minimum_qty=?,updated_at=? WHERE branch_id=? AND product_id=?",
                request.minimumQuantity(), now, branch, product.getId());
        }
        // A new enablement starts a new evaluation against its explicit minimum (RF-0019).
        alerts.evaluate(branch, product.getId());
        return response(product, true);
    }

    /**
     * Changes only the explicit minimum of an enabled product-branch combination (RF-0001). Stock, costs, documents
     * and restock history are untouched; nothing is blocked or purchased automatically.
     */
    @Transactional
    public ProductResponse updateMinimum(UUID externalId, UUID branchExternalId, ProductMinimumRequest request) {
        Product product = locked(externalId);
        if (!ProductEnablementRequest.admits(request.minimumQuantity(), product.isBaseDivisible())) {
            throw new ApiException(400, "VALIDATION_FAILED", "La unidad base no es divisible: el mínimo debe ser entero.");
        }
        List<Long> branch = jdbc.queryForList("SELECT id FROM identity_branch WHERE external_id=?", Long.class, branchExternalId);
        if (branch.isEmpty()) throw new ApiException(404, "BRANCH_NOT_FOUND", "La sucursal no existe.");
        List<Boolean> enabled = jdbc.queryForList("SELECT enabled FROM catalog_branch_product WHERE branch_id=? AND product_id=? "
            + "FOR UPDATE", Boolean.class, branch.getFirst(), product.getId());
        if (!enabled.contains(Boolean.TRUE)) {
            throw new ApiException(409, "PRODUCT_NOT_ENABLED",
                "El producto no está habilitado en la sucursal: habilítelo indicando su stock mínimo.");
        }
        jdbc.update("UPDATE catalog_branch_product SET minimum_qty=?,updated_at=? WHERE branch_id=? AND product_id=?",
            request.minimumQuantity(), now(), branch.getFirst(), product.getId());
        // RF-0019-CA-02: the next evaluation already uses the new minimum; no other threshold exists.
        alerts.evaluate(branch.getFirst(), product.getId());
        return response(product, true);
    }

    @Transactional
    public ProductResponse deactivate(UUID externalId) {
        Product product = locked(externalId);
        if (!product.isActive()) return response(product, true);
        long id = product.getId();
        if (exists("SELECT EXISTS (SELECT 1 FROM inventory_balance WHERE product_id=? AND physical_qty<>0)", id)) {
            throw blocked("El producto tiene existencias físicas distintas de cero en al menos una sucursal.");
        }
        if (exists("SELECT EXISTS (SELECT 1 FROM purchases_line l JOIN purchases_order o ON o.id=l.order_id "
            + "WHERE l.product_id=? AND o.status IN ('DRAFT','PENDING'))", id)) {
            throw blocked("El producto figura en compras en borrador o pendientes de recepción.");
        }
        if (exists("SELECT EXISTS (SELECT 1 FROM transfers_line l JOIN transfers_transfer t ON t.id=l.transfer_id "
            + "WHERE l.product_id=? AND t.status IN ('REQUESTED','PREPARING','APPROVED','IN_TRANSIT'))", id)) {
            throw blocked("El producto figura en transferencias abiertas.");
        }
        if (exists("SELECT EXISTS (SELECT 1 FROM inventory_transit i JOIN logistics_dispatch_line dl ON dl.id=i.dispatch_line_id "
            + "JOIN transfers_line l ON l.id=dl.transfer_line_id WHERE l.product_id=? AND "
            + "(i.pending_base_qty<>0 OR i.pending_base_value<>0 OR i.pending_rounding_value<>0))", id)) {
            throw blocked("El producto tiene inventario en tránsito pendiente.");
        }
        if (exists("SELECT EXISTS (SELECT 1 FROM logistics_discrepancy_line dsl JOIN logistics_discrepancy d ON d.id=dsl.discrepancy_id "
            + "JOIN logistics_arrival_line al ON al.id=dsl.arrival_line_id JOIN logistics_dispatch_line dl ON dl.id=al.dispatch_line_id "
            + "JOIN transfers_line l ON l.id=dl.transfer_line_id WHERE l.product_id=? AND d.status='OPEN')", id)) {
            throw blocked("El producto tiene faltantes de transferencia pendientes de tratamiento.");
        }
        Timestamp now = now();
        // Disabling closes active restock episodes; rows are kept for history and references.
        alerts.closeProduct(id);
        jdbc.update("UPDATE catalog_branch_product SET enabled=FALSE,minimum_qty=NULL,updated_at=? WHERE product_id=? "
            + "AND (enabled OR minimum_qty IS NOT NULL)", now, id);
        product.deactivate(clock.instant());
        return response(product, true);
    }

    @Transactional
    public ProductResponse reactivate(UUID externalId) {
        Product product = locked(externalId);
        // Catalog availability only: each branch must enable it again with a new explicit minimum.
        if (!product.isActive()) product.reactivate(clock.instant());
        return response(product, true);
    }

    private long activeBranch(UUID externalId) {
        List<Boolean> active = jdbc.queryForList("SELECT active FROM identity_branch WHERE external_id=? FOR SHARE",
            Boolean.class, externalId);
        if (active.isEmpty()) throw new ApiException(404, "BRANCH_NOT_FOUND", "La sucursal no existe.");
        if (!active.getFirst()) throw new ApiException(409, "BRANCH_INACTIVE", "La sucursal está inactiva.");
        return Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM identity_branch WHERE external_id=?", Long.class, externalId));
    }

    private ProductResponse response(Product product, boolean withBranches) {
        return response(product, inUse(product.getId()), withBranches ? branches(product.getId()) : null, units(product.getId()));
    }

    private ProductResponse response(Product product, boolean inUse, List<ProductResponse.Branch> branches,
        List<ProductResponse.Unit> units) {
        return new ProductResponse(product.getExternalId(), product.getSku(), product.getName(), product.getDescription(),
            product.getBaseUnitName(), product.isBaseDivisible(), product.isActive(), inUse, branches, units);
    }

    /** Base unit first, then commercial units by name; inactive units are listed for administration and history. */
    private List<ProductResponse.Unit> units(long productId) {
        return jdbc.query("SELECT name,factor,divisible,is_base,active FROM catalog_product_unit WHERE product_id=? "
            + "ORDER BY is_base DESC,name", (row, index) -> unit(row), productId);
    }

    static ProductResponse.Unit unit(java.sql.ResultSet row) throws java.sql.SQLException {
        return new ProductResponse.Unit(row.getString("name"), exact(row.getBigDecimal("factor")),
            row.getBoolean("divisible"), row.getBoolean("is_base"), row.getBoolean("active"));
    }

    private List<ProductResponse.Branch> branches(long productId) {
        return jdbc.query("SELECT b.external_id,b.code,b.name,b.active,bp.enabled,bp.minimum_qty FROM catalog_branch_product bp "
            + "JOIN identity_branch b ON b.id=bp.branch_id WHERE bp.product_id=? ORDER BY b.code", (row, index) -> {
                BigDecimal minimum = row.getBigDecimal("minimum_qty");
                return new ProductResponse.Branch(row.getObject("external_id", UUID.class), row.getString("code"),
                    row.getString("name"), row.getBoolean("active"), row.getBoolean("enabled"),
                    minimum == null ? null : minimum.setScale(3).toPlainString());
            }, productId);
    }

    /** Plain decimal string with at least three decimals; never rounds a stored value. */
    static String exact(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.scale() < 3 ? stripped.setScale(3) : stripped).toPlainString();
    }

    private boolean inUse(long productId) {
        return exists("SELECT EXISTS (SELECT 1 FROM (" + USAGE + ") used WHERE product_id=?)", productId);
    }

    private Product locked(UUID externalId) {
        return products.lockByExternalId(externalId).orElseThrow(ProductService::notFound);
    }

    private boolean exists(String sql, Object... values) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, values));
    }

    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }

    private static ApiException notFound() {
        return new ApiException(404, "PRODUCT_NOT_FOUND", "El producto no existe.");
    }

    private static ApiException skuExists() {
        return new ApiException(409, "PRODUCT_SKU_EXISTS", "El SKU ya está registrado en la red.");
    }

    private static ApiException blocked(String detail) {
        return new ApiException(409, "PRODUCT_DEACTIVATION_BLOCKED", detail);
    }
}
