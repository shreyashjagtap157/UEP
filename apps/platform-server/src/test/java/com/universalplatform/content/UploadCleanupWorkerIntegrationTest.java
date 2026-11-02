package com.universalplatform.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class UploadCleanupWorkerIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UploadSessionRepository uploads;
    @Autowired UploadCleanupWorker cleanup;

    @TempDir Path tempDir;

    private UUID tenantId;
    private final List<UUID> sessionIds = new ArrayList<>();

    @BeforeEach
    void createTenant() {
        tenantId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO tenant(id, slug, display_name, status) VALUES (?, ?, 'Upload cleanup test', 'ACTIVE')",
                tenantId, "upload-cleanup-" + tenantId);
    }

    @AfterEach
    void removeTestData() {
        if (!sessionIds.isEmpty()) uploads.deleteAllById(sessionIds);
        if (tenantId != null) jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
    }

    @Test
    void failedOldFilesDoNotStarveLaterCleanupAndAbortedSessionsStayAborted() throws IOException {
        Instant now = Instant.now();
        Instant olderExpiry = now.minus(Duration.ofDays(3));
        Instant newerExpiry = now.minus(Duration.ofDays(2));
        List<UploadSession> rows = new ArrayList<>();
        List<UUID> failedCleanupIds = new ArrayList<>();
        List<UUID> cleanableIds = new ArrayList<>();

        // More than one full cleanup page deliberately fails local deletion. The keyset
        // cursor must still advance to later rows instead of retrying this same first page.
        for (int index = 0; index < 101; index++) {
            UUID id = UUID.randomUUID();
            Path blockedDirectory = tempDir.resolve("blocked-" + id);
            Files.createDirectories(blockedDirectory);
            Files.writeString(blockedDirectory.resolve("keep.txt"), "prevent directory deletion");
            UploadSession upload = session(id, blockedDirectory, olderExpiry, now);
            upload.status = index % 2 == 0 ? UploadStatus.ABORTED : UploadStatus.UPLOADING;
            rows.add(upload);
            failedCleanupIds.add(id);
            sessionIds.add(id);
        }

        for (int index = 0; index < 105; index++) {
            UUID id = UUID.randomUUID();
            Path stagingFile = tempDir.resolve("cleanable-" + id + ".part");
            Files.writeString(stagingFile, "staging data");
            UploadSession upload = session(id, stagingFile, newerExpiry, now);
            upload.status = UploadStatus.UPLOADING;
            rows.add(upload);
            cleanableIds.add(id);
            sessionIds.add(id);
        }

        uploads.saveAll(rows);
        uploads.flush();

        cleanup.expire();

        List<UploadSession> persisted = uploads.findAllById(sessionIds);
        assertThat(persisted).hasSize(206);

        for (UploadSession upload : persisted) {
            if (failedCleanupIds.contains(upload.id)) {
                assertThat(upload.stagingCleanedAt).as("failed staging cleanup remains retryable").isNull();
                if (upload.id.equals(failedCleanupIds.getFirst())
                        || rows.stream().anyMatch(row -> row.id.equals(upload.id) && row.status == UploadStatus.ABORTED)) {
                    // Terminal user-abort state must not be rewritten as EXPIRED by the worker.
                    assertThat(upload.status).isEqualTo(UploadStatus.ABORTED);
                } else {
                    assertThat(upload.status).isEqualTo(UploadStatus.EXPIRED);
                }
            } else {
                assertThat(cleanableIds).contains(upload.id);
                assertThat(upload.stagingCleanedAt).as("later staging files must not be starved").isNotNull();
                assertThat(upload.status).isEqualTo(UploadStatus.EXPIRED);
                assertThat(Files.exists(Path.of(upload.stagingPath))).isFalse();
            }
        }
    }

    private UploadSession session(UUID id, Path stagingPath, Instant expiresAt, Instant createdAt) {
        UploadSession upload = new UploadSession(id, tenantId, "cleanup-test", "payload.bin",
                "application/octet-stream", 12, null, com.universalplatform.storage.StorageProviderType.LOCAL,
                stagingPath.toString(), createdAt);
        upload.expiresAt = expiresAt;
        upload.receivedBytes = 12;
        return upload;
    }
}
