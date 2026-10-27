package com.optiplant.inventory.service;

import com.optiplant.common.exception.ApiException;
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
 * odd/tasks/rf-0016-0018.md).
 *
 * <p>A document that must store the applied cost on its own line before posting (a sale line) calls {@link #lock}
 * first: the returned average cannot change until the transaction ends.
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
    }

    public record Line(long productId, long productUnitId, BigDecimal factor, BigDecimal commercialQty, BigDecimal baseQty,
                       Direction direction, Cost cost, Long purchaseLineId, Long saleLineId, Long originalMovementId) {}

    public record Operation(Kind kind, long branchId, long actorId, String reason, Long purchaseOrderId, Long saleId,
                            Long originalOperationId, List<Line> lines) {}

    public record Balance(BigDecimal quantity, BigDecimal value, BigDecimal average) {}

    public record PostedMovement(UUID externalId, long id, BigDecimal appliedCost, BigDecimal baseAmount,
                                 BigDecimal adjustmentAmount, Balance resulting) {}

    public record Posted(UUID externalId, long id, Instant occurredAt, List<PostedMovement> movements) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public InventoryLedger(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
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
        if (operation.lines().isEmpty()) throw new IllegalArgumentException("An operation needs at least one line");
        if (operation.reason() == null || operation.reason().isBlank()) {
            throw new ApiException(400, "VALIDATION_FAILED", "El motivo es obligatorio.");
        }
        // Lock every affected balance in a stable order to avoid deadlocks between concurrent operations.
        Map<Long, Balance> balances = new LinkedHashMap<>();
        operation.lines().stream().map(Line::productId).distinct().sorted(Comparator.naturalOrder())
            .forEach(product -> balances.put(product, lock(operation.branchId(), product)));

        // Value every line in memory first: a rejection must not leave partial writes.
        List<Valued> valued = new ArrayList<>();
        for (Line line : operation.lines()) {
            Valued value = value(line, balances.get(line.productId()));
            balances.put(line.productId(), value.resulting());
            valued.add(value);
        }

        Instant now = clock.instant();
        UUID operationExternalId = UUID.randomUUID();
        Long operationId = jdbc.queryForObject("INSERT INTO inventory_operation(external_id,kind,occurred_at,actor_id,reason,"
                + "purchase_order_id,sale_id,original_operation_id) VALUES (?,?,?,?,?,?,?,?) RETURNING id", Long.class,
            operationExternalId, operation.kind().name(), Timestamp.from(now), operation.actorId(), operation.reason().strip(),
            operation.purchaseOrderId(), operation.saleId(), operation.originalOperationId());
        List<PostedMovement> posted = new ArrayList<>();
        for (Valued value : valued) {
            Line line = value.line();
            UUID movementExternalId = UUID.randomUUID();
            Long movementId = jdbc.queryForObject("""
                INSERT INTO inventory_movement(external_id,operation_id,branch_id,product_id,product_unit_id,direction,
                    commercial_qty,base_qty,factor_snapshot,sku_snapshot,product_name_snapshot,unit_name_snapshot,
                    historical_base_cost,purchase_line_id,sale_line_id,original_movement_id)
                SELECT ?,?,?,p.id,u.id,?,?,?,?,p.sku,p.name,u.name,?,?,?,? FROM catalog_product p
                JOIN catalog_product_unit u ON u.product_id=p.id WHERE p.id=? AND u.id=?
                RETURNING id""", Long.class,
                movementExternalId, operationId, operation.branchId(), line.direction().name(), line.commercialQty(),
                line.baseQty(), line.factor(), value.appliedCost(), line.purchaseLineId(), line.saleLineId(),
                line.originalMovementId(), line.productId(), line.productUnitId());
            jdbc.update("INSERT INTO inventory_valuation_component(movement_id,component_kind,amount) VALUES (?,'BASE',?)",
                movementId, value.baseAmount());
            if (value.adjustmentAmount().signum() != 0) {
                jdbc.update("INSERT INTO inventory_valuation_component(movement_id,component_kind,amount) VALUES (?,?,?)",
                    movementId, value.adjustmentKind(), value.adjustmentAmount());
            }
            posted.add(new PostedMovement(movementExternalId, movementId, value.appliedCost(), value.baseAmount(),
                value.adjustmentAmount(), value.resulting()));
        }
        Timestamp updated = Timestamp.from(now);
        balances.forEach((product, balance) -> jdbc.update("UPDATE inventory_balance SET physical_qty=?,inventory_value=?,"
                + "average_base_cost=?,updated_at=? WHERE branch_id=? AND product_id=?",
            balance.quantity(), balance.value(), balance.average(), updated, operation.branchId(), product));
        return new Posted(operationExternalId, operationId, now, posted);
    }

    private record Valued(Line line, BigDecimal appliedCost, BigDecimal baseAmount, String adjustmentKind,
                          BigDecimal adjustmentAmount, Balance resulting) {}

    /** Values one line against the balance left by the previous lines of the same operation. */
    static Valued value(Line line, Balance balance) {
        BigDecimal quantity = line.baseQty();
        if (quantity.signum() <= 0) throw new IllegalArgumentException("Base quantity must be positive");
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
            throw new ApiException(409, "INSUFFICIENT_STOCK", "La salida de " + quantity.toPlainString()
                + " excede las existencias físicas disponibles (" + balance.quantity().toPlainString()
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

    private static BigDecimal entryCost(Cost cost, Balance balance) {
        BigDecimal value = switch (cost) {
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
