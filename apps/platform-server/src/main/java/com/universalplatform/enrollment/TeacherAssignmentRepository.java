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
    boolean existsActive(@Param("tenantId") UUID tenantId, @Param("membershipId") UUID membershipId,
                         @Param("programId") UUID programId, @Param("courseId") UUID courseId,
                         @Param("subjectId") UUID subjectId, @Param("moduleId") UUID moduleId,
                         @Param("batchId") UUID batchId, @Param("role") TeacherAssignmentRole role);

    @Query(value = """
            SELECT DISTINCT ta.membership_id
            FROM teacher_assignment ta
            WHERE ta.tenant_id = :tenantId
              AND ta.status = 'ACTIVE'
              AND (
                (:batchId IS NOT NULL AND ta.batch_id = :batchId)
                OR (:courseId IS NOT NULL AND (
                    ta.course_id = :courseId
                    OR ta.batch_id IN (
                        SELECT b.id FROM batch b
                        WHERE b.tenant_id = :tenantId AND b.course_id = :courseId
                    )
                    OR ta.subject_id IN (
                        SELECT s.id FROM subject s
                        WHERE s.tenant_id = :tenantId AND s.course_id = :courseId
                    )
                    OR ta.module_id IN (
                        SELECT m.id FROM curriculum_module m
                        WHERE m.tenant_id = :tenantId
                          AND (
                              m.course_id = :courseId
                              OR m.subject_id IN (
                                  SELECT s2.id FROM subject s2
                                  WHERE s2.tenant_id = :tenantId AND s2.course_id = :courseId
                              )
                          )
                    )
                ))
                OR (:subjectId IS NOT NULL AND (
                    ta.subject_id = :subjectId
                    OR ta.module_id IN (
                        SELECT m2.id FROM curriculum_module m2
                        WHERE m2.tenant_id = :tenantId AND m2.subject_id = :subjectId
                    )
                ))
              )
            """, nativeQuery = true)
    java.util.List<UUID> findActiveMembershipIdsForAudience(@Param("tenantId") UUID tenantId,
            @Param("batchId") UUID batchId, @Param("courseId") UUID courseId, @Param("subjectId") UUID subjectId);

    @Query("""
            select distinct t.batchId from TeacherAssignment t
            where t.tenantId = :tenantId and t.membershipId = :membershipId
              and t.status = com.universalplatform.enrollment.TeacherAssignmentStatus.ACTIVE and t.batchId is not null
            """)
    java.util.List<UUID> findActiveBatchIdsByMembership(@Param("tenantId") UUID tenantId, @Param("membershipId") UUID membershipId);

    @Query("""
            select distinct t.courseId from TeacherAssignment t
            where t.tenantId = :tenantId and t.membershipId = :membershipId
              and t.status = com.universalplatform.enrollment.TeacherAssignmentStatus.ACTIVE and t.courseId is not null
            """)
    java.util.List<UUID> findActiveCourseIdsByMembership(@Param("tenantId") UUID tenantId, @Param("membershipId") UUID membershipId);

    @Query("""
            select distinct t.subjectId from TeacherAssignment t
            where t.tenantId = :tenantId and t.membershipId = :membershipId
              and t.status = com.universalplatform.enrollment.TeacherAssignmentStatus.ACTIVE and t.subjectId is not null
            """)
    java.util.List<UUID> findActiveSubjectIdsByMembership(@Param("tenantId") UUID tenantId, @Param("membershipId") UUID membershipId);

    @Query("""
            select distinct t.moduleId from TeacherAssignment t
            where t.tenantId = :tenantId and t.membershipId = :membershipId
              and t.status = com.universalplatform.enrollment.TeacherAssignmentStatus.ACTIVE and t.moduleId is not null
            """)
    java.util.List<UUID> findActiveModuleIdsByMembership(@Param("tenantId") UUID tenantId, @Param("membershipId") UUID membershipId);

}
