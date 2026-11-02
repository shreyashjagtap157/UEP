package com.universalplatform.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import com.universalplatform.storage.StorageProviderType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class StorageCleanupOperationsServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @Mock TenantContext tenantContext;
    @Mock ActorContext actorContext;
    @Mock AuthorizationService authorization;
    @Mock StorageCleanupRepository jobs;
    @Mock AuditService audit;

    private StorageCleanupOperationsService service;

    @BeforeEach
    void setUp() {
        service = new StorageCleanupOperationsService(
                tenantContext, actorContext, authorization, jobs, audit);
    }

    @Test
    void deadLetterListingRequiresTenantWideOperationsScope() {
        when(tenantContext.requireTenantId()).thenReturn(tenantId);
        StorageCleanupJob job = deadLetteredJob();
        when(authorization.currentScope(PermissionKey.OPERATIONS_VIEW))
                .thenReturn(new AuthorizationService.AccessScope(true, Set.of()));
        when(jobs.findTop100ByTenantIdAndDeletedAtIsNullAndDeadLetteredAtIsNotNullOrderByDeadLetteredAtAsc(tenantId))
                .thenReturn(List.of(job));

        List<StorageCleanupOperationsService.FailureView> result = service.deadLettered();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(jobId);
        assertThat(result.getFirst().attemptCount()).isEqualTo(StorageCleanupJob.MAX_ATTEMPTS);
        assertThat(result.getFirst().failureType()).isEqualTo("IOException");
    }

    @Test
    void branchScopedOperationsCannotInspectTenantWideStorageCleanupFailures() {
        when(authorization.currentScope(PermissionKey.OPERATIONS_VIEW))
                .thenReturn(new AuthorizationService.AccessScope(false, Set.of(UUID.randomUUID())));

        assertThatThrownBy(() -> service.deadLettered()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requeueRequiresTenantWideManageScopeAndWritesAuditEvent() {
        when(tenantContext.requireTenantId()).thenReturn(tenantId);
        StorageCleanupJob job = deadLetteredJob();
        when(authorization.currentScope(PermissionKey.OPERATIONS_MANAGE))
                .thenReturn(new AuthorizationService.AccessScope(true, Set.of()));
        when(jobs.lockByTenantIdAndId(tenantId, jobId)).thenReturn(Optional.of(job));
        when(actorContext.requireSubject()).thenReturn("storage-operator");

        StorageCleanupOperationsService.FailureView result = service.requeue(jobId);

        assertThat(result.attemptCount()).isZero();
        assertThat(result.deadLetteredAt()).isNull();
        assertThat(job.nextAttemptAt()).isNotNull();
        verify(audit).record(tenantId, "storage-operator",
                "STORAGE_CLEANUP_REQUEUED", "storage_cleanup", jobId.toString());
        verify(jobs).flush();
    }

    @Test
    void requeueCannotResolveJobFromAnotherTenant() {
        when(tenantContext.requireTenantId()).thenReturn(tenantId);
        when(authorization.currentScope(PermissionKey.OPERATIONS_MANAGE))
                .thenReturn(new AuthorizationService.AccessScope(true, Set.of()));
        when(jobs.lockByTenantIdAndId(tenantId, jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requeue(jobId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Storage cleanup failure not found");
    }

    private StorageCleanupJob deadLetteredJob() {
        Instant start = Instant.parse("2026-11-02T00:00:00Z");
        StorageCleanupJob job = new StorageCleanupJob(jobId, tenantId, StorageProviderType.LOCAL,
                "private-locator", "private-object-key", UUID.randomUUID(), start);
        for (int attempt = 0; attempt < StorageCleanupJob.MAX_ATTEMPTS; attempt++) {
            job.failed("IOException", start.plusSeconds(attempt * 3600L));
        }
        return job;
    }
}
