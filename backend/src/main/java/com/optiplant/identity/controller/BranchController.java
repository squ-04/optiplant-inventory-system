package com.optiplant.identity.controller;

import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.dto.BranchCreateRequest;
import com.optiplant.identity.dto.BranchResponse;
import com.optiplant.identity.dto.BranchUpdateRequest;
import com.optiplant.identity.dto.EmptyCommandBody;
import com.optiplant.identity.service.BranchService;
import com.optiplant.identity.service.SessionPrincipal;
import java.util.List;
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

@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {
    private final BranchService branches;

    public BranchController(BranchService branches) { this.branches = branches; }

    @GetMapping
    public List<BranchResponse> list(@AuthenticationPrincipal SessionPrincipal principal) {
        if (admin(principal)) return branches.list();
        if (local(principal)) return List.of(branches.find(principal.branchExternalId()));
        throw roleForbidden();
    }

    @GetMapping("/{externalId}")
    public BranchResponse find(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID externalId) {
        if (admin(principal)) return branches.find(externalId);
        if (local(principal)) {
            if (!externalId.equals(principal.branchExternalId())) throw branchForbidden();
            return branches.find(externalId);
        }
        throw roleForbidden();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BranchResponse create(@AuthenticationPrincipal SessionPrincipal principal, @RequestBody JsonNode body) {
        requireAdmin(principal);
        return branches.create(BranchCreateRequest.from(body));
    }

    @PostMapping("/{externalId}/update")
    public BranchResponse update(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID externalId,
        @RequestBody JsonNode body) {
        requireAdmin(principal);
        return branches.update(externalId, BranchUpdateRequest.from(body));
    }

    @PostMapping("/{externalId}/deactivate")
    public BranchResponse deactivate(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID externalId,
        @RequestBody(required = false) JsonNode body) {
        requireAdmin(principal);
        EmptyCommandBody.validate(body);
        return branches.deactivate(externalId);
    }

    private static void requireAdmin(SessionPrincipal principal) {
        if (!admin(principal)) throw roleForbidden();
    }

    private static boolean admin(SessionPrincipal principal) { return "ADMIN".equals(principal.role()); }
    private static boolean local(SessionPrincipal principal) {
        return ("MANAGER".equals(principal.role()) || "OPERATOR".equals(principal.role())) && principal.branchExternalId() != null;
    }

    private static ApiException roleForbidden() {
        return new ApiException(403, "ROLE_FORBIDDEN", "El rol no permite administrar sucursales.");
    }

    private static ApiException branchForbidden() {
        return new ApiException(403, "BRANCH_FORBIDDEN", "La consulta está limitada a la sucursal asignada.");
    }
}
