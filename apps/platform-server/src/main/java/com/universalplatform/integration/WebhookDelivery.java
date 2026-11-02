package com.universalplatform.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "webhook_delivery", uniqueConstraints = @UniqueConstraint(
        name = "uq_webhook_event_subscription", columnNames = {"subscription_id", "event_id"}))
class WebhookDelivery {
    private static final Set<String> SAFE_FAILURE_MESSAGES = Set.of(
            "Subscription unavailable",
            "Delivery interrupted",
            "Outbound endpoint validation failed",
            "HTTP request timed out",
            "HTTP transport error");

    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID subscriptionId;
    @Column(nullable = false) private UUID eventId;
    @Column(nullable = false, length = 64) private String eventType;
    @Column(nullable = false, columnDefinition = "text") private String payloadJson;
    @Column(nullable = false) private int attemptCount;
    @Column(nullable = false) private Instant nextAttemptAt;
    private Instant deliveredAt;
    @Column(length = 1000) private String lastError;
    @Version private long version;

    protected WebhookDelivery() {}

    WebhookDelivery(UUID id, UUID tenantId, UUID subscriptionId, UUID eventId,
                    String eventType, String payloadJson, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.subscriptionId = subscriptionId;
        this.eventId = eventId;
        this.eventType = eventType;
        this.payloadJson = payloadJson;
        this.nextAttemptAt = now;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID subscriptionId() { return subscriptionId; }
    UUID eventId() { return eventId; }
    String eventType() { return eventType; }
    String payloadJson() { return payloadJson; }
    Instant nextAttemptAt() { return nextAttemptAt; }
    Instant deliveredAt() { return deliveredAt; }
    int attemptCount() { return attemptCount; }
    String lastError() { return lastError; }

    void success(Instant now) {
        deliveredAt = now;
        lastError = null;
    }

    void failed(String error, Instant now) {
        attemptCount++;
        lastError = safeFailure(error);
        nextAttemptAt = attemptCount >= 12
                ? now
                : now.plusSeconds(Math.min(3600, 5L << Math.min(attemptCount, 9)));
    }

    private static String safeFailure(String value) {
        if (value != null && value.matches("[A-Za-z0-9_$]{1,160}")) return value;
        if (value != null && value.matches("Webhook HTTP [1-5][0-9]{2}")) return value;
        return SAFE_FAILURE_MESSAGES.contains(value) ? value : "WebhookDeliveryFailure";
    }
}
