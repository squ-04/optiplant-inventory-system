package com.optiplant.dashboard.controller;

import com.optiplant.common.exception.ApiException;
import com.optiplant.common.web.PeriodDate;
import com.optiplant.common.web.PeriodMonth;
import com.optiplant.dashboard.dto.BranchComparisonResponse;
import com.optiplant.dashboard.dto.DemandResponse;
import com.optiplant.dashboard.dto.RestockIndicatorResponse;
import com.optiplant.dashboard.dto.RotationResponse;
import com.optiplant.dashboard.dto.SalesComparisonResponse;
import com.optiplant.dashboard.dto.TransferImpactResponse;
import com.optiplant.dashboard.service.BranchComparisonService;
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
 * Branch dashboard (UC-019): monthly sales comparison (RF-0044), inventory rotation (RF-0045), high and low demand
 * (RF-0046), active transfers (RF-0047) and products about to run out (RF-0048). Every profile reads its own branch; only
 * the administrator chooses another one (§5.2). The network comparison (RF-0049) is exclusive to the administrator.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardService dashboard;
    private final BranchComparisonService comparison;
    private final ScopePolicy policy;

    public DashboardController(DashboardService dashboard, BranchComparisonService comparison, ScopePolicy policy) {
        this.dashboard = dashboard;
        this.comparison = comparison;
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

    @GetMapping("/demand")
    public DemandResponse demand(@AuthenticationPrincipal SessionPrincipal principal,
                                 @RequestParam(required = false) String month,
                                 @RequestParam(required = false) UUID branch) {
        if (branch != null) policy.requirePublicReference(branch);
        return dashboard.demand(principal, branch, PeriodMonth.parse(month));
    }

    @GetMapping("/transfers")
    public TransferImpactResponse transfers(@AuthenticationPrincipal SessionPrincipal principal,
                                            @RequestParam(required = false) UUID branch) {
        if (branch != null) policy.requirePublicReference(branch);
        return dashboard.transfers(principal, branch);
    }

    @GetMapping("/restock")
    public RestockIndicatorResponse restock(@AuthenticationPrincipal SessionPrincipal principal,
                                            @RequestParam(required = false) UUID branch) {
        if (branch != null) policy.requirePublicReference(branch);
        return dashboard.restock(principal, branch);
    }

    @GetMapping("/branch-comparison")
    public BranchComparisonResponse branchComparison(@AuthenticationPrincipal SessionPrincipal principal,
                                                     @RequestParam(required = false) String month) {
        if (!"ADMIN".equals(principal.role())) {
            throw new ApiException(403, "ROLE_FORBIDDEN", "La comparativa entre sucursales corresponde al administrador general.");
        }
        return comparison.compare(principal, PeriodMonth.parse(month));
    }
}
