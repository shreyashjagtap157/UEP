package com.universalplatform.licensing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EntitlementGrantRepository extends JpaRepository<EntitlementGrant, UUID> {
    Optional<EntitlementGrant> findByTenantIdAndFeatureKey(UUID tenantId, FeatureKey featureKey);
}
