package com.universalplatform.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_participant")
class MessageParticipant {
    @EmbeddedId private MessageParticipantId id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private Instant joinedAt;
    private Instant lastReadAt;

    protected MessageParticipant() {}

    MessageParticipant(UUID conversationId, UUID tenantId, UUID membershipId, Instant joinedAt) {
        this.id = new MessageParticipantId(conversationId, membershipId);
        this.tenantId = tenantId;
        this.joinedAt = joinedAt;
    }

    MessageParticipantId id() { return id; }
    UUID conversationId() { return id.conversationId(); }
    UUID membershipId() { return id.membershipId(); }
    UUID tenantId() { return tenantId; }
    Instant joinedAt() { return joinedAt; }
    Instant lastReadAt() { return lastReadAt; }
    void markRead(Instant at) { lastReadAt = at; }
}
