package com.universalplatform.communication;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {
    Optional<Announcement> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<Announcement> findAllByTenantId(UUID tenantId, Pageable pageable);
    Page<Announcement> findAllByTenantIdAndAuthorizationBranchIdIn(UUID tenantId, java.util.Collection<UUID> branchIds, Pageable pageable);

    @Query("""
            select a.id from Announcement a
            where a.status = com.universalplatform.communication.AnnouncementStatus.SCHEDULED
              and a.publishAt <= :now
              and a.publishAvailableAt <= :now
              and a.publishDeadLetteredAt is null
            order by a.publishAt asc, a.id asc
            """)
    List<UUID> findDuePublishIds(@Param("now") Instant now, Pageable pageable);

    @Query("""
            select a.id from Announcement a
            where a.status = com.universalplatform.communication.AnnouncementStatus.PUBLISHED
              and a.expiresAt is not null and a.expiresAt <= :now
              and a.expiryAvailableAt <= :now
              and a.expiryDeadLetteredAt is null
            order by a.expiresAt asc, a.id asc
            """)
    List<UUID> findDueExpiryIds(@Param("now") Instant now, Pageable pageable);

    List<Announcement> findTop100ByTenantIdAndPublishDeadLetteredAtIsNotNullOrderByPublishDeadLetteredAtAsc(UUID tenantId);
    List<Announcement> findTop100ByTenantIdAndAuthorizationBranchIdInAndPublishDeadLetteredAtIsNotNullOrderByPublishDeadLetteredAtAsc(
            UUID tenantId, java.util.Collection<UUID> branchIds);
    List<Announcement> findTop100ByTenantIdAndExpiryDeadLetteredAtIsNotNullOrderByExpiryDeadLetteredAtAsc(UUID tenantId);
    List<Announcement> findTop100ByTenantIdAndAuthorizationBranchIdInAndExpiryDeadLetteredAtIsNotNullOrderByExpiryDeadLetteredAtAsc(
            UUID tenantId, java.util.Collection<UUID> branchIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Announcement a where a.id = :id")
    Optional<Announcement> lockById(@Param("id") UUID id);
}
