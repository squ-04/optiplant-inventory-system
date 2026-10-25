package com.optiplant.dashboard.controller;

import com.optiplant.dashboard.dto.VisibilityResponse;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.dashboard.dto.VisibilityResponse.InventoryItem;
import com.optiplant.dashboard.service.VisibilityService;
import com.optiplant.identity.service.SessionPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/visibility")
public class VisibilityController {
    private final VisibilityService service;
    public VisibilityController(VisibilityService service) { this.service = service; }
    @GetMapping("/branches") public List<Branch> branches() { return service.branches(); }
    @GetMapping("/branches/{id}") public VisibilityResponse history(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID id) {
        return service.history(principal, id);
    }
    @GetMapping("/inventory/{id}") public List<InventoryItem> inventory(@PathVariable UUID id) { return service.inventory(id); }
}
