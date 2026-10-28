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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final RecordingWorkerLock workerLock;
    private final TransactionTemplate claimTemplate;
    private final AuditService audit;
    private final NotificationPublisher notifications;
    private final Path processingRoot;
    private final byte[] playbackSecret;
    private final Clock clock = Clock.systemUTC();

    RecordingService(TenantContext tenants, ActorContext actors, AuthorizationService auth,
            MembershipDirectory memberships, EnrollmentDirectory enrollment, LiveClassDirectory liveClasses,
            RecordingRepository recordings,
            RecordingAssetRepository assets, RecordingStoragePolicyRepository policies, RecordingMediaProvider media,
            RecordingThumbnailService thumbnails, StorageAdapterRegistry adapters, RecordingWorkerLock workerLock,
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
        UUID t = tenants.requireTenantId();
        var lc = liveClasses.require(liveClassId);
        if (lc.status() != com.universalplatform.liveclass.LiveClassStatus.LIVE)
            throw new IllegalStateException("Recording can only be requested while the live classroom is live");
        if (recordings.findByTenantIdAndLiveClassId(t, liveClassId)
                .filter(r -> r.status() != RecordingProcessingStatus.DELETED).isPresent())
            throw new IllegalStateException("A recording already exists for this live class");
        var policy = policies.findById(t).orElseGet(() -> new RecordingStoragePolicy(t, clock.instant()));
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
            if (toArchive) r.archived(targetType, r.storageLocator(), r.objectKey(), now); else r.cached(targetType, r.storageLocator(), r.objectKey(), now);
            recordings.save(r); return;
        }
        migrateAssets(r, targetType);
        String newLocator = masterLocatorOnTarget(r, targetType);
        StorageAdapter source = adapters.require(r.storageProvider());
        String old = r.storageLocator();
        if (toArchive) r.archived(targetType, newLocator, r.objectKey(), now); else r.cached(targetType, newLocator, r.objectKey(), now);
        recordings.save(r); deleteQuietly(source, old, newLocator);
    }

    private String masterLocatorOnTarget(Recording r, StorageProviderType targetType) throws java.io.IOException {
        RecordingAsset master = assets.findByTenantIdAndRecordingIdAndKind(r.tenantId(), r.id(), RecordingAssetKind.MASTER).orElse(null);
        if (master != null && master.provider() == targetType && master.locator() != null) return master.locator();
        StorageAdapter src = adapters.require(r.storageProvider()); StorageAdapter dst = adapters.require(targetType);
        try (InputStream in = src.open(r.storageLocator())) { return dst.put(r.objectKey(), in, r.sizeBytes() == null ? 0 : r.sizeBytes(), "video/mp4"); }
    }

    private void deleteQuietly(StorageAdapter source, String oldLocator, String newLocator) {
        if (source == null || oldLocator == null || oldLocator.equals(newLocator)) return;
        try { source.delete(oldLocator); } catch (Exception ignored) {}
    }

    private void migrateAssets(Recording r, StorageProviderType targetType) throws java.io.IOException {
        for (RecordingAsset a : assets.findAllByTenantIdAndRecordingId(r.tenantId(), r.id())) {
            if (a.provider() == targetType) continue;
            StorageAdapter src = adapters.require(a.provider()); StorageAdapter dst = adapters.require(targetType);
            try (InputStream in = src.open(a.locator())) {
                String loc = dst.put(a.objectKey(), in, a.sizeBytes(), a.mimeType()); String old = a.locator(); a.migrateTo(targetType, loc); assets.save(a); deleteQuietly(src, old, loc);
            }
        }
    }

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
            if (recording.storageLocator() != null) {
                adapters.require(recording.storageProvider()).delete(recording.storageLocator());
            }
            for (RecordingAsset asset : recordingAssets) {
                // The MASTER asset usually points at the same object as recording.storageLocator.
                // Avoid issuing duplicate provider deletes for this canonical object.
                if (asset.provider() == recording.storageProvider()
                        && Objects.equals(asset.locator(), recording.storageLocator())) {
                    continue;
                }
                adapters.require(asset.provider()).delete(asset.locator());
            }

            // Commit metadata only after every provider delete succeeded. If a later delete fails,
            // leave the recording eligible for retry. Provider delete operations must be idempotent.
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
            long bytes = Files.size(video); String hash = RecordingThumbnailService.sha256(video); String key = r.tenantId() + "/recordings/" + r.id() + "/master.mp4"; StorageAdapter target = adapters.require(r.storageProvider());
            try (InputStream in = Files.newInputStream(video)) {
                String loc = target.put(key, in, bytes, "video/mp4"); r.ready(loc, key, hash, bytes, duration, clock.instant());
                assets.save(new RecordingAsset(UUID.randomUUID(), r.tenantId(), r.id(), RecordingAssetKind.MASTER, r.storageProvider(), loc, key, "video/mp4", bytes, hash, clock.instant()));
                createThumbnail(r, video, target, key);
            }
            Files.deleteIfExists(video); Files.deleteIfExists(video.getParent()); notifyReady(r);
        } catch (Exception e) { r.fail(trim(e.getMessage())); }
    }

    private void createThumbnail(Recording r, Path video, StorageAdapter target, String masterKey) {
        Path thumb = video.resolveSibling("thumbnail.jpg");
        try {
            thumbnails.create(video, thumb); long b = Files.size(thumb); String h = RecordingThumbnailService.sha256(thumb); String key = r.tenantId() + "/recordings/" + r.id() + "/thumbnail.jpg";
            try (InputStream in = Files.newInputStream(thumb)) { String loc = target.put(key, in, b, "image/jpeg"); assets.save(new RecordingAsset(UUID.randomUUID(), r.tenantId(), r.id(), RecordingAssetKind.THUMBNAIL, r.storageProvider(), loc, key, "image/jpeg", b, h, clock.instant())); }
            Files.deleteIfExists(thumb);
        } catch (Exception ignored) {}
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
    private record PlaybackToken(UUID recordingId, UUID membershipId, long expiresAt) {}
}
