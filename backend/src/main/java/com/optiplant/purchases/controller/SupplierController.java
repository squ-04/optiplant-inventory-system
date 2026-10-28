package com.optiplant.purchases.controller;

import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.purchases.dto.SupplierRequest;
import com.optiplant.purchases.dto.SupplierResponse;
import com.optiplant.purchases.service.SupplierService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** RF-0021: the administrator creates and modifies suppliers; every profile consults them to use them (§5.2). */
@RestController
@RequestMapping("/api/v1/purchases/suppliers")
public class SupplierController {
    private static final Set<String> READERS = Set.of("ADMIN", "MANAGER", "OPERATOR");
    private final SupplierService suppliers;
    private final ScopePolicy policy;

    public SupplierController(SupplierService suppliers, ScopePolicy policy) {
        this.suppliers = suppliers;
        this.policy = policy;
    }

    @GetMapping
    public List<SupplierResponse> list(@AuthenticationPrincipal SessionPrincipal principal) {
        if (!READERS.contains(principal.role())) throw forbidden();
        return suppliers.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(@AuthenticationPrincipal SessionPrincipal principal, @RequestBody JsonNode body) {
        requireAdmin(principal);
        return suppliers.create(SupplierRequest.from(body));
    }

    @PostMapping("/{externalId}/update")
    public SupplierResponse update(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID externalId,
                                   @RequestBody JsonNode body) {
        requireAdmin(principal);
        policy.requirePublicReference(externalId);
        return suppliers.update(externalId, SupplierRequest.from(body));
    }

    private static void requireAdmin(SessionPrincipal principal) {
        if (!"ADMIN".equals(principal.role())) throw forbidden();
    }

    private static ApiException forbidden() {
        return new ApiException(403, "ROLE_FORBIDDEN", "Solo el administrador crea y modifica proveedores.");
    }
}
