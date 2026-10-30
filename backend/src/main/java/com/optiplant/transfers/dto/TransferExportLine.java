package com.optiplant.transfers.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One transfer line for the exportable report (RF-0054). The received quantity comes from the single confirmed arrival.
 * {@code pendingDifference} is dispatched minus received once the arrival exists, and zero after the transit was written
 * off; {@code discrepancyStatus} and {@code treatment} are null when they do not apply.
 */
public record TransferExportLine(String transferCode, String originCode, String originName, String destinationCode,
                                 String destinationName, Instant requestedAt, Instant dispatchedAt, Instant arrivedAt,
                                 String status, String sku, String unit, BigDecimal requestedQuantity,
                                 BigDecimal dispatchedQuantity, BigDecimal receivedQuantity, BigDecimal pendingDifference,
                                 String discrepancyStatus, String treatment, boolean transitLoss) {}
