package com.universalplatform.notification;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "notification_outbox_recipient")
class NotificationOutboxRecipient {
    @EmbeddedId private NotificationOutboxRecipientId id;
    @Column(nullable = false) private UUID tenantId;
    @Column(length = 320) private String email;

    protected NotificationOutboxRecipient() {}

    NotificationOutboxRecipient(UUID outboxId, UUID membershipId, UUID tenantId, String email) {
        this.id = new NotificationOutboxRecipientId(outboxId, membershipId);
        this.tenantId = tenantId;
        this.email = email;
    }

    UUID membershipId() { return id.membershipId(); }
    String email() { return email; }
}
