package com.universalplatform.communication;

import com.universalplatform.security.TenantExecutionContext;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "platform.background-workers", name = "enabled", havingValue = "true", matchIfMissing = true)
class AnnouncementPublishingWorker {
    private static final Logger log = LoggerFactory.getLogger(AnnouncementPublishingWorker.class);
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
            try {
                Announcement item = announcements.findById(id).orElse(null);
                if (item != null) tenantExecution.run(item.tenantId(), () -> service.publishScheduled(id));
            } catch (RuntimeException failure) {
                recordPublicationFailure(id, failure);
            }
        }
    }

    @Scheduled(fixedDelayString = "${platform.announcements.expiry-poll-ms:60000}")
    void expireDue() {
        Instant now = clock.instant();
        for (UUID id : announcements.findDueExpiryIds(now, PageRequest.of(0, 100))) {
            try {
                Announcement item = announcements.findById(id).orElse(null);
                if (item != null) tenantExecution.run(item.tenantId(), () -> service.expireScheduled(id));
            } catch (RuntimeException failure) {
                recordExpiryFailure(id, failure);
            }
        }
    }

    private void recordPublicationFailure(UUID id, RuntimeException failure) {
        log.error("Scheduled announcement publication failed for {} ({})",
                id, failure.getClass().getSimpleName());
        try {
            announcements.findById(id).ifPresent(item -> {
                try {
                    tenantExecution.run(item.tenantId(),
                            () -> service.recordPublicationFailure(id, failure.getClass().getSimpleName()));
                } catch (RuntimeException persistenceFailure) {
                    log.error("Could not persist announcement publication retry for {} ({})",
                            id, persistenceFailure.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException lookupFailure) {
            log.error("Could not locate announcement publication retry state for {} ({})",
                    id, lookupFailure.getClass().getSimpleName());
        }
    }

    private void recordExpiryFailure(UUID id, RuntimeException failure) {
        log.error("Scheduled announcement expiry failed for {} ({})",
                id, failure.getClass().getSimpleName());
        try {
            announcements.findById(id).ifPresent(item -> {
                try {
                    tenantExecution.run(item.tenantId(),
                            () -> service.recordExpiryFailure(id, failure.getClass().getSimpleName()));
                } catch (RuntimeException persistenceFailure) {
                    log.error("Could not persist announcement expiry retry for {} ({})",
                            id, persistenceFailure.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException lookupFailure) {
            log.error("Could not locate announcement expiry retry state for {} ({})",
                    id, lookupFailure.getClass().getSimpleName());
        }
    }
}
