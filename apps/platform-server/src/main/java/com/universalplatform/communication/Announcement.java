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
    @Column(name = "publish_attempt_count", nullable = false) private int publishAttemptCount;
    @Column(name = "publish_available_at", nullable = false) private Instant publishAvailableAt;
    @Column(name = "publish_dead_lettered_at") private Instant publishDeadLetteredAt;
    @Column(name = "publish_last_error", length = 160) private String publishLastError;
    @Column(name = "expiry_attempt_count", nullable = false) private int expiryAttemptCount;
    @Column(name = "expiry_available_at", nullable = false) private Instant expiryAvailableAt;
    @Column(name = "expiry_dead_lettered_at") private Instant expiryDeadLetteredAt;
    @Column(name = "expiry_last_error", length = 160) private String expiryLastError;
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
        this.publishAvailableAt = now;
        this.expiryAvailableAt = now;
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
    int publishAttemptCount() { return publishAttemptCount; }
    Instant publishAvailableAt() { return publishAvailableAt; }
    Instant publishDeadLetteredAt() { return publishDeadLetteredAt; }
    String publishLastError() { return publishLastError; }
    int expiryAttemptCount() { return expiryAttemptCount; }
    Instant expiryAvailableAt() { return expiryAvailableAt; }
    Instant expiryDeadLetteredAt() { return expiryDeadLetteredAt; }
    String expiryLastError() { return expiryLastError; }

    void failPublication(Instant now, String failureType) {
        if (publishDeadLetteredAt != null) return;
        publishAttemptCount++;
        publishLastError = safeFailureType(failureType);
        if (publishAttemptCount >= MAX_WORKER_ATTEMPTS) publishDeadLetteredAt = now;
        else publishAvailableAt = now.plusSeconds(retryDelaySeconds(publishAttemptCount));
        updatedAt = now;
    }

    void failExpiry(Instant now, String failureType) {
        if (expiryDeadLetteredAt != null) return;
        expiryAttemptCount++;
        expiryLastError = safeFailureType(failureType);
        if (expiryAttemptCount >= MAX_WORKER_ATTEMPTS) expiryDeadLetteredAt = now;
        else expiryAvailableAt = now.plusSeconds(retryDelaySeconds(expiryAttemptCount));
        updatedAt = now;
    }

    void requeuePublication(Instant now) {
        publishAttemptCount = 0;
        publishAvailableAt = now;
        publishDeadLetteredAt = null;
        publishLastError = null;
        updatedAt = now;
    }

    void requeueExpiry(Instant now) {
        expiryAttemptCount = 0;
        expiryAvailableAt = now;
        expiryDeadLetteredAt = null;
        expiryLastError = null;
        updatedAt = now;
    }

    void clearPublicationFailures(Instant now) {
        publishAttemptCount = 0;
        publishAvailableAt = now;
        publishDeadLetteredAt = null;
        publishLastError = null;
    }

    void clearExpiryFailures(Instant now) {
        expiryAttemptCount = 0;
        expiryAvailableAt = now;
        expiryDeadLetteredAt = null;
        expiryLastError = null;
    }

    private static final int MAX_WORKER_ATTEMPTS = 12;

    private static long retryDelaySeconds(int attempt) {
        return Math.min(3600L, 5L << Math.min(attempt, 10));
    }

    private static String safeFailureType(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_$]{1,160}")) return "ProcessingFailure";
        return value;
    }

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
        clearPublicationFailures(now);
        clearExpiryFailures(now);
        this.updatedAt = now;
    }

    void publish(Instant now) {
        if (status == AnnouncementStatus.CANCELLED || status == AnnouncementStatus.EXPIRED) throw new CommunicationConflictException("Announcement cannot be published in its current state");
        this.status = AnnouncementStatus.PUBLISHED;
        this.publishAt = now;
        this.publishedAt = now;
        clearPublicationFailures(now);
        this.updatedAt = now;
    }

    void expire(Instant now) {
        clearExpiryFailures(now);
        this.status = AnnouncementStatus.EXPIRED;
        this.updatedAt = now;
    }

    void cancel(Instant now) {
        clearPublicationFailures(now);
        clearExpiryFailures(now);
        this.status = AnnouncementStatus.CANCELLED;
        this.updatedAt = now;
    }
}
