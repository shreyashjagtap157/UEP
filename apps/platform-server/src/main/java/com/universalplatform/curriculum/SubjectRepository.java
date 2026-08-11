package com.universalplatform.curriculum;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SubjectRepository extends JpaRepository<Subject, UUID> {
    Optional<Subject> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<Subject> findAllByTenantId(UUID tenantId, Pageable pageable);
    Page<Subject> findAllByTenantIdAndCourseId(UUID tenantId, UUID courseId, Pageable pageable);
    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
}
