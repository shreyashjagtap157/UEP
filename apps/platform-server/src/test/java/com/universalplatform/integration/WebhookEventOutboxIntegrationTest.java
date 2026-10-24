package com.universalplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "platform.integrations.webhooks.poll-ms=3600000",
        "platform.integrations.webhooks.fanout-poll-ms=3600000"
})
class WebhookEventOutboxIntegrationTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();
    private final UUID sourceOutboxId = UUID.randomUUID();
    private final UUID aggregateId = UUID.randomUUID();

    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired WebhookEventFanoutWorker fanoutWorker;
    @Autowired IntegrationSecretCipher cipher;

    @BeforeEach
    void seedTenantAndSubscription() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, ?, 'Webhook Outbox Test', 'ACTIVE')",
                tenantId, "webhook-outbox-" + tenantId);
        jdbc.update("""
                INSERT INTO webhook_subscription(
                    id, tenant_id, callback_url, event_filter, secret_ciphertext, enabled
                ) VALUES (?, ?, 'https://hooks.example.com/uep', '*', ?, true)
                """, subscriptionId, tenantId, cipher.encrypt("test-webhook-secret"));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
    }

    @Test
    void notificationEventIsDurablyFannedOutExactlyOnce() {
        var event = new com.universalplatform.notification.NotificationPublishedEvent(
                tenantId, sourceOutboxId, aggregateId, "CLASS_CREATED",
                "Class created", "A class was created", Instant.parse("2026-10-24T10:00:00Z"));
        eventPublisher.publishEvent(event);
        eventPublisher.publishEvent(event);

        Integer queued = jdbc.queryForObject("""
                SELECT count(*) FROM webhook_event_outbox
                WHERE tenant_id = ? AND source_outbox_id = ?
                """, Integer.class, tenantId, sourceOutboxId);
        assertThat(queued).isEqualTo(1);

        UUID fanoutId = jdbc.queryForObject("""
                SELECT id FROM webhook_event_outbox
                WHERE tenant_id = ? AND source_outbox_id = ?
                """, UUID.class, tenantId, sourceOutboxId);
        fanoutWorker.processOne(fanoutId);
        fanoutWorker.processOne(fanoutId);

        Integer deliveries = jdbc.queryForObject("""
                SELECT count(*) FROM webhook_delivery
                WHERE tenant_id = ? AND subscription_id = ? AND event_id = ?
                """, Integer.class, tenantId, subscriptionId, sourceOutboxId);
        assertThat(deliveries).isEqualTo(1);

        Instant processedAt = jdbc.queryForObject("""
                SELECT processed_at FROM webhook_event_outbox
                WHERE tenant_id = ? AND id = ?
                """, Instant.class, tenantId, fanoutId);
        assertThat(processedAt).isNotNull();
    }
}
