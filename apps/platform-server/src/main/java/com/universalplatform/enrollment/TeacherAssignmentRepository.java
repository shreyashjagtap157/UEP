package com.universalplatform.enrollment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, UUID> {
    Optional<TeacherAssignment> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<TeacherAssignment> findAllByTenantId(UUID tenantId, Pageable pageable);
    Page<TeacherAssignment> findAllByTenantIdAndMembershipId(UUID tenantId, UUID membershipId, Pageable pageable);

    @Query("""
            select count(t) > 0 from TeacherAssignment t
            where t.tenantId = :tenantId and t.membershipId = :membershipId
              and t.assignmentRole = :role
              and t.status = com.universalplatform.enrollment.TeacherAssignmentStatus.ACTIVE
              and ((:programId is not null and t.programId = :programId)
                   or (:courseId is not null and t.courseId = :courseId)
                   or (:subjectId is not null and t.subjectId = :subjectId)
                   or (:moduleId is not null and t.moduleId = :moduleId)
                   or (:batchId is not null and t.batchId = :batchId))
            """)
    @Query("""
            select distinct t.membershipId from TeacherAssignment t
            where t.tenantId = :tenantId and t.status = com.universalplatform.enrollment.TeacherAssignmentStatus.ACTIVE
              and ((:batchId is not null and t.batchId = :batchId)
                   or (:courseId is not null and t.courseId = :courseId)
                   or (:subjectId is not null and t.subjectId = :subjectId))
            """)
    java.util.List<UUID> findActiveMembershipIdsForAudience(@Param("tenantId") UUID tenantId,
            @Param("batchId") UUID batchId, @Param("courseId") UUID courseId, @Param("subjectId") UUID subjectId);

    boolean existsActive(@Param("tenantId") UUID tenantId, @Param("membershipId") UUID membershipId,
                         @Param("programId") UUID programId, @Param("courseId") UUID courseId,
                         @Param("subjectId") UUID subjectId, @Param("moduleId") UUID moduleId,
                         @Param("batchId") UUID batchId, @Param("role") TeacherAssignmentRole role);
}
