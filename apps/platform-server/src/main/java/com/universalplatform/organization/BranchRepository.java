package com.universalplatform.organization;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface BranchRepository extends JpaRepository<Branch, UUID> {
    Optional<Branch> findByTenantIdAndId(UUID tenantId, UUID id);
    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
    Page<Branch> findAllByTenantId(UUID tenantId, Pageable pageable);
}
