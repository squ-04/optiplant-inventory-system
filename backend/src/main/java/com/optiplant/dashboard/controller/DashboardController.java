package com.optiplant.dashboard.controller;

import com.optiplant.common.web.PeriodDate;
import com.optiplant.dashboard.dto.RotationResponse;
import com.optiplant.dashboard.dto.SalesComparisonResponse;
import com.optiplant.dashboard.service.DashboardService;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Branch dashboard (UC-019): monthly sales comparison (RF-0044) and inventory rotation (RF-0045). Every profile reads its
 * own branch; only the administrator chooses another one (§5.2). The network comparison is not part of these indicators.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardService dashboard;
    private final ScopePolicy policy;

    public DashboardController(DashboardService dashboard, ScopePolicy policy) {
        this.dashboard = dashboard;
        this.policy = policy;
    }

    @GetMapping("/sales-comparison")
    public SalesComparisonResponse sales(@AuthenticationPrincipal SessionPrincipal principal,
                                         @RequestParam(required = false) UUID branch) {
        if (branch != null) policy.requirePublicReference(branch);
        return dashboard.sales(principal, branch);
    }

    @GetMapping("/rotation")
    public RotationResponse rotation(@AuthenticationPrincipal SessionPrincipal principal,
                                     @RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to,
                                     @RequestParam(required = false) UUID branch) {
        if (branch != null) policy.requirePublicReference(branch);
        return dashboard.rotation(principal, branch, PeriodDate.parse(from, "inicial"), PeriodDate.parse(to, "final"));
    }
}
