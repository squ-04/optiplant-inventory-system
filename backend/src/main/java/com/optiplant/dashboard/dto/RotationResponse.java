package com.optiplant.dashboard.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Inventory rotation by product of one branch (RF-0045, INC-018/D03, H v0.15): units sold in the period / time-weighted
 * daily average of the physical quantity in the period, rebuilt from the inventory ledger. The period runs from the start
 * of {@code from} to the end of {@code to} in {@code timeZone}, or until {@code measuredUntil} when it includes today.
 */
public record RotationResponse(String timeZone, LocalDate from, LocalDate to, Instant measuredUntil, Instant generatedAt,
                               Branch branch, List<Item> items) {
    /**
     * {@code status}: CALCULATED, INSUFFICIENT_HISTORY when the ledger of the product in the branch starts after the
     * period's start ({@code historyStartedAt}), ZERO_AVERAGE when the average is zero, or INCONSISTENT_HISTORY when the
     * rebuilt quantity would be negative (a ledger that does not match the balance). {@code averageQuantity} has
     * three decimals and is null without enough history or with an inconsistent one; {@code rotation} has two decimals and is null («No calculable»)
     * unless CALCULATED. Quantities are in the product's base unit.
     */
    public record Item(UUID productExternalId, String sku, String name, String baseUnitName, Instant historyStartedAt,
                       String soldQuantity, String averageQuantity, String rotation, String status) {}
}
