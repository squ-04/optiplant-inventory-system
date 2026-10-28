package com.optiplant.sales.service;

import com.optiplant.catalog.service.OperableUnits;
import com.optiplant.catalog.service.PriceListService;
import com.optiplant.catalog.service.UnitConversion;
import com.optiplant.common.exception.ApiException;
import com.optiplant.common.idempotency.IdempotencyService;
import com.optiplant.common.text.SpanishDecimal;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.inventory.service.InventoryLedger;
import com.optiplant.inventory.service.InventoryLedger.Cost;
import com.optiplant.inventory.service.InventoryLedger.Direction;
import com.optiplant.inventory.service.InventoryLedger.Kind;
import com.optiplant.sales.dto.SaleRequest;
import com.optiplant.sales.dto.SaleResponse;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirms a sale of the session's own active branch (RF-0027–RF-0029, INC-014). There is no server-side draft: a sale
 * exists only once confirmed, so a draft or a failed attempt never produces a record or a withdrawal (INC-014/D03).
 *
 * <p>Confirmation is one transaction: every line is resolved against the branch catalog and the chosen price list,
 * the balances of the sold products are locked in product order and the physical stock (there are no reservations) is
 * validated for all lines together; any shortage rejects the whole sale (RF-0028, INC-014/D04). Only then the record
 * is written with its commercial snapshots and the withdrawal is posted through {@link InventoryLedger}, the only writer
 * of stock (ARQ PEN-005): one {@code SALE} operation linked to the sale with one exit per line at the current average.
 * The balance locks serialize concurrent sales of the same product, so two confirmations never consume the same units
 * (RF-0028-CA-03). The {@code Idempotency-Key} returns the original record when a confirmation is repeated, without a
 * second withdrawal (RF-0027-CA-02).
 */
@Service
public class SaleService {
    static final String CONFIRM = "sales.sale-confirm.v1";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final InventoryLedger ledger;
    private final IdempotencyService idempotency;
    private final OperableUnits units;
    private final PriceListService prices;
    private final SaleQueryService reader;

    public SaleService(JdbcTemplate jdbc, Clock clock, InventoryLedger ledger, IdempotencyService idempotency, OperableUnits units,
                       PriceListService prices, SaleQueryService reader) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.ledger = ledger;
        this.idempotency = idempotency;
        this.units = units;
        this.prices = prices;
        this.reader = reader;
    }

    private record Context(long branchId, long actorId) {}

    private record Resolved(OperableUnits.Unit unit, SaleRequest.Line line, BigDecimal base, PriceListService.ListPrice price,
                            String sku, String productName) {}

    @Transactional
    public SaleResponse confirm(SessionPrincipal principal, String key, SaleRequest request) {
        IdempotencyService.requireKey(key);
        Context context = context(principal);
        return idempotency.execute(context.actorId(), CONFIRM, principal.branchExternalId().toString(), key, request, 201,
            SaleResponse.class, () -> reader.record(insert(context, request)));
    }

    private long insert(Context context, SaleRequest request) {
        List<Resolved> lines = new ArrayList<>();
        for (int index = 0; index < request.lines().size(); index++) {
            SaleRequest.Line line = request.lines().get(index);
            lines.add(numbered(index + 1, () -> {
                OperableUnits.Unit unit = units.find(context.branchId(), line.product(), line.unit());
                BigDecimal base = UnitConversion.toBase(line.quantity(), unit.factor(), unit.divisible(), unit.baseDivisible());
                PriceListService.ListPrice price = prices.price(request.priceList(), unit.id());
                Map<String, Object> product = jdbc.queryForMap("SELECT sku,name FROM catalog_product WHERE id=?", unit.productId());
                return new Resolved(unit, line, base, price, (String) product.get("sku"), (String) product.get("name"));
            }));
        }

        // Lock every sold balance in product order and validate the whole sale before any write (RF-0028).
        Map<Long, BigDecimal> required = new TreeMap<>();
        lines.forEach(line -> required.merge(line.unit().productId(), line.base(), BigDecimal::add));
        Map<Long, InventoryLedger.Balance> balances = new TreeMap<>();
        required.keySet().forEach(product -> balances.put(product, ledger.lock(context.branchId(), product)));
        for (Resolved line : lines) {
            BigDecimal needed = required.get(line.unit().productId());
            BigDecimal available = balances.get(line.unit().productId()).quantity();
            if (needed.compareTo(available) > 0) {
                throw new ApiException(409, "INSUFFICIENT_STOCK", "Existencias insuficientes de " + line.sku() + " "
                    + line.productName() + ": la venta requiere " + SpanishDecimal.format(needed.setScale(3)) + " y hay "
                    + SpanishDecimal.format(available.setScale(3)) + " disponibles en la unidad base. No se confirmó la venta "
                    + "ni se descontó inventario.");
            }
        }

        Timestamp now = Timestamp.from(clock.instant());
        Map<String, Object> sale = jdbc.queryForMap("""
            INSERT INTO sales_sale(external_id,code,branch_id,confirmed_by,confirmed_at)
            VALUES (?,'VT-'||lpad(nextval('sales_sale_code_seq')::text,6,'0'),?,?,?) RETURNING id,code""",
            UUID.randomUUID(), context.branchId(), context.actorId(), now);
        long saleId = ((Number) sale.get("id")).longValue();
        String code = (String) sale.get("code");
        List<InventoryLedger.Line> exits = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            Resolved line = lines.get(index);
            OperableUnits.Unit unit = line.unit();
            BigDecimal quantity = line.line().quantity();
            BigDecimal discount = line.line().discountPercent();
            BigDecimal gross = line.price().grossPrice();
            // The applied cost is the locked average: it cannot change until this transaction ends.
            BigDecimal cost = balances.get(unit.productId()).average();
            long lineId = jdbc.queryForObject("""
                INSERT INTO sales_line(sale_id,line_no,product_id,product_unit_id,price_list_id,list_name_snapshot,sku_snapshot,
                    product_name_snapshot,unit_name_snapshot,factor_snapshot,commercial_qty,base_qty,gross_unit_price,discount_pct,
                    net_amount,historical_base_cost,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id""",
                Long.class, saleId, index + 1, unit.productId(), unit.id(), line.price().priceListId(), line.price().listName(),
                line.sku(), line.productName(), unit.unitName(), unit.factor(), quantity, line.base(), gross, discount,
                SaleQueryService.netUnitPrice(gross, discount).multiply(quantity), cost, now);
            exits.add(new InventoryLedger.Line(unit.productId(), unit.id(), unit.factor(), quantity, line.base(), Direction.OUT,
                new Cost.Average(), null, lineId, null, new InventoryLedger.Snapshot(line.sku(), line.productName(), unit.unitName())));
        }
        ledger.post(new InventoryLedger.Operation(Kind.SALE, context.branchId(), context.actorId(), "Venta " + code, null,
            saleId, null, exits));
        return saleId;
    }

    /** Runs a line resolution, prefixing a rejection with its line number. */
    private static <T> T numbered(int number, Supplier<T> resolution) {
        try {
            return resolution.get();
        } catch (ApiException error) {
            throw new ApiException(error.status(), error.code(), "Línea " + number + ": " + error.getMessage()
                + " No se confirmó la venta.");
        }
    }

    /** Only the operator of an active branch confirms sales (§5.2); managers and administrators consult them. */
    private Context context(SessionPrincipal principal) {
        if (!"OPERATOR".equals(principal.role()) || principal.branchExternalId() == null) {
            throw new ApiException(403, "SALE_FORBIDDEN", "MANAGER".equals(principal.role())
                ? "El gerente supervisa las ventas de su sucursal; las confirma el operador."
                : "El administrador consulta las ventas de la red; las confirma el operador de cada sucursal.");
        }
        Map<String, Object> branch = jdbc.queryForList("SELECT id,active FROM identity_branch WHERE external_id=?",
            principal.branchExternalId()).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal."));
        if (!(Boolean) branch.get("active")) {
            throw new ApiException(409, "BRANCH_INACTIVE", "La sucursal está inactiva: conserva su historial pero no admite nuevas operaciones.");
        }
        long actorId = jdbc.queryForObject("SELECT id FROM identity_user_account WHERE external_id=?", Long.class,
            principal.externalId());
        return new Context(((Number) branch.get("id")).longValue(), actorId);
    }
}
