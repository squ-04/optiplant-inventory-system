package com.optiplant.dashboard.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Active transfers of one branch and their effect on inventory (RF-0047, INC-018/D05), newest first. Active means the open
 * states of §5.1: requested, preparing, approved, in transit, and received with discrepancy while its alert is open or
 * something is still pending in transit. {@code role} is ORIGIN or DESTINATION for the consulted branch.
 * {@code alertStatus} (OPEN, RESOLVED) and {@code treatment} are the shortage alert: they never change a quantity.
 */
public record TransferImpactResponse(Instant generatedAt, Branch branch, List<Item> items) {
    public record Item(UUID externalId, String code, String status, String role, Branch counterpart, Instant requestedAt,
                       Instant estimatedArrivalAt, String alertStatus, String treatment, List<Line> lines) {}

    /**
     * Quantities in the product's base unit; each is null in the states where it does not apply, so none of them can be
     * added to another. {@code forecastQuantity}: requested or prepared before dispatch, no reservation and no effect on
     * stock. {@code inTransitQuantity}: dispatched, out of the origin's physical stock and not yet in the destination's.
     * {@code receivedQuantity}: already in the destination's physical stock. {@code pendingShortageQuantity}: the part of
     * the pending transit that did not arrive. {@code lostQuantity}: the transit loss written off, apart from the reception.
     */
    public record Line(UUID productExternalId, String sku, String name, String baseUnitName, String forecastQuantity,
                       String inTransitQuantity, String receivedQuantity, String pendingShortageQuantity,
                       String lostQuantity) {}
}
