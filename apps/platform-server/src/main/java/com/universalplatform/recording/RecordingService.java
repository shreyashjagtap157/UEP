package com.universalplatform.recording;

import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.*;
import com.universalplatform.enrollment.EnrollmentDirectory;
import com.universalplatform.liveclass.LiveClassDirectory;
import com.universalplatform.notification.NotificationEventType;
import com.universalplatform.notification.NotificationPublisher;
import com.universalplatform.security.*;
import com.universalplatform.storage.*;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
class RecordingService {
    private static final Logger log = LoggerFactory.getLogger(RecordingService.class);

    private final TenantContext tenants;
    private final ActorContext actors;
    private final AuthorizationService auth;
    private final MembershipDirectory memberships;
    private final EnrollmentDirectory enrollment;
    private final LiveClassDirectory liveClasses;
    private final RecordingRepository recordings;
    private final RecordingAssetRepository assets;
    private final RecordingStoragePolicyRepository policies;
    private final RecordingMediaProvider media;
    private final RecordingThumbnailService thumbnails;
    private final StorageAdapterRegistry adapters;
    private final StorageQuotaService storageQuota;
    private final StorageCleanupQueue cleanupQueue;
    private final RecordingWorkerLock workerLock;
    private final TransactionTemplate claimTemplate;
    private final AuditService audit;
    private final NotificationPublisher notifications;
    private final Path processingRoot;
    private final byte[] playbackSecret;
    @Value("${platform.live-learning.livekit.enabled:true}")
    private boolean liveKitEnabled = true;
    private final Clock clock = Clock.systemUTC();

    RecordingService(TenantContext tenants, ActorContext actors, AuthorizationService auth,
            MembershipDirectory memberships, EnrollmentDirectory enrollment, LiveClassDirectory liveClasses,
            RecordingRepository recordings,
            RecordingAssetRepository assets, RecordingStoragePolicyRepository policies, RecordingMediaProvider media,
            RecordingThumbnailService thumbnails, StorageAdapterRegistry adapters, StorageQuotaService storageQuota,
            StorageCleanupQueue cleanupQueue, RecordingWorkerLock workerLock,
            PlatformTransactionManager transactions,
            AuditService audit, NotificationPublisher notifications,
            @Value("${platform.recording.processing-root:./var/recordings}") String processingRoot,
            @Value("${platform.recording.playback-secret:change-me-in-production}") String playbackSecret) {
        this.tenants = tenants;
        this.actors = actors;
        this.auth = auth;
        this.memberships = memberships;
        this.enrollment = enrollment;
        this.liveClasses = liveClasses;
        this.recordings = recordings;
        this.assets = assets;
        this.policies = policies;
        this.media = media;
        this.thumbnails = thumbnails;
        this.adapters = adapters;
        this.storageQuota = storageQuota;
        this.cleanupQueue = cleanupQueue;
        this.workerLock = workerLock;
        this.claimTemplate = new TransactionTemplate(transactions);
        this.audit = audit;
        this.notifications = notifications;
        this.processingRoot = Path.of(processingRoot).toAbsolutePath().normalize();
        this.playbackSecret = playbackSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Transactional
    RecordingView request(UUID liveClassId, RecordingQualityPreset quality) {
        auth.require(PermissionKey.RECORDINGS_MANAGE);
        requireLiveKitEnabled();
        UUID t = tenants.requireTenantId();
        var lc = liveClasses.require(liveClassId);
        if (lc.status() != com.universalplatform.liveclass.LiveClassStatus.LIVE)
            throw new IllegalStateException("Recording can only be requested while the live classroom is live");
        if (recordings.findByTenantIdAndLiveClassId(t, liveClassId)
                .filter(r -> r.status() != RecordingProcessingStatus.DELETED).isPresent())
            throw new IllegalStateException("A recording already exists for this live class");
        var policy = policies.findById(t).orElseGet(() -> new RecordingStoragePolicy(t, clock.instant()));
        storageQuota.authorizeWrite(t, policy.hotProvider);
        Recording r = recordings.save(new Recording(UUID.randomUUID(), t, liveClassId, lc.classSessionId(),
                lc.roomName(), quality == null ? RecordingQualityPreset.BALANCED : quality, policy.hotProvider,
                clock.instant()));
        audit.record(t, actors.requireSubject(), "RECORDING_REQUESTED", "recording", r.id().toString());
        return view(r);
    }

    @Transactional(readOnly = true)
    PageResult<RecordingView> list(int page, int size) {
        auth.require(PermissionKey.RECORDINGS_VIEW);
        UUID t = tenants.requireTenantId();
        Pageable p = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "requestedAt", "id"));
        Page<Recording> rows;
        if (auth.has(PermissionKey.RECORDINGS_MANAGE)) {
            rows = recordings.findAllByTenantId(t, p);
        } else {
            Set<UUID> batchIds = enrollment.activeBatchIdsForMembership(memberships.current().membershipId());
            Set<UUID> liveClassIds = batchIds.isEmpty() ? Set.of()
                    : Set.copyOf(liveClasses.liveClassIdsForBatches(batchIds));
            if (liveClassIds.isEmpty()) rows = Page.empty(p);
            else rows = recordings.findAllByTenantIdAndLiveClassIdIn(t, liveClassIds, p);
        }
        return new PageResult<>(rows.getContent().stream().map(this::view).toList(), rows.getNumber(), rows.getSize(),
                rows.getTotalElements(), rows.getTotalPages());
    }

    @Transactional(readOnly = true)
    RecordingView get(UUID id) { return view(requireVisibleRecording(id)); }

    @Transactional
    void stop(UUID id) {
        auth.require(PermissionKey.RECORDINGS_MANAGE);
        requireLiveKitEnabled();
        Recording r = require(id);
        if (r.egressId() != null && (r.status() == RecordingProcessingStatus.RECORDING || r.status() == RecordingProcessingStatus.STARTING)) {
            media.stop(r.egressId());
            audit.record(r.tenantId(), actors.requireSubject(), "RECORDING_STOP_REQUESTED", "recording", r.id().toString());
        }
    }

    @Transactional
    PlaybackView playback(UUID id) {
        Recording r = requireVisibleRecording(id);
        if (!(r.status() == RecordingProcessingStatus.READY || r.status() == RecordingProcessingStatus.ARCHIVED))
            throw new IllegalStateException("Recording is not ready for playback");
        UUID mid = memberships.current().membershipId();
        String name = memberships.current().displayName();
        Instant expires = clock.instant().plusSeconds(300);
        return new PlaybackView("/api/v1/recordings/" + id + "/stream?token=" + opaque(id, mid, expires.toEpochMilli()), expires, name, mid.toString(), clock.instant());
    }

    @Transactional(readOnly = true)
    InputStream openStream(UUID id, String token) throws java.io.IOException {
        Recording r = requireVisibleRecording(id);
        if (!(r.status() == RecordingProcessingStatus.READY || r.status() == RecordingProcessingStatus.ARCHIVED)) throw new IllegalStateException("Recording is not ready");
        PlaybackToken parsed = parseToken(token);
        if (!parsed.recordingId().equals(id) || !parsed.membershipId().equals(memberships.current().membershipId()) || parsed.expiresAt() < clock.instant().toEpochMilli()) throw new SecurityException("Playback token is invalid or expired");
        storageQuota.authorizeRead(r.tenantId(), r.storageProvider());
        return adapters.require(r.storageProvider()).open(r.storageLocator());
    }

    @Transactional
    StoragePolicyView policy() {
        auth.require(PermissionKey.RECORDINGS_VIEW);
        UUID t = tenants.requireTenantId();
        RecordingStoragePolicy p = policies.findById(t).orElseGet(() -> new RecordingStoragePolicy(t, clock.instant()));
        return policyView(p);
    }

    @Transactional
    StoragePolicyView updatePolicy(int hotDays, int cacheDays, Integer retentionDays, StorageProviderType hotProvider,
            StorageProviderType cacheProvider, StorageProviderType archiveProvider, int grace, long expectedVersion) {
        auth.require(PermissionKey.RECORDINGS_MANAGE);
        UUID t = tenants.requireTenantId();
        RecordingStoragePolicy p = policies.findById(t).orElseGet(() -> new RecordingStoragePolicy(t, clock.instant()));
        if (p.version() != expectedVersion && policies.existsById(t)) throw new IllegalStateException("Recording storage policy was modified");
        if (hotDays < 0 || cacheDays < 0 || retentionDays != null && retentionDays < 1 || grace < 0) throw new IllegalArgumentException("Invalid recording retention policy");
        adapters.require(hotProvider); adapters.require(cacheProvider); adapters.require(archiveProvider);
        storageQuota.authorizeWrite(t, hotProvider);
        storageQuota.authorizeWrite(t, cacheProvider);
        storageQuota.authorizeWrite(t, archiveProvider);
        p.update(hotDays, cacheDays, retentionDays, hotProvider, cacheProvider, archiveProvider, grace, clock.instant());
        policies.save(p);
        audit.record(t, actors.requireSubject(), "RECORDING_STORAGE_POLICY_UPDATED", "recording-storage-policy", t.toString());
        return policyView(p);
    }

    private StoragePolicyView policyView(RecordingStoragePolicy p) { return new StoragePolicyView(p.hotCacheDays(), p.cacheDays(), p.retentionDays(), p.hotProvider(), p.cacheProvider(), p.archiveProvider(), p.deletedObjectGraceDays(), p.version()); }

    Recording processRequested(Recording r) {
        if (r.status() != RecordingProcessingStatus.REQUESTED) return r;
        try {
            Files.createDirectories(processingRoot.resolve(r.tenantId().toString()).resolve(r.id().toString()));
            r.markStarting(clock.instant());
            String path = "/recordings/" + r.tenantId() + "/" + r.id() + "/recording.mp4";
            var started = media.startRoomComposite(r.roomName(), r.qualityPreset(), path);
            r.start(started.egressId(), clock.instant());
        } catch (Exception e) { r.fail(trim(e.getMessage())); }
        return r;
    }

    @Transactional
    void processOrReconcile(Recording r) {
        workerLock.acquire(r.tenantId());
        Recording current = recordings.findByTenantIdAndId(r.tenantId(), r.id()).orElse(null);
        if (current == null) return;
        if (current.status() == RecordingProcessingStatus.REQUESTED) processRequested(current);
        else if (current.status() == RecordingProcessingStatus.RECORDING || current.status() == RecordingProcessingStatus.STARTING) reconcile(current);
    }

    Recording reconcile(Recording r) {
        if (r.egressId() == null) return r;
        try {
            var state = media.status(r.egressId());
            switch (normalizeEgressStatus(state.status())) {
                case "STARTING", "ACTIVE", "ENDING" -> { return r; }
                case "FAILED", "ABORTED", "LIMIT_REACHED" -> { r.fail(state.error() == null ? "LiveKit egress failed" : state.error()); return r; }
                case "COMPLETE", "INNER_COMPLETE" -> { return finalizeEgress(r, state); }
                default -> { return r; }
            }
        } catch (Exception e) { r.fail(trim(e.getMessage())); }
        return r;
    }

    private Recording finalizeEgress(Recording r, RecordingMediaProvider.EgressState state) {
        r.markFinalizing(clock.instant());
        Path video = localPath(r);
        if (!Files.exists(video)) { r.fail("LiveKit completed without the expected recording file"); return r; }
        finalizeFile(r, video, Math.max(0, state.durationNanos() / 1_000_000_000L));
        return r;
    }

    private static String normalizeEgressStatus(String raw) { return raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT).replace("EGRESS_STATUS_", ""); }

    @Transactional
    void archiveDue(Recording r) {
        if (r.status() != RecordingProcessingStatus.READY && r.status() != RecordingProcessingStatus.ARCHIVING) return;
        RecordingStoragePolicy p = policies.findById(r.tenantId()).orElse(null);
        if (p == null || r.readyAt() == null) return;
        Instant now = clock.instant();
        if (r.storageTier() == RecordingStorageTier.HOT) {
            if (r.status() == RecordingProcessingStatus.READY && now.isBefore(r.readyAt().plus(Duration.ofDays(p.hotCacheDays())))) return;
            Recording current = claimForMigration(r); if (current == null) return;
            try { migrateToTier(current, p.cacheProvider(), RecordingStorageTier.CACHE, now); audit.record(r.tenantId(), "system", "RECORDING_MOVED_TO_CACHE", "recording", r.id().toString()); }
            catch (Exception e) { restore(current); }
            return;
        }
        if (r.storageTier() == RecordingStorageTier.CACHE) {
            if (r.status() == RecordingProcessingStatus.READY && now.isBefore(r.readyAt().plus(Duration.ofDays((long) p.hotCacheDays() + p.cacheDays())))) return;
            Recording current = claimForMigration(r); if (current == null) return;
            try { migrateToTier(current, p.archiveProvider(), RecordingStorageTier.ARCHIVE, now); audit.record(r.tenantId(), "system", "RECORDING_ARCHIVED", "recording", r.id().toString()); }
            catch (Exception e) { restore(current); }
        }
    }

    private Recording claimForMigration(Recording r) {
        try { if (r.status() == RecordingProcessingStatus.READY && !claimMigration(r.tenantId(), r.id())) return null; }
        catch (Exception claimedByAnotherInstance) { return null; }
        Recording current = reload(r);
        if (current == null || current.status() != RecordingProcessingStatus.ARCHIVING) return null;
        return current;
    }

    private boolean claimMigration(UUID tenantId, UUID id) {
        Boolean claimed = claimTemplate.execute(status -> {
            workerLock.acquire(tenantId);
            Recording fresh = recordings.findByTenantIdAndId(tenantId, id).orElse(null);
            if (fresh == null || fresh.status() != RecordingProcessingStatus.READY) return false;
            fresh.markArchiving(); recordings.save(fresh); return true;
        });
        return Boolean.TRUE.equals(claimed);
    }

    private Recording reload(Recording r) { return recordings.findByTenantIdAndId(r.tenantId(), r.id()).orElse(null); }
    private void restore(Recording r) { try { r.restoreReady(); } catch (Exception ignored) {} try { recordings.save(r); } catch (Exception ignored) {} }

    private void migrateToTier(Recording r, StorageProviderType targetType, RecordingStorageTier tier, Instant now) throws java.io.IOException {
        boolean toArchive = tier == RecordingStorageTier.ARCHIVE;
        if (r.storageProvider() == targetType) {
            if (toArchive) r.archived(targetType, r.storageLocator(), r.objectKey(), r.quotaReservationId(), now);
            else r.cached(targetType, r.storageLocator(), r.objectKey(), r.quotaReservationId(), now);
            recordings.save(r);
            return;
        }

        migrateAssets(r, targetType);
        MasterCopy master = masterLocatorOnTarget(r, targetType);
        if (toArchive) r.archived(targetType, master.locator(), r.objectKey(), master.reservationId(), now);
        else r.cached(targetType, master.locator(), r.objectKey(), master.reservationId(), now);
        recordings.save(r);
    }

    private MasterCopy masterLocatorOnTarget(Recording r, StorageProviderType targetType) throws java.io.IOException {
        RecordingAsset master = assets.findByTenantIdAndRecordingIdAndKind(r.tenantId(), r.id(), RecordingAssetKind.MASTER).orElse(null);
        if (master != null && master.provider() == targetType && master.locator() != null
                && master.quotaReservationId() != null) {
            return new MasterCopy(master.locator(), master.quotaReservationId());
        }
        if (master != null) {
            throw new IllegalStateException("Recording master migration did not produce a destination reservation");
        }
        if (r.quotaReservationId() == null || r.storageLocator() == null) {
            throw new IllegalStateException("Recording master has no quota reservation; refusing unaccounted migration");
        }
        StorageAdapter source = adapters.require(r.storageProvider());
        StorageAdapter destination = adapters.require(targetType);
        storageQuota.authorizeRead(r.tenantId(), r.storageProvider());
        long bytes = r.sizeBytes() == null ? 0 : r.sizeBytes();
        UUID reservationId = storageQuota.reserveDurable(r.tenantId(), targetType,
                "migration:" + r.quotaReservationId() + ":" + targetType, r.objectKey(), bytes);
        String newLocator;
        try (InputStream in = source.open(r.storageLocator())) {
            newLocator = destination.put(r.objectKey(), in, bytes, "video/mp4");
        }
        registerProviderObjectCompensation(r.tenantId(), reservationId, targetType, newLocator,
                r.objectKey(), destination);
        storageQuota.commit(r.tenantId(), reservationId, bytes, newLocator, r.objectKey());
        deleteSourceAfterCommit(r.tenantId(), r.storageProvider(), r.storageLocator(), r.objectKey(),
                r.quotaReservationId(), source);
        return new MasterCopy(newLocator, reservationId);
    }

    private void migrateAssets(Recording r, StorageProviderType targetType) throws java.io.IOException {
        for (RecordingAsset asset : assets.findAllByTenantIdAndRecordingId(r.tenantId(), r.id())) {
            if (asset.provider() == targetType) continue;
            if (asset.quotaReservationId() == null) {
                throw new IllegalStateException("Recording asset has no quota reservation; refusing unaccounted migration");
            }
            StorageAdapter source = adapters.require(asset.provider());
            StorageAdapter destination = adapters.require(targetType);
            storageQuota.authorizeRead(r.tenantId(), asset.provider());
            UUID newReservation = storageQuota.reserveDurable(r.tenantId(), targetType,
                    "migration:" + asset.quotaReservationId() + ":" + targetType,
                    asset.objectKey(), asset.sizeBytes());
            String newLocator;
            try (InputStream in = source.open(asset.locator())) {
                newLocator = destination.put(asset.objectKey(), in, asset.sizeBytes(), asset.mimeType());
            }
            registerProviderObjectCompensation(r.tenantId(), newReservation, targetType, newLocator,
                    asset.objectKey(), destination);
            storageQuota.commit(r.tenantId(), newReservation, asset.sizeBytes(), newLocator, asset.objectKey());
            UUID oldReservation = asset.quotaReservationId();
            String oldLocator = asset.locator();
            StorageProviderType oldProvider = asset.provider();
            asset.migrateTo(targetType, newLocator, newReservation);
            assets.save(asset);
            deleteSourceAfterCommit(r.tenantId(), oldProvider, oldLocator, asset.objectKey(), oldReservation, source);
        }
    }

    private void registerProviderObjectCompensation(UUID tenantId, UUID reservationId,
                                                    StorageProviderType provider, String locator,
                                                    String objectKey, StorageAdapter adapter) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            try {
                adapter.delete(locator);
                storageQuota.releaseDurable(tenantId, reservationId);
            } catch (Exception failure) {
                cleanupQueue.enqueue(tenantId, provider, locator, objectKey, reservationId, failure);
            }
            throw new IllegalStateException("Recording storage writes require an active database transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) return;
                try {
                    adapter.delete(locator);
                    storageQuota.releaseDurable(tenantId, reservationId);
                } catch (Exception failure) {
                    cleanupQueue.enqueue(tenantId, provider, locator, objectKey, reservationId, failure);
                }
            }
        });
    }

    private void deleteSourceAfterCommit(UUID tenantId, StorageProviderType provider, String locator,
                                         String objectKey, UUID reservationId, StorageAdapter adapter) {
        if (locator == null || reservationId == null) {
            throw new IllegalStateException("Source storage object has no quota reservation; refusing untracked deletion");
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Recording migrations require an active database transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storageQuota.authorizeDelete(tenantId, provider);
                    adapter.delete(locator);
                    storageQuota.release(tenantId, reservationId);
                } catch (Exception failure) {
                    cleanupQueue.enqueue(tenantId, provider, locator, objectKey, reservationId, failure);
                }
            }
        });
    }

    private record MasterCopy(String locator, UUID reservationId) {}


    @Transactional
    void purgeDue(Recording candidate) {
        // The scheduled worker supplied a detached snapshot. Reload it inside a transaction and
        // serialize purge attempts for the tenant; otherwise the remote objects may be deleted
        // while the database row remains READY/ARCHIVED and gets purged on every poll.
        workerLock.acquire(candidate.tenantId());
        Recording recording = recordings.findByTenantIdAndId(candidate.tenantId(), candidate.id()).orElse(null);
        if (recording == null
                || (recording.status() != RecordingProcessingStatus.READY
                && recording.status() != RecordingProcessingStatus.ARCHIVED)) {
            return;
        }

        RecordingStoragePolicy policy = policies.findById(recording.tenantId()).orElse(null);
        if (policy == null || policy.retentionDays() == null || recording.readyAt() == null) return;
        Instant purgeAfter = recording.readyAt().plus(Duration.ofDays(
                (long) policy.retentionDays() + policy.deletedObjectGraceDays()));
        if (purgeAfter.isAfter(clock.instant())) return;

        List<RecordingAsset> recordingAssets = assets.findAllByTenantIdAndRecordingId(
                recording.tenantId(), recording.id());
        try {
            Set<String> deletedObjects = new HashSet<>();
            Set<UUID> reservationsToRelease = new HashSet<>();
            if (recording.storageLocator() != null) {
                if (recording.quotaReservationId() == null) {
                    throw new IllegalStateException("Recording master has no quota reservation; refusing unaccounted deletion");
                }
                String identity = recording.storageProvider() + ":" + recording.storageLocator();
                if (deletedObjects.add(identity)) {
                    storageQuota.authorizeDelete(recording.tenantId(), recording.storageProvider());
                    adapters.require(recording.storageProvider()).delete(recording.storageLocator());
                }
                reservationsToRelease.add(recording.quotaReservationId());
            }
            for (RecordingAsset asset : recordingAssets) {
                String identity = asset.provider() + ":" + asset.locator();
                if (!deletedObjects.add(identity)) continue;
                if (asset.quotaReservationId() == null) {
                    throw new IllegalStateException("Recording asset has no quota reservation; refusing unaccounted deletion");
                }
                storageQuota.authorizeDelete(recording.tenantId(), asset.provider());
                adapters.require(asset.provider()).delete(asset.locator());
                reservationsToRelease.add(asset.quotaReservationId());
            }
            for (UUID reservationId : reservationsToRelease) {
                storageQuota.release(recording.tenantId(), reservationId);
            }

            // Metadata is committed only after all provider deletes and quota releases succeed.
            assets.deleteAll(recordingAssets);
            recording.clearStoragePointer();
            recording.delete(clock.instant());
            recordings.save(recording);
            audit.record(recording.tenantId(), "system", "RECORDING_PURGED", "recording", recording.id().toString());
        } catch (Exception failure) {
            log.warn("Recording retention purge failed for tenant {} recording {}; retrying on a later poll",
                    recording.tenantId(), recording.id(), failure);
        }
    }

    private void finalizeFile(Recording r, Path video, long duration) {
        try {
            long bytes = Files.size(video);
            String hash = RecordingThumbnailService.sha256(video);
            String key = r.tenantId() + "/recordings/" + r.id() + "/master.mp4";
            StorageAdapter target = adapters.require(r.storageProvider());
            UUID reservationId = storageQuota.reserveDurable(r.tenantId(), r.storageProvider(),
                    "recording:" + r.id() + ":MASTER", key, bytes);
            String locator;
            try (InputStream in = Files.newInputStream(video)) {
                locator = target.put(key, in, bytes, "video/mp4");
            }
            registerProviderObjectCompensation(r.tenantId(), reservationId, r.storageProvider(), locator, key, target);
            storageQuota.commit(r.tenantId(), reservationId, bytes, locator, key);
            r.ready(locator, key, hash, bytes, duration, clock.instant());
            r.quotaReservationId = reservationId;
            assets.save(new RecordingAsset(UUID.randomUUID(), r.tenantId(), r.id(), RecordingAssetKind.MASTER,
                    r.storageProvider(), locator, key, "video/mp4", bytes, hash, clock.instant(), reservationId));
            createThumbnail(r, video, target);
            Files.deleteIfExists(video);
            Files.deleteIfExists(video.getParent());
            notifyReady(r);
        } catch (Exception e) {
            r.fail(trim(e.getMessage()));
        }
    }

    private void createThumbnail(Recording r, Path video, StorageAdapter target) {
        Path thumb = video.resolveSibling("thumbnail.jpg");
        try {
            thumbnails.create(video, thumb);
            long bytes = Files.size(thumb);
            String hash = RecordingThumbnailService.sha256(thumb);
            String key = r.tenantId() + "/recordings/" + r.id() + "/thumbnail.jpg";
            UUID reservationId = storageQuota.reserveDurable(r.tenantId(), r.storageProvider(),
                    "recording:" + r.id() + ":THUMBNAIL", key, bytes);
            String locator;
            try (InputStream in = Files.newInputStream(thumb)) {
                locator = target.put(key, in, bytes, "image/jpeg");
            }
            registerProviderObjectCompensation(r.tenantId(), reservationId, r.storageProvider(), locator, key, target);
            storageQuota.commit(r.tenantId(), reservationId, bytes, locator, key);
            assets.save(new RecordingAsset(UUID.randomUUID(), r.tenantId(), r.id(), RecordingAssetKind.THUMBNAIL,
                    r.storageProvider(), locator, key, "image/jpeg", bytes, hash, clock.instant(), reservationId));
            Files.deleteIfExists(thumb);
        } catch (Exception failure) {
            log.warn("Recording thumbnail storage failed for tenant {} recording {}; continuing without thumbnail",
                    r.tenantId(), r.id(), failure);
        }
    }

    private void notifyReady(Recording r) {
        try {
            Set<UUID> recipients = enrollment.activeMembershipIdsForBatch(liveClasses.require(r.liveClassId()).batchId());
            List<NotificationPublisher.Recipient> target = recipients.stream().limit(5000).map(id -> new NotificationPublisher.Recipient(id, null)).toList();
            notifications.enqueue(new NotificationPublisher.NotificationRequest(r.tenantId(), NotificationEventType.RECORDING_READY, "recording", r.id(), "Class recording ready", "The recording is ready for playback.", com.universalplatform.notification.NotificationPriority.NORMAL, "recording", r.id(), "recording-ready-" + r.id(), clock.instant(), target));
        } catch (Exception ignored) {}
    }

    private Recording requireVisibleRecording(UUID id) {
        auth.require(PermissionKey.RECORDINGS_VIEW);
        Recording recording = require(id);
        // Match list() visibility: recording managers can inspect the tenant, while read-only
        // members are limited to recordings attached to their currently active batches.
        if (auth.has(PermissionKey.RECORDINGS_MANAGE)) return recording;
        UUID membershipId = memberships.current().membershipId();
        Set<UUID> activeBatchIds = enrollment.activeBatchIdsForMembership(membershipId);
        UUID recordingBatchId = liveClasses.require(recording.liveClassId()).batchId();
        if (!activeBatchIds.contains(recordingBatchId)) {
            // Do not disclose whether another member's recording exists in this tenant.
            throw new IllegalArgumentException("Recording not found");
        }
        return recording;
    }

    private Recording require(UUID id) { return recordings.findByTenantIdAndId(tenants.requireTenantId(), id).orElseThrow(() -> new IllegalArgumentException("Recording not found")); }

    private RecordingView view(Recording r) { return new RecordingView(r.id(), r.liveClassId(), r.classSessionId(), r.qualityPreset(), r.status(), r.storageTier(), r.storageProvider(), r.sizeBytes(), r.durationSeconds(), r.requestedAt(), r.startedAt(), r.endedAt(), r.readyAt(), r.archivedAt(), r.failureReason, r.version()); }
    private Path localPath(Recording r) { return processingRoot.resolve(r.tenantId().toString()).resolve(r.id().toString()).resolve("recording.mp4"); }

    private String opaque(UUID id, UUID membership, long exp) {
        String payload = id + ":" + membership + ":" + exp; String sig = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(payload));
        return Base64.getUrlEncoder().withoutPadding().encodeToString((payload + ":" + sig).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private PlaybackToken parseToken(String token) {
        try {
            String[] p = new String(Base64.getUrlDecoder().decode(token), java.nio.charset.StandardCharsets.UTF_8).split(":", 4); String payload = p[0] + ":" + p[1] + ":" + p[2]; String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(payload));
            if (!MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8), p[3].getBytes(java.nio.charset.StandardCharsets.UTF_8))) throw new SecurityException("Playback token is invalid");
            return new PlaybackToken(UUID.fromString(p[0]), UUID.fromString(p[1]), Long.parseLong(p[2]));
        } catch (SecurityException e) { throw e; } catch (Exception e) { throw new SecurityException("Playback token is invalid"); }
    }

    private byte[] hmac(String text) {
        try { var mac = javax.crypto.Mac.getInstance("HmacSHA256"); mac.init(new javax.crypto.spec.SecretKeySpec(playbackSecret, "HmacSHA256")); return mac.doFinal(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException("Unable to sign playback token", e); }
    }

    private static String trim(String s) { return s == null ? "Recording processing failed" : s.length() > 900 ? s.substring(0, 900) : s; }

    record RecordingView(UUID id, UUID liveClassId, UUID classSessionId, RecordingQualityPreset qualityPreset, RecordingProcessingStatus status, RecordingStorageTier storageTier, StorageProviderType storageProvider, Long sizeBytes, Long durationSeconds, Instant requestedAt, Instant startedAt, Instant endedAt, Instant readyAt, Instant archivedAt, String failureReason, long version) {}
    record PlaybackView(String streamUrl, Instant expiresAt, String watermarkName, String watermarkId, Instant issuedAt) {}
    record StreamHandle(StorageProviderType provider, String locator, String fileName, Long sizeBytes) {}
    record StoragePolicyView(int hotCacheDays, int cacheDays, Integer retentionDays, StorageProviderType hotProvider, StorageProviderType cacheProvider, StorageProviderType archiveProvider, int deletedObjectGraceDays, long version) {}
    record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}

    private void requireLiveKitEnabled() {
        if (!liveKitEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Recording operations are unavailable because LiveKit is disabled on this deployment");
        }
    }

    private record PlaybackToken(UUID recordingId, UUID membershipId, long expiresAt) {}
}
