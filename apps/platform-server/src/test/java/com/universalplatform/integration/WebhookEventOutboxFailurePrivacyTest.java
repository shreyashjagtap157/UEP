package com.universalplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebhookEventOutboxFailurePrivacyTest {
    @Test
    void failedFanoutPersistsOnlyAnExceptionCategory() {
        Instant now = Instant.parse("2026-11-02T00:00:00Z");
        WebhookEventOutbox event = new WebhookEventOutbox(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CLASS_CREATED", "Class created", "Payload", now, now);

        event.failed("POST https://hooks.example.test/events?access_token=secret-value returned 503",
                now, now.plusSeconds(30));

        assertThat(event.attemptCount()).isEqualTo(1);
        assertThat(event.lastError()).isEqualTo("WebhookFanoutFailure");
        assertThat(event.lastError()).doesNotContain(
                "hooks.example.test", "access_token", "secret-value");
    }

    @Test
    void safeWebhookFailureCategoryIsRetained() {
        Instant now = Instant.parse("2026-11-02T00:00:00Z");
        WebhookEventOutbox event = new WebhookEventOutbox(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CLASS_CREATED", "Class created", "Payload", now, now);

        event.failed("HttpTimeoutException", now, now.plusSeconds(30));

        assertThat(event.lastError()).isEqualTo("HttpTimeoutException");
    }
}
