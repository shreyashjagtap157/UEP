package com.universalplatform.academics;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AcademicProgramRepository extends JpaRepository<AcademicProgram, UUID> {
    Optional<AcademicProgram> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<AcademicProgram> findAllByTenantId(UUID tenantId, Pageable pageable);
    Page<AcademicProgram> findAllByTenantIdAndAcademicPeriodId(UUID tenantId, UUID academicPeriodId, Pageable pageable);
    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
    long countByTenantId(UUID tenantId);
}
