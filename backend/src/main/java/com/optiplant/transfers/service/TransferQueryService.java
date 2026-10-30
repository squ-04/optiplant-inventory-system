package com.optiplant.transfers.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.transfers.dto.TransferExportLine;
import com.optiplant.transfers.dto.TransferHistoryResponse;
import com.optiplant.transfers.dto.TransferResponse;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultation of transfers (RF-0032-CA-01, RF-0033-CA-02, RF-0034-CA-01/CA-03). Local profiles read the transfers in
 * which their branch is origin or destination; administrators read one branch or the whole network (§5.2).
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class TransferQueryService {
    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 500;
    private static final Set<String> DIRECTIONS = Set.of("INCOMING", "OUTGOING");
    private static final Set<String> DISCREPANCIES = Set.of("OPEN", "RESOLVED", "ALL");

    private final JdbcTemplate jdbc;
    private final ScopePolicy policy;
    private final TransferReader reader;

    public TransferQueryService(JdbcTemplate jdbc, ScopePolicy policy, TransferReader reader) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.reader = reader;
    }

    public TransferHistoryResponse history(SessionPrincipal principal, UUID branch, String direction, String status,
                                           String discrepancy, Integer limit) {
        if (discrepancy != null && !DISCREPANCIES.contains(discrepancy)) {
            throw new ApiException(400, "VALIDATION_FAILED", "Estado de alerta no admitido: use OPEN, RESOLVED o ALL.");
        }
        if (branch != null) policy.requirePublicReference(branch);
        if (status != null && !TransferStatus.isCode(status)) {
            throw new ApiException(400, "VALIDATION_FAILED", "Estado no admitido: use " + TransferStatus.codes() + ".");
        }
        if (direction != null && !DIRECTIONS.contains(direction)) {
            throw new ApiException(400, "VALIDATION_FAILED", "Sentido no admitido: use INCOMING (hacia la sucursal) u OUTGOING (desde la sucursal).");
        }
        int rows = limit == null ? DEFAULT_LIMIT : limit;
        if (rows < 1 || rows > MAX_LIMIT) {
            throw new ApiException(400, "VALIDATION_FAILED", "El límite debe estar entre 1 y " + MAX_LIMIT + ".");
        }
        boolean admin = "ADMIN".equals(principal.role());
        UUID scope = branch != null ? branch : admin ? null : principal.branchExternalId();
        if (scope == null && !admin) {
            throw new ApiException(403, "BRANCH_FORBIDDEN", "La consulta está limitada a su sucursal asignada.");
        }
        if (scope == null && direction != null) {
            throw new ApiException(400, "VALIDATION_FAILED", "Indique la sucursal para filtrar por sentido.");
        }
        if (scope != null) policy.requireHistoryRead(principal, scope);
        Branch consulted = scope == null ? null : jdbc.query("SELECT external_id,code,name,active FROM identity_branch WHERE external_id=?",
                (r, i) -> new Branch(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getBoolean(4)), scope)
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal."));
        StringBuilder where = new StringBuilder("WHERE TRUE");
        List<Object> arguments = new ArrayList<>();
        if (scope != null) {
            where.append(direction == null ? " AND (o.external_id=? OR d.external_id=?)"
                : direction.equals("OUTGOING") ? " AND o.external_id=?" : " AND d.external_id=?");
            arguments.add(scope);
            if (direction == null) arguments.add(scope);
        }
        if (status != null) { where.append(" AND t.status=?"); arguments.add(status); }
        if (discrepancy != null) {
            where.append(discrepancy.equals("ALL") ? " AND x.id IS NOT NULL" : " AND x.status=?");
            if (!discrepancy.equals("ALL")) arguments.add(discrepancy);
        }
        // The same joins the listing filters on (TransferReader.TRANSFER), without the limit.
        int total = jdbc.queryForObject("""
            SELECT count(DISTINCT t.id) FROM transfers_transfer t JOIN identity_branch o ON o.id=t.origin_branch_id
            JOIN identity_branch d ON d.id=t.destination_branch_id LEFT JOIN logistics_dispatch ld ON ld.transfer_id=t.id
            LEFT JOIN logistics_arrival la ON la.dispatch_id=ld.id LEFT JOIN logistics_discrepancy x ON x.arrival_id=la.id
            """ + where, Integer.class, arguments.toArray());
        arguments.add(rows);
        String order = discrepancy == null ? " ORDER BY t.requested_at DESC,t.id DESC LIMIT ?" : " ORDER BY x.opened_at DESC,x.id DESC LIMIT ?";
        List<TransferResponse> items = reader.list(where + order, arguments);
        OffsetDateTime readAt = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class);
        return new TransferHistoryResponse(readAt.toInstant(), consulted, total, items);
    }

    /** One transfer with lines and history; origin readers and administrators also see the origin's availability. */
    public TransferResponse detail(SessionPrincipal principal, UUID transfer) {
        policy.requirePublicReference(transfer);
        Map<String, Object> found = jdbc.queryForList("""
            SELECT t.id,o.external_id AS origin,d.external_id AS destination FROM transfers_transfer t
            JOIN identity_branch o ON o.id=t.origin_branch_id JOIN identity_branch d ON d.id=t.destination_branch_id
            WHERE t.external_id=?""", transfer).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "TRANSFER_NOT_FOUND", "No se encontró la transferencia."));
        boolean admin = "ADMIN".equals(principal.role());
        boolean origin = found.get("origin").equals(principal.branchExternalId());
        if (!admin && !origin && !found.get("destination").equals(principal.branchExternalId())) {
            throw new ApiException(404, "TRANSFER_NOT_FOUND", "No se encontró la transferencia.");
        }
        return reader.detail(((Number) found.get("id")).longValue(), admin || origin);
    }

    /**
     * RF-0054: one row per line of the transfers requested in [start, end) in which {@code branch} is origin or destination,
     * or of the network when it is null. Dispatch, arrival and discrepancy are unique per transfer, so a line never repeats;
     * authorization is checked by the caller.
     */
    public List<TransferExportLine> exportLines(UUID branch, Instant start, Instant end, String status, int limit) {
        List<Object> arguments = new ArrayList<>(List.of(Timestamp.from(start), Timestamp.from(end)));
        StringBuilder where = new StringBuilder();
        if (branch != null) {
            where.append(" AND (o.external_id=? OR d.external_id=?)");
            arguments.add(branch);
            arguments.add(branch);
        }
        if (status != null) {
            where.append(" AND t.status=?");
            arguments.add(status);
        }
        arguments.add(limit);
        return jdbc.query("""
            SELECT t.code,o.code AS origin_code,o.name AS origin_name,d.code AS destination_code,d.name AS destination_name,
                t.requested_at,ld.dispatched_at,la.arrived_at,t.status,l.sku_snapshot,l.unit_name_snapshot,l.requested_commercial_qty,
                dl.dispatched_commercial_qty,al.received_commercial_qty,it.pending_base_qty,x.status AS discrepancy_status,x.treatment,
                EXISTS(SELECT 1 FROM inventory_operation lo WHERE lo.discrepancy_id=x.id AND lo.kind='TRANSIT_LOSS') AS transit_loss
            FROM transfers_line l JOIN transfers_transfer t ON t.id=l.transfer_id
            JOIN identity_branch o ON o.id=t.origin_branch_id JOIN identity_branch d ON d.id=t.destination_branch_id
            LEFT JOIN logistics_dispatch ld ON ld.transfer_id=t.id LEFT JOIN logistics_dispatch_line dl ON dl.transfer_line_id=l.id
            LEFT JOIN logistics_arrival la ON la.dispatch_id=ld.id LEFT JOIN logistics_arrival_line al ON al.dispatch_line_id=dl.id
            LEFT JOIN inventory_transit it ON it.dispatch_line_id=dl.id LEFT JOIN logistics_discrepancy x ON x.arrival_id=la.id
            WHERE t.requested_at>=? AND t.requested_at<?""" + where + " ORDER BY t.requested_at DESC,t.id DESC,l.line_no LIMIT ?", (r, i) -> {
                BigDecimal dispatched = r.getBigDecimal("dispatched_commercial_qty");
                BigDecimal received = r.getBigDecimal("received_commercial_qty");
                BigDecimal pending = r.getBigDecimal("pending_base_qty");
                BigDecimal difference = received == null ? null
                    : pending == null || pending.signum() == 0 ? BigDecimal.ZERO : dispatched.subtract(received);
                return new TransferExportLine(r.getString("code"), r.getString("origin_code"), r.getString("origin_name"),
                    r.getString("destination_code"), r.getString("destination_name"),
                    r.getObject("requested_at", OffsetDateTime.class).toInstant(), instant(r.getObject("dispatched_at", OffsetDateTime.class)),
                    instant(r.getObject("arrived_at", OffsetDateTime.class)), r.getString("status"), r.getString("sku_snapshot"),
                    r.getString("unit_name_snapshot"), r.getBigDecimal("requested_commercial_qty"), dispatched, received, difference,
                    r.getString("discrepancy_status"), r.getString("treatment"), r.getBoolean("transit_loss"));
            }, arguments.toArray());
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    /** Active branches other than the operator's own, which may be chosen as origin of a request (RF-0032). */
    public List<Branch> origins(SessionPrincipal principal) {
        return jdbc.query("SELECT external_id,code,name,active FROM identity_branch WHERE active AND external_id<>? ORDER BY code",
            (r, i) -> new Branch(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getBoolean(4)),
            principal.branchExternalId());
    }
}
