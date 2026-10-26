package com.universalplatform.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_block", uniqueConstraints = @UniqueConstraint(
        name = "uq_message_block_pair",
        columnNames = {"tenant_id", "blocker_membership_id", "blocked_membership_id"}))
class MessageBlock {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID blockerMembershipId;
    @Column(nullable = false) private UUID blockedMembershipId;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    protected MessageBlock() {}

    MessageBlock(UUID id, UUID tenantId, UUID blockerMembershipId, UUID blockedMembershipId, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.blockerMembershipId = blockerMembershipId;
        this.blockedMembershipId = blockedMembershipId;
        this.createdAt = createdAt;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID blockerMembershipId() { return blockerMembershipId; }
    UUID blockedMembershipId() { return blockedMembershipId; }
    Instant createdAt() { return createdAt; }
}
