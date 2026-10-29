package com.optiplant.logistics.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.inventory.service.InventoryLedger;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shortage alert of an arrival (RF-0038, RF-0039, H v0.14), inside the caller's transaction. «Resuelto» records the
 * selected treatment, who resolved it and when, once; it changes no quantity, value, movement, transit or transfer state.
 * The loss of the pending transit is a separate, authorized write-off through the inventory ledger.
 */
@Service
public class DiscrepancyService {
    private final JdbcTemplate jdbc;
    private final InventoryLedger ledger;
    private final Clock clock;

    public DiscrepancyService(JdbcTemplate jdbc, InventoryLedger ledger, Clock clock) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.clock = clock;
    }

    public record Locked(long id, String status) {}

    /** Locks the shortage alert of a transfer's arrival, if any. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Locked> lock(long transferId) {
        return jdbc.query("""
            SELECT x.id,x.status FROM logistics_discrepancy x JOIN logistics_arrival a ON a.id=x.arrival_id
            JOIN logistics_dispatch d ON d.id=a.dispatch_id WHERE d.transfer_id=? FOR UPDATE OF x""",
            (r, i) -> new Locked(r.getLong(1), r.getString(2)), transferId).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void resolve(Locked discrepancy, String treatment, long actorId) {
        if (!discrepancy.status().equals("OPEN")) {
            throw new ApiException(409, "DISCREPANCY_ALREADY_RESOLVED", "La alerta de faltante ya está resuelta: su tratamiento, "
                + "responsable y fecha se conservan. No se modificó la alerta.");
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("UPDATE logistics_discrepancy SET status='RESOLVED',treatment=?,resolved_by=?,resolved_at=?,updated_at=? "
            + "WHERE id=?", treatment, actorId, now, now, discrepancy.id());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryLedger.WrittenOff writeOff(Locked discrepancy, long actorId, String reason, String evidence) {
        return ledger.writeOff(new InventoryLedger.TransitLoss(discrepancy.id(), actorId, reason, evidence));
    }

    /** The internal id and branches of a shortage, to validate a resend that references it. */
    public record Source(long id, long originId, long destinationId) {}

    public Optional<Source> source(UUID externalId) {
        return jdbc.query("""
            SELECT x.id,t.origin_branch_id,t.destination_branch_id FROM logistics_discrepancy x
            JOIN logistics_arrival a ON a.id=x.arrival_id JOIN logistics_dispatch d ON d.id=a.dispatch_id
            JOIN transfers_transfer t ON t.id=d.transfer_id WHERE x.external_id=?""",
            (r, i) -> new Source(r.getLong(1), r.getLong(2), r.getLong(3)), externalId).stream().findFirst();
    }
}
