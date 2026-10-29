package com.optiplant.inventory.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.common.text.SpanishDecimal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only writer of stock, movements and valuation (ARQ PEN-005: inventory owns every stock change). Manual movements,
 * purchases, sales and transfers post their effects here instead of updating balances themselves.
 *
 * <p>An operation is posted in the caller's transaction: balances are locked in product order, every line is validated
 * and valued, then the operation, its movements, their valuation components and the balances are written together. Any
 * rejection throws before the first write, so a failed operation leaves no trace (RF-0017-CA-03). Valuation follows
 * RF-0026 / INC-013: moving weighted average per product and branch, average at 6 decimals rounded half up, exits at the
 * current average, the remaining value absorbed by the exit that depletes the stock, and linked compensations at the
 * historical cost of the movement they correct (H v0.13), never below zero value (project decision L12 in
 * odd/tasks/rf-0016-0018.md). The restock alerts of the affected combinations are re-evaluated in the same transaction
 * (RF-0020).
 *
 * <p>A document that must store the applied cost on its own line before posting (a sale line) calls {@link #lock}
 * first: the returned average cannot change until the transaction ends.
 *
 * <p>A dispatch (RF-0035, INC-016/D01) is an ordinary exit from the origin at its current average, and every dispatched
 * line opens its transit balance in the same posting: the transit receives the whole value withdrawn from the origin,
 * with the final rounding difference of a depleting exit kept apart (data model §5.2). The destination is not touched.
 *
 * <p>The single arrival of a dispatch (RF-0036/RF-0037, data model §5.2) enters the destination what was received and
 * releases it from transit in the same posting: the base component at the frozen dispatch cost and, apart, the received
 * share of the transit's rounding residual (proportional, scale 18 HALF_UP; the release of the whole pending quantity takes
 * the whole residual). An arrival of zero units is an operation without movements. A transit loss (RF-0039) writes off the
 * pending transit of a shortage once, without any physical movement.
 */
@Service
public class InventoryLedger {
    static final int AVERAGE_SCALE = 6;

    public enum Kind { PURCHASE_RECEIPT, SALE, DISPATCH, ARRIVAL, MANUAL_ENTRY, MANUAL_EXIT, SALE_RETURN,
        COUNT_ADJUSTMENT, ENTRY_CORRECTION, TRANSIT_LOSS }

    public enum Direction { IN, OUT }

    /** How a line is valued. */
    public sealed interface Cost {
        /** Ordinary exit or count adjustment: the current average. */
        record Average() implements Cost {}
        /** Entry without reference: the current average when stock exists, otherwise the justified cost (required). */
        record AverageOrJustified(BigDecimal justified) implements Cost {}
        /** Valued entry, such as a purchase receipt at its net base cost. */
        record Explicit(BigDecimal unitCost) implements Cost {}
        /** Linked compensation or sale return: the historical cost of the referenced movement. */
        record Historical(BigDecimal unitCost) implements Cost {}
        /** Arrival entry: the frozen cost of the dispatched line plus its share of the transit residual, read by the ledger. */
        record Transit() implements Cost {}
    }

    /**
     * Names fixed by a source document line (copy from reference). A movement posted for a document keeps the names
     * of that document even if the catalog changed after it was committed.
     */
    public record Snapshot(String sku, String productName, String unitName) {}

    /**
     * {@code snapshot} is null for movements without a source document: they take the current catalog names.
     * {@code dispatchLineId} is set only on the lines of a dispatch, {@code arrivalLineId} only on those of an arrival.
     */
    public record Line(long productId, long productUnitId, BigDecimal factor, BigDecimal commercialQty, BigDecimal baseQty,
                       Direction direction, Cost cost, Long purchaseLineId, Long saleLineId, Long originalMovementId,
                       Snapshot snapshot, Long dispatchLineId, Long arrivalLineId) {
        public Line(long productId, long productUnitId, BigDecimal factor, BigDecimal commercialQty, BigDecimal baseQty,
                    Direction direction, Cost cost, Long purchaseLineId, Long saleLineId, Long originalMovementId,
                    Snapshot snapshot, Long dispatchLineId) {
            this(productId, productUnitId, factor, commercialQty, baseQty, direction, cost, purchaseLineId, saleLineId,
                originalMovementId, snapshot, dispatchLineId, null);
        }

        public Line(long productId, long productUnitId, BigDecimal factor, BigDecimal commercialQty, BigDecimal baseQty,
                    Direction direction, Cost cost, Long purchaseLineId, Long saleLineId, Long originalMovementId,
                    Snapshot snapshot) {
            this(productId, productUnitId, factor, commercialQty, baseQty, direction, cost, purchaseLineId, saleLineId,
                originalMovementId, snapshot, null, null);
        }

        public Line(long productId, long productUnitId, BigDecimal factor, BigDecimal commercialQty, BigDecimal baseQty,
                    Direction direction, Cost cost, Long purchaseLineId, Long saleLineId, Long originalMovementId) {
            this(productId, productUnitId, factor, commercialQty, baseQty, direction, cost, purchaseLineId, saleLineId,
                originalMovementId, null, null, null);
        }
    }

    /**
     * {@code dispatchId} is set only on a {@link Kind#DISPATCH} operation, {@code arrivalId} only on a {@link Kind#ARRIVAL}
     * one, which is the only kind that may have no lines (an arrival of zero units).
     */
    public record Operation(Kind kind, long branchId, long actorId, String reason, Long purchaseOrderId, Long saleId,
                            Long originalOperationId, List<Line> lines, Long dispatchId, Long arrivalId) {
        public Operation(Kind kind, long branchId, long actorId, String reason, Long purchaseOrderId, Long saleId,
                         Long originalOperationId, List<Line> lines, Long dispatchId) {
            this(kind, branchId, actorId, reason, purchaseOrderId, saleId, originalOperationId, lines, dispatchId, null);
        }

        public Operation(Kind kind, long branchId, long actorId, String reason, Long purchaseOrderId, Long saleId,
                         Long originalOperationId, List<Line> lines) {
            this(kind, branchId, actorId, reason, purchaseOrderId, saleId, originalOperationId, lines, null, null);
        }
    }

    /** The authorized write-off of the pending transit of a shortage (RF-0039-CA-02). */
    public record TransitLoss(long discrepancyId, long actorId, String reason, String evidence) {}

    /** The written-off quantity of each transit balance, by dispatch line. */
    public record WrittenOff(UUID externalId, long id, Instant occurredAt, Map<Long, BigDecimal> quantities) {}

    public record Balance(BigDecimal quantity, BigDecimal value, BigDecimal average) {}

    /** {@code dispatchLineId} identifies the dispatch line a movement was posted for; null outside a dispatch. */
    public record PostedMovement(UUID externalId, long id, BigDecimal appliedCost, BigDecimal baseAmount,
                                 BigDecimal adjustmentAmount, Balance resulting, Long dispatchLineId) {}

    public record Posted(UUID externalId, long id, Instant occurredAt, List<PostedMovement> movements) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ReorderAlertService alerts;

    public InventoryLedger(JdbcTemplate jdbc, Clock clock, ReorderAlertService alerts) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.alerts = alerts;
    }

    /** Locks (creating it at zero when absent) and returns the balance of one product in one branch. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Balance lock(long branchId, long productId) {
        jdbc.update("INSERT INTO inventory_balance(branch_id,product_id,history_started_at) VALUES (?,?,?) "
            + "ON CONFLICT (branch_id,product_id) DO NOTHING", branchId, productId, Timestamp.from(clock.instant()));
        return jdbc.queryForObject("SELECT physical_qty,inventory_value,average_base_cost FROM inventory_balance "
                + "WHERE branch_id=? AND product_id=? FOR UPDATE",
            (row, index) -> new Balance(row.getBigDecimal(1), row.getBigDecimal(2), row.getBigDecimal(3)), branchId, productId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Posted post(Operation operation) {
        boolean arrival = operation.kind() == Kind.ARRIVAL;
        if (operation.lines().isEmpty() && !arrival) throw new IllegalArgumentException("An operation needs at least one line");
        if (operation.reason() == null || operation.reason().isBlank()) {
            throw new ApiException(400, "VALIDATION_FAILED", "El motivo es obligatorio.");
        }
        boolean dispatch = operation.kind() == Kind.DISPATCH;
        if (dispatch != (operation.dispatchId() != null) || operation.lines().stream().anyMatch(line -> dispatch
            != (line.dispatchLineId() != null) || (dispatch && (line.direction() != Direction.OUT
                || !(line.cost() instanceof Cost.Average))))) {
            throw new IllegalArgumentException("A dispatch, and only a dispatch, posts exits at the average linked to its lines");
        }
        if (arrival != (operation.arrivalId() != null) || operation.lines().stream().anyMatch(line -> arrival
            != (line.arrivalLineId() != null) || arrival != (line.cost() instanceof Cost.Transit)
                || (arrival && line.direction() != Direction.IN))) {
            throw new IllegalArgumentException("An arrival, and only an arrival, posts entries released from its transit");
        }
        // Lock every affected balance in a stable order to avoid deadlocks between concurrent operations, then the
        // transit balances by id (data model §6.7: balances before transits).
        Map<Long, Balance> balances = new LinkedHashMap<>();
        operation.lines().stream().map(Line::productId).distinct().sorted(Comparator.naturalOrder())
            .forEach(product -> balances.put(product, lock(operation.branchId(), product)));
        Map<Long, Release> releases = arrival ? releases(operation.lines()) : Map.of();

        // Value every line in memory first: a rejection must not leave partial writes.
        List<Valued> valued = new ArrayList<>();
        for (Line line : operation.lines()) {
            Valued value = value(line, balances.get(line.productId()), arrival ? releases.get(line.arrivalLineId()) : null);
            balances.put(line.productId(), value.resulting());
            valued.add(value);
        }

        Instant now = clock.instant();
        UUID operationExternalId = UUID.randomUUID();
        Long operationId = jdbc.queryForObject("INSERT INTO inventory_operation(external_id,kind,occurred_at,actor_id,reason,"
                + "purchase_order_id,sale_id,original_operation_id,dispatch_id,arrival_id) VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id",
            Long.class, operationExternalId, operation.kind().name(), Timestamp.from(now), operation.actorId(),
            operation.reason().strip(), operation.purchaseOrderId(), operation.saleId(), operation.originalOperationId(),
            operation.dispatchId(), operation.arrivalId());
        List<PostedMovement> posted = new ArrayList<>();
        for (Valued value : valued) {
            Line line = value.line();
            UUID movementExternalId = UUID.randomUUID();
            Long movementId = jdbc.queryForObject("""
                INSERT INTO inventory_movement(external_id,operation_id,branch_id,product_id,product_unit_id,direction,
                    commercial_qty,base_qty,factor_snapshot,sku_snapshot,product_name_snapshot,unit_name_snapshot,
                    historical_base_cost,purchase_line_id,sale_line_id,original_movement_id,dispatch_line_id,arrival_line_id)
                SELECT ?,?,?,p.id,u.id,?,?,?,?,coalesce(?,p.sku),coalesce(?,p.name),coalesce(?,u.name),?,?,?,?,?,?
                FROM catalog_product p
                JOIN catalog_product_unit u ON u.product_id=p.id WHERE p.id=? AND u.id=?
                RETURNING id""", Long.class,
                movementExternalId, operationId, operation.branchId(), line.direction().name(), line.commercialQty(),
                line.baseQty(), line.factor(), line.snapshot() == null ? null : line.snapshot().sku(),
                line.snapshot() == null ? null : line.snapshot().productName(),
                line.snapshot() == null ? null : line.snapshot().unitName(), value.appliedCost(), line.purchaseLineId(), line.saleLineId(),
                line.originalMovementId(), line.dispatchLineId(), line.arrivalLineId(), line.productId(), line.productUnitId());
            jdbc.update("INSERT INTO inventory_valuation_component(movement_id,component_kind,amount) VALUES (?,'BASE',?)",
                movementId, value.baseAmount());
            if (value.adjustmentAmount().signum() != 0) {
                jdbc.update("INSERT INTO inventory_valuation_component(movement_id,component_kind,amount) VALUES (?,?,?)",
                    movementId, value.adjustmentKind(), value.adjustmentAmount());
            }
            if (dispatch) openTransit(line, operationId, value);
            if (arrival) release(releases.get(line.arrivalLineId()), operationId, line.baseQty(), value);
            posted.add(new PostedMovement(movementExternalId, movementId, value.appliedCost(), value.baseAmount(),
                value.adjustmentAmount(), value.resulting(), line.dispatchLineId()));
        }
        Timestamp updated = Timestamp.from(now);
        balances.forEach((product, balance) -> jdbc.update("UPDATE inventory_balance SET physical_qty=?,inventory_value=?,"
                + "average_base_cost=?,updated_at=? WHERE branch_id=? AND product_id=?",
            balance.quantity(), balance.value(), balance.average(), updated, operation.branchId(), product));
        // RF-0019/RF-0020: every stock change re-evaluates the restock alert of its product-branch combination.
        balances.keySet().forEach(product -> alerts.evaluate(operation.branchId(), product));
        return new Posted(operationExternalId, operationId, now, posted);
    }

    /**
     * Opens the transit balance of a dispatched line with everything its exit withdrew from the origin: the base value
     * and, apart, the final rounding difference of a depleting exit (both negated: the exit's amounts are negative).
     */
    private void openTransit(Line line, long operationId, Valued exit) {
        BigDecimal value = exit.baseAmount().negate();
        BigDecimal rounding = exit.adjustmentAmount().negate();
        Long transitId = jdbc.queryForObject("INSERT INTO inventory_transit(dispatch_line_id,pending_base_qty,pending_base_value,"
            + "pending_rounding_value) VALUES (?,?,?,?) RETURNING id", Long.class, line.dispatchLineId(), line.baseQty(), value,
            rounding);
        jdbc.update("INSERT INTO inventory_transit_entry(transit_id,operation_id,entry_kind,qty_delta,base_value_delta,"
            + "rounding_value_delta) VALUES (?,?,'DISPATCH',?,?,?)", transitId, operationId, line.baseQty(), value, rounding);
    }

    /** The locked transit balance an arrival line releases, with the frozen cost of its dispatched line. */
    private record Release(long transitId, BigDecimal pendingQty, BigDecimal pendingValue, BigDecimal pendingRounding,
                           BigDecimal frozenCost) {}

    /** Locks, in id order, the transit balances of the dispatched lines the arrival lines receive. */
    private Map<Long, Release> releases(List<Line> lines) {
        Map<Long, Release> releases = new LinkedHashMap<>();
        if (lines.isEmpty()) return releases;
        String ids = String.join(",", java.util.Collections.nCopies(lines.size(), "?"));
        jdbc.query("""
            SELECT al.id AS arrival_line,t.id,t.pending_base_qty,t.pending_base_value,t.pending_rounding_value,dl.frozen_base_cost
            FROM logistics_arrival_line al JOIN logistics_dispatch_line dl ON dl.id=al.dispatch_line_id
            JOIN inventory_transit t ON t.dispatch_line_id=dl.id WHERE al.id IN (%s) ORDER BY t.id FOR UPDATE OF t""".formatted(ids),
            (java.sql.ResultSet r) -> {
                releases.put(r.getLong("arrival_line"), new Release(r.getLong("id"), r.getBigDecimal("pending_base_qty"),
                    r.getBigDecimal("pending_base_value"), r.getBigDecimal("pending_rounding_value"), r.getBigDecimal("frozen_base_cost")));
            }, lines.stream().map(Line::arrivalLineId).toArray());
        if (releases.size() != lines.size()) throw new IllegalArgumentException("Every arrival line needs an open transit balance");
        return releases;
    }

    /** Takes the received quantity, its base value and its residual share out of the transit balance. */
    private void release(Release release, long operationId, BigDecimal quantity, Valued entry) {
        jdbc.update("UPDATE inventory_transit SET pending_base_qty=pending_base_qty-?,pending_base_value=pending_base_value-?,"
            + "pending_rounding_value=pending_rounding_value-?,updated_at=CURRENT_TIMESTAMP WHERE id=?", quantity, entry.baseAmount(),
            entry.adjustmentAmount(), release.transitId());
        jdbc.update("INSERT INTO inventory_transit_entry(transit_id,operation_id,entry_kind,qty_delta,base_value_delta,"
            + "rounding_value_delta) VALUES (?,?,'ARRIVAL',?,?,?)", release.transitId(), operationId, quantity.negate(),
            entry.baseAmount().negate(), entry.adjustmentAmount().negate());
    }

    /**
     * RF-0039-CA-02: writes off, once, everything still pending in the transit balances of a shortage, with its reason,
     * evidence and authorizing origin manager. No physical balance changes; a second write-off finds nothing pending.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public WrittenOff writeOff(TransitLoss loss) {
        if (loss.reason() == null || loss.reason().isBlank() || loss.evidence() == null || loss.evidence().isBlank()) {
            throw new ApiException(400, "VALIDATION_FAILED", "El motivo y la evidencia de la pérdida son obligatorios.");
        }
        List<Map<String, Object>> transits = jdbc.queryForList("""
            SELECT t.id,t.dispatch_line_id,t.pending_base_qty,t.pending_base_value,t.pending_rounding_value
            FROM logistics_discrepancy_line xl JOIN logistics_arrival_line al ON al.id=xl.arrival_line_id
            JOIN inventory_transit t ON t.dispatch_line_id=al.dispatch_line_id WHERE xl.discrepancy_id=?
            ORDER BY t.id FOR UPDATE OF t""", loss.discrepancyId()).stream()
            .filter(row -> ((BigDecimal) row.get("pending_base_qty")).signum() > 0).toList();
        if (transits.isEmpty()) {
            throw new ApiException(409, "TRANSIT_ALREADY_SETTLED", "El faltante ya no tiene tránsito pendiente: la pérdida se "
                + "registra una sola vez. No se modificó el inventario.");
        }
        Instant now = clock.instant();
        UUID externalId = UUID.randomUUID();
        Long operationId = jdbc.queryForObject("INSERT INTO inventory_operation(external_id,kind,occurred_at,actor_id,"
                + "authorized_by,reason,evidence,discrepancy_id) VALUES (?,'TRANSIT_LOSS',?,?,?,?,?,?) RETURNING id", Long.class,
            externalId, Timestamp.from(now), loss.actorId(), loss.actorId(), loss.reason().strip(), loss.evidence().strip(),
            loss.discrepancyId());
        Map<Long, BigDecimal> quantities = new LinkedHashMap<>();
        for (Map<String, Object> transit : transits) {
            BigDecimal quantity = (BigDecimal) transit.get("pending_base_qty");
            jdbc.update("INSERT INTO inventory_transit_entry(transit_id,operation_id,entry_kind,qty_delta,base_value_delta,"
                    + "rounding_value_delta) VALUES (?,?,'LOSS',?,?,?)", transit.get("id"), operationId, quantity.negate(),
                ((BigDecimal) transit.get("pending_base_value")).negate(), ((BigDecimal) transit.get("pending_rounding_value")).negate());
            jdbc.update("UPDATE inventory_transit SET pending_base_qty=0,pending_base_value=0,pending_rounding_value=0,"
                + "updated_at=? WHERE id=?", Timestamp.from(now), transit.get("id"));
            quantities.put(((Number) transit.get("dispatch_line_id")).longValue(), quantity);
        }
        return new WrittenOff(externalId, operationId, now, quantities);
    }

    private record Valued(Line line, BigDecimal appliedCost, BigDecimal baseAmount, String adjustmentKind,
                          BigDecimal adjustmentAmount, Balance resulting) {}

    /** Values one line against the balance left by the previous lines of the same operation. */
    static Valued value(Line line, Balance balance) {
        return value(line, balance, null);
    }

    private static Valued value(Line line, Balance balance, Release release) {
        BigDecimal quantity = line.baseQty();
        if (quantity.signum() <= 0) throw new IllegalArgumentException("Base quantity must be positive");
        if (line.cost() instanceof Cost.Transit) return arrival(line, balance, release);
        if (line.direction() == Direction.IN) {
            BigDecimal cost = entryCost(line.cost(), balance);
            BigDecimal amount = quantity.multiply(cost);
            BigDecimal resultingQuantity = balance.quantity().add(quantity);
            BigDecimal resultingValue = balance.value().add(amount);
            // With no previous stock the entry cost is adopted (INC-013/D03); otherwise the recorded value is averaged.
            BigDecimal average = balance.quantity().signum() == 0 ? cost.setScale(AVERAGE_SCALE, RoundingMode.HALF_UP)
                : resultingValue.divide(resultingQuantity, AVERAGE_SCALE, RoundingMode.HALF_UP);
            return new Valued(line, cost, amount, null, BigDecimal.ZERO, new Balance(resultingQuantity, resultingValue, average));
        }
        if (quantity.compareTo(balance.quantity()) > 0) {
            throw new ApiException(409, "INSUFFICIENT_STOCK", "La salida de " + SpanishDecimal.format(quantity)
                + " excede las existencias físicas disponibles (" + SpanishDecimal.format(balance.quantity())
                + "). No se registró ningún movimiento.");
        }
        boolean historical = line.cost() instanceof Cost.Historical;
        BigDecimal cost = switch (line.cost()) {
            case Cost.Average ignored -> balance.average();
            case Cost.Historical h -> h.unitCost();
            default -> throw new IllegalArgumentException("Exits are valued at the average or a historical cost");
        };
        BigDecimal amount = quantity.multiply(cost).negate();
        BigDecimal resultingQuantity = balance.quantity().subtract(quantity);
        BigDecimal resultingValue = balance.value().add(amount);
        if (resultingQuantity.signum() == 0) {
            // The depleting exit consumes the remaining value: an ordinary exit records it as the final rounding
            // difference (H v0.12), a compensation as a separate valuation difference (H v0.13). The average is kept.
            BigDecimal adjustment = resultingValue.negate();
            return new Valued(line, cost, amount, historical ? "HISTORICAL_CORRECTION" : "FINAL_ROUNDING", adjustment,
                new Balance(resultingQuantity, BigDecimal.ZERO, balance.average()));
        }
        if (historical && resultingValue.signum() < 0) {
            // Project decision (odd/tasks/rf-0016-0018.md L12, extending H v0.13): a compensation never leaves a negative
            // value. It reaches zero and the excess is recorded as a separate valuation difference; the remaining units
            // keep a zero average until the next valued entry.
            return new Valued(line, cost, amount, "HISTORICAL_CORRECTION", resultingValue.negate(),
                new Balance(resultingQuantity, BigDecimal.ZERO, BigDecimal.ZERO.setScale(AVERAGE_SCALE)));
        }
        // Ordinary exits do not change the average (INC-013/D04); a compensation recomputes it from the corrected value.
        BigDecimal average = historical ? resultingValue.divide(resultingQuantity, AVERAGE_SCALE, RoundingMode.HALF_UP)
            : balance.average();
        return new Valued(line, cost, amount, null, BigDecimal.ZERO, new Balance(resultingQuantity, resultingValue, average));
    }

    /**
     * An arrival entry at the frozen dispatch cost. Receiving the whole pending quantity releases the whole pending value
     * and residual; a part releases its base value and a proportional share of the residual (data model §5.2). The
     * residual share is recorded apart as the movement's final rounding difference and enters the destination's value.
     */
    private static Valued arrival(Line line, Balance balance, Release release) {
        BigDecimal quantity = line.baseQty();
        if (quantity.compareTo(release.pendingQty()) > 0) {
            throw new ApiException(409, "RECEIVED_EXCEEDS_TRANSIT", "Se reciben " + SpanishDecimal.format(quantity.stripTrailingZeros())
                + " y en tránsito quedan " + SpanishDecimal.format(release.pendingQty().stripTrailingZeros())
                + ". No se registró la recepción.");
        }
        BigDecimal cost = release.frozenCost();
        boolean whole = quantity.compareTo(release.pendingQty()) == 0;
        BigDecimal amount = whole ? release.pendingValue() : quantity.multiply(cost);
        BigDecimal residual = whole ? release.pendingRounding()
            : release.pendingRounding().multiply(quantity).divide(release.pendingQty(), 18, RoundingMode.HALF_UP);
        BigDecimal resultingQuantity = balance.quantity().add(quantity);
        BigDecimal resultingValue = balance.value().add(amount).add(residual);
        // With no previous stock the frozen cost is adopted (data model §5.2); otherwise the recorded value is averaged.
        BigDecimal average = balance.quantity().signum() == 0 ? cost.setScale(AVERAGE_SCALE, RoundingMode.HALF_UP)
            : resultingValue.divide(resultingQuantity, AVERAGE_SCALE, RoundingMode.HALF_UP);
        return new Valued(line, cost, amount, residual.signum() == 0 ? null : "FINAL_ROUNDING", residual,
            new Balance(resultingQuantity, resultingValue, average));
    }

    private static BigDecimal entryCost(Cost cost, Balance balance) {
        BigDecimal value = switch (cost) {
            case Cost.Transit ignored -> throw new IllegalArgumentException("Arrival entries are valued from their transit");
            case Cost.Explicit explicit -> explicit.unitCost();
            case Cost.Historical h -> h.unitCost();
            case Cost.Average ignored -> {
                if (balance.quantity().signum() == 0) throw justifiedCostRequired();
                yield balance.average();
            }
            case Cost.AverageOrJustified a -> {
                if (balance.quantity().signum() > 0) {
                    if (a.justified() != null) {
                        throw new ApiException(400, "UNIT_COST_NOT_ALLOWED", "Con existencias en la sucursal, el ingreso se valora "
                            + "al costo promedio vigente; no indique un costo unitario.");
                    }
                    yield balance.average();
                }
                if (a.justified() == null) throw justifiedCostRequired();
                yield a.justified();
            }
        };
        Objects.requireNonNull(value);
        if (value.signum() < 0 || value.stripTrailingZeros().scale() > AVERAGE_SCALE) {
            throw new ApiException(400, "VALIDATION_FAILED", "El costo unitario debe ser mayor o igual que cero y tener como "
                + "máximo 6 decimales por unidad base.");
        }
        return value;
    }

    private static ApiException justifiedCostRequired() {
        return new ApiException(400, "UNIT_COST_REQUIRED", "La sucursal no tiene existencias de este producto: indique un "
            + "costo unitario justificado por unidad base.");
    }
}
