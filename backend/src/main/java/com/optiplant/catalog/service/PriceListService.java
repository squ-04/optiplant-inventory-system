package com.optiplant.catalog.service;

import com.optiplant.catalog.dto.PriceEntryRequest;
import com.optiplant.catalog.dto.PriceListRequest;
import com.optiplant.catalog.dto.PriceListResponse;
import com.optiplant.catalog.entity.PriceList;
import com.optiplant.catalog.repository.PriceListRepository;
import com.optiplant.common.exception.ApiException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Global price lists with one gross price per product commercial unit (RF-0030, INC-014/D01). Changing a price does
 * not re-express sales: a confirmed sale keeps its own list name and price snapshots. Local profiles only see active
 * lists, the ones they may choose when confirming a sale.
 */
@Service
public class PriceListService {
    /** The price a sale takes from a list: the list name and gross unit price are copied into the sale line. */
    public record ListPrice(long priceListId, String listName, BigDecimal grossPrice) {}

    private final PriceListRepository lists;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PriceListService(PriceListRepository lists, JdbcTemplate jdbc, Clock clock) {
        this.lists = lists;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PriceListResponse> list(boolean includeInactive) {
        return (includeInactive ? lists.findAllByOrderByCodeAsc() : lists.findAllByActiveTrueOrderByCodeAsc()).stream()
            .map(list -> response(list, null)).toList();
    }

    @Transactional(readOnly = true)
    public PriceListResponse find(UUID externalId, boolean includeInactive) {
        PriceList list = lists.findByExternalId(externalId).filter(found -> includeInactive || found.isActive())
            .orElseThrow(PriceListService::notFound);
        return response(list, prices(list.getId()));
    }

    @Transactional
    public PriceListResponse create(PriceListRequest request) {
        if (lists.existsByCodeIgnoreCase(request.code())) throw codeExists();
        try {
            return response(lists.saveAndFlush(PriceList.create(request.code(), request.name(), clock.instant())), List.of());
        } catch (DataIntegrityViolationException error) {
            throw codeExists();
        }
    }

    @Transactional
    public PriceListResponse update(UUID externalId, PriceListRequest request) {
        PriceList list = lists.findByExternalId(externalId).orElseThrow(PriceListService::notFound);
        if (lists.existsByCodeIgnoreCaseAndIdNot(request.code(), list.getId())) throw codeExists();
        list.update(request.code(), request.name(), request.active(), clock.instant());
        try {
            lists.saveAndFlush(list);
        } catch (DataIntegrityViolationException error) {
            throw codeExists();
        }
        return response(list, prices(list.getId()));
    }

    /** Defines or replaces the price of an active unit of an active product; other prices of the list are kept. */
    @Transactional
    public PriceListResponse setPrice(UUID externalId, PriceEntryRequest request) {
        PriceList list = lists.findByExternalId(externalId).orElseThrow(PriceListService::notFound);
        List<Map<String, Object>> product = jdbc.queryForList("SELECT id,active FROM catalog_product WHERE external_id=?",
            request.product());
        if (product.isEmpty()) throw new ApiException(404, "PRODUCT_NOT_FOUND", "El producto no existe.");
        if (!(Boolean) product.getFirst().get("active")) {
            throw new ApiException(409, "PRODUCT_INACTIVE", "El producto está inactivo: no se le asignan precios nuevos.");
        }
        Long unitId = jdbc.query("SELECT id FROM catalog_product_unit WHERE product_id=? AND name=? AND active",
                (row, index) -> row.getLong(1), product.getFirst().get("id"), request.unit()).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "PRODUCT_UNIT_NOT_FOUND", "La unidad no existe o no está activa para este producto."));
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
            INSERT INTO catalog_price_entry(price_list_id,product_unit_id,gross_price,created_at,updated_at) VALUES (?,?,?,?,?)
            ON CONFLICT (price_list_id,product_unit_id) DO UPDATE SET gross_price=EXCLUDED.gross_price,updated_at=EXCLUDED.updated_at""",
            list.getId(), unitId, request.price(), now, now);
        return response(list, prices(list.getId()));
    }

    /**
     * The price of a product unit in an active list, for the sale being confirmed (RF-0030-CA-01, RF-0027). There is no
     * free price: a unit without a price in the chosen list cannot be sold with it.
     */
    @Transactional(readOnly = true)
    public ListPrice price(UUID priceList, long productUnitId) {
        PriceList list = lists.findByExternalId(priceList).orElseThrow(PriceListService::notFound);
        if (!list.isActive()) throw new ApiException(409, "PRICE_LIST_INACTIVE", "La lista de precios está inactiva.");
        return jdbc.query("SELECT gross_price FROM catalog_price_entry WHERE price_list_id=? AND product_unit_id=?",
                (row, index) -> new ListPrice(list.getId(), list.getName(), row.getBigDecimal(1)), list.getId(), productUnitId)
            .stream().findFirst()
            .orElseThrow(() -> new ApiException(409, "PRICE_NOT_DEFINED", "La lista elegida no define precio para esta unidad."));
    }

    private List<PriceListResponse.Price> prices(long listId) {
        return jdbc.query("""
            SELECT p.external_id,p.sku,p.name AS product_name,p.active AS product_active,u.name AS unit_name,u.active AS unit_active,
                   e.gross_price
            FROM catalog_price_entry e JOIN catalog_product_unit u ON u.id=e.product_unit_id JOIN catalog_product p ON p.id=u.product_id
            WHERE e.price_list_id=? ORDER BY p.sku,u.is_base DESC,u.name""",
            (row, index) -> new PriceListResponse.Price(row.getObject("external_id", UUID.class), row.getString("sku"),
                row.getString("product_name"), row.getBoolean("product_active"), row.getString("unit_name"),
                row.getBoolean("unit_active"), row.getBigDecimal("gross_price").stripTrailingZeros().toPlainString()), listId);
    }

    private static PriceListResponse response(PriceList list, List<PriceListResponse.Price> prices) {
        return new PriceListResponse(list.getExternalId(), list.getCode(), list.getName(), list.isActive(), prices);
    }

    private static ApiException notFound() {
        return new ApiException(404, "PRICE_LIST_NOT_FOUND", "No se encontró la lista de precios.");
    }

    private static ApiException codeExists() {
        return new ApiException(409, "PRICE_LIST_CODE_EXISTS", "Ya existe una lista de precios con ese código. No se modificó el catálogo.");
    }
}
