package com.optiplant.logistics.dto;

import java.time.Duration;
import java.time.Instant;

/**
 * Estimated versus real delivery time of one dispatched transfer (RF-0040, H v0.15). The real time runs from the actual
 * departure to the single arrival confirmed at reception; it is null while nothing has arrived, never invented. A partial
 * arrival keeps its own duration with {@code delivery} DISCREPANCY and is never presented as complete. Resolving the
 * shortage alert is not an arrival and does not change these values. All durations are exact seconds;
 * {@code deviationSeconds} is the arrival minus the estimated arrival (positive when late). {@code onTime} (RF-0043) is
 * true only for a complete reception not later than the estimated arrival, false for any other arrival and null without
 * one. {@code overdue} marks a transfer still without arrival whose estimated arrival has passed. {@code consistent} is
 * false when the recorded arrival precedes the departure: the reception refuses that case, so it only comes from data
 * corrected outside the application; its negative duration is kept as recorded and it never counts as on time.
 */
public record DeliveryTiming(long estimatedSeconds, Long actualSeconds, Long deviationSeconds, String delivery,
                             Boolean onTime, boolean overdue, boolean consistent) {
    public static final String COMPLETE = "COMPLETE";
    public static final String DISCREPANCY = "DISCREPANCY";

    /**
     * The timing of a dispatch; {@code complete} tells a complete reception from one received with discrepancy and is
     * ignored without arrival. {@code now} is the server time of the reading.
     */
    public static DeliveryTiming of(Instant departedAt, Instant estimatedArrivalAt, Instant arrivedAt, boolean complete,
                                    Instant now) {
        long estimated = Duration.between(departedAt, estimatedArrivalAt).toSeconds();
        if (arrivedAt == null) {
            return new DeliveryTiming(estimated, null, null, null, null, now.isAfter(estimatedArrivalAt), true);
        }
        boolean late = arrivedAt.isAfter(estimatedArrivalAt);
        boolean consistent = !arrivedAt.isBefore(departedAt);
        return new DeliveryTiming(estimated, Duration.between(departedAt, arrivedAt).toSeconds(),
            Duration.between(estimatedArrivalAt, arrivedAt).toSeconds(), complete ? COMPLETE : DISCREPANCY,
            complete && consistent && !late, false, consistent);
    }
}
