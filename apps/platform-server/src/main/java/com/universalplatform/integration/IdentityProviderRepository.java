package com.universalplatform.integration;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface IdentityProviderRepository extends JpaRepository<IdentityProvider,UUID>{List<IdentityProvider> findAllByTenantIdAndEnabledTrue(UUID tenantId);Optional<IdentityProvider> findByTenantIdAndProviderKey(UUID tenantId,String key);}
