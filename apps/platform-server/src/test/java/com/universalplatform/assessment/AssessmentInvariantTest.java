package com.universalplatform.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Exercises real assessment-module classes (entities and the packet redaction helper) instead of
 * local clones of production logic.
 */
class AssessmentInvariantTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void assessmentLifecycleMovesFromDraftThroughPublishedToClosed() {
        Instant now = Instant.parse("2026-10-18T09:00:00Z");
        Assessment assessment = new Assessment(UUID.randomUUID(), UUID.randomUUID(), "Midterm", now);
        assertEquals(AssessmentStatus.DRAFT, assessment.status());
        assessment.publish(now);
        assertEquals(AssessmentStatus.PUBLISHED, assessment.status());
        assessment.close(now);
        assertEquals(AssessmentStatus.CLOSED, assessment.status());
    }

    @Test
    void attemptStartsInProgressAndRecordsSubmissionState() {
        Instant now = Instant.parse("2026-10-18T09:00:00Z");
        Attempt attempt = new Attempt(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                now, now.plusSeconds(3600), 1);
        assertEquals(AttemptStatus.IN_PROGRESS, attempt.status());
        assertEquals(1, attempt.attemptNumber());
        attempt.submit(now.plusSeconds(1800), AttemptStatus.SUBMITTED);
        assertEquals(AttemptStatus.SUBMITTED, attempt.status());
        assertEquals(now.plusSeconds(1800), attempt.submittedAt());
        attempt.submit(now.plusSeconds(1900), AttemptStatus.EXPIRED);
        assertEquals(AttemptStatus.EXPIRED, attempt.status());
        assertEquals(now.plusSeconds(1900), attempt.submittedAt());
    }

    @Test
    void answerUpdateAdvancesServerSequenceAndPayload() {
        Instant now = Instant.parse("2026-10-18T09:00:00Z");
        Answer answer = new Answer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "{\"answer\":\"A\"}", 1, "idem-1", now);
        assertEquals(1, answer.serverSequence());
        answer.update("{\"answer\":\"B\"}", 5, now.plusSeconds(30));
        assertEquals(5, answer.serverSequence());
        assertEquals("{\"answer\":\"B\"}", answer.payloadJson());
    }

    @Test
    void packetRedactionStripsAnswerKeysAtAnyDepth() throws Exception {
        String payload = "{\"question\":\"Pick one\",\"correctAnswer\":\"A\",\"correctOptionId\":2,"
                + "\"nested\":{\"hint\":\"n\",\"correctOptionIds\":[\"A\",\"B\"]},\"order\":[\"x\",\"y\"],"
                + "\"matching\":{\"matches\":{}}}";
        JsonNode redacted = mapper.readTree(AssessmentService.redactQuestionPayload(mapper, payload));
        assertFalse(redacted.has("correctAnswer"));
        assertFalse(redacted.has("correctOptionId"));
        assertFalse(redacted.has("order"));
        assertTrue(redacted.has("question"));
        assertTrue(redacted.get("nested").has("hint"));
        assertFalse(redacted.get("nested").has("correctOptionIds"));
        assertFalse(redacted.get("matching").has("matches"));
    }

    @Test
    void packetRedactionKeepsNonSecretFieldsIntact() throws Exception {
        String payload = "{\"question\":\"Hello\",\"solutionsHint\":\"x\"}";
        JsonNode redacted = mapper.readTree(AssessmentService.redactQuestionPayload(mapper, payload));
        assertTrue(redacted.has("question"));
        assertFalse(redacted.has("solutionsHint"));
    }

    @Test
    void unparseablePacketPayloadRedactsToEmptyObjectInsteadOfLeaking() {
        assertEquals("{}", AssessmentService.redactQuestionPayload(mapper, "not json"));
        assertEquals("{}", AssessmentService.redactQuestionPayload(mapper, null));
        assertEquals("{}", AssessmentService.redactQuestionPayload(mapper, "  "));
    }
}
