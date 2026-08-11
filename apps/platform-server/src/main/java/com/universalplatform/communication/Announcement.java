package com.universalplatform.communication;

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
@Table(name = "announcement")
class Announcement {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    private UUID authorizationBranchId;
    @Column(nullable = false, length = 240) private String title;
    @Column(nullable = false, columnDefinition = "text") private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AnnouncementPriority priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AnnouncementStatus status;
    private Instant publishAt;
    private Instant expiresAt;
    @Column(nullable = false) private boolean acknowledgementRequired;
    @Column(nullable = false, updatable = false, length = 160) private String createdBySubject;
    private Instant publishedAt;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected Announcement() {}

    Announcement(UUID id, UUID tenantId, UUID authorizationBranchId, String title, String body, AnnouncementPriority priority,
                 Instant publishAt, Instant expiresAt, boolean acknowledgementRequired,
                 String createdBySubject, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.authorizationBranchId = authorizationBranchId;
        this.title = title;
        this.body = body;
        this.priority = priority;
        this.publishAt = publishAt;
        this.expiresAt = expiresAt;
        this.acknowledgementRequired = acknowledgementRequired;
        this.createdBySubject = createdBySubject;
        this.status = publishAt == null ? AnnouncementStatus.DRAFT : AnnouncementStatus.SCHEDULED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID authorizationBranchId() { return authorizationBranchId; }
    String title() { return title; }
    String body() { return body; }
    AnnouncementPriority priority() { return priority; }
    AnnouncementStatus status() { return status; }
    Instant publishAt() { return publishAt; }
    Instant expiresAt() { return expiresAt; }
    boolean acknowledgementRequired() { return acknowledgementRequired; }
    String createdBySubject() { return createdBySubject; }
    Instant publishedAt() { return publishedAt; }
    Instant createdAt() { return createdAt; }
    long version() { return version; }

    void update(UUID authorizationBranchId, String title, String body, AnnouncementPriority priority, Instant publishAt, Instant expiresAt,
                boolean acknowledgementRequired, Instant now) {
        if (status == AnnouncementStatus.PUBLISHED || status == AnnouncementStatus.EXPIRED || status == AnnouncementStatus.CANCELLED) {
            throw new CommunicationConflictException("Published, expired, or cancelled announcements are immutable");
        }
        this.authorizationBranchId = authorizationBranchId;
        this.title = title;
        this.body = body;
        this.priority = priority;
        this.publishAt = publishAt;
        this.expiresAt = expiresAt;
        this.acknowledgementRequired = acknowledgementRequired;
        this.status = publishAt == null ? AnnouncementStatus.DRAFT : AnnouncementStatus.SCHEDULED;
        this.updatedAt = now;
    }

    void publish(Instant now) {
        if (status == AnnouncementStatus.CANCELLED || status == AnnouncementStatus.EXPIRED) throw new CommunicationConflictException("Announcement cannot be published in its current state");
        this.status = AnnouncementStatus.PUBLISHED;
        this.publishAt = now;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    void expire(Instant now) { this.status = AnnouncementStatus.EXPIRED; this.updatedAt = now; }
    void cancel(Instant now) { this.status = AnnouncementStatus.CANCELLED; this.updatedAt = now; }
}
