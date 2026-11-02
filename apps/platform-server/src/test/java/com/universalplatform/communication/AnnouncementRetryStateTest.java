package com.universalplatform.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnnouncementRetryStateTest {
    @Test
    void publicationBackoffDeadLettersAfterTwelveAttemptsAndSupportsReplay() {
        Instant start = Instant.parse("2026-11-02T00:00:00Z");
        Announcement announcement = announcement(start);

        announcement.failPublication(start, "IllegalStateException");
        assertThat(announcement.publishAttemptCount()).isEqualTo(1);
        assertThat(announcement.publishAvailableAt()).isEqualTo(start.plusSeconds(10));
        assertThat(announcement.publishDeadLetteredAt()).isNull();

        long elapsed = 10;
        for (int attempt = 2; attempt <= 12; attempt++) {
            Instant attemptAt = start.plusSeconds(elapsed);
            announcement.failPublication(attemptAt, "IllegalStateException");
            if (attempt == 11) {
                assertThat(announcement.publishAvailableAt()).isEqualTo(attemptAt.plusSeconds(3600));
            }
            if (attempt < 12) elapsed += Math.min(3600L, 5L << Math.min(attempt, 10));
        }

        assertThat(announcement.publishAttemptCount()).isEqualTo(12);
        assertThat(announcement.publishDeadLetteredAt()).isNotNull();
        assertThat(announcement.publishLastError()).isEqualTo("IllegalStateException");

        Instant replayAt = start.plusSeconds(180);
        announcement.requeuePublication(replayAt);
        assertThat(announcement.publishAttemptCount()).isZero();
        assertThat(announcement.publishDeadLetteredAt()).isNull();
        assertThat(announcement.publishLastError()).isNull();
        assertThat(announcement.publishAvailableAt()).isEqualTo(replayAt);
    }

    @Test
    void expiryBackoffDeadLettersAndSanitizesFailureCategory() {
        Instant start = Instant.parse("2026-11-02T00:00:00Z");
        Announcement announcement = announcement(start);
        announcement.publish(start.plusSeconds(1));

        announcement.failExpiry(start.plusSeconds(1), "TransportException");
        assertThat(announcement.expiryAttemptCount()).isEqualTo(1);
        assertThat(announcement.expiryAvailableAt()).isEqualTo(start.plusSeconds(10));
        assertThat(announcement.expiryDeadLetteredAt()).isNull();

        long elapsed = 11;
        for (int attempt = 2; attempt <= 12; attempt++) {
            Instant attemptAt = start.plusSeconds(elapsed);
            announcement.failExpiry(attemptAt, "secret endpoint / token");
            if (attempt == 11) {
                assertThat(announcement.publishAvailableAt()).isEqualTo(attemptAt.plusSeconds(3600));
            }
            if (attempt < 12) elapsed += Math.min(3600L, 5L << Math.min(attempt, 10));
        }

        assertThat(announcement.expiryAttemptCount()).isEqualTo(12);
        assertThat(announcement.expiryDeadLetteredAt()).isNotNull();
        assertThat(announcement.expiryLastError()).isEqualTo("ProcessingFailure");

        Instant replayAt = start.plusSeconds(180);
        announcement.requeueExpiry(replayAt);
        assertThat(announcement.expiryAttemptCount()).isZero();
        assertThat(announcement.expiryDeadLetteredAt()).isNull();
        assertThat(announcement.expiryLastError()).isNull();
        assertThat(announcement.expiryAvailableAt()).isEqualTo(replayAt);
    }

    private static Announcement announcement(Instant now) {
        return new Announcement(UUID.randomUUID(), UUID.randomUUID(), null, "Retry test", "body",
                AnnouncementPriority.NORMAL, now.minusSeconds(1), now.plusSeconds(3600),
                false, "worker-test", now.minusSeconds(10));
    }
}
