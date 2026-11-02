package com.universalplatform.content;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.universalplatform.audit.AuditService;
import com.universalplatform.storage.StorageAdapterRegistry;
import com.universalplatform.storage.StorageQuotaService;
import java.time.Instant;
import java.time.Duration;
import java.util.Comparator;
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
class ContentRetentionWorkerKeysetPaginationTest {
    private static final int RESOURCE_COUNT = 205;
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final Instant EXPIRED_AT = Instant.parse("2026-07-01T00:00:00Z");

    @Mock LearningResourceRepository resources;
    @Mock LearningResourceVersionRepository versions;
    @Mock StorageObjectRepository objects;
    @Mock ContentRetentionPolicyRepository policies;
    @Mock StorageAdapterRegistry adapters;
    @Mock StorageQuotaService storageQuota;
    @Mock AuditService audit;

    private ContentRetentionWorker worker;
    private List<LearningResource> candidates;

    @BeforeEach
    void setUp() {
        worker = new ContentRetentionWorker(resources, versions, objects, policies, adapters, storageQuota, audit);
        candidates = java.util.stream.IntStream.range(0, RESOURCE_COUNT)
                .mapToObj(this::resource)
                .toList();

        when(versions.findAllByTenantIdAndResourceIdOrderByVersionNumberDesc(any(UUID.class), any(UUID.class)))
                .thenReturn(List.of());
        when(policies.findById(any(UUID.class))).thenReturn(Optional.empty());
        when(resources.findExpiredForPurgeAfter(
                any(Instant.class), any(Instant.class), any(UUID.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Instant before = invocation.getArgument(0);
                    Instant afterExpiresAt = invocation.getArgument(1);
                    UUID afterId = invocation.getArgument(2);
                    Pageable page = invocation.getArgument(3);
                    return candidates.stream()
                            .filter(resource -> resource.purgedAt == null)
                            .filter(resource -> resource.expiresAt.isBefore(before))
                            .filter(resource -> resource.expiresAt.isAfter(afterExpiresAt)
                                    || (resource.expiresAt.equals(afterExpiresAt)
                                    && resource.id.compareTo(afterId) > 0))
                            .sorted(Comparator.comparing((LearningResource resource) -> resource.expiresAt)
                                    .thenComparing(resource -> resource.id))
                            .limit(page.getPageSize())
                            .toList();
                });
    }

    @Test
    void retentionPollVisitsEveryResourceAcrossMoreThanTwoPages() {
        worker.purgeExpired();

        assertThatPurgedCountIs(RESOURCE_COUNT);
        verify(resources, times(4)).findExpiredForPurgeAfter(
                any(Instant.class), any(Instant.class), any(UUID.class), any(Pageable.class));
        verify(audit, times(RESOURCE_COUNT)).record(
                any(UUID.class), any(String.class), any(String.class), any(String.class), any(String.class));
    }

    private void assertThatPurgedCountIs(int expected) {
        long purged = candidates.stream().filter(resource -> resource.purgedAt != null).count();
        org.assertj.core.api.Assertions.assertThat(purged).isEqualTo(expected);
    }

    private LearningResource resource(int index) {
        Instant now = Instant.now();
        return new LearningResource(new UUID(0L, index + 1L), TENANT_ID, "retention-worker",
                "Expired resource " + index, null, ResourceKind.DOCUMENT, null, null, null,
                "en", ResourceVisibility.TENANT, DownloadPolicy.DOWNLOAD_ALLOWED,
                null, EXPIRED_AT.plusSeconds(index), now.minus(Duration.ofDays(120)));
    }
}
