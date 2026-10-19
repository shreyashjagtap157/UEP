package com.universalplatform.academicreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real review entities' state transitions (package-private production classes)
 * instead of local helper clones.
 */
class AcademicReviewInvariantTest {
    private final Instant now = Instant.parse("2026-10-18T09:00:00Z");

    @Test
    void newCasesOpenAtVersionZeroWithoutTargetAnswer() {
        ReviewCase review = new ReviewCase(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, ReviewType.GRADE_CHALLENGE, "Score looks wrong",
                "Please recheck question 3", now);
        assertEquals(ReviewStatus.OPEN, review.status());
        assertEquals(0, review.version());
        assertNull(review.targetAnswerId());
        assertEquals(now, review.createdAt());
        assertEquals(now, review.updatedAt());
    }

    @Test
    void statusTransitionsBumpTheAuditVersionAndUpdateTimestamp() {
        ReviewCase review = new ReviewCase(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), ReviewType.ANSWER_REVISION, "Wrong answer",
                "Payload changed under auto-grading", now);
        review.status(ReviewStatus.UNDER_REVIEW, now.plusSeconds(60));
        assertEquals(ReviewStatus.UNDER_REVIEW, review.status());
        assertEquals(1, review.version());
        assertEquals(now.plusSeconds(60), review.updatedAt());
        review.status(ReviewStatus.RESOLVED, now.plusSeconds(120));
        assertEquals(ReviewStatus.RESOLVED, review.status());
        assertEquals(2, review.version());
    }

    @Test
    void answerRevisionsStartProposedAndRecordTheirNumber() {
        AnswerRevision revision = new AnswerRevision(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, "{\"answer\":\"A\"}", "student", now);
        assertEquals(AnswerRevisionStatus.PROPOSED, revision.status());
        assertEquals(1, revision.revisionNumber());
        revision.status(AnswerRevisionStatus.ACCEPTED);
        assertEquals(AnswerRevisionStatus.ACCEPTED, revision.status());
        revision.status(AnswerRevisionStatus.REJECTED);
        assertEquals(AnswerRevisionStatus.REJECTED, revision.status());
    }
}
