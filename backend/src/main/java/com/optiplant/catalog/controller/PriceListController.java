package com.optiplant.catalog.controller;

import com.optiplant.catalog.dto.PriceEntryRequest;
import com.optiplant.catalog.dto.PriceListRequest;
import com.optiplant.catalog.dto.PriceListResponse;
import com.optiplant.catalog.service.PriceListService;
import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
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

/**
 * RF-0030, INC-003/D06: only the administrator maintains price lists; managers and operators consult the active ones
 * to use them in the sales they are allowed to register.
 */
@RestController
@RequestMapping("/api/v1/catalog/price-lists")
public class PriceListController {
    private static final Set<String> READERS = Set.of("ADMIN", "MANAGER", "OPERATOR");
    private final PriceListService priceLists;
    private final ScopePolicy policy;

    public PriceListController(PriceListService priceLists, ScopePolicy policy) {
        this.priceLists = priceLists;
        this.policy = policy;
    }

    @GetMapping
    public List<PriceListResponse> list(@AuthenticationPrincipal SessionPrincipal principal) {
        requireReader(principal);
        return priceLists.list(isAdmin(principal));
    }

    @GetMapping("/{externalId}")
    public PriceListResponse find(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID externalId) {
        requireReader(principal);
        policy.requirePublicReference(externalId);
        return priceLists.find(externalId, isAdmin(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PriceListResponse create(@AuthenticationPrincipal SessionPrincipal principal, @RequestBody JsonNode body) {
        requireAdmin(principal);
        return priceLists.create(PriceListRequest.create(body));
    }

    @PostMapping("/{externalId}/update")
    public PriceListResponse update(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID externalId,
                                    @RequestBody JsonNode body) {
        requireAdmin(principal);
        policy.requirePublicReference(externalId);
        return priceLists.update(externalId, PriceListRequest.update(body));
    }

    @PostMapping("/{externalId}/prices")
    public PriceListResponse setPrice(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID externalId,
                                      @RequestBody JsonNode body) {
        requireAdmin(principal);
        policy.requirePublicReference(externalId);
        return priceLists.setPrice(externalId, PriceEntryRequest.from(body));
    }

    private static boolean isAdmin(SessionPrincipal principal) {
        return "ADMIN".equals(principal.role());
    }

    private static void requireReader(SessionPrincipal principal) {
        if (!READERS.contains(principal.role())) {
            throw new ApiException(403, "ROLE_FORBIDDEN", "El perfil no puede consultar listas de precios.");
        }
    }

    private static void requireAdmin(SessionPrincipal principal) {
        if (!isAdmin(principal)) {
            throw new ApiException(403, "ROLE_FORBIDDEN", "Solo el administrador crea y modifica listas de precios.");
        }
    }
}
