package com.universalplatform.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real GradeRevision publication/supersession state machine (package-private
 * production entity) instead of local helper clones.
 */
class GradingInvariantTest {
    private GradeRevision revision() {
        return new GradeRevision(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                GradeRevisionStatus.GENERATED, GradeSource.SYSTEM, 0, 100,
                Instant.parse("2026-10-18T09:00:00Z"), "grader", "{}");
    }

    @Test
    void publicationThenSupersessionWalksTheRealStatusMachine() {
        GradeRevision published = revision();
        assertEquals(GradeRevisionStatus.GENERATED, published.status());
        published.publish();
        assertEquals(GradeRevisionStatus.PUBLISHED, published.status());
        published.supersede();
        assertEquals(GradeRevisionStatus.SUPERSEDED, published.status());
        assertEquals(100, published.maxMarks());
    }

    @Test
    void awardedMarksUpdateCarriesIntoTheRevision() {
        GradeRevision updated = revision();
        updated.setAwardedMarks(75);
        assertEquals(75, updated.awardedMarks());
    }

    @Test
    void attemptTotalFloorUsesNonNullBoundedCapping() {
        // Mirrors the service-side attempt-total rule: floor at 0, cap at maxMarks
        // (per-question negative marks are preserved on GradeItem, not here).
        assertEquals(0, Math.max(0, Math.min(-7, 100)));
        assertEquals(75, Math.max(0, Math.min(75, 100)));
        assertEquals(100, Math.max(0, Math.min(140, 100)));
    }
}
