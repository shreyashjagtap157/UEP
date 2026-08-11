package com.universalplatform.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecurrenceExpanderTest {
    private final RecurrenceExpander expander = new RecurrenceExpander();

    @Test
    void weeklyRecurrenceMaterializesDeterministically() {
        var items = expander.expand(
                LocalDateTime.of(2026, 9, 1, 10, 0), 60, "UTC",
                RecurrenceFrequency.WEEKLY, 1, Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                null, null, 4);

        assertThat(items).hasSize(4);
        assertThat(items.get(0).startsAt().toString()).isEqualTo("2026-09-01T10:00:00Z");
        assertThat(items.get(3).startsAt().toString()).isEqualTo("2026-09-10T10:00:00Z");
    }

    @Test
    void rejectsNonexistentLocalTimeAcrossDstGap() {
        assertThatThrownBy(() -> expander.expand(
                LocalDateTime.of(2026, 3, 8, 2, 30), 60, "America/New_York",
                RecurrenceFrequency.NONE, 1, Set.of(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }
}
