package com.optiplant.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One movement for the exportable report (RF-0052): no costs and no internal keys. {@code sourceDocument} is null for a
 * movement without a document, such as a manual entry.
 */
public record MovementExportRow(UUID externalId, Instant occurredAt, String branchCode, String branchName, String sku,
                                String productName, String kind, String reason, String direction, BigDecimal quantity,
                                String unit, String actorCode, String actorName, String sourceDocument) {}
