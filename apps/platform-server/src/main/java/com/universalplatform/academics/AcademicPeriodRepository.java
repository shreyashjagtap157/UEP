package com.universalplatform.academics;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AcademicPeriodRepository extends JpaRepository<AcademicPeriod, UUID> {
    Optional<AcademicPeriod> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<AcademicPeriod> findAllByTenantId(UUID tenantId, Pageable pageable);
    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
    long countByTenantId(UUID tenantId);
}
