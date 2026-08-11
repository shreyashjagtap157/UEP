package com.universalplatform.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditEventRepository events;
    private final Clock clock = Clock.systemUTC();

    AuditService(AuditEventRepository events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID tenantId, String actorSubject, String action, String resourceType, String resourceId) {
        events.save(new AuditEvent(UUID.randomUUID(), tenantId, actorSubject, action, resourceType, resourceId, clock.instant()));
    }
}
