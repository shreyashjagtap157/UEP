package com.universalplatform.content;

import com.universalplatform.storage.StorageAdapterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class StorageCleanupWorker {
    private static final Logger log = LoggerFactory.getLogger(StorageCleanupWorker.class);

    private final StorageCleanupRepository jobs;
    private final StorageAdapterRegistry adapters;
    private final TransactionTemplate processingTransaction;
    private final TransactionTemplate failureTransaction;
    private final int batchSize;
    private final Clock clock = Clock.systemUTC();

    StorageCleanupWorker(StorageCleanupRepository jobs, StorageAdapterRegistry adapters,
                         PlatformTransactionManager transactionManager,
                         @Value("${platform.storage.cleanup-batch-size:50}") int batchSize) {
        this.jobs = jobs;
        this.adapters = adapters;
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
                    adapters.require(job.provider()).delete(job.locator());
                    job.markDeleted(clock.instant());
                } catch (Exception exception) {
                    // Marking failed is committed inside this transaction; external deletion is
                    // idempotent, so an ambiguous commit can safely be retried.
                    job.failed(exception.getClass().getSimpleName(), clock.instant());
                    log.warn("Storage cleanup failed for tenant {} cleanup-job {} (provider {})",
                            job.tenantId(), job.id(), job.provider(), exception);
                }
            });
        } catch (RuntimeException transactionFailure) {
            log.error("Storage cleanup transaction failed for job {}", id, transactionFailure);
        }
    }
}
