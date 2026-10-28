package com.universalplatform.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.universalplatform.audit.AuditService;
import com.universalplatform.enrollment.EnrollmentDirectory;
import com.universalplatform.security.ActorContext;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.MembershipDirectory;
import com.universalplatform.identity.MembershipStatus;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.notification.NotificationPublisher;
import com.universalplatform.security.TenantContext;
import com.universalplatform.storage.StorageAdapter;
import com.universalplatform.storage.StorageAdapterRegistry;
import com.universalplatform.storage.StorageProviderType;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.universalplatform.liveclass.LiveClassDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class RecordingPurgeLifecycleTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID liveClassId = UUID.randomUUID();
    private final UUID classSessionId = UUID.randomUUID();
    private final UUID recordingId = UUID.randomUUID();
    private final UUID callerMembershipId = UUID.randomUUID();
    private final UUID visibleBatchId = UUID.randomUUID();
    private final UUID recordingBatchId = UUID.randomUUID();

    @Mock TenantContext tenants;
    @Mock ActorContext actors;
    @Mock AuthorizationService auth;
    @Mock MembershipDirectory memberships;
    @Mock EnrollmentDirectory enrollment;
    @Mock LiveClassDirectory liveClasses;
    @Mock RecordingRepository recordings;
    @Mock RecordingAssetRepository assets;
    @Mock RecordingStoragePolicyRepository policies;
    @Mock RecordingMediaProvider media;
    @Mock RecordingThumbnailService thumbnails;
    @Mock StorageAdapterRegistry adapters;
    @Mock RecordingWorkerLock workerLock;
    @Mock PlatformTransactionManager transactionManager;
    @Mock AuditService audit;
    @Mock NotificationPublisher notifications;
    @Mock StorageAdapter localStorage;

    private RecordingService service;
    private Recording recording;
    private RecordingAsset master;
    private RecordingAsset thumbnail;

    @BeforeEach
    void setUp() {
        service = new RecordingService(tenants, actors, auth, memberships, enrollment, liveClasses,
                recordings, assets, policies, media, thumbnails, adapters, workerLock, transactionManager,
                audit, notifications, "./target/test-recording-processing", "test-playback-secret");
        lenient().when(tenants.requireTenantId()).thenReturn(tenantId);
        lenient().when(auth.has(PermissionKey.RECORDINGS_MANAGE)).thenReturn(false);
        lenient().when(liveClasses.require(liveClassId)).thenReturn(new LiveClassDirectory.LiveClassReference(
                liveClassId, classSessionId, recordingBatchId, "recording-room", null, null, 0, 0,
                false, false, null, null));
        lenient().when(memberships.current()).thenReturn(new MembershipDirectory.MembershipReference(
                callerMembershipId, UUID.randomUUID(), "recording-viewer", "viewer@example.invalid",
                "Recording Viewer", null, MembershipStatus.ACTIVE));

        Instant oldReadyAt = Instant.now().minus(Duration.ofDays(100));
        recording = new Recording(recordingId, tenantId, liveClassId, classSessionId, "recording-room",
                RecordingQualityPreset.BALANCED, StorageProviderType.LOCAL, oldReadyAt);
        recording.ready("master-locator", "master-object-key", "a".repeat(64), 1024, 10, oldReadyAt);
        master = new RecordingAsset(UUID.randomUUID(), tenantId, recordingId, RecordingAssetKind.MASTER,
                StorageProviderType.LOCAL, "master-locator", "master-object-key", "video/mp4", 1024,
                "a".repeat(64), oldReadyAt);
        thumbnail = new RecordingAsset(UUID.randomUUID(), tenantId, recordingId, RecordingAssetKind.THUMBNAIL,
                StorageProviderType.LOCAL, "thumbnail-locator", "thumbnail-object-key", "image/jpeg", 128,
                "b".repeat(64), oldReadyAt);

        RecordingStoragePolicy policy = new RecordingStoragePolicy(tenantId, oldReadyAt);
        policy.retentionDays = 30;
        policy.deletedObjectGraceDays = 0;

        when(recordings.findByTenantIdAndId(tenantId, recordingId)).thenReturn(Optional.of(recording));
        when(policies.findById(tenantId)).thenReturn(Optional.of(policy));
        lenient().when(assets.findAllByTenantIdAndRecordingId(tenantId, recordingId)).thenReturn(List.of(master, thumbnail));
        lenient().when(adapters.require(StorageProviderType.LOCAL)).thenReturn(localStorage);
    }

    @Test
    void directReadPlaybackAndStreamEnforceActiveBatchVisibility() throws Exception {
        when(enrollment.activeBatchIdsForMembership(callerMembershipId)).thenReturn(Set.of(visibleBatchId));

        assertThatThrownBy(() -> service.get(recordingId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Recording not found");
        assertThatThrownBy(() -> service.playback(recordingId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Recording not found");

        when(enrollment.activeBatchIdsForMembership(callerMembershipId)).thenReturn(Set.of(recordingBatchId));
        RecordingService.PlaybackView playback = service.playback(recordingId);
        assertThat(service.get(recordingId).id()).isEqualTo(recordingId);
        String token = playback.streamUrl().substring(playback.streamUrl().indexOf("token=") + "token=".length());

        // Enrollment revocation after token issuance must stop playback at stream-open time.
        when(enrollment.activeBatchIdsForMembership(callerMembershipId)).thenReturn(Set.of());
        assertThatThrownBy(() -> service.openStream(recordingId, token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Recording not found");
        verify(adapters, never()).require(StorageProviderType.LOCAL);
    }

    @Test
    void recordingManagerCanReadAnyBatchRecordingWithinTheTenant() {
        when(auth.has(PermissionKey.RECORDINGS_MANAGE)).thenReturn(true);

        assertThat(service.get(recordingId).id()).isEqualTo(recordingId);
        verify(enrollment, never()).activeBatchIdsForMembership(callerMembershipId);
    }

    @Test
    void purgeReloadsAndPersistsDeletedStateOnlyAfterAllProviderObjectsDelete() throws Exception {
        service.purgeDue(recording);

        assertThat(recording.status()).isEqualTo(RecordingProcessingStatus.DELETED);
        assertThat(recording.storageLocator()).isNull();
        assertThat(recording.objectKey()).isNull();
        verify(workerLock).acquire(tenantId);
        verify(localStorage).delete("master-locator");
        verify(localStorage).delete("thumbnail-locator");
        verify(assets).deleteAll(List.of(master, thumbnail));
        verify(recordings).save(recording);
        verify(audit).record(tenantId, "system", "RECORDING_PURGED", "recording", recordingId.toString());
    }

    @Test
    void providerFailureLeavesRecordingAndAssetMetadataEligibleForRetry() throws Exception {
        doThrow(new IOException("provider unavailable")).when(localStorage).delete("thumbnail-locator");

        service.purgeDue(recording);

        assertThat(recording.status()).isEqualTo(RecordingProcessingStatus.READY);
        assertThat(recording.storageLocator()).isEqualTo("master-locator");
        assertThat(recording.objectKey()).isEqualTo("master-object-key");
        verify(assets, never()).deleteAll(org.mockito.ArgumentMatchers.<RecordingAsset>anyList());
        verify(recordings, never()).save(recording);
        verify(audit, never()).record(tenantId, "system", "RECORDING_PURGED", "recording", recordingId.toString());
    }

    @Test
    void purgeDoesNothingUntilRetentionAndGracePeriodHasElapsed() throws Exception {
        RecordingStoragePolicy policy = new RecordingStoragePolicy(tenantId, Instant.now());
        policy.retentionDays = 365;
        policy.deletedObjectGraceDays = 30;
        when(policies.findById(tenantId)).thenReturn(Optional.of(policy));

        service.purgeDue(recording);

        assertThat(recording.status()).isEqualTo(RecordingProcessingStatus.READY);
        verify(localStorage, never()).delete(anyString());
        verify(assets, never()).deleteAll(org.mockito.ArgumentMatchers.<RecordingAsset>anyList());
        verify(recordings, never()).save(recording);
    }
}
