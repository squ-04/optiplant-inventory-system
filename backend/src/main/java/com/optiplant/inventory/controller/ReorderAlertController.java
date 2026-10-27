package com.optiplant.inventory.controller;

import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.inventory.dto.ReorderAlertResponse;
import com.optiplant.inventory.service.ReorderAlertService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class ReorderAlertController {
    private final ReorderAlertService alerts;

    public ReorderAlertController(ReorderAlertService alerts) {
        this.alerts = alerts;
    }

    /**
     * RF-0020: active restock alerts ({@code status=ACTIVE}, default) or their whole history ({@code status=ALL}).
     * Managers read their own branch; administrators any branch or, without {@code branch}, the whole network.
     */
    @GetMapping("/reorder-alerts")
    public ReorderAlertResponse list(@AuthenticationPrincipal SessionPrincipal principal,
                                     @RequestParam(required = false) UUID branch,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) Integer limit) {
        return alerts.list(principal, branch, status, limit);
    }
}
