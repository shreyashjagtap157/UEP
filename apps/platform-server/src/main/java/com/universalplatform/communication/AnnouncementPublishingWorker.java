package com.universalplatform.communication;

import com.universalplatform.security.TenantExecutionContext;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
class AnnouncementPublishingWorker {
    private final AnnouncementRepository announcements;
    private final AnnouncementService service;
    private final TenantExecutionContext tenantExecution;
    private final Clock clock = Clock.systemUTC();

    AnnouncementPublishingWorker(AnnouncementRepository announcements, AnnouncementService service,
                                 TenantExecutionContext tenantExecution) {
        this.announcements = announcements;
        this.service = service;
        this.tenantExecution = tenantExecution;
    }

    @Scheduled(fixedDelayString = "${platform.announcements.publish-poll-ms:1000}")
    void publishDue() {
        Instant now = clock.instant();
        for (UUID id : announcements.findDuePublishIds(now, PageRequest.of(0, 50))) {
            announcements.findById(id).ifPresent(item -> tenantExecution.run(item.tenantId(), () -> service.publishScheduled(id)));
        }
    }

    @Scheduled(fixedDelayString = "${platform.announcements.expiry-poll-ms:60000}")
    void expireDue() {
        Instant now = clock.instant();
        for (UUID id : announcements.findDueExpiryIds(now, PageRequest.of(0, 100))) {
            announcements.findById(id).ifPresent(item -> tenantExecution.run(item.tenantId(), () -> service.expireScheduled(id)));
        }
    }
}
