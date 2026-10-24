package com.universalplatform.communication;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AnnouncementTargetRepository extends JpaRepository<AnnouncementTarget, UUID> {
    List<AnnouncementTarget> findAllByTenantIdAndAnnouncementId(UUID tenantId, UUID announcementId);
    List<AnnouncementTarget> findAllByTenantIdAndAnnouncementIdIn(UUID tenantId, java.util.Collection<UUID> announcementIds);
    void deleteAllByTenantIdAndAnnouncementId(UUID tenantId, UUID announcementId);
}
