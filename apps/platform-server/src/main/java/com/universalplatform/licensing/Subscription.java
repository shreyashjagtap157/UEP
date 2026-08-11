package com.universalplatform.licensing;

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
@Table(name = "subscription")
class Subscription {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubscriptionStatus status;

    @Column(nullable = false)
    private Instant startsAt;

    private Instant graceEndsAt;
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LicenseAuthorityKind authorityKind;

    @Column(length = 120)
    private String externalLicenseId;

    @Column(nullable = false)
    private long licenseRevision;

    @Version
    private long version;

    protected Subscription() {}

    Subscription(UUID id, UUID tenantId, SubscriptionStatus status, Instant startsAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.status = status;
        this.startsAt = startsAt;
        this.authorityKind = LicenseAuthorityKind.PLATFORM_MANAGED;
        this.licenseRevision = 0;
    }

    UUID tenantId() { return tenantId; }
    SubscriptionStatus status() { return status; }
    Instant graceEndsAt() { return graceEndsAt; }
    Instant expiresAt() { return expiresAt; }
    LicenseAuthorityKind authorityKind() { return authorityKind; }
    String externalLicenseId() { return externalLicenseId; }
    long licenseRevision() { return licenseRevision; }
}
