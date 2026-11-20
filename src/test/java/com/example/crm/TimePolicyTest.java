package com.example.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.crm.common.TimePolicy;
import com.example.crm.common.ValidationException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TimePolicyTest {

    @Test
    void almatyIsUtcPlusFive() {
        Instant start = TimePolicy.parseStartAt("start_at", "2030-01-07T09:00:00+05:00");

        assertThat(TimePolicy.format(start)).isEqualTo("2030-01-07T04:00:00Z");
    }

    @Test
    void seventeenOClockFitsButSeventeenThirtyDoesNot() {
        Instant five = Instant.parse("2030-01-07T12:00:00Z");
        Instant fiveThirty = Instant.parse("2030-01-07T12:30:00Z");

        assertThat(TimePolicy.workingHoursViolation(five, five.plus(TimePolicy.MEETING_DURATION))).isEmpty();
        assertThat(TimePolicy.workingHoursViolation(fiveThirty, fiveThirty.plus(TimePolicy.MEETING_DURATION)))
                .hasValueSatisfying(message -> assertThat(message).contains("17:30-18:30"));
    }

    @Test
    void localDayBoundsAreConvertedToUtc() {
        TimePolicy.DayBounds bounds = TimePolicy.dayBoundsUtc(LocalDate.parse("2030-01-07"));

        assertThat(bounds.start()).isEqualTo(Instant.parse("2030-01-06T19:00:00Z"));
        assertThat(bounds.end()).isEqualTo(Instant.parse("2030-01-07T19:00:00Z"));
    }

    @Test
    void naiveDateTimeIsRejected() {
        assertThatThrownBy(() -> TimePolicy.parseStartAt("start_at", "2030-01-07T10:00:00"))
                .isInstanceOf(ValidationException.class);
    }
}
