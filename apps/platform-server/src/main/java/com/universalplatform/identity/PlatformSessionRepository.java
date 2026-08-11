package com.universalplatform.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface PlatformSessionRepository extends JpaRepository<PlatformSession, UUID> {
    Optional<PlatformSession> findByTenantIdAndMembershipIdAndOidcSessionId(UUID tenantId, UUID membershipId, String oidcSessionId);
    Optional<PlatformSession> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<PlatformSession> findAllByTenantIdAndMembershipId(UUID tenantId, UUID membershipId, Pageable pageable);
}
