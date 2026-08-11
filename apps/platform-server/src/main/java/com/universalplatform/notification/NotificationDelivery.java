package com.universalplatform.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_delivery")
class NotificationDelivery {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID notificationId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private NotificationChannel channel;
    @Column(nullable = false, length = 320) private String destination;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private NotificationDeliveryStatus status;
    @Column(nullable = false) private int attemptCount;
    @Column(nullable = false) private Instant nextAttemptAt;
    private Instant sentAt;
    @Column(length = 1000) private String lastError;
    @Column(nullable = false, unique = true, length = 240) private String idempotencyKey;
    @Version private long version;

    protected NotificationDelivery() {}

    NotificationDelivery(UUID id, UUID tenantId, UUID notificationId, String destination,
                         String idempotencyKey, boolean enabled, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.notificationId = notificationId;
        this.channel = NotificationChannel.EMAIL;
        this.destination = destination;
        this.status = enabled ? NotificationDeliveryStatus.PENDING : NotificationDeliveryStatus.SKIPPED;
        this.nextAttemptAt = now;
        this.lastError = enabled ? null : "Email delivery is disabled for this deployment";
        this.idempotencyKey = idempotencyKey;
    }

    UUID id() { return id; }
    UUID notificationId() { return notificationId; }
    String destination() { return destination; }
    NotificationDeliveryStatus status() { return status; }
    int attemptCount() { return attemptCount; }
    void sent(Instant now) { this.status = NotificationDeliveryStatus.SENT; this.sentAt = now; this.lastError = null; }
    void failed(String error, Instant failedAt, Instant retryAt) {
        this.attemptCount++;
        this.status = this.attemptCount >= 8 ? NotificationDeliveryStatus.DEAD_LETTERED : NotificationDeliveryStatus.FAILED;
        this.nextAttemptAt = this.status == NotificationDeliveryStatus.DEAD_LETTERED ? failedAt : retryAt;
        this.lastError = error == null ? "Email delivery failed" : (error.length() <= 1000 ? error : error.substring(0, 1000));
    }
}
