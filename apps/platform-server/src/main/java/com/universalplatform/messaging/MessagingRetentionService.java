package com.universalplatform.messaging;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MessagingRetentionService {
    private static final int BATCH_SIZE = 500;
    private final MessageTenantPolicyRepository policies;
    private final MessageReportRepository reports;
    private final MessageRecordRepository messages;
    private final MessageRateWindowRepository rateWindows;

    MessagingRetentionService(MessageTenantPolicyRepository policies, MessageReportRepository reports,
                              MessageRecordRepository messages, MessageRateWindowRepository rateWindows) {
        this.policies = policies;
        this.reports = reports;
        this.messages = messages;
        this.rateWindows = rateWindows;
    }

    @Transactional
    int purgeTenant(UUID tenantId, Instant now) {
        MessageTenantPolicy policy = policies.findById(tenantId).orElse(null);
        if (policy == null) return 0;

        int removed = 0;
        int count;
        Instant reportCutoff = now.minus(policy.reportRetentionDays(), ChronoUnit.DAYS);
        do {
            count = reports.deleteExpiredByTenantId(tenantId, reportCutoff, BATCH_SIZE);
            removed += count;
        } while (count == BATCH_SIZE);

        Instant messageCutoff = now.minus(policy.messageRetentionDays(), ChronoUnit.DAYS);
        do {
            count = messages.deleteExpiredUnreported(tenantId, messageCutoff, BATCH_SIZE);
            removed += count;
        } while (count == BATCH_SIZE);

        rateWindows.deleteExpired(now.minus(2, ChronoUnit.DAYS));
        return removed;
    }
}
