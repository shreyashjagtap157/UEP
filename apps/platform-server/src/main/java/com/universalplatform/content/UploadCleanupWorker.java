package com.universalplatform.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class UploadCleanupWorker {
    private static final Logger log = LoggerFactory.getLogger(UploadCleanupWorker.class);
    private final UploadSessionRepository uploads;
    private final Clock clock = Clock.systemUTC();

    UploadCleanupWorker(UploadSessionRepository uploads) {
        this.uploads = uploads;
    }

    @Scheduled(fixedDelayString = "${platform.storage.upload-cleanup-poll-ms:3600000}")
    @Transactional
    void expire() {
        var now = clock.instant();
        for (UploadSession upload : uploads.findTop100ByStatusInAndExpiresAtBeforeOrderByExpiresAtAsc(
                List.of(UploadStatus.INITIATED, UploadStatus.UPLOADING, UploadStatus.VERIFYING, UploadStatus.EXPIRED), now)) {
            upload.status = UploadStatus.EXPIRED;
            deleteStaging(upload, "expired upload");
        }

        // If staging deletion failed in an after-commit callback, retry it without touching the
        // already-committed provider object. Keep the session row as the durable cleanup reference.
        for (UploadSession upload : uploads.findTop100ByStatusAndCompletedAtBeforeOrderByCompletedAtAsc(
                UploadStatus.COMPLETED, now.minus(Duration.ofHours(1)))) {
            deleteStaging(upload, "completed upload");
        }
    }

    private static void deleteStaging(UploadSession upload, String purpose) {
        try {
            Files.deleteIfExists(Path.of(upload.stagingPath));
        } catch (IOException failure) {
            log.warn("Could not delete staging file for tenant {} {} {}", upload.tenantId, purpose, upload.id, failure);
        }
    }
}
