package com.universalplatform.curriculum;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface CourseRepository extends JpaRepository<Course, UUID> {
    Optional<Course> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<Course> findAllByTenantId(UUID tenantId, Pageable pageable);
    Page<Course> findAllByTenantIdAndProgramId(UUID tenantId, UUID programId, Pageable pageable);
    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
}
