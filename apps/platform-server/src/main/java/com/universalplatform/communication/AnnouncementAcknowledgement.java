package com.universalplatform.communication;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "announcement_acknowledgement")
class AnnouncementAcknowledgement {
    @EmbeddedId private AnnouncementAcknowledgementId id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false, updatable = false) private Instant acknowledgedAt;
    protected AnnouncementAcknowledgement() {}
    AnnouncementAcknowledgement(UUID tenantId, UUID announcementId, UUID membershipId, Instant at) {
        this.id = new AnnouncementAcknowledgementId(announcementId, membershipId);
        this.tenantId = tenantId;
        this.acknowledgedAt = at;
    }
    Instant acknowledgedAt() { return acknowledgedAt; }
}
