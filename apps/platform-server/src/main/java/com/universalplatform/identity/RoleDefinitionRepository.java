package com.universalplatform.identity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleDefinitionRepository extends JpaRepository<RoleDefinition, UUID> {
    Optional<RoleDefinition> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<RoleDefinition> findByTenantIdAndSystemKey(UUID tenantId, String systemKey);
    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);
    Page<RoleDefinition> findAllByTenantId(UUID tenantId, Pageable pageable);
    List<RoleDefinition> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
}
