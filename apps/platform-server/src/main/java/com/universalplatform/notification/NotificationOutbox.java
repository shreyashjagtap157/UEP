package com.universalplatform.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_outbox", uniqueConstraints = @UniqueConstraint(name = "uq_notification_outbox_tenant_dedup", columnNames = {"tenant_id", "deduplication_key"}))
class NotificationOutbox {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 64) private String eventType;
    @Column(nullable = false, length = 64) private String aggregateType;
    @Column(nullable = false) private UUID aggregateId;
    @Column(nullable = false, length = 240) private String title;
    @Column(nullable = false, length = 4000) private String body;
    @Column(nullable = false, length = 24) private String priority;
    @Column(length = 64) private String resourceType;
    private UUID resourceId;
    @Column(nullable = false, length = 240) private String deduplicationKey;
    @Column(nullable = false, updatable = false) private Instant occurredAt;
    @Column(nullable = false) private Instant availableAt;
    @Column(nullable = false) private int attemptCount;
    private Instant processedAt;
    private Instant deadLetteredAt;
    @Column(length = 1000) private String lastError;
    @Version private long version;

    protected NotificationOutbox() {}

    NotificationOutbox(UUID id, UUID tenantId, NotificationEventType eventType, String aggregateType,
                       UUID aggregateId, String title, String body, NotificationPriority priority,
                       String resourceType, UUID resourceId, String deduplicationKey,
                       Instant occurredAt, Instant availableAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.eventType = eventType.name();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.title = title;
        this.body = body;
        this.priority = priority.name();
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.deduplicationKey = deduplicationKey;
        this.occurredAt = occurredAt;
        this.availableAt = availableAt;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    NotificationEventType eventType() { return NotificationEventType.valueOf(eventType); }
    String aggregateType() { return aggregateType; }
    UUID aggregateId() { return aggregateId; }
    String title() { return title; }
    String body() { return body; }
    NotificationPriority priority() { return NotificationPriority.valueOf(priority); }
    String resourceType() { return resourceType; }
    UUID resourceId() { return resourceId; }
    Instant availableAt() { return availableAt; }
    Instant processedAt() { return processedAt; }
    Instant deadLetteredAt() { return deadLetteredAt; }
    int attemptCount() { return attemptCount; }

    void processed(Instant now) {
        this.processedAt = now;
        this.lastError = null;
    }

    void failed(String message, Instant failedAt, Instant retryAt) {
        this.attemptCount++;
        this.lastError = message == null ? "Notification outbox processing failed" : truncate(message, 1000);
        if (this.attemptCount >= 12) {
            this.deadLetteredAt = failedAt;
        } else {
            this.availableAt = retryAt;
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
