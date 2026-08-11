package com.universalplatform.communication;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AnnouncementAcknowledgementRepository extends JpaRepository<AnnouncementAcknowledgement, AnnouncementAcknowledgementId> {
    Optional<AnnouncementAcknowledgement> findByTenantIdAndIdAnnouncementIdAndIdMembershipId(UUID tenantId, UUID announcementId, UUID membershipId);
}
