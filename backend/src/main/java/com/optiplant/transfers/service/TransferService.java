package com.optiplant.transfers.service;

import com.optiplant.catalog.service.OperableUnits;
import com.optiplant.catalog.service.UnitConversion;
import com.optiplant.common.exception.ApiException;
import com.optiplant.common.idempotency.IdempotencyService;
import com.optiplant.common.text.LineDetail;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.transfers.dto.TransferPreparationRequest;
import com.optiplant.transfers.dto.TransferReasonRequest;
import com.optiplant.transfers.dto.TransferRequest;
import com.optiplant.transfers.dto.TransferResponse;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Transfer request, preparation and origin decision (RF-0032–RF-0034, INC-015). The destination operator requests;
 * the origin operator prepares, confirming or lowering each quantity; the origin manager approves, rejects with a reason
 * while preparing, or cancels with a reason once approved and before dispatch; the destination operator cancels before
 * approval. None of these steps reserves or moves stock (INC-015/D05): only the dispatch (RF-0035) will. Every write
 * locks the transfer row, validates the transition and the actor under that lock, and records one append-only event, so
 * concurrent or repeated requests cannot apply a transition twice; an {@code Idempotency-Key} replays a committed result.
 */
@Service
public class TransferService {
    static final String CREATE = "transfers.request.v1";
    static final String PREPARE = "transfers.prepare.v1";
    static final String APPROVE = "transfers.approve.v1";
    static final String REJECT = "transfers.reject.v1";
    static final String CANCEL = "transfers.cancel.v1";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final IdempotencyService idempotency;
    private final OperableUnits units;
    private final TransferReader reader;
    private final JsonMapper json;

    public TransferService(JdbcTemplate jdbc, Clock clock, IdempotencyService idempotency, OperableUnits units,
                           TransferReader reader, JsonMapper json) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.idempotency = idempotency;
        this.units = units;
        this.reader = reader;
        this.json = json;
    }

    /** The actor's identity and branch; the branch must be active for new effects (ARQ-007), checked inside the action. */
    private record Actor(long id, String role, long branchId, boolean branchActive) {}

    private record Locked(long id, String code, String status, long originId, long destinationId) {}

    /** RF-0032: the destination operator requests products from another active branch; stock does not change. */
    @Transactional
    public TransferResponse request(SessionPrincipal principal, String key, TransferRequest request) {
        IdempotencyService.requireKey(key);
        Actor actor = actor(principal);
        return idempotency.execute(actor.id(), CREATE, principal.branchExternalId().toString(), key, request, 201,
            TransferResponse.class, () -> reader.detail(insert(active(actor), request), false));
    }

    /** RF-0033: the origin operator confirms or lowers the quantity of every line; nothing is reserved. */
    @Transactional
    public TransferResponse prepare(SessionPrincipal principal, UUID transfer, String key, TransferPreparationRequest request) {
        IdempotencyService.requireKey(key);
        Actor actor = actor(principal);
        return idempotency.execute(actor.id(), PREPARE, transfer.toString(), key, Map.of("transfer", transfer, "lines",
            request.quantities()), 200, TransferResponse.class, () -> reader.detail(prepare(active(actor), transfer, request), true));
    }

    /** RF-0034-CA-01: the origin manager approves a prepared transfer. */
    @Transactional
    public TransferResponse approve(SessionPrincipal principal, UUID transfer, String key) {
        IdempotencyService.requireKey(key);
        Actor actor = actor(principal);
        return idempotency.execute(actor.id(), APPROVE, transfer.toString(), key, Map.of("transfer", transfer), 200,
            TransferResponse.class, () -> reader.detail(decide(active(actor), transfer, "APPROVED", null), true));
    }

    /** RF-0034-CA-03/CA-04: the origin manager rejects a transfer in preparation, always with a reason. */
    @Transactional
    public TransferResponse reject(SessionPrincipal principal, UUID transfer, String key, TransferReasonRequest request) {
        IdempotencyService.requireKey(key);
        String reason = request.required("Indique el motivo del rechazo. No se modificó la transferencia.");
        Actor actor = actor(principal);
        return idempotency.execute(actor.id(), REJECT, transfer.toString(), key, Map.of("transfer", transfer, "reason", reason),
            200, TransferResponse.class, () -> reader.detail(decide(active(actor), transfer, "REJECTED", reason), true));
    }

    /**
     * RF-0034-CA-02: before approval the destination operator cancels (reason optional); once approved and before dispatch
     * only the origin manager cancels, with a reason. A transfer in transit is never cancelled.
     */
    @Transactional
    public TransferResponse cancel(SessionPrincipal principal, UUID transfer, String key, TransferReasonRequest request) {
        IdempotencyService.requireKey(key);
        Actor actor = actor(principal);
        return idempotency.execute(actor.id(), CANCEL, transfer.toString(), key,
            Map.of("transfer", transfer, "reason", request.reason() == null ? "" : request.reason()), 200,
            TransferResponse.class, () -> {
                long id = cancel(active(actor), transfer, request);
                return reader.detail(id, actor.branchId() == originOf(id));
            });
    }

    private record Resolved(OperableUnits.Unit unit, TransferRequest.Line line, BigDecimal base) {}

    private long insert(Actor actor, TransferRequest request) {
        Map<String, Object> origin = jdbc.queryForList("SELECT id,active FROM identity_branch WHERE external_id=?", request.origin())
            .stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal origen. No se creó la transferencia."));
        long originId = ((Number) origin.get("id")).longValue();
        if (originId == actor.branchId()) {
            throw new ApiException(400, "SAME_BRANCH", "El origen debe ser una sucursal distinta de la suya, que es el destino. "
                + "No se creó la transferencia.");
        }
        if (!(Boolean) origin.get("active")) {
            throw new ApiException(409, "BRANCH_INACTIVE", "La sucursal origen está inactiva y no admite nuevas transferencias. "
                + "No se creó la transferencia.");
        }
        // Every line is resolved before the first write: a rejected line leaves no transfer behind (RF-0032-CA-02).
        List<Resolved> lines = new ArrayList<>();
        for (int index = 0; index < request.lines().size(); index++) {
            TransferRequest.Line line = request.lines().get(index);
            try {
                OperableUnits.Unit unit = units.find(actor.branchId(), line.product(), line.unit());
                try {
                    units.requireOperable(originId, unit.productId());
                } catch (ApiException error) {
                    throw new ApiException(error.status(), error.code(),
                        "El producto no está activo y habilitado en el catálogo de la sucursal origen.");
                }
                BigDecimal base = UnitConversion.toBase(line.quantity(), unit.factor(), unit.divisible(), unit.baseDivisible());
                lines.add(new Resolved(unit, line, base));
            } catch (ApiException error) {
                throw new ApiException(error.status(), error.code(), LineDetail.of(index + 1, error.getMessage())
                    + " No se creó la transferencia.");
            }
        }
        Timestamp now = Timestamp.from(clock.instant());
        UUID externalId = UUID.randomUUID();
        // The business code derives from the internal id, so it is written once the id exists.
        long transferId = jdbc.queryForObject("""
            INSERT INTO transfers_transfer(external_id,code,origin_branch_id,destination_branch_id,requested_by,requested_at,
                status,created_at,updated_at) VALUES (?,?,?,?,?,?,'REQUESTED',?,?) RETURNING id""",
            Long.class, externalId, externalId.toString(), originId, actor.branchId(), actor.id(), now, now, now);
        jdbc.update("UPDATE transfers_transfer SET code=? WHERE id=?", "TR-%06d".formatted(transferId), transferId);
        for (int index = 0; index < lines.size(); index++) {
            Resolved resolved = lines.get(index);
            jdbc.update("""
                INSERT INTO transfers_line(transfer_id,line_no,product_id,product_unit_id,sku_snapshot,product_name_snapshot,
                    unit_name_snapshot,factor_snapshot,requested_commercial_qty,requested_base_qty,created_at,updated_at)
                SELECT ?,?,p.id,u.id,p.sku,p.name,u.name,u.factor,?,?,?,? FROM catalog_product p
                JOIN catalog_product_unit u ON u.product_id=p.id WHERE p.id=? AND u.id=?""",
                transferId, index + 1, resolved.line().quantity(), resolved.base(), now, now, resolved.unit().productId(),
                resolved.unit().id());
        }
        event(transferId, null, "REQUESTED", actor.id(), now, null, null);
        return transferId;
    }

    private long prepare(Actor actor, UUID externalId, TransferPreparationRequest request) {
        Locked transfer = lock(actor, externalId);
        if (actor.branchId() != transfer.originId()) {
            throw forbidden("La preparación corresponde al operador de la sucursal origen; el destino ve la cantidad propuesta.");
        }
        if (!transfer.status().equals("REQUESTED") && !transfer.status().equals("PREPARING")) {
            throw conflict(transfer, "Solo se prepara una transferencia solicitada o en preparación");
        }
        List<Map<String, Object>> lines = jdbc.queryForList("""
            SELECT l.id,l.line_no,l.factor_snapshot,l.requested_commercial_qty,l.requested_base_qty,l.unit_name_snapshot,
                u.divisible,p.base_divisible
            FROM transfers_line l JOIN catalog_product_unit u ON u.id=l.product_unit_id JOIN catalog_product p ON p.id=l.product_id
            WHERE l.transfer_id=? ORDER BY l.line_no""", transfer.id());
        Map<Integer, BigDecimal> quantities = request.quantities();
        for (Integer lineNo : quantities.keySet()) {
            if (lines.stream().noneMatch(line -> ((Number) line.get("line_no")).intValue() == lineNo)) {
                throw new ApiException(400, "VALIDATION_FAILED", "Línea " + lineNo + ": no existe en la transferencia "
                    + transfer.code() + ". No se modificó la preparación.");
            }
        }
        // Every line is validated before the first write, so a rejected line leaves the previous preparation intact.
        Map<Long, BigDecimal[]> prepared = new LinkedHashMap<>();
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Map<String, Object> line : lines) {
            int lineNo = ((Number) line.get("line_no")).intValue();
            BigDecimal quantity = quantities.get(lineNo);
            if (quantity == null) {
                throw new ApiException(400, "VALIDATION_FAILED", "Línea " + lineNo + ": indique la cantidad preparada, aunque sea 0. "
                    + "Se preparan todas las líneas a la vez. No se modificó la preparación.");
            }
            BigDecimal base;
            try {
                base = quantity.signum() == 0 ? BigDecimal.ZERO.setScale(3) : UnitConversion.toBase(quantity,
                    (BigDecimal) line.get("factor_snapshot"), (Boolean) line.get("divisible"), (Boolean) line.get("base_divisible"));
            } catch (ApiException error) {
                throw new ApiException(error.status(), error.code(), LineDetail.of(lineNo, error.getMessage())
                    + " No se modificó la preparación.");
            }
            if (base.compareTo((BigDecimal) line.get("requested_base_qty")) > 0) {
                throw new ApiException(409, "PREPARED_EXCEEDS_REQUESTED", LineDetail.of(lineNo, "se solicitaron "
                    + plain((BigDecimal) line.get("requested_commercial_qty")) + " " + line.get("unit_name_snapshot")
                    + " y no se puede enviar más de lo pedido sin una nueva solicitud.") + " No se modificó la preparación.");
            }
            BigDecimal commercial = quantity.setScale(3);
            prepared.put(((Number) line.get("id")).longValue(), new BigDecimal[] {commercial, base});
            snapshot.add(Map.of("lineNo", lineNo, "quantity", commercial.toPlainString(), "baseQuantity", base.toPlainString()));
        }
        Timestamp now = Timestamp.from(clock.instant());
        prepared.forEach((lineId, values) -> jdbc.update(
            "UPDATE transfers_line SET prepared_commercial_qty=?,prepared_base_qty=?,updated_at=? WHERE id=?",
            values[0], values[1], now, lineId));
        jdbc.update("UPDATE transfers_transfer SET status='PREPARING',updated_at=? WHERE id=?", now, transfer.id());
        event(transfer.id(), transfer.status(), "PREPARING", actor.id(), now, null, json.writeValueAsString(snapshot));
        return transfer.id();
    }

    /** Approval or rejection by the origin manager, both only from preparation (H v1.2/01). */
    private long decide(Actor actor, UUID externalId, String to, String reason) {
        Locked transfer = lock(actor, externalId);
        boolean approve = to.equals("APPROVED");
        if (actor.branchId() != transfer.originId()) {
            throw forbidden(approve
                ? "La aprobación corresponde al gerente de la sucursal origen; el destino supervisa la recepción."
                : "Solo el gerente de la sucursal origen rechaza la transferencia; el gerente de destino supervisa la recepción. "
                    + "No se modificó la transferencia.");
        }
        if (!transfer.status().equals("PREPARING")) {
            throw conflict(transfer, approve
                ? "Solo se aprueba una transferencia en preparación, después de que el operador de origen confirme las cantidades"
                : "Solo se rechaza una transferencia en preparación");
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("UPDATE transfers_transfer SET status=?,updated_at=? WHERE id=?", to, now, transfer.id());
        event(transfer.id(), "PREPARING", to, actor.id(), now, reason, null);
        return transfer.id();
    }

    private long cancel(Actor actor, UUID externalId, TransferReasonRequest request) {
        Locked transfer = lock(actor, externalId);
        String reason = request.reason();
        switch (transfer.status()) {
            case "REQUESTED", "PREPARING" -> {
                if (!actor.role().equals("OPERATOR") || actor.branchId() != transfer.destinationId()) {
                    throw forbidden("Antes de la aprobación cancela el operador de la sucursal destino; el gerente de origen puede "
                        + "rechazarla mientras está en preparación. No se modificó la transferencia.");
                }
            }
            case "APPROVED" -> {
                if (!actor.role().equals("MANAGER") || actor.branchId() != transfer.originId()) {
                    throw forbidden("Una transferencia aprobada solo la cancela el gerente de la sucursal origen, antes del despacho. "
                        + "No se modificó la transferencia.");
                }
                reason = request.required("Indique el motivo de la cancelación de una transferencia aprobada. "
                    + "No se modificó la transferencia.");
            }
            case "IN_TRANSIT" -> throw new ApiException(409, "TRANSFER_IN_TRANSIT", "La transferencia " + transfer.code()
                + " está en tránsito: no se cancela. Se resuelve con su recepción. No se modificó la transferencia ni el inventario.");
            default -> throw conflict(transfer, "Solo se cancela una transferencia antes de su despacho");
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("UPDATE transfers_transfer SET status='CANCELLED',updated_at=? WHERE id=?", now, transfer.id());
        event(transfer.id(), transfer.status(), "CANCELLED", actor.id(), now, reason, null);
        return transfer.id();
    }

    /** Locks a transfer of which the actor's branch is origin or destination; any other is reported as absent. */
    private Locked lock(Actor actor, UUID externalId) {
        return jdbc.query("SELECT id,code,status,origin_branch_id,destination_branch_id FROM transfers_transfer "
                + "WHERE external_id=? AND ? IN (origin_branch_id,destination_branch_id) FOR UPDATE",
            (row, index) -> new Locked(row.getLong(1), row.getString(2), row.getString(3), row.getLong(4), row.getLong(5)),
            externalId, actor.branchId()).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "TRANSFER_NOT_FOUND", "No existe esa transferencia para su sucursal."));
    }

    private long originOf(long transferId) {
        return jdbc.queryForObject("SELECT origin_branch_id FROM transfers_transfer WHERE id=?", Long.class, transferId);
    }

    private void event(long transferId, String from, String to, long actorId, Timestamp at, String reason, String preparation) {
        jdbc.update("INSERT INTO transfers_event(transfer_id,sequence_no,from_status,to_status,actor_id,occurred_at,reason,"
                + "preparation_snapshot) SELECT ?,coalesce(max(sequence_no),0)+1,?,?,?,?,?,?::jsonb FROM transfers_event WHERE transfer_id=?",
            transferId, from, to, actorId, at, reason, preparation, transferId);
    }

    /**
     * Role checks made before reading the body (§5.2): only operators request and prepare, only managers approve and
     * reject, and administrators never execute transfer steps by role inheritance (RF-0032, RF-0034).
     */
    public void requireRole(SessionPrincipal principal, String role, String detail) {
        if (!role.equals(principal.role()) || principal.branchExternalId() == null) throw forbidden(detail);
    }

    /** Cancellation is decided by state under lock; here only administrators are turned away. */
    public void requireLocal(SessionPrincipal principal) {
        if (principal.branchExternalId() == null || "ADMIN".equals(principal.role())) {
            throw forbidden("El administrador consulta las transferencias de la red; no las cancela por herencia de rol.");
        }
    }

    private Actor actor(SessionPrincipal principal) {
        Map<String, Object> branch = jdbc.queryForList("SELECT id,active FROM identity_branch WHERE external_id=?",
            principal.branchExternalId()).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal."));
        long actorId = jdbc.queryForObject("SELECT id FROM identity_user_account WHERE external_id=?", Long.class,
            principal.externalId());
        return new Actor(actorId, principal.role(), ((Number) branch.get("id")).longValue(), (Boolean) branch.get("active"));
    }

    private static Actor active(Actor actor) {
        if (!actor.branchActive()) {
            throw new ApiException(409, "BRANCH_INACTIVE", "La sucursal está inactiva: conserva su historial pero no admite nuevas operaciones.");
        }
        return actor;
    }

    private static ApiException forbidden(String detail) {
        return new ApiException(403, "TRANSFER_FORBIDDEN", detail);
    }

    private static ApiException conflict(Locked transfer, String rule) {
        return new ApiException(409, "TRANSFER_STATUS_CONFLICT", rule + "; la transferencia " + transfer.code() + " está "
            + label(transfer.status()) + ". No se modificó la transferencia ni el inventario.");
    }

    static String label(String status) {
        return TransferStatus.labelOf(status);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
