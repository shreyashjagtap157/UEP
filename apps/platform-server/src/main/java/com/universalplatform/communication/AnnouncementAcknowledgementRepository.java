package com.universalplatform.communication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AnnouncementAcknowledgementRepository extends JpaRepository<AnnouncementAcknowledgement, AnnouncementAcknowledgementId> {
    Optional<AnnouncementAcknowledgement> findByTenantIdAndIdAnnouncementIdAndIdMembershipId(UUID tenantId, UUID announcementId, UUID membershipId);
    List<AnnouncementAcknowledgement> findAllByTenantIdAndIdAnnouncementIdInAndIdMembershipId(UUID tenantId, java.util.Collection<UUID> announcementIds, UUID membershipId);
}
