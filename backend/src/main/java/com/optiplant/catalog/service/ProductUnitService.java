package com.optiplant.catalog.service;

import com.optiplant.catalog.dto.ProductResponse;
import com.optiplant.catalog.dto.ProductUnitRequest;
import com.optiplant.catalog.dto.UnitConversionResponse;
import com.optiplant.catalog.entity.Product;
import com.optiplant.catalog.repository.ProductRepository;
import com.optiplant.common.exception.ApiException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commercial units per product (RF-0013, INC-007/D01–D05). Each product owns its conversions; there are no universal
 * equivalences. A new factor applies from now on: documents keep their own unit, factor and quantity snapshots, and
 * balances are kept in base units, so nothing historical is re-expressed. The base unit (factor 1) is not managed here.
 */
@Service
public class ProductUnitService {
    private final ProductRepository products;
    private final ProductService catalog;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ProductUnitService(ProductRepository products, ProductService catalog, JdbcTemplate jdbc, Clock clock) {
        this.products = products;
        this.catalog = catalog;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public ProductResponse create(UUID externalId, ProductUnitRequest request) {
        Product product = locked(externalId);
        UnitConversion.requireValidFactor(request.factor(), request.divisible(), product.isBaseDivisible());
        // Names are compared ignoring case so that units stay distinguishable (RF-0013-CA-01).
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM catalog_product_unit WHERE product_id=? "
            + "AND lower(name)=lower(?))", Boolean.class, product.getId(), request.name()))) {
            throw unitExists();
        }
        Timestamp now = Timestamp.from(clock.instant());
        try {
            jdbc.update("INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base,active,created_at,updated_at) "
                + "VALUES (?,?,?,?,FALSE,TRUE,?,?)", product.getId(), request.name(), request.factor(), request.divisible(), now, now);
        } catch (DataIntegrityViolationException error) {
            throw unitExists();
        }
        return catalog.find(externalId, true);
    }

    @Transactional
    public ProductResponse update(UUID externalId, ProductUnitRequest request) {
        Product product = locked(externalId);
        List<Boolean> divisible = commercialUnit(product, request.name());
        UnitConversion.requireValidFactor(request.factor(), divisible.getFirst(), product.isBaseDivisible());
        jdbc.update("UPDATE catalog_product_unit SET factor=?,active=?,updated_at=? WHERE product_id=? AND name=? AND NOT is_base",
            request.factor(), request.active(), Timestamp.from(clock.instant()), product.getId(), request.name());
        return catalog.find(externalId, true);
    }

    /** Exact base equivalent with the factor in force now; inactive units are not offered for new operations. */
    @Transactional(readOnly = true)
    public UnitConversionResponse convert(UUID externalId, String unitName, BigDecimal quantity) {
        Product product = products.findByExternalId(externalId).orElseThrow(ProductUnitService::productNotFound);
        List<Object[]> units = jdbc.query("SELECT factor,divisible FROM catalog_product_unit WHERE product_id=? AND name=? AND active",
            (row, index) -> new Object[] {row.getBigDecimal("factor"), row.getBoolean("divisible")}, product.getId(), unitName);
        if (units.isEmpty()) throw unitNotFound();
        BigDecimal factor = (BigDecimal) units.getFirst()[0];
        BigDecimal base = UnitConversion.toBase(quantity, factor, (Boolean) units.getFirst()[1], product.isBaseDivisible());
        return new UnitConversionResponse(unitName, quantity.toPlainString(), ProductService.exact(factor),
            product.getBaseUnitName(), base.toPlainString());
    }

    /** Locks and returns the divisibility of a commercial unit; the base unit is fixed and rejected. */
    private List<Boolean> commercialUnit(Product product, String name) {
        List<Boolean> base = jdbc.queryForList("SELECT is_base FROM catalog_product_unit WHERE product_id=? AND name=? FOR UPDATE",
            Boolean.class, product.getId(), name);
        if (base.isEmpty()) throw unitNotFound();
        if (base.getFirst()) {
            throw new ApiException(409, "PRODUCT_BASE_UNIT_FIXED",
                "La unidad base tiene factor 1 y se modifica desde los datos del producto mientras no tenga historial.");
        }
        return jdbc.queryForList("SELECT divisible FROM catalog_product_unit WHERE product_id=? AND name=?", Boolean.class,
            product.getId(), name);
    }

    private Product locked(UUID externalId) {
        return products.lockByExternalId(externalId).orElseThrow(ProductUnitService::productNotFound);
    }

    private static ApiException productNotFound() {
        return new ApiException(404, "PRODUCT_NOT_FOUND", "El producto no existe.");
    }

    private static ApiException unitNotFound() {
        return new ApiException(404, "PRODUCT_UNIT_NOT_FOUND", "La unidad no existe o no está activa para este producto.");
    }

    private static ApiException unitExists() {
        return new ApiException(409, "PRODUCT_UNIT_EXISTS", "El producto ya tiene una unidad con ese nombre.");
    }
}
