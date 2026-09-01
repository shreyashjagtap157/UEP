package com.universalplatform.assessment;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AssessmentInvariantTest {
    @Test
    void availabilityWindowMustBeOrdered() {
        assertThrows(IllegalArgumentException.class,
                () -> validate(Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T09:00:00Z")));
    }

    @Test
    void passMarksCannotExceedTotalMarks() {
        assertThrows(IllegalArgumentException.class, () -> marks(10, 11));
    }

    @Test
    void durationCannotBeBelowMinimum() {
        assertThrows(IllegalArgumentException.class, () -> duration(29));
    }

    private static void validate(Instant from, Instant until) {
        if (from == null || until == null || !from.isBefore(until)) throw new IllegalArgumentException();
    }

    private static void marks(int total, int pass) {
        if (total < 1 || pass < 0 || pass > total) throw new IllegalArgumentException();
    }

    private static void duration(int seconds) {
        if (seconds < 30) throw new IllegalArgumentException();
    }
}
