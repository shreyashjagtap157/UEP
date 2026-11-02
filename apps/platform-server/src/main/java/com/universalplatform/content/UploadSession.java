package com.universalplatform.content;

import com.universalplatform.storage.StorageProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upload_session")
class UploadSession {
    @Id UUID id;
    @Column(nullable = false) UUID tenantId;
    @Column(nullable = false) String createdBy;
    @Column(nullable = false) String fileName;
    @Column(nullable = false) String contentType;
    @Column(nullable = false) long expectedBytes;
    @Column(nullable = false) long receivedBytes;
    String expectedSha256;
    @Enumerated(EnumType.STRING) @Column(nullable = false) StorageProviderType provider;
    @Column(nullable = false, length = 200) String objectPrefix;
    @Enumerated(EnumType.STRING) @Column(nullable = false) UploadStatus status;
    @Column(nullable = false, length = 2048) String stagingPath;
    @Column(nullable = false) Instant createdAt;
    @Column(nullable = false) Instant expiresAt;
    Instant completedAt;
    Instant stagingCleanedAt;
    UUID storageObjectId;
    UUID quotaReservationId;

    protected UploadSession() {}

    UploadSession(UUID id, UUID tenantId, String actor, String fileName, String contentType,
                  long expectedBytes, String expectedSha256, StorageProviderType provider,
                  String stagingPath, Instant now) {
        this(id, tenantId, actor, fileName, contentType, expectedBytes, expectedSha256,
                provider, "", stagingPath, now);
    }

    UploadSession(UUID id, UUID tenantId, String actor, String fileName, String contentType,
                  long expectedBytes, String expectedSha256, StorageProviderType provider,
                  String objectPrefix, String stagingPath, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.createdBy = actor;
        this.fileName = fileName;
        this.contentType = contentType;
        this.expectedBytes = expectedBytes;
        this.expectedSha256 = expectedSha256;
        this.provider = provider;
        this.objectPrefix = objectPrefix == null ? "" : objectPrefix;
        this.stagingPath = stagingPath;
        this.status = UploadStatus.INITIATED;
        this.createdAt = now;
        this.expiresAt = now.plusSeconds(86400);
    }
}
