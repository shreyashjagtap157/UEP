package com.universalplatform.content;

import com.universalplatform.storage.StorageProviderType;
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
@Table(name = "storage_cleanup_outbox")
class StorageCleanupJob {
    static final int MAX_ATTEMPTS = 12;

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StorageProviderType provider;

    @Column(nullable = false, length = 2048)
    private String locator;

    @Column(nullable = false, length = 1024)
    private String objectKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column(nullable = false)
    private int attemptCount;

    private Instant deletedAt;
    private Instant deadLetteredAt;
    private UUID processingToken;
    private Instant processingLeaseExpiresAt;

    @Column(length = 240)
    private String lastError;

    @Column(name = "quota_reservation_id")
    private UUID quotaReservationId;

    @Version
    private long rowVersion;

    protected StorageCleanupJob() {}

    StorageCleanupJob(UUID id, UUID tenantId, StorageProviderType provider, String locator,
                      String objectKey, Instant now) {
        this(id, tenantId, provider, locator, objectKey, null, now);
    }

    StorageCleanupJob(UUID id, UUID tenantId, StorageProviderType provider, String locator,
                      String objectKey, UUID quotaReservationId, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.provider = provider;
        this.locator = locator;
        this.objectKey = objectKey;
        this.quotaReservationId = quotaReservationId;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    StorageProviderType provider() { return provider; }
    String locator() { return locator; }
    String objectKey() { return objectKey; }
    UUID quotaReservationId() { return quotaReservationId; }
    Instant nextAttemptAt() { return nextAttemptAt; }
    int attemptCount() { return attemptCount; }
    Instant deletedAt() { return deletedAt; }
    Instant deadLetteredAt() { return deadLetteredAt; }
    UUID processingToken() { return processingToken; }
    Instant processingLeaseExpiresAt() { return processingLeaseExpiresAt; }
    String lastError() { return lastError; }

    void claim(UUID token, Instant leaseExpiresAt) {
        this.processingToken = token;
        this.processingLeaseExpiresAt = leaseExpiresAt;
    }

    void markDeleted(Instant now) {
        this.deletedAt = now;
        this.deadLetteredAt = null;
        this.processingToken = null;
        this.processingLeaseExpiresAt = null;
        this.lastError = null;
    }

    void failed(String failureType, Instant now) {
        if (deletedAt != null || deadLetteredAt != null) return;
        this.processingToken = null;
        this.processingLeaseExpiresAt = null;
        this.attemptCount++;
        this.lastError = safeFailureType(failureType);
        if (attemptCount >= MAX_ATTEMPTS) {
            deadLetteredAt = now;
        } else {
            nextAttemptAt = now.plusSeconds(retryDelaySeconds(attemptCount));
        }
    }

    void requeue(Instant now) {
        if (deletedAt != null || deadLetteredAt == null) {
            throw new IllegalStateException("Only a pending dead-lettered storage cleanup can be requeued");
        }
        attemptCount = 0;
        deadLetteredAt = null;
        processingToken = null;
        processingLeaseExpiresAt = null;
        lastError = null;
        nextAttemptAt = now;
    }

    private static long retryDelaySeconds(int attempt) {
        return Math.min(3600L, 5L << Math.min(attempt, 10));
    }

    private static String safeFailureType(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_$]{1,160}")) return "StorageDeletionFailure";
        return value;
    }
}
