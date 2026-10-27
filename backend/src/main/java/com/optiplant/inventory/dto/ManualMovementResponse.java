package com.optiplant.inventory.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Confirmed manual movement (RF-0016/RF-0017). Quantities are exact decimal strings; the resulting physical stock is in
 * base units. Valuation is not returned here: operators do not consult costs (RF-0026).
 */
public record ManualMovementResponse(UUID operationExternalId, UUID movementExternalId, Instant occurredAt, String kind,
                                     String direction, UUID productExternalId, String sku, String productName, String unit,
                                     String commercialQuantity, String baseQuantity, String baseUnitName, String reason,
                                     String responsibleCode, UUID referenceExternalId, String resultingPhysicalQuantity) {}
