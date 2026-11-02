package com.universalplatform.storage;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reports ledger/aggregate drift without silently rewriting accounting history. */
@Component
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
    }
}
