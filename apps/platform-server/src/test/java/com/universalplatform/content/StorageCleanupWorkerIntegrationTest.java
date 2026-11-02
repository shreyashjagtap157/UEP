package com.universalplatform.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.universalplatform.storage.StorageAdapter;
import com.universalplatform.storage.StorageAdapterRegistry;
import com.universalplatform.storage.StorageQuotaService;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "platform.storage.cleanup-poll-ms=3600000",
        "platform.storage.cleanup.enabled=false"
})
class StorageCleanupWorkerIntegrationTest {
    private final UUID tenantId = UUID.randomUUID();

    @Autowired JdbcTemplate jdbc;
    @Autowired StorageCleanupRepository jobs;
    @Autowired StorageQuotaService storageQuota;
    @Autowired StorageCleanupOutboxService cleanupOutbox;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void seedTenant() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, ?, 'Storage Cleanup Test', 'ACTIVE')",
                tenantId, "storage-cleanup-" + tenantId);
    }

    @AfterEach
    void cleanTenant() {
        List<UUID> outstanding = jdbc.query("""
                SELECT id FROM storage_quota_reservation
                WHERE tenant_id = ? AND state IN ('RESERVED', 'COMMITTED')
                """, (rs, row) -> rs.getObject(1, UUID.class), tenantId);
        for (UUID reservationId : outstanding) storageQuota.release(tenantId, reservationId);
        jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
    }

    @Test
    void enqueueWithinCurrentTransactionRollsBackWithCaller() {
        UUID reservationId = storageQuota.reserve(tenantId, StorageProviderType.LOCAL,
                "recording-cleanup-rollback:" + UUID.randomUUID(), "recording/cleanup-rollback", 512);
        String locator = "recording-cleanup-rollback-" + UUID.randomUUID();
        storageQuota.commit(tenantId, reservationId, 512, locator, "recording/cleanup-rollback");
        TransactionTemplate callerTransaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> callerTransaction.executeWithoutResult(status -> {
            cleanupOutbox.enqueueWithinCurrentTransaction(tenantId, StorageProviderType.LOCAL,
                    locator, "recording/cleanup-rollback", reservationId, null);
            throw new IllegalStateException("abort recording metadata retirement");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("abort recording metadata retirement");

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM storage_cleanup_outbox
                WHERE tenant_id = ? AND provider = 'LOCAL' AND locator = ?
                """, Long.class, tenantId, locator)).isZero();

        callerTransaction.executeWithoutResult(status -> cleanupOutbox.enqueueWithinCurrentTransaction(
                tenantId, StorageProviderType.LOCAL, locator, "recording/cleanup-rollback", reservationId, null));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM storage_cleanup_outbox
                WHERE tenant_id = ? AND provider = 'LOCAL' AND locator = ?
                """, Long.class, tenantId, locator)).isEqualTo(1L);
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
        assertThat(persisted.quotaReservationId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT state FROM storage_quota_reservation WHERE id = ?",
                String.class, persisted.quotaReservationId())).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT released_bytes FROM storage_quota_reservation WHERE id = ?",
                Long.class, persisted.quotaReservationId())).isEqualTo(512L);
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

    @Test
    void quotaFailurePersistsBackoffInIndependentTransaction() {
        jdbc.update("""
                UPDATE storage_provider_quota
                SET class_a_daily_limit = 0
                WHERE provider = 'LOCAL'
                """);
        AtomicInteger deletes = new AtomicInteger();
        StorageCleanupWorker worker = worker(adapter(locator -> deletes.incrementAndGet()));
        UUID jobId = saveDueJob("quota-budget-locator");

        try {
            worker.processOne(jobId);

            StorageCleanupJob failed = jobs.findById(jobId).orElseThrow();
            assertThat(failed.deletedAt()).isNull();
            assertThat(failed.attemptCount()).isEqualTo(1);
            assertThat(failed.nextAttemptAt()).isAfter(Instant.now());
            assertThat(failed.lastError()).isEqualTo("StorageQuotaException");
            assertThat(deletes).hasValue(0);
        } finally {
            jdbc.update("""
                    UPDATE storage_provider_quota
                    SET class_a_daily_limit = NULL
                    WHERE provider = 'LOCAL'
                    """);
        }
    }

    private UUID saveDueJob(String locator) {
        UUID id = UUID.randomUUID();
        String objectKey = "cleanup/" + id;
        UUID reservationId = storageQuota.reserve(tenantId, StorageProviderType.LOCAL,
                "cleanup:" + id, objectKey, 512);
        storageQuota.commit(tenantId, reservationId, 512, locator, objectKey);
        Instant due = Instant.now().minusSeconds(2);
        jobs.saveAndFlush(new StorageCleanupJob(id, tenantId, StorageProviderType.LOCAL,
                locator, objectKey, reservationId, due));
        return id;
    }

    private StorageCleanupWorker worker(StorageAdapter adapter) {
        return new StorageCleanupWorker(jobs, new StorageAdapterRegistry(List.of(adapter)), storageQuota, transactionManager, 20);
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
