package com.universalplatform.communication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.universalplatform.security.TenantExecutionContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AnnouncementPublishingWorkerTest {
    @Mock AnnouncementRepository announcements;
    @Mock AnnouncementService service;
    @Mock TenantExecutionContext tenantExecution;

    private AnnouncementPublishingWorker worker;

    @BeforeEach
    void setUp() {
        worker = new AnnouncementPublishingWorker(announcements, service, tenantExecution);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(tenantExecution).run(any(UUID.class), any(Runnable.class));
    }

    @Test
    void oneInvalidScheduledAnnouncementDoesNotBlockLaterDueAnnouncements() {
        UUID tenantId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();
        when(announcements.findDuePublishIds(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(firstId, secondId, thirdId));
        when(announcements.findById(firstId)).thenReturn(Optional.of(announcement(firstId, tenantId)));
        when(announcements.findById(secondId)).thenReturn(Optional.of(announcement(secondId, tenantId)));
        when(announcements.findById(thirdId)).thenReturn(Optional.of(announcement(thirdId, tenantId)));
        doThrow(new IllegalStateException("invalid audience target"))
                .when(service).publishScheduled(firstId);

        worker.publishDue();

        verify(service).publishScheduled(firstId);
        verify(service).recordPublicationFailure(firstId, "IllegalStateException");
        verify(service).publishScheduled(secondId);
        verify(service).publishScheduled(thirdId);
    }

    @Test
    void oneInvalidExpiryDoesNotBlockLaterDueAnnouncements() {
        UUID tenantId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();
        when(announcements.findDueExpiryIds(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(firstId, secondId, thirdId));
        when(announcements.findById(firstId)).thenReturn(Optional.of(announcement(firstId, tenantId)));
        when(announcements.findById(secondId)).thenReturn(Optional.of(announcement(secondId, tenantId)));
        when(announcements.findById(thirdId)).thenReturn(Optional.of(announcement(thirdId, tenantId)));
        doThrow(new IllegalStateException("expiry persistence failure"))
                .when(service).expireScheduled(firstId);

        worker.expireDue();

        verify(service).expireScheduled(firstId);
        verify(service).recordExpiryFailure(firstId, "IllegalStateException");
        verify(service).expireScheduled(secondId);
        verify(service).expireScheduled(thirdId);
    }

    private static Announcement announcement(UUID id, UUID tenantId) {
        Instant now = Instant.now();
        return new Announcement(id, tenantId, null, "Worker test", "body",
                AnnouncementPriority.NORMAL, now.minusSeconds(60), now.plusSeconds(3600),
                false, "worker-test", now.minusSeconds(120));
    }
}
