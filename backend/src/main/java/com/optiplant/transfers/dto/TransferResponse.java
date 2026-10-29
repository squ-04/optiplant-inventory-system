package com.optiplant.transfers.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.logistics.dto.DeliveryTiming;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A transfer with its origin, destination, lines and state history (RF-0032–RF-0035). Decimals are exact strings.
 * Each line keeps the requested and the prepared quantities apart (RF-0033-CA-02); the prepared ones are null until the
 * origin prepares the transfer. {@code originAvailableQuantity} is the origin's current physical stock in base units
 * (no reservations, glossary §5.3), returned only to readers of the origin branch and administrators; it is null for the
 * destination. {@code unitDivisible} and {@code baseDivisible} let the client apply the preparation's unit rules
 * before sending. {@code approval} is the approval event (RF-0034-CA-01), null until approved. {@code events} is null in
 * listings. {@code dispatch} (RF-0035-CA-01) holds the carrier, the actual departure, the estimated arrival and the route
 * used with its priority at dispatch time, and each line its dispatched quantity; both are null until dispatched.
 * {@code arrival} (RF-0036/RF-0037) is the single confirmed arrival, and each line its received and missing quantities;
 * null until received. {@code timing} (RF-0040) compares the estimated and the real delivery time, null until dispatched.
 * {@code inTransitBaseQuantity} is what is still pending in transit for a dispatched line.
 * {@code discrepancy} (RF-0038/RF-0039) is the shortage alert of a received-with-discrepancy transfer, with its treatment
 * and the transit loss registered apart; its {@code resends} (detail only) are the new transfers that reference it.
 * {@code sourceDiscrepancy} is the shortage a resend refers to. Costs are not exposed: operators do not consult
 * valuation (INC-003/D04).
 */
public record TransferResponse(UUID externalId, String code, String status, Branch origin, Branch destination,
                               Instant requestedAt, Person requestedBy, Decision approval, Dispatch dispatch, Decision arrival,
                               DeliveryTiming timing, Discrepancy discrepancy, SourceDiscrepancy sourceDiscrepancy,
                               List<Line> lines, List<Event> events) {
    public record Person(String code, String fullName) {}

    public record Decision(Instant occurredAt, Person actor) {}

    public record Dispatch(String carrier, Instant departedAt, Instant estimatedArrivalAt, Person actor, String routeCode,
                           String routePriority) {}

    public record Line(int lineNo, UUID productExternalId, String sku, String productName, String unit, String factor,
                       String baseUnitName, String requestedQuantity, String requestedBaseQuantity, String preparedQuantity,
                       String preparedBaseQuantity, String dispatchedQuantity, String dispatchedBaseQuantity,
                       String originAvailableQuantity, boolean unitDivisible, boolean baseDivisible, String receivedQuantity,
                       String receivedBaseQuantity, String missingQuantity, String missingBaseQuantity,
                       String inTransitBaseQuantity) {}

    /** {@code treatment}, {@code resolvedAt} and {@code resolvedBy} are null while the alert is open. */
    public record Discrepancy(UUID externalId, String status, Instant openedAt, String treatment, Instant resolvedAt,
                              Person resolvedBy, TransitLoss transitLoss, List<Reference> resends) {}

    /** The authorized write-off of the pending transit, registered apart from the alert. */
    public record TransitLoss(Instant occurredAt, Person authorizedBy, String reason, String evidence) {}

    public record Reference(UUID externalId, String code, String status) {}

    public record SourceDiscrepancy(UUID externalId, UUID transferExternalId, String transferCode) {}

    public record Prepared(int lineNo, String quantity, String baseQuantity) {}

    /** {@code preparation} is the snapshot of a preparation event, null for other events. */
    public record Event(String fromStatus, String toStatus, Instant occurredAt, Person actor, String reason,
                        List<Prepared> preparation) {}
}
