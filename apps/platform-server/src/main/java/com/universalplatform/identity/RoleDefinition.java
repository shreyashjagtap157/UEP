package com.universalplatform.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "role_definition")
class RoleDefinition {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(length = 64)
    private String systemKey;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean systemManaged;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected RoleDefinition() {}

    RoleDefinition(UUID id, UUID tenantId, SystemRoleKey systemKey, String name, String description, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.systemKey = systemKey.name();
        this.name = name;
        this.description = description;
        this.systemManaged = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    RoleDefinition(UUID id, UUID tenantId, String name, String description, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.systemManaged = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    String systemKey() { return systemKey; }
    String name() { return name; }
    String description() { return description; }
    boolean systemManaged() { return systemManaged; }
    long version() { return version; }

    void updateCustom(String name, String description, Instant now) {
        if (systemManaged) throw new IdentityConflictException("System-managed roles cannot be modified");
        this.name = name;
        this.description = description;
        this.updatedAt = now;
    }
}
