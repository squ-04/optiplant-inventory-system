package com.optiplant.purchases.service;

import com.optiplant.catalog.service.OperableUnits;
import com.optiplant.catalog.service.UnitConversion;
import com.optiplant.common.exception.ApiException;
import com.optiplant.common.idempotency.IdempotencyService;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.inventory.service.InventoryLedger;
import com.optiplant.inventory.service.InventoryLedger.Cost;
import com.optiplant.inventory.service.InventoryLedger.Direction;
import com.optiplant.inventory.service.InventoryLedger.Kind;
import com.optiplant.purchases.dto.PurchaseOrderRequest;
import com.optiplant.purchases.dto.PurchaseOrderResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purchase orders of the session's own active branch: creation as a draft with its conditions (RF-0021, RF-0023), the
 * transition to pending receipt that fixes the supplier name and line snapshots (RF-0021-CA-03) and the complete receipt
 * (RF-0024). The receipt changes stock only through {@link InventoryLedger} (ARQ PEN-005): one {@code PURCHASE_RECEIPT}
 * operation linked to the order, with one valued entry per line at its net cost per base unit. Only operators execute
 * purchases (§5.2); managers and administrators consult them through {@link PurchaseHistoryService}. Since only
 * operators call these operations, their responses never carry the valuation cost (INC-003/D04).
 */
@Service
public class PurchaseOrderService {
    static final String CREATE = "purchases.order-create.v1";
    static final String SUBMIT = "purchases.order-submit.v1";
    static final String RECEIVE = "purchases.order-receive.v1";
    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final InventoryLedger ledger;
    private final IdempotencyService idempotency;
    private final OperableUnits units;
    private final PurchaseOrderReader reader;

    public PurchaseOrderService(JdbcTemplate jdbc, Clock clock, InventoryLedger ledger, IdempotencyService idempotency,
                                OperableUnits units, PurchaseOrderReader reader) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.ledger = ledger;
        this.idempotency = idempotency;
        this.units = units;
        this.reader = reader;
    }

    private record Context(long branchId, long actorId) {}

    @Transactional
    public PurchaseOrderResponse create(SessionPrincipal principal, String key, PurchaseOrderRequest request) {
        IdempotencyService.requireKey(key);
        Context context = context(principal);
        return idempotency.execute(context.actorId(), CREATE, principal.branchExternalId().toString(), key, request, 201,
            PurchaseOrderResponse.class, () -> reader.detail(insert(context, request), false));
    }

    @Transactional
    public PurchaseOrderResponse submit(SessionPrincipal principal, UUID order, String key) {
        IdempotencyService.requireKey(key);
        Context context = context(principal);
        return idempotency.execute(context.actorId(), SUBMIT, order.toString(), key, Map.of("order", order), 200,
            PurchaseOrderResponse.class, () -> reader.detail(submit(context, order), false));
    }

    @Transactional
    public PurchaseOrderResponse receive(SessionPrincipal principal, UUID order, String key) {
        IdempotencyService.requireKey(key);
        Context context = context(principal);
        return idempotency.execute(context.actorId(), RECEIVE, order.toString(), key, Map.of("order", order), 200,
            PurchaseOrderResponse.class, () -> reader.detail(receive(context, order), false));
    }

    private long insert(Context context, PurchaseOrderRequest request) {
        Map<String, Object> supplier = jdbc.queryForList("SELECT id,name,active FROM purchases_supplier WHERE external_id=?",
            request.supplier()).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "SUPPLIER_NOT_FOUND", "No se encontró el proveedor."));
        if (!(Boolean) supplier.get("active")) {
            throw new ApiException(409, "SUPPLIER_INACTIVE", "El proveedor está inactivo y no admite nuevas órdenes.");
        }
        // Resolve and validate every line before the first write: a rejected line leaves no order behind.
        record Resolved(OperableUnits.Unit unit, PurchaseOrderRequest.Line line, BigDecimal base, BigDecimal net) {}
        List<Resolved> lines = new ArrayList<>();
        for (int index = 0; index < request.lines().size(); index++) {
            PurchaseOrderRequest.Line line = request.lines().get(index);
            OperableUnits.Unit unit;
            BigDecimal base;
            try {
                unit = units.find(context.branchId(), line.product(), line.unit());
                base = UnitConversion.toBase(line.quantity(), unit.factor(), unit.divisible(), unit.baseDivisible());
            } catch (ApiException error) {
                throw new ApiException(error.status(), error.code(), "Línea " + (index + 1) + ": " + error.getMessage());
            }
            lines.add(new Resolved(unit, line, base, net(line.quantity(), line.unitPrice(), line.discountPercent())));
        }
        Timestamp now = Timestamp.from(clock.instant());
        UUID externalId = UUID.randomUUID();
        // The business code derives from the internal id, so it is written once the id exists.
        long orderId = jdbc.queryForObject("""
            INSERT INTO purchases_order(external_id,code,branch_id,supplier_id,supplier_name_snapshot,status,created_by,
                payment_term_days,created_at,updated_at) VALUES (?,?,?,?,?,'DRAFT',?,?,?,?) RETURNING id""", Long.class,
            externalId, externalId.toString(), context.branchId(), supplier.get("id"), supplier.get("name"), context.actorId(),
            request.paymentTermDays(), now, now);
        jdbc.update("UPDATE purchases_order SET code=? WHERE id=?", "OC-%06d".formatted(orderId), orderId);
        for (int index = 0; index < lines.size(); index++) {
            Resolved resolved = lines.get(index);
            jdbc.update("""
                INSERT INTO purchases_line(order_id,line_no,product_id,product_unit_id,sku_snapshot,product_name_snapshot,
                    unit_name_snapshot,factor_snapshot,commercial_qty,base_qty,gross_unit_price,discount_pct,net_amount,created_at)
                SELECT ?,?,p.id,u.id,p.sku,p.name,u.name,u.factor,?,?,?,?,?,? FROM catalog_product p
                JOIN catalog_product_unit u ON u.product_id=p.id WHERE p.id=? AND u.id=?""",
                orderId, index + 1, resolved.line().quantity(), resolved.base(), resolved.line().unitPrice(),
                resolved.line().discountPercent(), resolved.net(), now, resolved.unit().productId(), resolved.unit().id());
        }
        event(orderId, null, "DRAFT", context.actorId(), now);
        return orderId;
    }

    private long submit(Context context, UUID externalId) {
        Locked order = lock(context, externalId);
        if (!order.status().equals("DRAFT")) {
            throw new ApiException(409, "PURCHASE_STATUS_CONFLICT", "Solo una orden en borrador puede pasar a pendiente de "
                + "recepción; la orden " + order.code() + " está " + label(order.status()) + ". No se modificó.");
        }
        // The line snapshots are fixed when the order is committed (data model §6.3): each line is re-validated against
        // the current catalog and takes its current names and factor.
        List<Map<String, Object>> lines = jdbc.queryForList("SELECT id,line_no,product_id,product_unit_id,commercial_qty "
            + "FROM purchases_line WHERE order_id=? ORDER BY line_no", order.id());
        if (lines.isEmpty()) throw new ApiException(409, "PURCHASE_EMPTY", "La orden no tiene líneas.");
        Timestamp now = Timestamp.from(clock.instant());
        for (Map<String, Object> line : lines) {
            OperableUnits.Unit unit;
            BigDecimal base;
            try {
                unit = units.find(context.branchId(), ((Number) line.get("product_id")).longValue(),
                    ((Number) line.get("product_unit_id")).longValue());
                base = UnitConversion.toBase((BigDecimal) line.get("commercial_qty"), unit.factor(), unit.divisible(),
                    unit.baseDivisible());
            } catch (ApiException error) {
                throw new ApiException(error.status(), error.code(), "Línea " + line.get("line_no") + ": " + error.getMessage());
            }
            jdbc.update("""
                UPDATE purchases_line l SET sku_snapshot=p.sku,product_name_snapshot=p.name,unit_name_snapshot=u.name,
                    factor_snapshot=u.factor,base_qty=? FROM catalog_product p JOIN catalog_product_unit u ON u.product_id=p.id
                WHERE l.id=? AND p.id=l.product_id AND u.id=l.product_unit_id""", base, line.get("id"));
        }
        // H v1.3/02: the transition takes the supplier's current name; from PENDING on it never changes again.
        jdbc.update("UPDATE purchases_order o SET status='PENDING',supplier_name_snapshot=s.name,updated_at=? "
            + "FROM purchases_supplier s WHERE o.id=? AND s.id=o.supplier_id", now, order.id());
        event(order.id(), "DRAFT", "PENDING", context.actorId(), now);
        return order.id();
    }

    private long receive(Context context, UUID externalId) {
        Locked order = lock(context, externalId);
        if (order.status().equals("RECEIVED")) {
            // INC-011/D04: a repeated confirmation is an identifiable conflict and never posts a second entry.
            throw new ApiException(409, "PURCHASE_ALREADY_RECEIVED", "La recepción de la orden " + order.code()
                + " ya fue confirmada. No se registraron ingresos adicionales.");
        }
        if (!order.status().equals("PENDING")) {
            throw new ApiException(409, "PURCHASE_STATUS_CONFLICT", "Solo una orden pendiente de recepción puede recibirse; "
                + "la orden " + order.code() + " está " + label(order.status()) + ". No se modificó el inventario.");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id,line_no,product_id,product_unit_id,factor_snapshot,commercial_qty,base_qty,gross_unit_price,discount_pct,
                sku_snapshot,product_name_snapshot,unit_name_snapshot
            FROM purchases_line WHERE order_id=? ORDER BY line_no""", order.id());
        List<InventoryLedger.Line> lines = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long productId = ((Number) row.get("product_id")).longValue();
            try {
                units.requireOperable(context.branchId(), productId);
            } catch (ApiException error) {
                throw new ApiException(error.status(), error.code(), "Línea " + row.get("line_no") + ": " + error.getMessage()
                    + " No se recibió ninguna línea de la orden.");
            }
            BigDecimal factor = (BigDecimal) row.get("factor_snapshot");
            BigDecimal cost = baseCost((BigDecimal) row.get("gross_unit_price"), (BigDecimal) row.get("discount_pct"), factor);
            long lineId = ((Number) row.get("id")).longValue();
            jdbc.update("UPDATE purchases_line SET historical_base_cost=? WHERE id=?", cost, lineId);
            lines.add(new InventoryLedger.Line(productId, ((Number) row.get("product_unit_id")).longValue(), factor,
                (BigDecimal) row.get("commercial_qty"), (BigDecimal) row.get("base_qty"), Direction.IN, new Cost.Explicit(cost),
                lineId, null, null, new InventoryLedger.Snapshot((String) row.get("sku_snapshot"),
                    (String) row.get("product_name_snapshot"), (String) row.get("unit_name_snapshot"))));
        }
        InventoryLedger.Posted posted = ledger.post(new InventoryLedger.Operation(Kind.PURCHASE_RECEIPT, context.branchId(),
            context.actorId(), "Recepción de la compra " + order.code() + " del proveedor " + order.supplierName(), order.id(),
            null, null, lines));
        Instant receivedAt = posted.occurredAt();
        // INC-012/D04: the term counts from the confirmed receipt date in Bogotá; a zero term falls due that same day.
        LocalDate due = LocalDate.ofInstant(receivedAt, BOGOTA).plusDays(order.paymentTermDays());
        Timestamp at = Timestamp.from(receivedAt);
        jdbc.update("UPDATE purchases_order SET status='RECEIVED',received_at=?,received_by=?,due_date=?,updated_at=? WHERE id=?",
            at, context.actorId(), due, at, order.id());
        event(order.id(), "PENDING", "RECEIVED", context.actorId(), at);
        return order.id();
    }

    /** Exact line net: commercial quantity × gross price × (1 − discount/100), never rounded (data model §5.1). */
    static BigDecimal net(BigDecimal quantity, BigDecimal price, BigDecimal discount) {
        return quantity.multiply(price).multiply(HUNDRED.subtract(discount)).movePointLeft(2);
    }

    /** Net cost per base unit: price × (1 − discount/100) / factor in one division, 6 decimals half up (§5.1). */
    static BigDecimal baseCost(BigDecimal price, BigDecimal discount, BigDecimal factor) {
        return price.multiply(HUNDRED.subtract(discount)).divide(factor.multiply(HUNDRED), 6, RoundingMode.HALF_UP);
    }

    private record Locked(long id, String code, String status, String supplierName, int paymentTermDays) {}

    /** Locks an order of the session's branch; another branch's order is reported as absent. */
    private Locked lock(Context context, UUID externalId) {
        return jdbc.query("SELECT id,code,status,supplier_name_snapshot,payment_term_days FROM purchases_order "
                + "WHERE external_id=? AND branch_id=? FOR UPDATE",
            (row, index) -> new Locked(row.getLong(1), row.getString(2), row.getString(3), row.getString(4), row.getInt(5)),
            externalId, context.branchId()).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "PURCHASE_NOT_FOUND", "No existe esa orden de compra en su sucursal."));
    }

    private void event(long orderId, String from, String to, long actorId, Timestamp at) {
        jdbc.update("INSERT INTO purchases_event(order_id,sequence_no,from_status,to_status,actor_id,occurred_at) "
            + "SELECT ?,coalesce(max(sequence_no),0)+1,?,?,?,? FROM purchases_event WHERE order_id=?",
            orderId, from, to, actorId, at, orderId);
    }

    /** Only the operator of an active branch executes purchases (§5.2, RF-0003-CA-08). */
    private Context context(SessionPrincipal principal) {
        if (!"OPERATOR".equals(principal.role()) || principal.branchExternalId() == null) {
            throw new ApiException(403, "PURCHASE_FORBIDDEN", "MANAGER".equals(principal.role())
                ? "El gerente consulta y supervisa las compras de su sucursal; las registra y recibe el operador."
                : "El administrador consulta las compras de la red; las registra y recibe el operador de cada sucursal.");
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

    private static String label(String status) {
        return switch (status) {
            case "DRAFT" -> "en borrador";
            case "PENDING" -> "pendiente de recepción";
            case "RECEIVED" -> "recibida";
            case "CANCELLED" -> "cancelada";
            default -> status;
        };
    }
}
