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

    @Column(length = 240)
    private String lastError;

    @Version
    private long rowVersion;

    protected StorageCleanupJob() {}

    StorageCleanupJob(UUID id, UUID tenantId, StorageProviderType provider, String locator,
                      String objectKey, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.provider = provider;
        this.locator = locator;
        this.objectKey = objectKey;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    StorageProviderType provider() { return provider; }
    String locator() { return locator; }
    String objectKey() { return objectKey; }
    Instant nextAttemptAt() { return nextAttemptAt; }
    int attemptCount() { return attemptCount; }
    Instant deletedAt() { return deletedAt; }
    String lastError() { return lastError; }

    void markDeleted(Instant now) {
        this.deletedAt = now;
        this.lastError = null;
    }

    void failed(String safeError, Instant now) {
        this.attemptCount++;
        this.lastError = safeError == null ? "Storage deletion failed"
                : safeError.substring(0, Math.min(240, safeError.length()));
        long backoffSeconds = Math.min(3600, 5L << Math.min(this.attemptCount, 9));
        this.nextAttemptAt = now.plusSeconds(backoffSeconds);
    }
}
