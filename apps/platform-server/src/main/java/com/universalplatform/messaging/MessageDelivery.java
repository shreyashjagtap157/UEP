package com.universalplatform.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_delivery", uniqueConstraints = @UniqueConstraint(
        name = "uq_message_delivery_recipient",
        columnNames = {"tenant_id", "message_id", "recipient_membership_id"}))
class MessageDelivery {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID conversationId;
    @Column(nullable = false) private UUID messageId;
    @Column(nullable = false) private UUID recipientMembershipId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private MessageDeliveryStatus status;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    private Instant deliveredAt;
    private Instant readAt;
    @Version private long version;

    protected MessageDelivery() {}

    MessageDelivery(UUID id, UUID tenantId, UUID conversationId, UUID messageId,
                    UUID recipientMembershipId, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.recipientMembershipId = recipientMembershipId;
        this.status = MessageDeliveryStatus.PENDING;
        this.createdAt = createdAt;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID conversationId() { return conversationId; }
    UUID messageId() { return messageId; }
    UUID recipientMembershipId() { return recipientMembershipId; }
    MessageDeliveryStatus status() { return status; }
    Instant createdAt() { return createdAt; }
    Instant deliveredAt() { return deliveredAt; }
    Instant readAt() { return readAt; }
    long version() { return version; }
}
