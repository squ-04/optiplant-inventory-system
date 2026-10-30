package com.optiplant.transfers.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import java.time.Instant;
import java.util.List;

/**
 * Transfers of a branch, as origin or destination, or of the network for administrators; newest first.
 * {@code total} counts every matching transfer; {@code items} holds the newest ones up to the limit.
 */
public record TransferHistoryResponse(Instant readAt, Branch branch, int total, List<TransferResponse> items) {}
