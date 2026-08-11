package com.universalplatform.identity;

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
@Table(name = "tenant_membership")
class TenantMembership {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID userId;

    private UUID primaryBranchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MembershipStatus status;

    @Column(length = 160)
    private String externalReference;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    private Instant endedAt;

    @Version
    private long version;

    protected TenantMembership() {}

    TenantMembership(UUID id, UUID tenantId, UUID userId, UUID primaryBranchId, String externalReference, Instant joinedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.primaryBranchId = primaryBranchId;
        this.externalReference = externalReference;
        this.status = MembershipStatus.ACTIVE;
        this.joinedAt = joinedAt;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID userId() { return userId; }
    UUID primaryBranchId() { return primaryBranchId; }
    MembershipStatus status() { return status; }
    String externalReference() { return externalReference; }
    Instant joinedAt() { return joinedAt; }
    long version() { return version; }

    void update(UUID primaryBranchId, String externalReference) {
        this.primaryBranchId = primaryBranchId;
        this.externalReference = externalReference;
    }

    void changeStatus(MembershipStatus status, Instant now) {
        this.status = status;
        this.endedAt = status == MembershipStatus.ENDED ? now : null;
    }
}
