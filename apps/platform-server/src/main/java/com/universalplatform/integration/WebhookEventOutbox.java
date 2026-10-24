package com.universalplatform.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_event_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_webhook_event_outbox_source",
                columnNames = {"tenant_id", "source_outbox_id"}))
class WebhookEventOutbox {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID sourceOutboxId;

    @Column(nullable = false)
    private UUID aggregateId;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private Instant availableAt;

    @Column(nullable = false)
    private int attemptCount;

    private Instant processedAt;
    private Instant deadLetteredAt;

    @Column(length = 1000)
    private String lastError;

    @Version
    private long version;

    protected WebhookEventOutbox() {}

    WebhookEventOutbox(UUID id, UUID tenantId, UUID sourceOutboxId, UUID aggregateId,
                       String eventType, String title, String body, Instant occurredAt, Instant availableAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.sourceOutboxId = sourceOutboxId;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.title = title;
        this.body = body;
        this.occurredAt = occurredAt;
        this.availableAt = availableAt;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID sourceOutboxId() { return sourceOutboxId; }
    UUID aggregateId() { return aggregateId; }
    String eventType() { return eventType; }
    String title() { return title; }
    String body() { return body; }
    Instant occurredAt() { return occurredAt; }
    Instant availableAt() { return availableAt; }
    Instant processedAt() { return processedAt; }
    Instant deadLetteredAt() { return deadLetteredAt; }
    int attemptCount() { return attemptCount; }

    void processed(Instant now) {
        processedAt = now;
        lastError = null;
    }

    void failed(String message, Instant failedAt, Instant retryAt) {
        attemptCount++;
        lastError = message == null ? "Webhook fan-out failed"
                : message.substring(0, Math.min(1000, message.length()));
        if (attemptCount >= 12) {
            deadLetteredAt = failedAt;
        } else {
            availableAt = retryAt;
        }
    }
}
