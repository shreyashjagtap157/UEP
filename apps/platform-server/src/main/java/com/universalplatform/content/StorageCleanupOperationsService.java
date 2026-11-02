package com.universalplatform.content;

import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StorageCleanupOperationsService {
    private final TenantContext tenantContext;
    private final ActorContext actorContext;
    private final AuthorizationService authorization;
    private final StorageCleanupRepository jobs;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    StorageCleanupOperationsService(TenantContext tenantContext, ActorContext actorContext,
                                    AuthorizationService authorization, StorageCleanupRepository jobs,
                                    AuditService audit) {
        this.tenantContext = tenantContext;
        this.actorContext = actorContext;
        this.authorization = authorization;
        this.jobs = jobs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<FailureView> deadLettered() {
        requireTenantWideOperations(PermissionKey.OPERATIONS_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        return jobs.findTop100ByTenantIdAndDeletedAtIsNullAndDeadLetteredAtIsNotNullOrderByDeadLetteredAtAsc(
                        tenantId)
                .stream().map(StorageCleanupOperationsService::view).toList();
    }

    @Transactional
    public FailureView requeue(UUID id) {
        requireTenantWideOperations(PermissionKey.OPERATIONS_MANAGE);
        UUID tenantId = tenantContext.requireTenantId();
        StorageCleanupJob job = jobs.lockByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Storage cleanup failure not found"));
        if (job.deletedAt() != null || job.deadLetteredAt() == null) {
            throw new IllegalArgumentException("Storage cleanup job is not pending in the dead-letter queue");
        }
        job.requeue(clock.instant());
        audit.record(tenantId, actorContext.requireSubject(),
                "STORAGE_CLEANUP_REQUEUED", "storage_cleanup", id.toString());
        jobs.flush();
        return view(job);
    }

    private void requireTenantWideOperations(PermissionKey permission) {
        AuthorizationService.AccessScope scope = authorization.currentScope(permission);
        if (!scope.tenantWide()) {
            throw new AccessDeniedException("Tenant-wide operations scope is required for storage cleanup recovery");
        }
    }

    private static FailureView view(StorageCleanupJob job) {
        return new FailureView(job.id(), job.provider(), job.attemptCount(),
                job.lastError(), job.deadLetteredAt());
    }

    record FailureView(UUID id, com.universalplatform.storage.StorageProviderType provider,
                       int attemptCount, String failureType, Instant deadLetteredAt) {}
}
