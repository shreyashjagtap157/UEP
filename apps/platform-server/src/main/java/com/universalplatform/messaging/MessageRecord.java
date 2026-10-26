package com.universalplatform.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_record")
class MessageRecord {
    @Id private UUID id;
    @Column(nullable = false) private UUID conversationId;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID senderMembershipId;
    @Column(nullable = false, columnDefinition = "text") private String body;
    @Column(nullable = false) private Instant createdAt;
    private Instant editedAt;
    private Instant deletedAt;
    @Version private long version;

    protected MessageRecord() {}

    MessageRecord(UUID id, UUID conversationId, UUID tenantId, UUID senderMembershipId, String body, Instant now) {
        this.id = id;
        this.conversationId = conversationId;
        this.tenantId = tenantId;
        this.senderMembershipId = senderMembershipId;
        this.body = body;
        this.createdAt = now;
    }

    UUID id() { return id; }
    UUID conversationId() { return conversationId; }
    UUID tenantId() { return tenantId; }
    UUID senderMembershipId() { return senderMembershipId; }
    String body() { return body; }
    Instant createdAt() { return createdAt; }
    Instant editedAt() { return editedAt; }
    Instant deletedAt() { return deletedAt; }
    long version() { return version; }

    void edit(String nextBody, Instant now) { body = nextBody; editedAt = now; }
    void delete(Instant now) { deletedAt = now; }
}
