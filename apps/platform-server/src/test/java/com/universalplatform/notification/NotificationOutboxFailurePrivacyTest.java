package com.universalplatform.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationOutboxFailurePrivacyTest {
    @Test
    void failedOutboxFailurePersistsOnlyAnExceptionCategory() {
        Instant now = Instant.parse("2026-11-02T00:00:00Z");
        NotificationOutbox event = event(now);

        event.failed("Database error at db.private.test for tenant learner@example.test; password=secret-value",
                now, now.plusSeconds(30));

        assertThat(event.attemptCount()).isEqualTo(1);
        assertThat(event.lastError()).isEqualTo("NotificationOutboxFailure");
        assertThat(event.lastError()).doesNotContain(
                "db.private.test", "learner@example.test", "password", "secret-value");
    }

    @Test
    void safeOutboxFailureCategoryIsRetained() {
        Instant now = Instant.parse("2026-11-02T00:00:00Z");
        NotificationOutbox event = event(now);

        event.failed("IllegalArgumentException", now, now.plusSeconds(30));

        assertThat(event.lastError()).isEqualTo("IllegalArgumentException");
    }

    private static NotificationOutbox event(Instant now) {
        UUID aggregateId = UUID.randomUUID();
        return new NotificationOutbox(
                UUID.randomUUID(), UUID.randomUUID(), NotificationEventType.CLASS_SCHEDULED,
                "class_session", aggregateId, "Class scheduled", "Class details",
                NotificationPriority.NORMAL, "class_session", aggregateId,
                "notification-outbox-privacy:" + aggregateId, now, now);
    }
}
