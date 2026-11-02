package com.universalplatform.content;

import com.universalplatform.storage.StorageAdapterRegistry;
import com.universalplatform.storage.StorageQuotaService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(prefix = "platform.storage.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
class StorageCleanupWorker {
    private static final Logger log = LoggerFactory.getLogger(StorageCleanupWorker.class);

    private final StorageCleanupRepository jobs;
    private final StorageAdapterRegistry adapters;
    private final StorageQuotaService storageQuota;
    private final TransactionTemplate processingTransaction;
    private final TransactionTemplate failureTransaction;
    private final int batchSize;
    private final Clock clock = Clock.systemUTC();

    StorageCleanupWorker(StorageCleanupRepository jobs, StorageAdapterRegistry adapters,
                         StorageQuotaService storageQuota, PlatformTransactionManager transactionManager,
                         @Value("${platform.storage.cleanup-batch-size:50}") int batchSize) {
        this.jobs = jobs;
        this.adapters = adapters;
        this.storageQuota = storageQuota;
        this.processingTransaction = new TransactionTemplate(transactionManager);
        this.processingTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.failureTransaction = new TransactionTemplate(transactionManager);
        this.failureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.batchSize = Math.max(1, Math.min(500, batchSize));
    }

    @Scheduled(fixedDelayString = "${platform.storage.cleanup-poll-ms:5000}")
    void poll() {
        for (UUID id : jobs.findDueIds(clock.instant(), PageRequest.of(0, batchSize))) {
            processOne(id);
        }
    }

    void processOne(UUID id) {
        try {
            processingTransaction.executeWithoutResult(status -> {
                StorageCleanupJob job = jobs.lockById(id).orElse(null);
                if (job == null || job.deletedAt() != null || job.nextAttemptAt().isAfter(clock.instant())) {
                    return;
                }
                try {
                    if (job.quotaReservationId() == null) {
                        throw new IllegalStateException("Pending storage cleanup has no quota reservation; refusing unaccounted deletion");
                    }
                    storageQuota.authorizeDelete(job.tenantId(), job.provider());
                    storageQuota.consumeProviderDeleteOperations(job.tenantId(), job.provider(),
                            StorageQuotaService.PROVIDER_OPERATION_MAX_ATTEMPTS);
                    adapters.require(job.provider()).delete(job.locator());
                    storageQuota.release(job.tenantId(), job.quotaReservationId());
                    job.markDeleted(clock.instant());
                } catch (Exception failure) {
                    // Roll back the processing transaction before persisting retry metadata.
                    // A REQUIRED quota-ledger exception can mark this transaction rollback-only
                    // even when caught, so the backoff write must use its own transaction.
                    status.setRollbackOnly();
                    throw new StorageCleanupProcessingException(failure);
                }
            });
        } catch (RuntimeException transactionFailure) {
            Throwable reason = transactionFailure instanceof StorageCleanupProcessingException
                    && transactionFailure.getCause() != null
                    ? transactionFailure.getCause() : transactionFailure;
            try {
                failureTransaction.executeWithoutResult(status -> {
                    StorageCleanupJob job = jobs.lockById(id).orElse(null);
                    if (job == null || job.deletedAt() != null || job.nextAttemptAt().isAfter(clock.instant())) {
                        return;
                    }
                    job.failed(reason.getClass().getSimpleName(), clock.instant());
                });
            } catch (RuntimeException persistenceFailure) {
                transactionFailure.addSuppressed(persistenceFailure);
                log.error("Could not persist storage cleanup backoff for job {}", id, transactionFailure);
            }
            log.warn("Storage cleanup failed for job {} ({}); retry metadata is handled independently",
                    id, reason.getClass().getSimpleName(), reason);
        }
    }

    private static final class StorageCleanupProcessingException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private StorageCleanupProcessingException(Exception cause) {
            super(cause.getClass().getSimpleName(), cause);
        }
    }
}
