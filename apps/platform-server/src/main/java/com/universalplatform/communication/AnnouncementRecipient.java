package com.universalplatform.communication;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "announcement_recipient")
class AnnouncementRecipient {
    @EmbeddedId private AnnouncementRecipientId id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false, updatable = false) private Instant resolvedAt;

    protected AnnouncementRecipient() {}
    AnnouncementRecipient(UUID tenantId, UUID announcementId, UUID membershipId, Instant resolvedAt) {
        this.id = new AnnouncementRecipientId(announcementId, membershipId);
        this.tenantId = tenantId;
        this.resolvedAt = resolvedAt;
    }
    UUID membershipId() { return id.membershipId(); }
}
