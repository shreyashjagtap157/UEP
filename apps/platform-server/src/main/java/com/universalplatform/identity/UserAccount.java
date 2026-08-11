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
@Table(name = "user_account")
class UserAccount {
    @Id
    private UUID id;

    @Column(name = "oidc_subject", nullable = false, unique = true, length = 160)
    private String oidcSubject;

    @Column(length = 320)
    private String email;

    @Column(nullable = false, length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected UserAccount() {}

    UserAccount(UUID id, String oidcSubject, String email, String displayName, Instant now) {
        this.id = id;
        this.oidcSubject = oidcSubject;
        this.email = email;
        this.displayName = displayName;
        this.status = UserStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() { return id; }
    String oidcSubject() { return oidcSubject; }
    String email() { return email; }
    String displayName() { return displayName; }
    UserStatus status() { return status; }
    long version() { return version; }

    void refreshProfile(String email, String displayName, Instant now) {
        this.email = email;
        this.displayName = displayName;
        this.updatedAt = now;
    }

    void disable(Instant now) {
        this.status = UserStatus.DISABLED;
        this.updatedAt = now;
    }
}
