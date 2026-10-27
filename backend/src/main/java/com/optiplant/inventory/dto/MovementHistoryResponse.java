package com.optiplant.inventory.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Auditable movement history of one branch, newest first (RF-0018). Every entry keeps date, responsible person, reason
 * and quantity; corrections and returns link the movement they compensate. {@code pendingQuantity} is the base quantity
 * still open to a linked return (sale exits) or a downward correction (identified entries), {@code null} otherwise.
 * {@code valuation} is returned to managers and administrators only (RF-0026).
 */
public record MovementHistoryResponse(Branch branch, Instant readAt, List<Entry> items) {
    public record Entry(UUID externalId, UUID operationExternalId, Instant occurredAt, String kind, String direction,
                        UUID productExternalId, String sku, String productName, String unit, String commercialQuantity,
                        String baseQuantity, String baseUnitName, String reason, Responsible responsible,
                        UUID referenceExternalId, String pendingQuantity, Valuation valuation) {}

    public record Responsible(String code, String fullName) {}

    /**
     * Unit cost applied (6 decimals), base value effect and the separately identified difference: {@code FINAL_ROUNDING}
     * for the exit that depletes the stock, {@code HISTORICAL_CORRECTION} for a depleting compensation. Amounts are
     * signed value effects rounded half up to 2 decimals for display; {@code totalAmount} is computed before rounding.
     */
    public record Valuation(String unitCost, String baseAmount, String differenceKind, String differenceAmount,
                            String exactDifferenceAmount, String totalAmount) {}
}
