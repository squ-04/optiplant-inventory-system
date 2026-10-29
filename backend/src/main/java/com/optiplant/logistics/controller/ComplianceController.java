package com.optiplant.logistics.controller;

import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.logistics.dto.ComplianceReportResponse;
import com.optiplant.logistics.service.ComplianceService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF-0043: logistics compliance report by branch and by route for a period of dates in America/Bogota. Administrators
 * consult one branch or the network; managers only their own branch; operators consult their operational records in the
 * transfers instead (§5.2).
 */
@RestController
@RequestMapping("/api/v1/logistics/compliance")
public class ComplianceController {
    static final int MIN_YEAR = 1900;
    static final int MAX_YEAR = 9999;
    private final ComplianceService compliance;
    private final ScopePolicy policy;

    public ComplianceController(ComplianceService compliance, ScopePolicy policy) {
        this.compliance = compliance;
        this.policy = policy;
    }

    @GetMapping
    public ComplianceReportResponse report(@AuthenticationPrincipal SessionPrincipal principal,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to,
                                           @RequestParam(required = false) UUID branch) {
        boolean admin = "ADMIN".equals(principal.role());
        if (!admin && !("MANAGER".equals(principal.role()) && principal.branchExternalId() != null)) {
            throw new ApiException(403, "ROLE_FORBIDDEN", "El reporte de cumplimiento logístico corresponde a gerentes y "
                + "administradores. El operador consulta los tiempos de sus transferencias en Transferencias.");
        }
        if (branch != null) policy.requirePublicReference(branch);
        return compliance.report(principal, date(from, "inicial"), date(to, "final"), branch);
    }

    private static LocalDate date(String value, String which) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, "VALIDATION_FAILED", "Indique la fecha " + which + " del período (AAAA-MM-DD).");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new ApiException(400, "VALIDATION_FAILED", "La fecha " + which + " del período no es válida: use AAAA-MM-DD.");
        }
        // Bounded so that the period's limits always exist as PostgreSQL instants: an extreme year answers 400, not 500.
        if (date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR) {
            throw new ApiException(400, "VALIDATION_FAILED", "La fecha " + which + " del período debe estar entre los años "
                + MIN_YEAR + " y " + MAX_YEAR + ".");
        }
        return date;
    }
}
