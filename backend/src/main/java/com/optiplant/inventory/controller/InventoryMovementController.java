package com.optiplant.inventory.controller;

import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.inventory.dto.ManualMovementRequest;
import com.optiplant.inventory.dto.ManualMovementResponse;
import com.optiplant.inventory.dto.MovementHistoryResponse;
import com.optiplant.inventory.service.ManualMovementService;
import com.optiplant.inventory.service.MovementHistoryService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryMovementController {
    private final ManualMovementService movements;
    private final MovementHistoryService history;

    public InventoryMovementController(ManualMovementService movements, MovementHistoryService history) {
        this.movements = movements;
        this.history = history;
    }

    /**
     * RF-0016/RF-0017: registers a manual entry or exit in the session's own branch. A repeated request with the same
     * {@code Idempotency-Key} returns the original result without new effects.
     */
    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public ManualMovementResponse register(@AuthenticationPrincipal SessionPrincipal principal,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                           @RequestBody JsonNode body) {
        return movements.register(principal, key, ManualMovementRequest.from(body));
    }

    /** RF-0018: movement history of a branch, newest first, optionally limited to one product. */
    @GetMapping("/branches/{branch}/movements")
    public MovementHistoryResponse history(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID branch,
                                           @RequestParam(required = false) UUID product,
                                           @RequestParam(required = false) Integer limit) {
        return history.history(principal, branch, product, limit);
    }
}
