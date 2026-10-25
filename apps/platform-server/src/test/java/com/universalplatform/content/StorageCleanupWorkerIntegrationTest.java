package com.universalplatform.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.universalplatform.storage.StorageAdapter;
import com.universalplatform.storage.StorageAdapterRegistry;
import com.universalplatform.storage.StorageProviderType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = {
        "platform.storage.cleanup-poll-ms=3600000",
        "platform.storage.cleanup.enabled=false"
})
class StorageCleanupWorkerIntegrationTest {
    private final UUID tenantId = UUID.randomUUID();

    @Autowired JdbcTemplate jdbc;
    @Autowired StorageCleanupRepository jobs;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void seedTenant() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, ?, 'Storage Cleanup Test', 'ACTIVE')",
                tenantId, "storage-cleanup-" + tenantId);
    }

    @AfterEach
    void cleanTenant() {
        jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
    }

    @Test
    void successfulDeleteIsPersistedAndRepeatedProcessingIsIdempotent() {
        AtomicInteger deletes = new AtomicInteger();
        StorageAdapter adapter = adapter((locator) -> deletes.incrementAndGet());
        StorageCleanupWorker worker = worker(adapter);
        UUID jobId = saveDueJob("success-locator");

        worker.processOne(jobId);
        worker.processOne(jobId);

        StorageCleanupJob persisted = jobs.findById(jobId).orElseThrow();
        assertThat(persisted.deletedAt()).isNotNull();
        assertThat(persisted.attemptCount()).isZero();
        assertThat(deletes).hasValue(1);
    }

    @Test
    void providerFailurePersistsBackoffAndImmediateRepollDoesNotRetryEarly() {
        AtomicInteger deletes = new AtomicInteger();
        StorageAdapter adapter = adapter((locator) -> {
            deletes.incrementAndGet();
            throw new IOException("private endpoint details must not enter persistent error text");
        });
        StorageCleanupWorker worker = worker(adapter);
        UUID jobId = saveDueJob("failure-locator");

        worker.processOne(jobId);
        StorageCleanupJob failed = jobs.findById(jobId).orElseThrow();

        assertThat(failed.deletedAt()).isNull();
        assertThat(failed.attemptCount()).isEqualTo(1);
        assertThat(failed.nextAttemptAt()).isAfter(Instant.now());
        assertThat(failed.lastError()).isEqualTo("IOException");

        worker.processOne(jobId);
        StorageCleanupJob stillFailed = jobs.findById(jobId).orElseThrow();
        assertThat(stillFailed.attemptCount()).isEqualTo(1);
        assertThat(deletes).hasValue(1);
    }

    private UUID saveDueJob(String locator) {
        UUID id = UUID.randomUUID();
        Instant due = Instant.now().minusSeconds(2);
        jobs.saveAndFlush(new StorageCleanupJob(id, tenantId, StorageProviderType.LOCAL,
                locator, "cleanup/" + id, due));
        return id;
    }

    private StorageCleanupWorker worker(StorageAdapter adapter) {
        return new StorageCleanupWorker(jobs, new StorageAdapterRegistry(List.of(adapter)), transactionManager, 20);
    }

    private static StorageAdapter adapter(DeleteAction deleteAction) {
        return new StorageAdapter() {
            @Override public StorageProviderType type() { return StorageProviderType.LOCAL; }
            @Override public String put(String objectKey, InputStream source, long length, String contentType) {
                return objectKey;
            }
            @Override public InputStream open(String locator) { return new ByteArrayInputStream(new byte[0]); }
            @Override public void delete(String locator) throws IOException { deleteAction.delete(locator); }
        };
    }

    @FunctionalInterface
    private interface DeleteAction {
        void delete(String locator) throws IOException;
    }
}
