package com.universalplatform.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
class AuditEvent {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 160)
    private String actorSubject;

    @Column(nullable = false, length = 120)
    private String action;

    @Column(nullable = false, length = 120)
    private String resourceType;

    @Column(length = 160)
    private String resourceId;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEvent() {}

    AuditEvent(UUID id, UUID tenantId, String actorSubject, String action, String resourceType, String resourceId, Instant occurredAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.actorSubject = actorSubject;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.occurredAt = occurredAt;
    }
}
