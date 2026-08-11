package com.universalplatform.curriculum;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface CurriculumModuleRepository extends JpaRepository<CurriculumModule, UUID> {
    Optional<CurriculumModule> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<CurriculumModule> findAllByTenantId(UUID tenantId, Pageable pageable);
    Page<CurriculumModule> findAllByTenantIdAndProgramId(UUID tenantId, UUID programId, Pageable pageable);
    Page<CurriculumModule> findAllByTenantIdAndCourseId(UUID tenantId, UUID courseId, Pageable pageable);
    Page<CurriculumModule> findAllByTenantIdAndSubjectId(UUID tenantId, UUID subjectId, Pageable pageable);
    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
}
