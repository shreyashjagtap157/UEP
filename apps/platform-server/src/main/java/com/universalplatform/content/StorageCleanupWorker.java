package com.universalplatform.content;

import com.universalplatform.storage.StorageAdapterRegistry;
import com.universalplatform.storage.StorageQuotaService;
import java.time.Clock;
import java.time.Duration;
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
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(5);

    private final StorageCleanupRepository jobs;
    private final StorageAdapterRegistry adapters;
    private final StorageQuotaService storageQuota;
    private final TransactionTemplate claimTransaction;
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
        this.claimTransaction = new TransactionTemplate(transactionManager);
        this.claimTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
        UUID claimToken = UUID.randomUUID();
        Instant claimTime = clock.instant();
        try {
            Integer claimed = claimTransaction.execute(status ->
                    jobs.claimIfAvailable(id, claimToken, claimTime, claimTime.plus(CLAIM_LEASE)));
            if (claimed == null || claimed != 1) return;
        } catch (RuntimeException claimFailure) {
            log.error("Could not claim storage cleanup job {} ({})", id, claimFailure.getClass().getSimpleName());
            return;
        }

        try {
            processingTransaction.executeWithoutResult(status -> {
                StorageCleanupJob job = jobs.lockById(id).orElse(null);
                Instant now = clock.instant();
                if (job == null || !claimToken.equals(job.processingToken())
                        || job.deletedAt() != null || job.deadLetteredAt() != null
                        || job.attemptCount() >= StorageCleanupJob.MAX_ATTEMPTS
                        || job.nextAttemptAt().isAfter(now)) {
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
                    // Roll back provider/quota accounting before persisting retry metadata.
                    // The independently committed claim lease prevents another instance from
                    // taking this row in the rollback-to-backoff handoff window.
                    status.setRollbackOnly();
                    throw new StorageCleanupProcessingException(failure);
                }
            });
        } catch (RuntimeException transactionFailure) {
            Throwable reason = transactionFailure instanceof StorageCleanupProcessingException
                    && transactionFailure.getCause() != null
                    ? transactionFailure.getCause() : transactionFailure;
            String failureType = reason.getClass().getSimpleName();
            try {
                failureTransaction.executeWithoutResult(status -> {
                    StorageCleanupJob job = jobs.lockById(id).orElse(null);
                    if (job == null || !claimToken.equals(job.processingToken())
                            || job.deletedAt() != null || job.deadLetteredAt() != null
                            || job.attemptCount() >= StorageCleanupJob.MAX_ATTEMPTS) {
                        return;
                    }
                    job.failed(failureType, clock.instant());
                });
            } catch (RuntimeException persistenceFailure) {
                log.error("Could not persist storage cleanup retry for job {} (processing failure {}, retry-state failure {})",
                        id, failureType, persistenceFailure.getClass().getSimpleName());
            }
            log.warn("Storage cleanup failed for job {} ({}); retry metadata is handled independently",
                    id, failureType);
        }
    }

    private static final class StorageCleanupProcessingException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private StorageCleanupProcessingException(Exception cause) {
            super(cause.getClass().getSimpleName(), cause);
        }
    }
}
