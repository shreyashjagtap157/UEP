package com.universalplatform.notification;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_preference")
class NotificationPreference {
    @EmbeddedId private NotificationPreferenceId id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private boolean inAppEnabled;
    @Column(nullable = false) private boolean emailEnabled;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected NotificationPreference() {}

    NotificationPreference(UUID tenantId, UUID membershipId, String eventType, boolean inAppEnabled, boolean emailEnabled, Instant now) {
        this.id = new NotificationPreferenceId(membershipId, eventType);
        this.tenantId = tenantId;
        this.inAppEnabled = inAppEnabled;
        this.emailEnabled = emailEnabled;
        this.updatedAt = now;
    }

    UUID membershipId() { return id.membershipId(); }
    String eventType() { return id.eventType(); }
    boolean inAppEnabled() { return inAppEnabled; }
    boolean emailEnabled() { return emailEnabled; }
    long version() { return version; }
    void update(boolean inApp, boolean email, Instant now) { this.inAppEnabled = inApp; this.emailEnabled = email; this.updatedAt = now; }
}
