package com.optiplant.sales.controller;

import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.sales.dto.SaleHistoryResponse;
import com.optiplant.sales.dto.SaleRequest;
import com.optiplant.sales.dto.SaleResponse;
import com.optiplant.sales.service.SaleQueryService;
import com.optiplant.sales.service.SaleService;
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

/**
 * Sales (RF-0027–RF-0029, RF-0031). A sale is created confirmed and its record is never edited or deleted: there are no
 * update or DELETE operations. The confirmation takes an {@code Idempotency-Key}: repeating it with the same key returns
 * the original record without a second withdrawal.
 */
@RestController
@RequestMapping("/api/v1/sales")
public class SaleController {
    private final SaleService sales;
    private final SaleQueryService records;

    public SaleController(SaleService sales, SaleQueryService records) {
        this.sales = sales;
        this.records = records;
    }

    /** RF-0027/RF-0028/RF-0029: validates stock and confirms the sale, withdrawing it through the inventory ledger. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponse confirm(@AuthenticationPrincipal SessionPrincipal principal,
                                @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                @RequestBody(required = false) JsonNode body) {
        return sales.confirm(principal, key, SaleRequest.from(body));
    }

    /** RF-0031: confirmed sale records of a branch or, for administrators, of the network; newest first. */
    @GetMapping
    public SaleHistoryResponse history(@AuthenticationPrincipal SessionPrincipal principal,
                                       @RequestParam(required = false) UUID branch,
                                       @RequestParam(required = false) Integer limit) {
        return records.history(principal, branch, limit);
    }

    /** RF-0031: the immutable record of one sale. */
    @GetMapping("/{sale}")
    public SaleResponse detail(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID sale) {
        return records.detail(principal, sale);
    }
}
