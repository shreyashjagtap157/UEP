package com.universalplatform.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reports ledger/aggregate drift without silently rewriting accounting history. */
@Component
@ConditionalOnProperty(prefix = "platform.background-workers", name = "enabled", havingValue = "true", matchIfMissing = true)
class StorageQuotaReconciliationWorker {
    private static final Logger log = LoggerFactory.getLogger(StorageQuotaReconciliationWorker.class);
    private final StorageQuotaService quota;

    StorageQuotaReconciliationWorker(StorageQuotaService quota) {
        this.quota = quota;
    }

    @Scheduled(fixedDelayString = "${platform.storage.quota-reconciliation-poll-ms:21600000}")
    void reconcile() {
        List<StorageQuotaService.UsageDrift> drift = quota.reconciliationDrift();
        for (StorageQuotaService.UsageDrift item : drift) {
            log.error("Storage quota ledger drift detected: scope={} key={} aggregate reserved={} ledger reserved={} aggregate committed={} ledger committed={}",
                    item.scope(), item.scopeKey(), item.aggregateReserved(), item.ledgerReserved(),
                    item.aggregateCommitted(), item.ledgerCommitted());
        }

        // Never auto-release these rows. A provider may have accepted a PUT before its
        // response was lost, so manual provider reconciliation is required before release.
        for (StorageQuotaService.StaleReservation reservation
                : quota.staleRecordingReservations(Instant.now().minus(Duration.ofHours(1)))) {
            log.error("Stale recording storage reservation requires provider reconciliation: "
                            + "reservation={} tenant={} provider={} key={} objectKey={} createdAt={}",
                    reservation.id(), reservation.tenantId(), reservation.provider(),
                    reservation.reservationKey(), reservation.objectKey(), reservation.createdAt());
        }
    }
}
