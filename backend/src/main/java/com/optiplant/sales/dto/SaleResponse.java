package com.optiplant.sales.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The internal record of a confirmed sale (RF-0031, INC-014/D06): public identifier and code, date, branch, responsible
 * and lines with the commercial snapshots kept at confirmation (SKU, product and unit names, list name and gross price),
 * so later catalog or price changes never alter it. It is never edited. Decimals are exact strings; amounts are given at
 * 2 decimals half up for display and exactly for traceability. The valuation cost of each exit is not returned
 * (INC-003/D04). {@code operationExternalId} is the inventory withdrawal of the sale; each line names its exit movement,
 * which a return references (RF-0027-CA-02).
 */
public record SaleResponse(UUID externalId, String code, Branch branch, Instant confirmedAt, Person confirmedBy,
                           UUID operationExternalId, String grossTotal, String discountTotal, String totalNet,
                           String exactTotalNet, List<Line> lines) {
    public record Person(String code, String fullName) {}

    /**
     * {@code unitPrice} is the gross list price per commercial unit and {@code netUnitPrice} the exact price after the
     * line discount (RF-0029-CA-02).
     */
    public record Line(int lineNo, UUID productExternalId, String sku, String productName, String unit, String factor,
                       String commercialQuantity, String baseQuantity, String baseUnitName, UUID priceListExternalId,
                       String priceListName, String unitPrice, String discountPercent, String netUnitPrice,
                       String netAmount, String exactNetAmount, UUID movementExternalId) {}
}
