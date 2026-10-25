package com.optiplant.identity.service;

import com.optiplant.common.exception.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Scope is always derived from the authenticated identity, never from a UI selection. */
@Component
public class ScopePolicy {
    public void requireHistoryRead(SessionPrincipal principal, UUID branch) {
        if (!principal.role().equals("ADMIN") && !branch.equals(principal.branchExternalId())) {
            throw new ApiException(403, "BRANCH_FORBIDDEN", "La consulta está limitada a su sucursal asignada.");
        }
    }

    public void requirePublicReference(UUID reference) {
        if (reference.version() != 4 || reference.variant() != 2) {
            throw new ApiException(400, "VALIDATION_FAILED", "La referencia debe ser un UUIDv4.");
        }
    }
}
