package com.universalplatform.content;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UploadSession u where u.tenantId = :tenantId and u.id = :id")
    Optional<UploadSession> lock(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("""
            select u from UploadSession u
            where u.status in :statuses
              and u.stagingCleanedAt is null
              and u.expiresAt < :before
              and (
                    :afterExpiresAt is null
                    or u.expiresAt > :afterExpiresAt
                    or (u.expiresAt = :afterExpiresAt and u.id > :afterId)
              )
            order by u.expiresAt asc, u.id asc
            """)
    List<UploadSession> findExpiredWithUncleanedStaging(
            @Param("statuses") Collection<UploadStatus> statuses,
            @Param("before") Instant before,
            @Param("afterExpiresAt") Instant afterExpiresAt,
            @Param("afterId") UUID afterId,
            Pageable pageable);

    @Query("""
            select u from UploadSession u
            where u.status = :status
              and u.stagingCleanedAt is null
              and u.completedAt < :before
              and (
                    :afterCompletedAt is null
                    or u.completedAt > :afterCompletedAt
                    or (u.completedAt = :afterCompletedAt and u.id > :afterId)
              )
            order by u.completedAt asc, u.id asc
            """)
    List<UploadSession> findCompletedWithUncleanedStaging(
            @Param("status") UploadStatus status,
            @Param("before") Instant before,
            @Param("afterCompletedAt") Instant afterCompletedAt,
            @Param("afterId") UUID afterId,
            Pageable pageable);
}
