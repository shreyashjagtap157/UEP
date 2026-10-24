package com.universalplatform.content;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

interface StorageCleanupRepository extends JpaRepository<StorageCleanupJob, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO storage_cleanup_outbox(
                id, tenant_id, provider, locator, object_key, created_at, next_attempt_at
            ) VALUES (
                :id, :tenantId, :provider, :locator, :objectKey, :createdAt, :nextAttemptAt
            )
            ON CONFLICT (tenant_id, provider, locator) DO NOTHING
            """, nativeQuery = true)
    int enqueueIfAbsent(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("provider") String provider,
            @Param("locator") String locator,
            @Param("objectKey") String objectKey,
            @Param("createdAt") Instant createdAt,
            @Param("nextAttemptAt") Instant nextAttemptAt);

    @Query("""
            select job.id from StorageCleanupJob job
            where job.deletedAt is null and job.nextAttemptAt <= :now
            order by job.nextAttemptAt, job.createdAt
            """)
    List<UUID> findDueIds(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from StorageCleanupJob job where job.id = :id")
    Optional<StorageCleanupJob> lockById(@Param("id") UUID id);

    List<StorageCleanupJob> findTop100ByDeletedAtIsNullOrderByCreatedAtAsc();
}
