package com.optiplant.transfers.controller;

import com.optiplant.common.exception.ApiException;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.transfers.dto.TransferHistoryResponse;
import com.optiplant.transfers.dto.TransferPreparationRequest;
import com.optiplant.transfers.dto.TransferReasonRequest;
import com.optiplant.transfers.dto.TransferRequest;
import com.optiplant.transfers.dto.TransferResponse;
import com.optiplant.transfers.service.TransferQueryService;
import com.optiplant.transfers.service.TransferService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * Transfers between branches (RF-0032–RF-0034). Transfers are never deleted. Writes take an {@code Idempotency-Key}:
 * repeating a request with the same key returns the original result without new effects. No step here moves stock.
 */
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {
    private static final String OPERATOR_ONLY_REQUEST = "Las transferencias las solicita el operador de la sucursal destino; "
        + "el gerente las supervisa y el administrador las consulta.";
    private static final String OPERATOR_ONLY_PREPARE = "La preparación corresponde al operador de la sucursal origen.";
    private static final String MANAGER_ONLY_APPROVE = "La aprobación corresponde al gerente de la sucursal origen.";
    private static final String MANAGER_ONLY_REJECT = "Solo el gerente de la sucursal origen rechaza una transferencia. "
        + "No se modificó la transferencia.";

    private final TransferService transfers;
    private final TransferQueryService queries;
    private final ScopePolicy policy;

    public TransferController(TransferService transfers, TransferQueryService queries, ScopePolicy policy) {
        this.transfers = transfers;
        this.queries = queries;
        this.policy = policy;
    }

    /** RF-0032: requests products from another branch to the operator's branch; nothing is reserved. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse request(@AuthenticationPrincipal SessionPrincipal principal,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                    @RequestBody(required = false) JsonNode body) {
        transfers.requireRole(principal, "OPERATOR", OPERATOR_ONLY_REQUEST);
        return transfers.request(principal, key, TransferRequest.from(body));
    }

    /** RF-0033: records the quantity the origin will send for every line, at most the requested one. */
    @PostMapping("/{transfer}/prepare")
    public TransferResponse prepare(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID transfer,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                    @RequestBody(required = false) JsonNode body) {
        transfers.requireRole(principal, "OPERATOR", OPERATOR_ONLY_PREPARE);
        policy.requirePublicReference(transfer);
        return transfers.prepare(principal, transfer, key, TransferPreparationRequest.from(body));
    }

    /** RF-0034-CA-01: the origin manager approves a transfer in preparation. */
    @PostMapping("/{transfer}/approve")
    public TransferResponse approve(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID transfer,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                    @RequestBody(required = false) JsonNode body) {
        transfers.requireRole(principal, "MANAGER", MANAGER_ONLY_APPROVE);
        policy.requirePublicReference(transfer);
        if (body != null && !body.isNull() && !(body.isObject() && body.isEmpty())) {
            throw new ApiException(400, "VALIDATION_FAILED", "La aprobación no admite datos: las cantidades las confirma el operador de origen.");
        }
        return transfers.approve(principal, transfer, key);
    }

    /** RF-0034-CA-03/CA-04: the origin manager rejects a transfer in preparation with a mandatory reason. */
    @PostMapping("/{transfer}/reject")
    public TransferResponse reject(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID transfer,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                   @RequestBody(required = false) JsonNode body) {
        transfers.requireRole(principal, "MANAGER", MANAGER_ONLY_REJECT);
        policy.requirePublicReference(transfer);
        return transfers.reject(principal, transfer, key, TransferReasonRequest.from(body));
    }

    /** RF-0034-CA-02: cancellation before dispatch by the actor the transfer's state allows. */
    @PostMapping("/{transfer}/cancel")
    public TransferResponse cancel(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID transfer,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                   @RequestBody(required = false) JsonNode body) {
        transfers.requireLocal(principal);
        policy.requirePublicReference(transfer);
        return transfers.cancel(principal, transfer, key, TransferReasonRequest.from(body));
    }

    /** Transfers of a branch as origin or destination, or of the network for administrators; newest first. */
    @GetMapping
    public TransferHistoryResponse history(@AuthenticationPrincipal SessionPrincipal principal,
                                           @RequestParam(required = false) UUID branch,
                                           @RequestParam(required = false) String direction,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) Integer limit) {
        return queries.history(principal, branch, direction, status, limit);
    }

    /** The active branches a destination operator may request from. */
    @GetMapping("/origins")
    public List<Branch> origins(@AuthenticationPrincipal SessionPrincipal principal) {
        transfers.requireRole(principal, "OPERATOR", OPERATOR_ONLY_REQUEST);
        return queries.origins(principal);
    }

    @GetMapping("/{transfer}")
    public TransferResponse detail(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID transfer) {
        return queries.detail(principal, transfer);
    }
}
