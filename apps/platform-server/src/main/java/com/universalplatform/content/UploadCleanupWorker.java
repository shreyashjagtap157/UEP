package com.universalplatform.content;

import com.universalplatform.storage.StorageQuotaService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class UploadCleanupWorker {
    private static final Logger log = LoggerFactory.getLogger(UploadCleanupWorker.class);
    private static final int CLEANUP_BATCH_SIZE = 100;

    private final UploadSessionRepository uploads;
    private final StorageQuotaService storageQuota;
    private final Clock clock = Clock.systemUTC();

    UploadCleanupWorker(UploadSessionRepository uploads, StorageQuotaService storageQuota) {
        this.uploads = uploads;
        this.storageQuota = storageQuota;
    }

    @Scheduled(fixedDelayString = "${platform.storage.upload-cleanup-poll-ms:3600000}")
    @Transactional
    void expire() {
        Instant now = clock.instant();
        List<UploadStatus> expiredStatuses = List.of(
                UploadStatus.INITIATED,
                UploadStatus.UPLOADING,
                UploadStatus.VERIFYING,
                UploadStatus.EXPIRED,
                UploadStatus.ABORTED);
        Instant afterExpiresAt = null;
        UUID afterId = null;

        // Keyset pagination is important here: a failed file deletion remains eligible.
        // Offset pagination would repeatedly select the same first page and starve later rows.
        while (true) {
            List<UploadSession> batch = uploads.findExpiredWithUncleanedStaging(
                    expiredStatuses, now, UploadStatus.ABORTED,
                    afterExpiresAt, afterId, PageRequest.of(0, CLEANUP_BATCH_SIZE));
            if (batch.isEmpty()) break;

            for (UploadSession upload : batch) {
                if (upload.status != UploadStatus.ABORTED) {
                    upload.status = UploadStatus.EXPIRED;
                }
                if (upload.quotaReservationId != null) {
                    storageQuota.release(upload.tenantId, upload.quotaReservationId);
                }
                deleteStaging(upload, upload.status == UploadStatus.ABORTED ? "aborted upload" : "expired upload");
                afterExpiresAt = upload.expiresAt;
                afterId = upload.id;
            }
            if (batch.size() < CLEANUP_BATCH_SIZE) break;
        }

        // Completed uploads retain a durable staging path when after-commit cleanup fails.
        // Keyset pagination ensures one persistent filesystem failure cannot starve newer rows.
        Instant completedBefore = now.minus(Duration.ofHours(1));
        Instant afterCompletedAt = null;
        afterId = null;
        while (true) {
            List<UploadSession> batch = uploads.findCompletedWithUncleanedStaging(
                    UploadStatus.COMPLETED, completedBefore, afterCompletedAt, afterId,
                    PageRequest.of(0, CLEANUP_BATCH_SIZE));
            if (batch.isEmpty()) break;

            for (UploadSession upload : batch) {
                deleteStaging(upload, "completed upload");
                afterCompletedAt = upload.completedAt;
                afterId = upload.id;
            }
            if (batch.size() < CLEANUP_BATCH_SIZE) break;
        }
    }

    private void deleteStaging(UploadSession upload, String purpose) {
        try {
            Files.deleteIfExists(Path.of(upload.stagingPath));
            upload.stagingCleanedAt = clock.instant();
        } catch (IOException failure) {
            log.warn("Could not delete staging file for tenant {} {} {}",
                    upload.tenantId, purpose, upload.id, failure);
        }
    }
}
