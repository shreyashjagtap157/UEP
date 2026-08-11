package com.universalplatform.enrollment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    Optional<Enrollment> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<Enrollment> findAllByTenantId(UUID tenantId, Pageable pageable);
    Page<Enrollment> findAllByTenantIdAndMembershipId(UUID tenantId, UUID membershipId, Pageable pageable);

    @Query("""
            select count(e) > 0 from Enrollment e
            where e.tenantId = :tenantId and e.membershipId = :membershipId
              and e.status = com.universalplatform.enrollment.EnrollmentStatus.ENROLLED
              and ((:programId is not null and e.programId = :programId)
                   or (:courseId is not null and e.courseId = :courseId)
                   or (:batchId is not null and e.batchId = :batchId))
            """)
    boolean existsActive(@Param("tenantId") UUID tenantId, @Param("membershipId") UUID membershipId,
                         @Param("programId") UUID programId, @Param("courseId") UUID courseId,
                         @Param("batchId") UUID batchId);

    @Query("""
            select count(e) from Enrollment e
            where e.tenantId = :tenantId and e.batchId = :batchId
              and e.status = com.universalplatform.enrollment.EnrollmentStatus.ENROLLED
            """)
    long countActiveByBatch(@Param("tenantId") UUID tenantId, @Param("batchId") UUID batchId);
}
