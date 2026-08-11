package com.universalplatform.tenancy;

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
@Table(name = "tenant")
class Tenant {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false, length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TenantStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Tenant() {}

    Tenant(UUID id, String slug, String displayName, Instant createdAt) {
        this.id = id;
        this.slug = slug;
        this.displayName = displayName;
        this.status = TenantStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    UUID id() { return id; }
    String slug() { return slug; }
    String displayName() { return displayName; }
    TenantStatus status() { return status; }
}
