package com.optiplant.transfers.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import java.time.Instant;
import java.util.List;

/** Transfers of a branch, as origin or destination, or of the network for administrators; newest first. */
public record TransferHistoryResponse(Instant readAt, Branch branch, List<TransferResponse> items) {}
