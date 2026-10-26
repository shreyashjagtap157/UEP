package com.universalplatform.messaging;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MessageTenantPolicyRepository extends JpaRepository<MessageTenantPolicy, UUID> {
    List<MessageTenantPolicy> findAllByOrderByTenantIdAsc();

    @Modifying
    @Query(value = """
            insert into message_tenant_policy (tenant_id)
            values (:tenantId)
            on conflict (tenant_id) do nothing
            """, nativeQuery = true)
    int ensureExists(@Param("tenantId") UUID tenantId);
}
