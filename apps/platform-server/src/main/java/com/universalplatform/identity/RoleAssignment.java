package com.universalplatform.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import java.util.Objects;

@Entity
@Table(name = "role_assignment")
class RoleAssignment {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID membershipId;

    @Column(nullable = false)
    private UUID roleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AssignmentScopeKind scopeKind;

    private UUID scopeId;

    @Column(nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(nullable = false, updatable = false, length = 160)
    private String assignedBySubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleAssignmentSource source;

    protected RoleAssignment() {}

    RoleAssignment(UUID id, UUID tenantId, UUID membershipId, UUID roleId, AssignmentScopeKind scopeKind,
                   UUID scopeId, String assignedBySubject, Instant assignedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.membershipId = membershipId;
        this.roleId = roleId;
        this.scopeKind = scopeKind;
        this.scopeId = scopeId;
        this.assignedBySubject = assignedBySubject;
        this.assignedAt = assignedAt;
        this.source = RoleAssignmentSource.MANUAL;
    }

    RoleAssignment(UUID id, UUID tenantId, UUID membershipId, UUID roleId, AssignmentScopeKind scopeKind,
                   UUID scopeId, String assignedBySubject, Instant assignedAt, RoleAssignmentSource source) {
        this(id, tenantId, membershipId, roleId, scopeKind, scopeId, assignedBySubject, assignedAt);
        this.source = Objects.requireNonNull(source);
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID membershipId() { return membershipId; }
    RoleAssignmentSource source() { return source; }
    UUID roleId() { return roleId; }
    AssignmentScopeKind scopeKind() { return scopeKind; }
    UUID scopeId() { return scopeId; }
}
