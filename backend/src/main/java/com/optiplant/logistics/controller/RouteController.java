package com.optiplant.logistics.controller;

import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.logistics.dto.RouteRequest;
import com.optiplant.logistics.dto.RouteResponse;
import com.optiplant.logistics.service.RouteService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * RF-0041, INC-003/D06: only the administrator maintains the shared routes; managers and operators consult the routes
 * of their branch and use them when dispatching.
 */
@RestController
@RequestMapping("/api/v1/logistics/routes")
public class RouteController {
    private static final Set<String> READERS = Set.of("ADMIN", "MANAGER", "OPERATOR");
    private final RouteService routes;
    private final ScopePolicy policy;

    public RouteController(RouteService routes, ScopePolicy policy) {
        this.routes = routes;
        this.policy = policy;
    }

    @GetMapping
    public List<RouteResponse> list(@AuthenticationPrincipal SessionPrincipal principal,
                                    @RequestParam(required = false) String priority) {
        if (!READERS.contains(principal.role()) || (!"ADMIN".equals(principal.role()) && principal.branchExternalId() == null)) {
            throw new ApiException(403, "ROLE_FORBIDDEN", "El perfil no puede consultar rutas.");
        }
        return routes.list(principal, priority);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RouteResponse create(@AuthenticationPrincipal SessionPrincipal principal, @RequestBody(required = false) JsonNode body) {
        requireAdmin(principal);
        return routes.create(RouteRequest.create(body));
    }

    @PostMapping("/{externalId}/update")
    public RouteResponse update(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID externalId,
                                @RequestBody(required = false) JsonNode body) {
        requireAdmin(principal);
        policy.requirePublicReference(externalId);
        return routes.update(externalId, RouteRequest.update(body));
    }

    private static void requireAdmin(SessionPrincipal principal) {
        if (!"ADMIN".equals(principal.role())) {
            throw new ApiException(403, "ROLE_FORBIDDEN", "Solo el administrador registra y clasifica las rutas.");
        }
    }
}
