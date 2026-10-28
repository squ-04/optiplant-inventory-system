package com.optiplant.purchases.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A purchase order with its supplier, products and conditions (RF-0021-CA-01, RF-0023-CA-01). Decimals are exact
 * strings; amounts are given at 2 decimals half up for display and exactly for traceability. {@code supplier.name} is
 * the historical name kept by the order (RF-0021-CA-03), not the current catalog name. Receipt fields stay null until
 * the receipt is confirmed; {@code events} is null in history listings. {@code replacesOrder} and {@code replacedBy} link
 * a cancelled order with the order created to replace it (RF-0022-CA-02); both stay consultable.
 */
public record PurchaseOrderResponse(UUID externalId, String code, String status, Branch branch, Supplier supplier,
                                    int paymentTermDays, Instant createdAt, Person createdBy, Instant receivedAt,
                                    Person receivedBy, String dueDate, UUID receiptOperationExternalId,
                                    OrderReference replacesOrder, OrderReference replacedBy, String totalNet,
                                    String exactTotalNet, List<Line> lines, List<Event> events) {
    public record Supplier(UUID externalId, String code, String name) {}

    public record OrderReference(UUID externalId, String code) {}

    public record Person(String code, String fullName) {}

    /**
     * {@code unitBaseCost} is the net cost per base unit fixed by the receipt: inventory valuation, null before the
     * receipt and always null for operators (INC-003/D04).
     */
    public record Line(int lineNo, UUID productExternalId, String sku, String productName, String unit, String factor,
                       String commercialQuantity, String baseQuantity, String baseUnitName, String unitPrice,
                       String discountPercent, String netAmount, String exactNetAmount, String unitBaseCost) {}

    /** {@code reason} is the optional reason of a cancellation, null otherwise. */
    public record Event(String fromStatus, String toStatus, Instant occurredAt, Person actor, String reason) {}
}
