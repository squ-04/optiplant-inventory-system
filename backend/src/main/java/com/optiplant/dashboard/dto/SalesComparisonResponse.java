package com.optiplant.dashboard.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sales of one branch in the current month and the three previous ones (RF-0044, INC-018/D01–D02,D07), oldest first,
 * in {@code timeZone}. {@code recordedFrom} is the day the branch was registered: months before it carry no data
 * ({@code dataAvailable} false, counts null), which is different from a real zero. {@code amountsIncluded} is false for the
 * operator, whose local panel has counts and units but no aggregated amounts (INC-003/D04): {@code netAmount} is then null
 * in every month, which is not missing data.
 */
public record SalesComparisonResponse(String timeZone, Instant generatedAt, Branch branch, LocalDate recordedFrom,
                                      boolean amountsIncluded, List<Month> months) {
    /**
     * {@code to} is the last day of a complete month or today for the month in progress. {@code partialHistory} marks the
     * month in which the branch was registered: it does not cover the whole month. {@code netAmount} is the exact sum of
     * the confirmed lines net of discounts, presented with two decimals (HALF_UP). Returns are counted apart, never
     * subtracted from the sales.
     */
    public record Month(String month, LocalDate from, LocalDate to, boolean inProgress, boolean dataAvailable,
                        boolean partialHistory, Integer salesCount, String netAmount, Integer returnsCount,
                        List<ProductSales> products) {}

    /** Quantities in the product's base unit; products are never added together. */
    public record ProductSales(UUID productExternalId, String sku, String name, String baseUnitName,
                               String soldQuantity, String returnedQuantity) {}
}
