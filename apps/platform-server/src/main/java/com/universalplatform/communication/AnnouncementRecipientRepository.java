package com.universalplatform.communication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AnnouncementRecipientRepository extends JpaRepository<AnnouncementRecipient, AnnouncementRecipientId> {
    boolean existsByTenantIdAndIdAnnouncementIdAndIdMembershipId(UUID tenantId, UUID announcementId, UUID membershipId);
    void deleteAllByTenantIdAndIdAnnouncementId(UUID tenantId, UUID announcementId);

    @Query("""
            select a from Announcement a, AnnouncementRecipient r
            where r.tenantId = :tenantId and r.id.membershipId = :membershipId
              and r.id.announcementId = a.id and a.tenantId = :tenantId
              and a.status = com.universalplatform.communication.AnnouncementStatus.PUBLISHED
              and (a.expiresAt is null or a.expiresAt > :now)
            order by a.publishedAt desc, a.id desc
            """)
    Page<Announcement> findVisibleForMember(@Param("tenantId") UUID tenantId, @Param("membershipId") UUID membershipId,
                                             @Param("now") Instant now, Pageable pageable);
}
