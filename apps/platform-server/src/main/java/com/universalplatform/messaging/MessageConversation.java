package com.universalplatform.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_conversation")
class MessageConversation {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(length = 240) private String subject;
    @Column(nullable = false) private UUID createdByMembershipId;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected MessageConversation() {}

    MessageConversation(UUID id, UUID tenantId, String subject, UUID createdByMembershipId, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.subject = subject;
        this.createdByMembershipId = createdByMembershipId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    String subject() { return subject; }
    UUID createdByMembershipId() { return createdByMembershipId; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
    long version() { return version; }
    void touched(Instant now) { updatedAt = now; }
}
