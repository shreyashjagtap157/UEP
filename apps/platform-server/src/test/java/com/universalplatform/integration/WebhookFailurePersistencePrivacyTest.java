package com.universalplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebhookFailurePersistencePrivacyTest {
    @Test
    void webhookDeliveryCannotPersistRawProviderFailureText() {
        Instant now = Instant.parse("2026-11-02T00:00:00Z");
        WebhookDelivery delivery = new WebhookDelivery(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CLASS_CREATED", "{}", now);

        delivery.failed("POST https://hooks.private.test/events?token=secret-value for learner@example.test",
                now);

        assertThat(delivery.lastError()).isEqualTo("WebhookDeliveryFailure");
        assertThat(delivery.lastError()).doesNotContain(
                "hooks.private.test", "token", "secret-value", "learner@example.test");
    }

    @Test
    void webhookSubscriptionCannotPersistRawProviderFailureText() {
        Instant now = Instant.parse("2026-11-02T00:00:00Z");
        WebhookSubscription subscription = new WebhookSubscription(
                UUID.randomUUID(), UUID.randomUUID(), "https://hooks.private.test/events",
                "*", "encrypted-secret", now);

        subscription.failed(now, "HTTP 500 from hooks.private.test?token=secret-value");

        assertThat(subscription.lastError()).isEqualTo("WebhookSubscriptionFailure");
        assertThat(subscription.lastError()).doesNotContain(
                "hooks.private.test", "token", "secret-value");
    }

    @Test
    void knownSafeWebhookFailureCategoriesRemainOperationallyUseful() {
        Instant now = Instant.parse("2026-11-02T00:00:00Z");
        WebhookDelivery delivery = new WebhookDelivery(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CLASS_CREATED", "{}", now);
        WebhookSubscription subscription = new WebhookSubscription(
                UUID.randomUUID(), UUID.randomUUID(), "https://hooks.example.test/events",
                "*", "encrypted-secret", now);

        delivery.failed("Outbound endpoint validation failed", now);
        subscription.failed(now, "Webhook HTTP 503");

        assertThat(delivery.lastError()).isEqualTo("Outbound endpoint validation failed");
        assertThat(subscription.lastError()).isEqualTo("Webhook HTTP 503");
    }
}
