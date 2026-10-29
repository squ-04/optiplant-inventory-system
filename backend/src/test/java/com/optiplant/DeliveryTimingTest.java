package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.logistics.dto.DeliveryTiming;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** RF-0040 (H v0.15): real time from the actual departure to the single confirmed arrival; RF-0043 punctuality. */
class DeliveryTimingTest {
    static Instant bogota(int hour, int minute) {
        return OffsetDateTime.of(2026, 10, 20, hour, minute, 0, 0, ZoneOffset.ofHours(-5)).toInstant();
    }

    @Test void aPartialArrivalKeepsItsOwnDurationAndIsNeverComplete() {
        // RF-0040-CA-02: dispatched at 10:00, single partial reception confirmed at 12:00.
        DeliveryTiming timing = DeliveryTiming.of(bogota(10, 0), bogota(11, 0), bogota(12, 0), false, bogota(15, 0));
        assertThat(timing).isEqualTo(new DeliveryTiming(3600, 7200L, 3600L, "DISCREPANCY", false, false, true));
        // Resolving the alert at 15:00 is not an arrival: a later reading gives the same 2 hours.
        assertThat(DeliveryTiming.of(bogota(10, 0), bogota(11, 0), bogota(12, 0), false, bogota(23, 0))).isEqualTo(timing);
    }

    @Test void aCompleteArrivalIsOnTimeUpToTheEstimatedInstantIncluded() {
        assertThat(DeliveryTiming.of(bogota(10, 0), bogota(12, 0), bogota(12, 0), true, bogota(12, 0)))
            .isEqualTo(new DeliveryTiming(7200, 7200L, 0L, "COMPLETE", true, false, true));
        assertThat(DeliveryTiming.of(bogota(10, 0), bogota(12, 0), bogota(11, 30), true, bogota(12, 0)).onTime()).isTrue();
        DeliveryTiming late = DeliveryTiming.of(bogota(10, 0), bogota(12, 0), bogota(12, 1), true, bogota(13, 0));
        assertThat(late.onTime()).isFalse();
        assertThat(late.deviationSeconds()).isEqualTo(60L);
        // Half a second late is late: the deviation reads 1 second, never 0 next to onTime false.
        DeliveryTiming barely = DeliveryTiming.of(bogota(10, 0), bogota(12, 0), bogota(12, 0).plusMillis(500), true, bogota(13, 0));
        assertThat(barely.onTime()).isFalse();
        assertThat(barely.deviationSeconds()).isEqualTo(1L);
        // Both durations round up the same way, so the deviation always equals real minus estimated.
        assertThat(barely.actualSeconds()).isEqualTo(7201L);
        assertThat(barely.actualSeconds() - barely.estimatedSeconds()).isEqualTo(barely.deviationSeconds());
        DeliveryTiming early43 = DeliveryTiming.of(bogota(10, 0), bogota(10, 0), bogota(10, 0).plusMillis(43_700), true, bogota(13, 0));
        assertThat(early43.actualSeconds()).isEqualTo(44L);
        assertThat(early43.deviationSeconds()).isEqualTo(44L);
        // Half a second early is on time and reads as the estimated instant.
        DeliveryTiming early = DeliveryTiming.of(bogota(10, 0), bogota(12, 0), bogota(12, 0).minusMillis(500), true, bogota(13, 0));
        assertThat(early.onTime()).isTrue();
        assertThat(early.deviationSeconds()).isZero();
        // An early partial arrival is still not compliant.
        assertThat(DeliveryTiming.of(bogota(10, 0), bogota(12, 0), bogota(11, 0), false, bogota(13, 0)).onTime()).isFalse();
    }

    @Test void withoutArrivalNothingIsInventedAndOnlyTheDeadlineIsChecked() {
        DeliveryTiming pending = DeliveryTiming.of(bogota(10, 0), bogota(12, 0), null, false, bogota(12, 0));
        assertThat(pending).isEqualTo(new DeliveryTiming(7200, null, null, null, null, false, true));
        assertThat(DeliveryTiming.of(bogota(10, 0), bogota(12, 0), null, false, bogota(12, 1)).overdue()).isTrue();
    }

    @Test void anArrivalRecordedBeforeTheDepartureIsKeptButNeverOnTime() {
        // Only data corrected outside the application: the reception refuses an arrival before the departure.
        DeliveryTiming timing = DeliveryTiming.of(bogota(10, 0), bogota(12, 0), bogota(9, 58), true, bogota(13, 0));
        assertThat(timing).isEqualTo(new DeliveryTiming(7200, -120L, -7320L, "COMPLETE", false, false, false));
        // The departure itself is a valid arrival instant.
        assertThat(DeliveryTiming.of(bogota(10, 0), bogota(12, 0), bogota(10, 0), true, bogota(13, 0)).consistent()).isTrue();
    }
}
