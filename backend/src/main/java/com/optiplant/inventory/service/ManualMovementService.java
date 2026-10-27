package com.optiplant.inventory.service;

import com.optiplant.catalog.service.UnitConversion;
import com.optiplant.common.exception.ApiException;
import com.optiplant.common.idempotency.IdempotencyService;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.inventory.dto.ManualMovementRequest;
import com.optiplant.inventory.dto.ManualMovementResponse;
import com.optiplant.inventory.service.InventoryLedger.Cost;
import com.optiplant.inventory.service.InventoryLedger.Direction;
import com.optiplant.inventory.service.InventoryLedger.Kind;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manual entries and exits of the session's own active branch (RF-0016/RF-0017, INC-009/D03): operators register
 * ordinary entries, returns and shrinkage exits; managers register count adjustments and linked corrections. Every
 * effect goes through {@link InventoryLedger}.
 */
@Service
public class ManualMovementService {
    static final String OPERATION_CODE = "inventory.manual-movement.v1";
    private static final Map<String, Set<Kind>> PERMITTED = Map.of(
        "OPERATOR", Set.of(Kind.MANUAL_ENTRY, Kind.SALE_RETURN, Kind.MANUAL_EXIT),
        "MANAGER", Set.of(Kind.COUNT_ADJUSTMENT, Kind.ENTRY_CORRECTION));
    /**
     * Base quantity of movement {@code m} still open to compensation: a sale exit minus its net returns (a return later
     * corrected releases its units, decision L14), or an identified entry minus its downward corrections.
     */
    static final String PENDING = """
        m.base_qty - coalesce((SELECT sum(c.base_qty - coalesce((SELECT sum(cc.base_qty) FROM inventory_movement cc
            WHERE cc.original_movement_id=c.id AND cc.direction<>c.direction),0))
            FROM inventory_movement c WHERE c.original_movement_id=m.id AND c.direction<>m.direction),0)""";
    /** Identified entries whose quantity a manager may correct at their historical cost (H v0.13). */
    static final Set<String> CORRECTABLE = Set.of("PURCHASE_RECEIPT", "MANUAL_ENTRY", "SALE_RETURN");

    private final JdbcTemplate jdbc;
    private final InventoryLedger ledger;
    private final IdempotencyService idempotency;

    public ManualMovementService(JdbcTemplate jdbc, InventoryLedger ledger, IdempotencyService idempotency) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.idempotency = idempotency;
    }

    @Transactional
    public ManualMovementResponse register(SessionPrincipal principal, String idempotencyKey, ManualMovementRequest request) {
        IdempotencyService.requireKey(idempotencyKey);
        Set<Kind> permitted = PERMITTED.getOrDefault(principal.role(), Set.of());
        if (principal.branchExternalId() == null || !permitted.contains(request.kind())) {
            throw new ApiException(403, "MOVEMENT_FORBIDDEN", switch (principal.role()) {
                case "OPERATOR" -> "Los ajustes por conteo y las correcciones corresponden al gerente de la sucursal.";
                case "MANAGER" -> "Los ingresos ordinarios, devoluciones y mermas los registra el operador de la sucursal.";
                default -> "El administrador no registra movimientos locales de inventario.";
            });
        }
        Map<String, Object> branch = jdbc.queryForList("SELECT id,active FROM identity_branch WHERE external_id=?",
            principal.branchExternalId()).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal."));
        if (!(Boolean) branch.get("active")) {
            throw new ApiException(409, "BRANCH_INACTIVE", "La sucursal está inactiva: conserva su historial pero no admite nuevas operaciones.");
        }
        long branchId = ((Number) branch.get("id")).longValue();
        long actorId = jdbc.queryForObject("SELECT id FROM identity_user_account WHERE external_id=?", Long.class,
            principal.externalId());
        return idempotency.execute(actorId, OPERATION_CODE, principal.branchExternalId().toString(), idempotencyKey, request,
            201, ManualMovementResponse.class, () -> post(branchId, actorId, request));
    }

    private ManualMovementResponse post(long branchId, long actorId, ManualMovementRequest request) {
        InventoryLedger.Line line;
        Long originalOperation = null;
        if (request.reference() != null) {
            // The balance lock serializes every compensation of the same product: the pending quantity is read after it.
            ledger.lock(branchId, original(branchId, request.reference()).productId());
            Original original = original(branchId, request.reference());
            BigDecimal base = UnitConversion.toBase(request.quantity(), original.factor(), original.divisible(),
                original.baseDivisible());
            if (request.kind() == Kind.SALE_RETURN) {
                if (!original.kind().equals("SALE") || !original.direction().equals("OUT")) {
                    throw new ApiException(409, "RETURN_REFERENCE_INVALID", "Una devolución solo puede referenciar la salida de una venta.");
                }
                requireWithin(base, original.pending(), "La devolución supera lo vendido menos lo ya devuelto");
                line = new InventoryLedger.Line(original.productId(), original.unitId(), original.factor(), request.quantity(), base,
                    Direction.IN, new Cost.Historical(original.cost()), null, original.saleLineId(), original.id());
            } else {
                if (!CORRECTABLE.contains(original.kind()) || !original.direction().equals("IN")) {
                    throw new ApiException(409, "CORRECTION_REFERENCE_INVALID",
                        "Solo se corrigen entradas identificadas: compras recibidas, ingresos ordinarios o devoluciones.");
                }
                requireWithin(base, original.pending(), "La corrección supera la cantidad pendiente de corregir de la entrada");
                line = new InventoryLedger.Line(original.productId(), original.unitId(), original.factor(), request.quantity(), base,
                    Direction.OUT, new Cost.Historical(original.cost()), null, null, original.id());
                originalOperation = original.operationId();
            }
        } else {
            Unit unit = unit(branchId, request.product(), request.unit());
            BigDecimal base = UnitConversion.toBase(request.quantity(), unit.factor(), unit.divisible(), unit.baseDivisible());
            Cost cost = request.direction() == Direction.IN ? new Cost.AverageOrJustified(request.unitCost()) : new Cost.Average();
            line = new InventoryLedger.Line(unit.productId(), unit.id(), unit.factor(), request.quantity(), base,
                request.direction(), cost, null, null, null);
        }
        InventoryLedger.Posted posted = ledger.post(new InventoryLedger.Operation(request.kind(), branchId, actorId,
            request.reason(), null, null, originalOperation, List.of(line)));
        InventoryLedger.PostedMovement movement = posted.movements().getFirst();
        return jdbc.queryForObject("""
            SELECT m.sku_snapshot,m.product_name_snapshot,m.unit_name_snapshot,p.external_id,p.base_unit_name,u.code
            FROM inventory_movement m JOIN catalog_product p ON p.id=m.product_id
            JOIN inventory_operation o ON o.id=m.operation_id JOIN identity_user_account u ON u.id=o.actor_id WHERE m.id=?""",
            (row, index) -> new ManualMovementResponse(posted.externalId(), movement.externalId(), posted.occurredAt(),
                request.kind().name(), line.direction().name(), row.getObject("external_id", UUID.class), row.getString(1),
                row.getString(2), row.getString(3), line.commercialQty().setScale(3).toPlainString(),
                line.baseQty().setScale(3).toPlainString(), row.getString("base_unit_name"), request.reason(),
                row.getString("code"), request.reference(), movement.resulting().quantity().setScale(3).toPlainString()), movement.id());
    }

    private record Unit(long id, long productId, BigDecimal factor, boolean divisible, boolean baseDivisible) {}

    /** An active unit of a product that is active and enabled in the branch catalog (RF-0012). */
    private Unit unit(long branchId, UUID product, String unitName) {
        List<Map<String, Object>> found = jdbc.queryForList("""
            SELECT p.id AS product_id,p.base_divisible,(p.active AND coalesce(bp.enabled,false)) AS operable
            FROM catalog_product p LEFT JOIN catalog_branch_product bp ON bp.product_id=p.id AND bp.branch_id=?
            WHERE p.external_id=?""", branchId, product);
        if (found.isEmpty()) throw new ApiException(404, "PRODUCT_NOT_FOUND", "El producto no existe.");
        if (!(Boolean) found.getFirst().get("operable")) {
            throw new ApiException(409, "PRODUCT_NOT_OPERABLE", "El producto no está activo y habilitado en el catálogo de la sucursal.");
        }
        long productId = ((Number) found.getFirst().get("product_id")).longValue();
        boolean baseDivisible = (Boolean) found.getFirst().get("base_divisible");
        return jdbc.query("SELECT id,factor,divisible FROM catalog_product_unit WHERE product_id=? AND name=? AND active",
                (row, index) -> new Unit(row.getLong(1), productId, row.getBigDecimal(2), row.getBoolean(3), baseDivisible),
                productId, unitName).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "PRODUCT_UNIT_NOT_FOUND", "La unidad no existe o no está activa para este producto."));
    }

    private record Original(long id, long operationId, String kind, String direction, long productId, long unitId,
                            BigDecimal factor, BigDecimal cost, Long saleLineId, BigDecimal pending, boolean divisible,
                            boolean baseDivisible) {}

    /** The referenced movement of the same branch, with the base quantity still open to compensation. */
    private Original original(long branchId, UUID reference) {
        return jdbc.query("""
            SELECT m.id,m.operation_id,o.kind,m.direction,m.product_id,m.product_unit_id,m.factor_snapshot,
                m.historical_base_cost,m.sale_line_id,u.divisible,p.base_divisible,
                %s AS pending
            FROM inventory_movement m JOIN inventory_operation o ON o.id=m.operation_id
            JOIN catalog_product_unit u ON u.id=m.product_unit_id JOIN catalog_product p ON p.id=m.product_id
            WHERE m.external_id=? AND m.branch_id=?""".formatted(PENDING), (row, index) -> new Original(row.getLong("id"),
                row.getLong("operation_id"), row.getString("kind"), row.getString("direction"), row.getLong("product_id"),
                row.getLong("product_unit_id"), row.getBigDecimal("factor_snapshot"), row.getBigDecimal("historical_base_cost"),
                (Long) row.getObject("sale_line_id", Long.class), row.getBigDecimal("pending"), row.getBoolean("divisible"),
                row.getBoolean("base_divisible")), reference, branchId).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "MOVEMENT_NOT_FOUND", "No existe ese movimiento en su sucursal."));
    }

    private static void requireWithin(BigDecimal base, BigDecimal pending, String detail) {
        if (base.compareTo(pending) > 0) {
            throw new ApiException(409, "COMPENSATION_EXCEEDS_PENDING", detail + " (pendiente: "
                + pending.setScale(3).toPlainString() + " en unidad base). No se registró ningún movimiento.");
        }
    }
}
