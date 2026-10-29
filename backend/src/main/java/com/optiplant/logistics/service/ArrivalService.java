package com.optiplant.logistics.service;

import com.optiplant.inventory.service.InventoryLedger;
import com.optiplant.inventory.service.InventoryLedger.Cost;
import com.optiplant.inventory.service.InventoryLedger.Direction;
import com.optiplant.inventory.service.InventoryLedger.Kind;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the single arrival of a dispatch (RF-0036, RF-0037, H v0.11/v1.1) inside the caller's transaction: every
 * dispatched line gets its received quantity, the inventory ledger enters what was received into the destination and
 * releases it from transit, and any shortage opens one linked discrepancy (RF-0038) with a line per missing quantity. The
 * unique arrival per dispatch (V3) makes a second arrival impossible; an arrival of zero units has an operation and the
 * discrepancy, but no movement.
 */
@Service
public class ArrivalService {
    private final JdbcTemplate jdbc;
    private final InventoryLedger ledger;
    private final Clock clock;

    public ArrivalService(JdbcTemplate jdbc, InventoryLedger ledger, Clock clock) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.clock = clock;
    }

    /** A dispatched line with the quantity received, in its commercial unit and in base units. */
    public record Line(long dispatchLineId, long productId, long productUnitId, BigDecimal factor, BigDecimal dispatchedBaseQty,
                       BigDecimal receivedCommercialQty, BigDecimal receivedBaseQty, InventoryLedger.Snapshot snapshot) {}

    public record Order(String transferCode, String originName, long destinationId, long dispatchId, long actorId,
                        List<Line> lines) {}

    private record Shortage(long arrivalLineId, BigDecimal quantity) {}

    /** Whether every line arrived complete. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean record(Order order) {
        Instant now = clock.instant();
        long arrivalId = jdbc.queryForObject("INSERT INTO logistics_arrival(external_id,dispatch_id,arrived_at,confirmed_by) "
            + "VALUES (?,?,?,?) RETURNING id", Long.class, UUID.randomUUID(), order.dispatchId(), Timestamp.from(now), order.actorId());
        List<InventoryLedger.Line> entries = new ArrayList<>();
        List<Shortage> missing = new ArrayList<>();
        for (Line line : order.lines()) {
            long arrivalLineId = jdbc.queryForObject("INSERT INTO logistics_arrival_line(arrival_id,dispatch_line_id,"
                + "received_commercial_qty,received_base_qty) VALUES (?,?,?,?) RETURNING id", Long.class, arrivalId,
                line.dispatchLineId(), line.receivedCommercialQty(), line.receivedBaseQty());
            if (line.receivedBaseQty().signum() > 0) {
                entries.add(new InventoryLedger.Line(line.productId(), line.productUnitId(), line.factor(),
                    line.receivedCommercialQty(), line.receivedBaseQty(), Direction.IN, new Cost.Transit(), null, null, null,
                    line.snapshot(), null, arrivalLineId));
            }
            BigDecimal shortage = line.dispatchedBaseQty().subtract(line.receivedBaseQty());
            if (shortage.signum() > 0) {
                missing.add(new Shortage(arrivalLineId, shortage));
            }
        }
        ledger.post(new InventoryLedger.Operation(Kind.ARRIVAL, order.destinationId(), order.actorId(), "Recepción de la "
            + "transferencia " + order.transferCode() + " desde " + order.originName(), null, null, null, entries, null, arrivalId));
        if (missing.isEmpty()) return true;
        long discrepancyId = jdbc.queryForObject("INSERT INTO logistics_discrepancy(external_id,arrival_id,opened_at) "
            + "VALUES (?,?,?) RETURNING id", Long.class, UUID.randomUUID(), arrivalId, Timestamp.from(now));
        for (Shortage shortage : missing) {
            jdbc.update("INSERT INTO logistics_discrepancy_line(discrepancy_id,arrival_line_id,missing_base_qty) VALUES (?,?,?)",
                discrepancyId, shortage.arrivalLineId(), shortage.quantity());
        }
        return false;
    }
}
