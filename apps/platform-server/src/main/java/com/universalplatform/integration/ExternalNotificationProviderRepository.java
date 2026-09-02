package com.universalplatform.integration;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface ExternalNotificationProviderRepository extends JpaRepository<ExternalNotificationProvider,UUID>{List<ExternalNotificationProvider> findAllByTenantIdAndEnabledTrue(UUID tenantId);Optional<ExternalNotificationProvider> findByTenantIdAndProviderKey(UUID tenantId,String key);}
