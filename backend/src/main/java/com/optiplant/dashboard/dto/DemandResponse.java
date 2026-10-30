package com.optiplant.dashboard.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * High and low demand of one branch in one month (RF-0046, INC-018/D04, H v0.15): a relative ranking of the base units
 * sold in confirmed sales, not a threshold or a prediction. {@code dataAvailable} is false, with empty lists, for a month
 * that ended before the branch was registered; {@code partialHistory} marks the month of its registration.
 * {@code withoutSales} lists the products enabled in the branch that sold nothing in the month.
 */
public record DemandResponse(String timeZone, String month, LocalDate from, LocalDate to, boolean inProgress,
                             boolean dataAvailable, boolean partialHistory, Instant generatedAt, Branch branch,
                             List<Ranking> rankings, List<Product> withoutSales) {
    /** One ranking per base unit, because pieces and kilos are not comparable. No product is in both groups. */
    public record Ranking(String baseUnitName, int productsWithSales, List<Item> high, List<Item> low) {}

    public record Item(UUID productExternalId, String sku, String name, String soldQuantity) {}

    public record Product(UUID productExternalId, String sku, String name, String baseUnitName) {}
}
