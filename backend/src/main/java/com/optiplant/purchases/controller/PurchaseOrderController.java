package com.optiplant.purchases.controller;

import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.purchases.dto.PurchaseCancelRequest;
import com.optiplant.purchases.dto.PurchaseHistoryResponse;
import com.optiplant.purchases.dto.PurchaseOrderRequest;
import com.optiplant.purchases.dto.PurchaseOrderResponse;
import com.optiplant.purchases.service.PurchaseHistoryService;
import com.optiplant.purchases.service.PurchaseOrderService;
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
 * Purchase orders (RF-0021–RF-0025). Orders are never deleted: there is no DELETE operation (RF-0022). Writes take
 * an {@code Idempotency-Key}: repeating a request with the same key returns the original result without new effects.
 */
@RestController
@RequestMapping("/api/v1/purchases/orders")
public class PurchaseOrderController {
    private final PurchaseOrderService orders;
    private final PurchaseHistoryService history;
    private final ScopePolicy policy;

    public PurchaseOrderController(PurchaseOrderService orders, PurchaseHistoryService history, ScopePolicy policy) {
        this.orders = orders;
        this.history = history;
        this.policy = policy;
    }

    /** RF-0021/RF-0023: creates a draft order with its conditions in the operator's branch; stock does not change. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderResponse create(@AuthenticationPrincipal SessionPrincipal principal,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                        @RequestBody JsonNode body) {
        return orders.create(principal, key, PurchaseOrderRequest.from(body));
    }

    /** RF-0022: rewrites a draft's supplier, conditions and lines; a pending or received order is never rewritten. */
    @PostMapping("/{order}/update")
    public PurchaseOrderResponse update(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID order,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                        @RequestBody(required = false) JsonNode body) {
        policy.requirePublicReference(order);
        return orders.update(principal, order, key, PurchaseOrderRequest.edit(body));
    }

    /** RF-0022: cancels a draft or pending order before its receipt; stock and valuation do not change. */
    @PostMapping("/{order}/cancel")
    public PurchaseOrderResponse cancel(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID order,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                        @RequestBody(required = false) JsonNode body) {
        policy.requirePublicReference(order);
        return orders.cancel(principal, order, key, PurchaseCancelRequest.from(body).reason());
    }

    /** RF-0021-CA-03: draft → pending receipt, fixing the supplier's historical name. */
    @PostMapping("/{order}/submit")
    public PurchaseOrderResponse submit(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID order,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                        @RequestBody(required = false) JsonNode body) {
        policy.requirePublicReference(order);
        requireEmpty(body, "VALIDATION_FAILED", "El envío a recepción no admite datos: las condiciones se registran al crear la orden.");
        return orders.submit(principal, order, key);
    }

    /** RF-0024: confirms the complete receipt of a pending order and posts its entries through the inventory ledger. */
    @PostMapping("/{order}/receive")
    public PurchaseOrderResponse receive(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID order,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                         @RequestBody(required = false) JsonNode body) {
        policy.requirePublicReference(order);
        requireEmpty(body, "PARTIAL_RECEIPT_NOT_SUPPORTED", "La recepción de compra es completa: se reciben todas las líneas de la orden y no admite "
            + "cantidades parciales. No se modificó la compra ni el inventario.");
        return orders.receive(principal, order, key);
    }

    /** RF-0025: history by branch, supplier, product and status, newest first. */
    @GetMapping
    public PurchaseHistoryResponse history(@AuthenticationPrincipal SessionPrincipal principal,
                                           @RequestParam(required = false) UUID branch,
                                           @RequestParam(required = false) UUID supplier,
                                           @RequestParam(required = false) UUID product,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) Integer limit) {
        return history.history(principal, branch, supplier, product, status, limit);
    }

    @GetMapping("/{order}")
    public PurchaseOrderResponse detail(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID order) {
        return history.detail(principal, order);
    }

    /** Transitions take no data; an absent body or {@code {}} is accepted. */
    private static void requireEmpty(JsonNode body, String code, String detail) {
        if (body != null && !body.isNull() && !(body.isObject() && body.isEmpty())) throw new ApiException(400, code, detail);
    }
}
