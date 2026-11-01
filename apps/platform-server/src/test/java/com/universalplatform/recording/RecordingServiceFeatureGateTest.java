package com.universalplatform.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.universalplatform.audit.AuditService;
import com.universalplatform.enrollment.EnrollmentDirectory;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.MembershipDirectory;
import com.universalplatform.liveclass.LiveClassDirectory;
import com.universalplatform.notification.NotificationPublisher;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import com.universalplatform.storage.StorageAdapterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

class RecordingServiceFeatureGateTest {
    private final TenantContext tenants = mock(TenantContext.class);
    private final ActorContext actors = mock(ActorContext.class);
    private final AuthorizationService auth = mock(AuthorizationService.class);
    private final MembershipDirectory memberships = mock(MembershipDirectory.class);
    private final EnrollmentDirectory enrollment = mock(EnrollmentDirectory.class);
    private final LiveClassDirectory liveClasses = mock(LiveClassDirectory.class);
    private final RecordingRepository recordings = mock(RecordingRepository.class);
    private final RecordingAssetRepository assets = mock(RecordingAssetRepository.class);
    private final RecordingStoragePolicyRepository policies = mock(RecordingStoragePolicyRepository.class);
    private final RecordingMediaProvider media = mock(RecordingMediaProvider.class);
    private final RecordingThumbnailService thumbnails = mock(RecordingThumbnailService.class);
    private final StorageAdapterRegistry adapters = mock(StorageAdapterRegistry.class);
    private final RecordingWorkerLock workerLock = mock(RecordingWorkerLock.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final AuditService audit = mock(AuditService.class);
    private final NotificationPublisher notifications = mock(NotificationPublisher.class);

    @Test
    void cannotRequestRecordingWhenLiveKitIsDisabled() {
        RecordingService service = serviceWithLiveKitDisabled();

        assertThatThrownBy(() -> service.request(UUID.randomUUID(), RecordingQualityPreset.BALANCED))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verifyNoInteractions(tenants, liveClasses, recordings, assets, policies, media, audit, notifications);
    }

    @Test
    void cannotStopRemoteRecordingWhenLiveKitIsDisabled() {
        RecordingService service = serviceWithLiveKitDisabled();

        assertThatThrownBy(() -> service.stop(UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verifyNoInteractions(recordings, media, audit);
    }

    private RecordingService serviceWithLiveKitDisabled() {
        RecordingService service = new RecordingService(tenants, actors, auth, memberships, enrollment, liveClasses,
                recordings, assets, policies, media, thumbnails, adapters, workerLock, transactions, audit,
                notifications, "./target/test-recording-processing", "test-playback-secret-0123456789abcdef");
        ReflectionTestUtils.setField(service, "liveKitEnabled", false);
        return service;
    }
}
