package com.universalplatform.content;

import com.universalplatform.audit.AuditService;
import com.universalplatform.storage.StorageObjectStatus;
import com.universalplatform.storage.StorageAdapterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purges storage objects of learning resources whose retention window elapsed.
 * Safety rules enforced per version: a version is purgeable only once its grace period passed,
 * its provider object is not referenced by any other version row (reference counted across the
 * tenant), and the object actually leaves provider storage before the version's storage pointer
 * is dropped. Provider failures are retried on the next poll instead of corrupting metadata.
 * {@code ContentRetentionPolicy.retainVersions = true} keeps the newest (current) version object
 * and only purges historical versions; {@code false} purges all versions. A resource whose
 * versions are fully handled is stamped {@code purgedAt} so this poller stops re-scanning it
 * (the previous top-100-oldest query would otherwise head-of-line block forever).
 */
@Component
class ContentRetentionWorker {
    private static final String RETENTION_ACTOR = "content-retention-worker";

    private final LearningResourceRepository resources;
    private final LearningResourceVersionRepository versions;
    private final StorageObjectRepository objects;
    private final ContentRetentionPolicyRepository policies;
    private final StorageAdapterRegistry adapters;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    ContentRetentionWorker(LearningResourceRepository resources,
                           LearningResourceVersionRepository versions,
                           StorageObjectRepository objects,
                           ContentRetentionPolicyRepository policies,
                           StorageAdapterRegistry adapters,
                           AuditService audit) {
        this.resources = resources;
        this.versions = versions;
        this.objects = objects;
        this.policies = policies;
        this.adapters = adapters;
        this.audit = audit;
    }

    @Scheduled(fixedDelayString = "${platform.storage.retention-poll-ms:3600000}")
    @Transactional
    void purgeExpired() {
        Instant now = clock.instant();
        for (LearningResource r : resources.findTop100ByExpiresAtBeforeAndPurgedAtIsNullOrderByExpiresAtAsc(now)) {
            purgeResource(r, now);
        }
    }

    private void purgeResource(LearningResource r, Instant now) {
        ContentRetentionPolicy policy = policies.findById(r.tenantId).orElse(null);
        int graceDays = policy == null ? 30 : policy.deletedObjectGraceDays;
        if (r.expiresAt.plus(Duration.ofDays(graceDays)).isAfter(now)) return;
        boolean retainCurrentVersion = policy == null || policy.retainVersions;
        boolean fullyHandled = true;

        for (LearningResourceVersion v : versions.findAllByTenantIdAndResourceIdOrderByVersionNumberDesc(r.tenantId, r.id)) {
            if (v.storageObjectId == null) continue;
            // While retainVersions is on, the newest version object survives the purge.
            if (retainCurrentVersion && v.versionNumber == r.currentVersion) {
                fullyHandled = false;
                continue;
            }
            StorageObject o = objects.findByTenantIdAndId(r.tenantId, v.storageObjectId).orElse(null);
            if (o == null || o.status == StorageObjectStatus.DELETED) {
                // Dangling or already-purged pointer: drop the pointer, nothing to remove.
                v.storageObjectId = null;
                continue;
            }
            // Reference counted: this version row deletes the provider object only when it holds
            // the last reference; every other sharer just drops its own pointer.
            if (versions.countByTenantIdAndStorageObjectId(r.tenantId, o.id) > 1) {
                v.storageObjectId = null;
                continue;
            }
            try {
                adapters.require(o.provider).delete(o.locator);
                o.markDeleted(now);
                v.storageObjectId = null;
                audit.record(r.tenantId, RETENTION_ACTOR, "STORAGE_OBJECT_PURGED", "storage_object", o.id.toString());
            } catch (Exception providerFailure) {
                // Provider outage or unconfigured adapter: keep metadata consistent, retry next poll.
                fullyHandled = false;
            }
        }

        if (fullyHandled) {
            r.markPurged(now);
            audit.record(r.tenantId, RETENTION_ACTOR, "LEARNING_RESOURCE_PURGED", "learning_resource", r.id.toString());
        }
    }
}
