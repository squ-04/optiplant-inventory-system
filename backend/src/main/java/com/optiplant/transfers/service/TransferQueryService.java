package com.optiplant.transfers.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.transfers.dto.TransferHistoryResponse;
import com.optiplant.transfers.dto.TransferResponse;
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
    private static final Set<String> STATUSES = Set.of("REQUESTED", "PREPARING", "APPROVED", "IN_TRANSIT", "RECEIVED_COMPLETE",
        "RECEIVED_DISCREPANCY", "CANCELLED", "REJECTED");
    private static final Set<String> DIRECTIONS = Set.of("INCOMING", "OUTGOING");

    private final JdbcTemplate jdbc;
    private final ScopePolicy policy;
    private final TransferReader reader;

    public TransferQueryService(JdbcTemplate jdbc, ScopePolicy policy, TransferReader reader) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.reader = reader;
    }

    public TransferHistoryResponse history(SessionPrincipal principal, UUID branch, String direction, String status, Integer limit) {
        if (branch != null) policy.requirePublicReference(branch);
        if (status != null && !STATUSES.contains(status)) {
            throw new ApiException(400, "VALIDATION_FAILED", "Estado no admitido: use " + String.join(", ", STATUSES.stream().sorted().toList()) + ".");
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
        arguments.add(rows);
        List<TransferResponse> items = reader.list(where + " ORDER BY t.requested_at DESC,t.id DESC LIMIT ?", arguments);
        OffsetDateTime readAt = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class);
        return new TransferHistoryResponse(readAt.toInstant(), consulted, items);
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

    /** Active branches other than the operator's own, which may be chosen as origin of a request (RF-0032). */
    public List<Branch> origins(SessionPrincipal principal) {
        return jdbc.query("SELECT external_id,code,name,active FROM identity_branch WHERE active AND external_id<>? ORDER BY code",
            (r, i) -> new Branch(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getBoolean(4)),
            principal.branchExternalId());
    }
}
