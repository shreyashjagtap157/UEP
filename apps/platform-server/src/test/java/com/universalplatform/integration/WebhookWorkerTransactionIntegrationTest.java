package com.universalplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "platform.integrations.webhooks.poll-ms=3600000")
class WebhookWorkerTransactionIntegrationTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @Autowired JdbcTemplate jdbc;
    @Autowired IntegrationSecretCipher cipher;
    @Autowired WebhookWorker worker;

    @BeforeEach
    void seedDueWebhook() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, ?, 'Webhook Transaction Test', 'ACTIVE')",
                tenantId, "webhook-tx-" + tenantId);
        jdbc.update("""
                INSERT INTO webhook_subscription(
                    id, tenant_id, callback_url, event_filter, secret_ciphertext, enabled
                ) VALUES (?, ?, 'http://localhost:8080/webhook', '*', ?, true)
                """, subscriptionId, tenantId, cipher.encrypt("transaction-test-secret"));
        jdbc.update("""
                INSERT INTO webhook_delivery(
                    id, tenant_id, subscription_id, event_id, event_type, payload_json,
                    attempt_count, next_attempt_at
                ) VALUES (?, ?, ?, ?, 'TRANSACTION_TEST', '{}', 0, CURRENT_TIMESTAMP - interval '1 second')
                """, deliveryId, tenantId, subscriptionId, eventId);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
    }

    @Test
    void pollProcessesDeliveryInsideTransactionAndPersistsFailureForRetry() {
        worker.poll();

        Integer attempts = jdbc.queryForObject(
                "SELECT attempt_count FROM webhook_delivery WHERE tenant_id = ? AND id = ?",
                Integer.class, tenantId, deliveryId);
        String lastError = jdbc.queryForObject(
                "SELECT last_error FROM webhook_delivery WHERE tenant_id = ? AND id = ?",
                String.class, tenantId, deliveryId);
        assertThat(attempts).isEqualTo(1);
        assertThat(lastError).contains("public unicast");
    }
}
