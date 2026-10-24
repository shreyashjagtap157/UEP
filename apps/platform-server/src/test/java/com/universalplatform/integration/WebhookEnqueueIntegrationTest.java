package com.universalplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "platform.integrations.webhooks.poll-ms=3600000")
class WebhookEnqueueIntegrationTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @Autowired JdbcTemplate jdbc;
    @Autowired WebhookService webhooks;

    @BeforeEach
    void seedSubscription() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, ?, 'Webhook Enqueue Test', 'ACTIVE')",
                tenantId, "webhook-enqueue-" + tenantId);
        jdbc.update("""
                INSERT INTO webhook_subscription(
                    id, tenant_id, callback_url, event_filter, secret_ciphertext, enabled
                ) VALUES (?, ?, 'https://hooks.example.com/uep', '*', 'test-secret-ciphertext', true)
                """, subscriptionId, tenantId);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
    }

    @Test
    void duplicateEventPublicationCreatesOneDeliveryWithoutHidingDatabaseErrors() {
        webhooks.publishForTenant(tenantId, eventId, "CLASS_CREATED", Map.of("classId", "class-1"));
        webhooks.publishForTenant(tenantId, eventId, "CLASS_CREATED", Map.of("classId", "class-1"));

        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM webhook_delivery
                WHERE tenant_id = ? AND subscription_id = ? AND event_id = ?
                """, Integer.class, tenantId, subscriptionId, eventId);
        assertThat(count).isEqualTo(1);
        String eventType = jdbc.queryForObject("""
                SELECT event_type FROM webhook_delivery
                WHERE tenant_id = ? AND subscription_id = ? AND event_id = ?
                """, String.class, tenantId, subscriptionId, eventId);
        assertThat(eventType).isEqualTo("CLASS_CREATED");
    }
}
