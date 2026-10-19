package com.universalplatform.assessment;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the attempt expiry transition in an independent transaction. The caller (autosave/submit)
 * rejects the write with IllegalArgumentException-free IllegalStateException afterwards, which rolls its
 * own transaction back; without the REQUIRES_NEW commit here the EXPIRED transition would be lost.
 */
@Service
class AttemptExpiryService {
    private final AttemptRepository attempts;

    AttemptExpiryService(AttemptRepository attempts) {
        this.attempts = attempts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void persistExpired(UUID tenantId, UUID attemptId, Instant now) {
        attempts.findByTenantIdAndId(tenantId, attemptId).ifPresent(attempt -> {
            if (attempt.status() == AttemptStatus.IN_PROGRESS) {
                attempt.submit(now, AttemptStatus.EXPIRED);
                attempts.save(attempt);
            }
        });
    }
}
