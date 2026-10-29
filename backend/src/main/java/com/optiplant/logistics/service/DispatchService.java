package com.optiplant.logistics.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.common.text.LineDetail;
import com.optiplant.common.text.SpanishDecimal;
import com.optiplant.inventory.service.InventoryLedger;
import com.optiplant.inventory.service.InventoryLedger.Cost;
import com.optiplant.inventory.service.InventoryLedger.Direction;
import com.optiplant.inventory.service.InventoryLedger.Kind;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the single dispatch of a transfer (RF-0035, INC-015/D05, D07–D08, INC-016/D01) inside the caller's
 * transaction: the transfer module coordinates the state, this module owns the dispatch, and the inventory ledger owns
 * the exit and the transit. The origin's balances are locked and every line is checked against them before the first
 * write, so a line without stock rejects the whole dispatch; any later failure rolls back with the caller, so there is
 * never an implicit partial dispatch. Each line is frozen at the origin's average cost; a line prepared at zero is kept
 * with zero quantity and value, without movement or transit.
 */
@Service
public class DispatchService {
    private final JdbcTemplate jdbc;
    private final InventoryLedger ledger;

    public DispatchService(JdbcTemplate jdbc, InventoryLedger ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    /** A transfer line with the quantity prepared to be sent and the names fixed by the transfer. */
    public record Line(int lineNo, long transferLineId, long productId, long productUnitId, BigDecimal factor,
                       BigDecimal commercialQty, BigDecimal baseQty, String unitName, String baseUnitName,
                       InventoryLedger.Snapshot snapshot) {}

    public record Order(String transferCode, long originId, String destinationName, RouteService.ActiveRoute route,
                        String carrier, Instant departedAt, Instant estimatedArrivalAt, long actorId, long transferId,
                        List<Line> lines) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public long record(Order order) {
        // Lock in product order, as the ledger does, and revalidate the stock of every line before writing anything.
        Map<Long, InventoryLedger.Balance> balances = new TreeMap<>();
        order.lines().stream().filter(line -> line.baseQty().signum() > 0).map(Line::productId).sorted()
            .forEach(product -> balances.computeIfAbsent(product, id -> ledger.lock(order.originId(), id)));
        Map<Long, BigDecimal> leaving = new HashMap<>();
        for (Line line : order.lines()) {
            if (line.baseQty().signum() == 0) continue;
            BigDecimal total = leaving.merge(line.productId(), line.baseQty(), BigDecimal::add);
            BigDecimal available = balances.get(line.productId()).quantity();
            if (total.compareTo(available) > 0) {
                throw new ApiException(409, "INSUFFICIENT_STOCK", LineDetail.of(line.lineNo(), "existencias insuficientes en "
                    + "el origen: se requieren " + SpanishDecimal.format(total.stripTrailingZeros()) + " y hay "
                    + SpanishDecimal.format(available.stripTrailingZeros()) + " (" + line.baseUnitName() + ").")
                    + " No se despachó la transferencia " + order.transferCode() + ": no hubo salida ni tránsito.");
            }
        }

        long dispatchId = jdbc.queryForObject("""
            INSERT INTO logistics_dispatch(external_id,transfer_id,route_id,carrier_text,dispatched_at,estimated_arrival_at,
                dispatched_by,route_priority_snapshot) VALUES (?,?,?,?,?,?,?,?) RETURNING id""", Long.class,
            UUID.randomUUID(), order.transferId(), order.route().id(), order.carrier(), Timestamp.from(order.departedAt()),
            Timestamp.from(order.estimatedArrivalAt()), order.actorId(), order.route().priority());
        List<InventoryLedger.Line> exits = new ArrayList<>();
        for (Line line : order.lines()) {
            boolean sent = line.baseQty().signum() > 0;
            // The frozen cost is the origin's average, which ordinary exits do not change until the transaction ends.
            BigDecimal cost = sent ? balances.get(line.productId()).average() : currentAverage(order.originId(), line.productId());
            long dispatchLineId = jdbc.queryForObject("""
                INSERT INTO logistics_dispatch_line(dispatch_id,transfer_line_id,dispatched_commercial_qty,dispatched_base_qty,
                    frozen_base_cost,base_value) VALUES (?,?,?,?,?,?) RETURNING id""", Long.class, dispatchId,
                line.transferLineId(), line.commercialQty(), line.baseQty(), cost, line.baseQty().multiply(cost));
            if (!sent) continue;
            exits.add(new InventoryLedger.Line(line.productId(), line.productUnitId(), line.factor(), line.commercialQty(),
                line.baseQty(), Direction.OUT, new Cost.Average(), null, null, null, line.snapshot(), dispatchLineId));
        }
        InventoryLedger.Posted posted = ledger.post(new InventoryLedger.Operation(Kind.DISPATCH, order.originId(),
            order.actorId(), "Despacho de la transferencia " + order.transferCode() + " hacia " + order.destinationName()
                + " por la ruta " + order.route().code(), null, null, null, exits, dispatchId));
        // A depleting exit also withdraws the origin's final rounding difference; the transit received it apart. Each
        // movement names its dispatch line, so the difference never depends on the order of the posted movements.
        for (InventoryLedger.PostedMovement movement : posted.movements()) {
            if (movement.adjustmentAmount().signum() != 0) {
                jdbc.update("UPDATE logistics_dispatch_line SET rounding_value=? WHERE id=?", movement.adjustmentAmount().negate(),
                    movement.dispatchLineId());
            }
        }
        return dispatchId;
    }

    private BigDecimal currentAverage(long branchId, long productId) {
        return jdbc.queryForList("SELECT average_base_cost FROM inventory_balance WHERE branch_id=? AND product_id=?",
            BigDecimal.class, branchId, productId).stream().findFirst().orElse(BigDecimal.ZERO);
    }
}
