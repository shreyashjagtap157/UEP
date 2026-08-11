package com.universalplatform.notification;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface PlatformNotificationRepository extends JpaRepository<PlatformNotification, UUID> {
    Page<PlatformNotification> findAllByTenantIdAndMembershipIdAndVisibleInAppTrue(UUID tenantId, UUID membershipId, Pageable pageable);
    Optional<PlatformNotification> findByTenantIdAndMembershipIdAndIdAndVisibleInAppTrue(UUID tenantId, UUID membershipId, UUID id);
    boolean existsBySourceOutboxIdAndMembershipId(UUID outboxId, UUID membershipId);
    Optional<PlatformNotification> findBySourceOutboxIdAndMembershipId(UUID outboxId, UUID membershipId);
    long countByTenantIdAndMembershipIdAndVisibleInAppTrueAndReadAtIsNull(UUID tenantId, UUID membershipId);
}
