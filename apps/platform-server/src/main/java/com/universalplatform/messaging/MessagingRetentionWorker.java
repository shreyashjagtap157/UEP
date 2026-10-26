package com.universalplatform.messaging;

import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class MessagingRetentionWorker {
    private final MessageTenantPolicyRepository policies;
    private final MessagingRetentionService retention;
    private final Clock clock = Clock.systemUTC();

    MessagingRetentionWorker(MessageTenantPolicyRepository policies, MessagingRetentionService retention) {
        this.policies = policies;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${platform.messaging.retention-poll-ms:3600000}")
    void purgeExpiredMessagesAndReports() {
        Instant now = clock.instant();
        for (MessageTenantPolicy policy : policies.findAllByOrderByTenantIdAsc()) {
            retention.purgeTenant(policy.tenantId(), now);
        }
    }
}
