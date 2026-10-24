package com.universalplatform.content;

import com.universalplatform.audit.AuditService;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.storage.StorageAdapter;
import com.universalplatform.storage.StorageAdapterRegistry;
import com.universalplatform.storage.StorageProviderType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
class UploadService {
    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final TenantContext tenants;
    private final ActorContext actors;
    private final AuthorizationService auth;
    private final UploadSessionRepository uploads;
    private final StorageObjectRepository objects;
    private final StorageAdapterRegistry adapters;
    private final AuditService audit;
    private final MalwareScanner malwareScanner;
    private final StorageCleanupOutboxService storageCleanupOutbox;
    private final Path staging;
    private final long maxUploadBytes;
    private final Clock clock = Clock.systemUTC();

    UploadService(TenantContext tenants,
                  ActorContext actors,
                  AuthorizationService auth,
                  UploadSessionRepository uploads,
                  StorageObjectRepository objects,
                  StorageAdapterRegistry adapters,
                  AuditService audit,
                  MalwareScanner malwareScanner,
                  StorageCleanupOutboxService storageCleanupOutbox,
                  @Value("${platform.storage.staging.root:./var/uploads}") String staging,
                  @Value("${platform.storage.max-upload-bytes:5368709120}") long maxUploadBytes) {
        this.tenants = tenants;
        this.actors = actors;
        this.auth = auth;
        this.uploads = uploads;
        this.objects = objects;
        this.adapters = adapters;
        this.audit = audit;
        this.malwareScanner = malwareScanner;
        this.storageCleanupOutbox = storageCleanupOutbox;
        this.staging = Path.of(staging).toAbsolutePath().normalize();
        this.maxUploadBytes = maxUploadBytes;
    }

    @Transactional
    UploadView initiate(String fileName, String contentType, long bytes, String sha256, StorageProviderType provider)
            throws IOException {
        auth.require(PermissionKey.CONTENT_MANAGE);
        if (bytes < 0) throw new IllegalArgumentException("File size cannot be negative");
        if (bytes > maxUploadBytes)
            throw new IllegalArgumentException("File exceeds platform upload limit of " + maxUploadBytes + " bytes");
        UUID tenant = tenants.requireTenantId();
        UUID id = UUID.randomUUID();
        Path dir = staging.resolve(tenant.toString()).normalize();
        Files.createDirectories(dir);
        Path path = dir.resolve(id + ".part");
        Files.createFile(path);
        UploadSession u = uploads.save(new UploadSession(id, tenant, actors.requireSubject(), safeName(fileName),
                safeType(contentType), bytes, hash(sha256), provider, path.toString(), clock.instant()));
        audit.record(tenant, actors.requireSubject(), "UPLOAD_INITIATED", "upload_session", id.toString());
        return view(u);
    }

    @Transactional
    UploadView append(UUID id, long offset, InputStream body, long contentLength) throws IOException {
        auth.require(PermissionKey.CONTENT_MANAGE);
        UploadSession u = uploads.lock(tenants.requireTenantId(), id)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found"));
        if (u.status == UploadStatus.COMPLETED || u.status == UploadStatus.ABORTED || u.status == UploadStatus.EXPIRED) {
            throw new IllegalStateException("Upload is not writable");
        }
        if (clock.instant().isAfter(u.expiresAt)) {
            u.status = UploadStatus.EXPIRED;
            throw new IllegalStateException("Upload expired");
        }
        if (offset != u.receivedBytes) {
            throw new IllegalArgumentException("Upload offset mismatch; expected " + u.receivedBytes);
        }
        if (contentLength < 0 || u.receivedBytes + contentLength > u.expectedBytes) {
            throw new IllegalArgumentException("Chunk exceeds expected file length");
        }
        Path path = Path.of(u.stagingPath);
        realignIfStagingIsAheadOfCommittedOffset(path, u.receivedBytes);
        try (OutputStream out = Files.newOutputStream(path, StandardOpenOption.APPEND); body) {
            body.transferTo(out);
        }
        long actual = Files.size(path);
        if (actual != u.receivedBytes + contentLength) {
            throw new IOException("Chunk length mismatch");
        }
        u.receivedBytes = actual;
        u.status = UploadStatus.UPLOADING;
        return view(u);
    }

    /**
     * If a previous append committed file bytes but its transaction rolled back, the staging file
     * is longer than the committed {@code receivedBytes}. Re-align the file to the committed
     * offset before appending so a legitimate offset retry cannot produce a corrupt file.
     */
    private static void realignIfStagingIsAheadOfCommittedOffset(Path path, long committedBytes) throws IOException {
        if (Files.exists(path) && Files.size(path) > committedBytes) {
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
                channel.truncate(committedBytes);
            }
        }
    }

    /**
     * Idempotent completion: if the session is already COMPLETED, return the storage object it
     * produced instead of re-scanning, re-uploading to the provider, and inserting a duplicate
     * {@link StorageObject}. The check runs under the session's pessimistic row lock.
     */
    @Transactional
    StorageView finalizeUpload(UUID id) throws IOException, GeneralSecurityException {
        auth.require(PermissionKey.CONTENT_MANAGE);
        UUID tenant = tenants.requireTenantId();
        UploadSession u = uploads.lock(tenant, id)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found"));
        if (u.receivedBytes != u.expectedBytes) {
            throw new IllegalStateException("Upload is incomplete");
        }
        if (u.status == UploadStatus.COMPLETED) {
            StorageObject committed = objects.findByTenantIdAndId(tenant, u.storageObjectId)
                    .orElseThrow(() -> new IllegalStateException("Completed upload has no storage object"));
            audit.record(tenant, actors.requireSubject(), "UPLOAD_FINALIZE_REPEATED", "upload_session", u.id.toString());
            return view(committed);
        }
        if (u.expectedBytes > maxUploadBytes) {
            throw new IllegalArgumentException("Upload exceeds platform upload limit of " + maxUploadBytes + " bytes");
        }
        u.status = UploadStatus.VERIFYING;
        Path path = Path.of(u.stagingPath);
        String digest = sha256(path);
        if (u.expectedSha256 != null && !MessageDigest.isEqual(digest.getBytes(), u.expectedSha256.getBytes())) {
            throw new IllegalStateException("SHA-256 mismatch");
        }
        ScanStatus scan = malwareScanner.scan(path);
        if (scan == ScanStatus.REJECTED) {
            u.status = UploadStatus.ABORTED;
            Files.deleteIfExists(path);
            audit.record(tenant, actors.requireSubject(), "UPLOAD_REJECTED_MALWARE", "upload_session", u.id.toString());
            throw new IllegalStateException("Upload rejected by malware scanner");
        }
        String key = tenant + "/" + LocalDate.now(ZoneOffset.UTC) + "/" + UUID.randomUUID() + "/" + u.fileName;
        StorageAdapter adapter = adapters.require(u.provider);
        String locator;
        try (InputStream in = Files.newInputStream(path)) {
            locator = adapter.put(key, in, u.expectedBytes, u.contentType);
        }
        // Compensate the external side effect if metadata/audit persistence rolls back.
        // Keep the staging file until commit so the caller can safely retry after a rollback.
        registerProviderObjectCompensation(tenant, u.provider, locator, key, path, adapter);
        StorageObject o = objects.save(new StorageObject(UUID.randomUUID(), tenant, u.provider, locator, key,
                u.contentType, u.expectedBytes, digest, scan, clock.instant()));
        u.status = UploadStatus.COMPLETED;
        u.completedAt = clock.instant();
        u.storageObjectId = o.id;
        audit.record(tenant, actors.requireSubject(), "UPLOAD_COMPLETED", "storage_object", o.id.toString());
        return view(o);
    }

    private void registerProviderObjectCompensation(UUID tenantId, StorageProviderType provider,
                                                    String locator, String objectKey, Path stagingPath,
                                                    StorageAdapter adapter) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            try {
                adapter.delete(locator);
            } catch (Exception cleanupFailure) {
                storageCleanupOutbox.enqueue(tenantId, provider, locator, objectKey, cleanupFailure);
            }
            throw new IllegalStateException("Upload finalization requires an active database transaction");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int completionStatus) {
                if (completionStatus == STATUS_COMMITTED) {
                    try {
                        Files.deleteIfExists(stagingPath);
                    } catch (IOException cleanupFailure) {
                        // Completed sessions retain their staging path so the cleanup worker can retry.
                        log.warn("Could not remove committed upload staging file for tenant {} object key {}",
                                tenantId, objectKey, cleanupFailure);
                    }
                    return;
                }

                // Database state rolled back after the provider accepted the object.
                try {
                    adapter.delete(locator);
                } catch (Exception cleanupFailure) {
                    log.warn("Could not compensate remote object after upload rollback for tenant {} provider {}",
                            tenantId, provider, cleanupFailure);
                    try {
                        storageCleanupOutbox.enqueue(tenantId, provider, locator, objectKey, cleanupFailure);
                    } catch (RuntimeException enqueueFailure) {
                        cleanupFailure.addSuppressed(enqueueFailure);
                        log.error("Could not persist remote-object cleanup after upload rollback for tenant {}",
                                tenantId, cleanupFailure);
                    }
                }
            }
        });
    }

    @Transactional
    void abort(UUID id) throws IOException {
        auth.require(PermissionKey.CONTENT_MANAGE);
        UploadSession u = uploads.lock(tenants.requireTenantId(), id).orElseThrow();
        if (u.status != UploadStatus.COMPLETED) {
            u.status = UploadStatus.ABORTED;
            Files.deleteIfExists(Path.of(u.stagingPath));
        }
    }

    private static String sha256(Path p) throws IOException, GeneralSecurityException {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(p)) {
            byte[] b = new byte[1024 * 1024];
            for (int n; (n = in.read(b)) > 0; ) {
                d.update(b, 0, n);
            }
        }
        return HexFormat.of().formatHex(d.digest());
    }

    private static String safeName(String s) {
        String n = s == null ? "" : Path.of(s).getFileName().toString().trim();
        if (n.isBlank() || n.length() > 512) throw new IllegalArgumentException("Invalid file name");
        return n;
    }

    private static String safeType(String s) {
        return s == null || s.isBlank() ? "application/octet-stream" : s.trim();
    }

    private static String hash(String h) {
        if (h == null || h.isBlank()) return null;
        String n = h.toLowerCase(Locale.ROOT);
        if (!n.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid SHA-256");
        return n;
    }

    private static UploadView view(UploadSession u) {
        return new UploadView(u.id, u.fileName, u.contentType, u.expectedBytes, u.receivedBytes, u.provider, u.status, u.expiresAt);
    }

    private static StorageView view(StorageObject o) {
        return new StorageView(o.id, o.provider, o.contentType, o.sizeBytes, o.sha256, o.scanStatus);
    }

    record UploadView(UUID id, String fileName, String contentType, long expectedBytes, long receivedBytes,
                      StorageProviderType provider, UploadStatus status, Instant expiresAt) {}

    record StorageView(UUID id, StorageProviderType provider, String contentType, long sizeBytes, String sha256,
                       ScanStatus scanStatus) {}
}
