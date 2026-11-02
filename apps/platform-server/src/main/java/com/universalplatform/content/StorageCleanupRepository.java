package com.universalplatform.content;

import jakarta.persistence.LockModeType;
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

interface StorageCleanupRepository extends JpaRepository<StorageCleanupJob, UUID> {
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO storage_cleanup_outbox(
                id, tenant_id, provider, locator, object_key, quota_reservation_id, created_at, next_attempt_at
            ) VALUES (
                :id, :tenantId, :provider, :locator, :objectKey, :reservationId, :createdAt, :nextAttemptAt
            )
            ON CONFLICT (tenant_id, provider, locator) DO UPDATE
            SET object_key = CASE
                    WHEN storage_cleanup_outbox.deleted_at IS NOT NULL
                     AND (storage_cleanup_outbox.object_key, storage_cleanup_outbox.quota_reservation_id)
                         IS DISTINCT FROM (EXCLUDED.object_key, EXCLUDED.quota_reservation_id)
                    THEN EXCLUDED.object_key
                    ELSE storage_cleanup_outbox.object_key
                END,
                quota_reservation_id = CASE
                    WHEN storage_cleanup_outbox.deleted_at IS NOT NULL
                     AND (storage_cleanup_outbox.object_key, storage_cleanup_outbox.quota_reservation_id)
                         IS DISTINCT FROM (EXCLUDED.object_key, EXCLUDED.quota_reservation_id)
                    THEN EXCLUDED.quota_reservation_id
                    ELSE storage_cleanup_outbox.quota_reservation_id
                END,
                deleted_at = CASE
                    WHEN storage_cleanup_outbox.deleted_at IS NOT NULL
                     AND (storage_cleanup_outbox.object_key, storage_cleanup_outbox.quota_reservation_id)
                         IS DISTINCT FROM (EXCLUDED.object_key, EXCLUDED.quota_reservation_id)
                    THEN NULL
                    ELSE storage_cleanup_outbox.deleted_at
                END,
                attempt_count = CASE
                    WHEN storage_cleanup_outbox.deleted_at IS NOT NULL
                     AND (storage_cleanup_outbox.object_key, storage_cleanup_outbox.quota_reservation_id)
                         IS DISTINCT FROM (EXCLUDED.object_key, EXCLUDED.quota_reservation_id)
                    THEN 0
                    ELSE storage_cleanup_outbox.attempt_count
                END,
                last_error = CASE
                    WHEN storage_cleanup_outbox.deleted_at IS NOT NULL
                     AND (storage_cleanup_outbox.object_key, storage_cleanup_outbox.quota_reservation_id)
                         IS DISTINCT FROM (EXCLUDED.object_key, EXCLUDED.quota_reservation_id)
                    THEN NULL
                    ELSE storage_cleanup_outbox.last_error
                END,
                next_attempt_at = CASE
                    WHEN storage_cleanup_outbox.deleted_at IS NOT NULL
                     AND (storage_cleanup_outbox.object_key, storage_cleanup_outbox.quota_reservation_id)
                         IS DISTINCT FROM (EXCLUDED.object_key, EXCLUDED.quota_reservation_id)
                    THEN EXCLUDED.next_attempt_at
                    ELSE LEAST(storage_cleanup_outbox.next_attempt_at, EXCLUDED.next_attempt_at)
                END,
                row_version = storage_cleanup_outbox.row_version + 1
            WHERE storage_cleanup_outbox.dead_lettered_at IS NULL
              AND (
                  storage_cleanup_outbox.deleted_at IS NOT NULL
                  OR (
                      storage_cleanup_outbox.object_key = EXCLUDED.object_key
                      AND storage_cleanup_outbox.quota_reservation_id = EXCLUDED.quota_reservation_id
                  )
              )
            """, nativeQuery = true)
    int enqueueIfAbsent(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("provider") String provider,
            @Param("locator") String locator,
            @Param("objectKey") String objectKey,
            @Param("reservationId") UUID reservationId,
            @Param("createdAt") Instant createdAt,
            @Param("nextAttemptAt") Instant nextAttemptAt);

    @Query("""
            select job.id from StorageCleanupJob job
            where job.deletedAt is null and job.deadLetteredAt is null
              and job.attemptCount < 12 and job.nextAttemptAt <= :now
              and (job.processingLeaseExpiresAt is null or job.processingLeaseExpiresAt <= :now)
            order by job.nextAttemptAt, job.createdAt, job.id
            """)
    List<UUID> findDueIds(@Param("now") Instant now, Pageable pageable);


    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE storage_cleanup_outbox
            SET processing_token = :token,
                processing_lease_expires_at = :leaseExpiresAt,
                row_version = row_version + 1
            WHERE id = :id
              AND deleted_at IS NULL
              AND dead_lettered_at IS NULL
              AND attempt_count < 12
              AND next_attempt_at <= :now
              AND (processing_lease_expires_at IS NULL OR processing_lease_expires_at <= :now)
            """, nativeQuery = true)
    int claimIfAvailable(@Param("id") UUID id,
                         @Param("token") UUID token,
                         @Param("now") Instant now,
                         @Param("leaseExpiresAt") Instant leaseExpiresAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from StorageCleanupJob job where job.id = :id")
    Optional<StorageCleanupJob> lockById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from StorageCleanupJob job where job.tenantId = :tenantId and job.id = :id")
    Optional<StorageCleanupJob> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    List<StorageCleanupJob> findTop100ByTenantIdAndDeletedAtIsNullAndDeadLetteredAtIsNotNullOrderByDeadLetteredAtAsc(
            UUID tenantId);
}
