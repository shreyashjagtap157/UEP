package com.universalplatform.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, NotificationPreferenceId> {
    List<NotificationPreference> findAllByTenantIdAndIdMembershipId(UUID tenantId, UUID membershipId);
    Optional<NotificationPreference> findByTenantIdAndIdMembershipIdAndIdEventType(UUID tenantId, UUID membershipId, String eventType);
}
