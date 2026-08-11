package com.universalplatform.communication;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AnnouncementTargetRepository extends JpaRepository<AnnouncementTarget, UUID> {
    List<AnnouncementTarget> findAllByTenantIdAndAnnouncementId(UUID tenantId, UUID announcementId);
    void deleteAllByTenantIdAndAnnouncementId(UUID tenantId, UUID announcementId);
}
