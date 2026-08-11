package com.universalplatform.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_session")
class PlatformSession {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID membershipId;

    @Column(nullable = false, length = 200)
    private String oidcSessionId;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    private Instant expiresAt;
    private Instant revokedAt;

    @Column(length = 160)
    private String revokedBySubject;

    @Column(length = 500)
    private String revocationReason;

    @Version
    private long version;

    protected PlatformSession() {}

    PlatformSession(UUID id, UUID tenantId, UUID membershipId, String oidcSessionId, Instant startedAt, Instant expiresAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.membershipId = membershipId;
        this.oidcSessionId = oidcSessionId;
        this.startedAt = startedAt;
        this.lastSeenAt = startedAt;
        this.expiresAt = expiresAt;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID membershipId() { return membershipId; }
    String oidcSessionId() { return oidcSessionId; }
    Instant startedAt() { return startedAt; }
    Instant lastSeenAt() { return lastSeenAt; }
    Instant expiresAt() { return expiresAt; }
    Instant revokedAt() { return revokedAt; }
    String revocationReason() { return revocationReason; }
    boolean revoked() { return revokedAt != null; }

    void touch(Instant now, Instant expiresAt) {
        this.lastSeenAt = now;
        if (expiresAt != null) this.expiresAt = expiresAt;
    }

    void revoke(String actorSubject, String reason, Instant now) {
        if (revokedAt != null) return;
        this.revokedAt = now;
        this.revokedBySubject = actorSubject;
        this.revocationReason = reason;
    }
}
