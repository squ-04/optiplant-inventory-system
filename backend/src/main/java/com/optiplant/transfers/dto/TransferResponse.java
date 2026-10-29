package com.optiplant.transfers.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
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
 * used with its priority at dispatch time, and each line its dispatched quantity; both are null until dispatched. Costs
 * are not exposed: operators do not consult valuation (INC-003/D04).
 */
public record TransferResponse(UUID externalId, String code, String status, Branch origin, Branch destination,
                               Instant requestedAt, Person requestedBy, Decision approval, Dispatch dispatch, List<Line> lines,
                               List<Event> events) {
    public record Person(String code, String fullName) {}

    public record Decision(Instant occurredAt, Person actor) {}

    public record Dispatch(String carrier, Instant departedAt, Instant estimatedArrivalAt, Person actor, String routeCode,
                           String routePriority) {}

    public record Line(int lineNo, UUID productExternalId, String sku, String productName, String unit, String factor,
                       String baseUnitName, String requestedQuantity, String requestedBaseQuantity, String preparedQuantity,
                       String preparedBaseQuantity, String dispatchedQuantity, String dispatchedBaseQuantity,
                       String originAvailableQuantity, boolean unitDivisible, boolean baseDivisible) {}

    public record Prepared(int lineNo, String quantity, String baseQuantity) {}

    /** {@code preparation} is the snapshot of a preparation event, null for other events. */
    public record Event(String fromStatus, String toStatus, Instant occurredAt, Person actor, String reason,
                        List<Prepared> preparation) {}
}
