package com.universalplatform.organization;

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
@Table(name = "branch")
class Branch {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String displayName;

    @Column(nullable = false, length = 80)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BranchStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Branch() {}

    Branch(UUID id, UUID tenantId, String code, String displayName, String timezone, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.displayName = displayName;
        this.timezone = timezone;
        this.status = BranchStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    String code() { return code; }
    String displayName() { return displayName; }
    String timezone() { return timezone; }
    BranchStatus status() { return status; }
    Instant createdAt() { return createdAt; }
    long version() { return version; }

    void update(String code, String displayName, String timezone, BranchStatus status, Instant now) {
        this.code = code;
        this.displayName = displayName;
        this.timezone = timezone;
        this.status = status;
        this.updatedAt = now;
    }
}
