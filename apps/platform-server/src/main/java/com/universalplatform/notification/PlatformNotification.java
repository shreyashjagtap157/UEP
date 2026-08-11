package com.universalplatform.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
class PlatformNotification {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID membershipId;
    @Column(nullable = false) private UUID sourceOutboxId;
    @Column(nullable = false, length = 64) private String eventType;
    @Column(nullable = false, length = 240) private String title;
    @Column(nullable = false, length = 4000) private String body;
    @Column(length = 64) private String resourceType;
    private UUID resourceId;
    @Column(nullable = false, length = 24) private String priority;
    @Column(nullable = false) private boolean visibleInApp;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    private Instant readAt;
    @Version private long version;

    protected PlatformNotification() {}

    PlatformNotification(UUID id, UUID tenantId, UUID membershipId, UUID sourceOutboxId,
                         NotificationEventType eventType, String title, String body,
                         String resourceType, UUID resourceId, NotificationPriority priority,
                         boolean visibleInApp, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.membershipId = membershipId;
        this.sourceOutboxId = sourceOutboxId;
        this.eventType = eventType.name();
        this.title = title;
        this.body = body;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.priority = priority.name();
        this.visibleInApp = visibleInApp;
        this.createdAt = now;
    }

    UUID id() { return id; }
    UUID membershipId() { return membershipId; }
    String eventType() { return eventType; }
    String title() { return title; }
    String body() { return body; }
    String resourceType() { return resourceType; }
    UUID resourceId() { return resourceId; }
    String priority() { return priority; }
    Instant createdAt() { return createdAt; }
    Instant readAt() { return readAt; }
    long version() { return version; }
    void markRead(Instant now) { if (readAt == null) readAt = now; }
}
