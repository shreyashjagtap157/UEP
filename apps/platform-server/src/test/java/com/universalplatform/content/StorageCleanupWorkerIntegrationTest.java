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
import org.springframework.data.domain.PageRequest;
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
    void durableClaimLeaseExcludesConcurrentClaimsAndRecoversAfterExpiry() {
        UUID jobId = saveDueJob("claim-lease-locator");
        UUID firstToken = UUID.randomUUID();
        UUID secondToken = UUID.randomUUID();
        Instant now = Instant.now();
        TransactionTemplate claimTransaction = new TransactionTemplate(transactionManager);

        Integer firstClaim = claimTransaction.execute(status ->
                jobs.claimIfAvailable(jobId, firstToken, now, now.plusSeconds(300)));
        assertThat(firstClaim).isEqualTo(1);
        assertThat(jobs.findDueIds(Instant.now(), org.springframework.data.domain.PageRequest.of(0, 20)))
                .doesNotContain(jobId);
        Integer overlappingClaim = claimTransaction.execute(status ->
                jobs.claimIfAvailable(jobId, secondToken, Instant.now(), Instant.now().plusSeconds(300)));
        assertThat(overlappingClaim).isZero();

        jdbc.update("""
                UPDATE storage_cleanup_outbox
                SET processing_lease_expires_at = CURRENT_TIMESTAMP - interval '1 second'
                WHERE id = ?
                """, jobId);

        assertThat(jobs.findDueIds(Instant.now(), org.springframework.data.domain.PageRequest.of(0, 20)))
                .contains(jobId);
        Integer recoveredClaim = claimTransaction.execute(status ->
                jobs.claimIfAvailable(jobId, secondToken, Instant.now(), Instant.now().plusSeconds(300)));
        assertThat(recoveredClaim).isEqualTo(1);
        assertThat(jobs.findById(jobId).orElseThrow().processingToken()).isEqualTo(secondToken);
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
    void providerCleanupDeadLettersAfterTwelveFailuresAndCanBeRequeued() {
        AtomicInteger deletes = new AtomicInteger();
        StorageCleanupWorker failingWorker = worker(adapter(locator -> {
            deletes.incrementAndGet();
            throw new IOException("provider endpoint and credentials must not be retained");
        }));
        UUID jobId = saveDueJob("dead-letter-locator");

        for (int attempt = 0; attempt < StorageCleanupJob.MAX_ATTEMPTS; attempt++) {
            jdbc.update("""
                    UPDATE storage_cleanup_outbox
                    SET next_attempt_at = CURRENT_TIMESTAMP - interval '1 second'
                    WHERE id = ?
                    """, jobId);
            failingWorker.processOne(jobId);
        }

        StorageCleanupJob deadLettered = jobs.findById(jobId).orElseThrow();
        assertThat(deadLettered.attemptCount()).isEqualTo(StorageCleanupJob.MAX_ATTEMPTS);
        assertThat(deadLettered.deadLetteredAt()).isNotNull();
        assertThat(deadLettered.lastError()).isEqualTo("IOException");
        assertThat(jobs.findDueIds(Instant.now(), PageRequest.of(0, 20))).doesNotContain(jobId);

        failingWorker.processOne(jobId);
        assertThat(deletes).hasValue(StorageCleanupJob.MAX_ATTEMPTS);
        assertThat(jobs.findById(jobId).orElseThrow().attemptCount()).isEqualTo(StorageCleanupJob.MAX_ATTEMPTS);

        TransactionTemplate operatorTransaction = new TransactionTemplate(transactionManager);
        operatorTransaction.executeWithoutResult(status ->
                jobs.lockByTenantIdAndId(tenantId, jobId).orElseThrow().requeue(Instant.now()));
        assertThat(jobs.findDueIds(Instant.now(), PageRequest.of(0, 20))).contains(jobId);

        AtomicInteger successfulDeletes = new AtomicInteger();
        worker(adapter(locator -> successfulDeletes.incrementAndGet())).processOne(jobId);
        StorageCleanupJob completed = jobs.findById(jobId).orElseThrow();
        assertThat(completed.deletedAt()).isNotNull();
        assertThat(completed.deadLetteredAt()).isNull();
        assertThat(completed.attemptCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT state FROM storage_quota_reservation WHERE id = ?",
                String.class, completed.quotaReservationId())).isEqualTo("RELEASED");
        assertThat(successfulDeletes).hasValue(1);
    }

    @Test
    void enqueueRejectsAConflictingObjectIdentityForAnActiveLocator() {
        UUID jobId = saveDueJob("identity-conflict-locator");
        StorageCleanupJob existing = jobs.findById(jobId).orElseThrow();

        cleanupOutbox.enqueue(tenantId, StorageProviderType.LOCAL, existing.locator(),
                existing.objectKey(), existing.quotaReservationId(), null);

        assertThatThrownBy(() -> cleanupOutbox.enqueue(tenantId, StorageProviderType.LOCAL,
                existing.locator(), existing.objectKey() + "-different", existing.quotaReservationId(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity conflicts");

        StorageCleanupJob unchanged = jobs.findById(jobId).orElseThrow();
        assertThat(unchanged.objectKey()).isEqualTo(existing.objectKey());
        assertThat(unchanged.quotaReservationId()).isEqualTo(existing.quotaReservationId());
    }

    @Test
    void enqueueRejectsQuotaReservationReuseForAnotherPhysicalLocator() {
        UUID jobId = saveDueJob("reservation-reuse-first-locator");
        StorageCleanupJob existing = jobs.findById(jobId).orElseThrow();

        assertThatThrownBy(() -> cleanupOutbox.enqueue(tenantId, StorageProviderType.LOCAL,
                "reservation-reuse-second-locator", existing.objectKey(),
                existing.quotaReservationId(), null))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(jobs.count()).isEqualTo(1L);
    }

    @Test
    void enqueueAllowsANewObjectGenerationAfterThePreviousLocatorWasDeleted() {
        AtomicInteger deletes = new AtomicInteger();
        StorageCleanupWorker worker = worker(adapter(locator -> deletes.incrementAndGet()));
        UUID oldJobId = saveDueJob("reused-locator");
        worker.processOne(oldJobId);
        StorageCleanupJob oldJob = jobs.findById(oldJobId).orElseThrow();
        assertThat(oldJob.deletedAt()).isNotNull();

        cleanupOutbox.enqueue(tenantId, StorageProviderType.LOCAL,
                "reused-locator", oldJob.objectKey(), oldJob.quotaReservationId(), null);
        assertThat(jobs.findById(oldJobId).orElseThrow().deletedAt()).isNotNull();

        String newObjectKey = "cleanup/new-generation/" + UUID.randomUUID();
        UUID newReservationId = storageQuota.reserve(tenantId, StorageProviderType.LOCAL,
                "cleanup:new-generation:" + UUID.randomUUID(), newObjectKey, 512);
        storageQuota.commit(tenantId, newReservationId, 512, "reused-locator", newObjectKey);

        cleanupOutbox.enqueue(tenantId, StorageProviderType.LOCAL, "reused-locator",
                newObjectKey, newReservationId, null);

        StorageCleanupJob pending = jobs.findById(oldJobId).orElseThrow();
        assertThat(pending.deletedAt()).isNull();
        assertThat(pending.id()).isEqualTo(oldJobId);
        assertThat(pending.objectKey()).isEqualTo(newObjectKey);
        assertThat(pending.quotaReservationId()).isEqualTo(newReservationId);
        assertThat(pending.attemptCount()).isZero();
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
