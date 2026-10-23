package com.optiplant.common.controller;

import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.service.SessionPrincipal;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BootstrapController {
    private final Clock clock;

    public BootstrapController(Clock clock) { this.clock = clock; }

    @GetMapping("/api/v1/bootstrap")
    public BootstrapResponse bootstrap(@AuthenticationPrincipal SessionPrincipal principal,
        @RequestParam(required = false) UUID branchExternalId, @RequestParam(defaultValue = "context") String view) {
        if (branchExternalId != null && (branchExternalId.version() != 4 || branchExternalId.variant() != 2)) {
            throw new ApiException(400, "VALIDATION_FAILED", "La referencia debe ser un UUIDv4.");
        }
        if (!view.equals("context") && !view.equals("management")) {
            throw new ApiException(400, "VALIDATION_FAILED", "La vista técnica no es válida.");
        }
        if (view.equals("management") && principal.role().equals("OPERATOR")) {
            throw new ApiException(403, "ROLE_FORBIDDEN", "El rol no permite consultar esta vista técnica.");
        }
        if (!principal.role().equals("ADMIN") && branchExternalId != null
            && !branchExternalId.equals(principal.branchExternalId())) {
            throw new ApiException(403, "BRANCH_FORBIDDEN", "La vista técnica está limitada a la sucursal asignada.");
        }
        return new BootstrapResponse(UUID.randomUUID(), clock.instant(), "123456789012345678.123456789012345678",
            principal.role(), branchExternalId == null ? principal.branchExternalId() : branchExternalId, view);
    }

    public record BootstrapResponse(UUID externalId, Instant occurredAt,
        @Schema(type = "string", example = "123456789012345678.123456789012345678") String amount,
        String role, UUID branchExternalId, String view) {}
}
