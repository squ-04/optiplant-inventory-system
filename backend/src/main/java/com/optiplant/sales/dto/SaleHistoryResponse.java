package com.optiplant.sales.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import java.time.Instant;
import java.util.List;

/**
 * RF-0031: confirmed sale records of one branch, or of the whole network when {@code branch} is null, newest first.
 * {@code total} counts every matching sale; {@code items} holds the newest ones up to the limit.
 */
public record SaleHistoryResponse(Instant readAt, Branch branch, int total, List<SaleResponse> items) {}
