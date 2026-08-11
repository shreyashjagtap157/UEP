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

    @Query("""
            select distinct t.membershipId from TeacherAssignment t
            where t.tenantId = :tenantId and t.status = com.universalplatform.enrollment.TeacherAssignmentStatus.ACTIVE
              and ((:batchId is not null and t.batchId = :batchId)
                   or (:courseId is not null and (
                        t.courseId = :courseId
                        or t.batchId in (select b.id from Batch b where b.tenantId = :tenantId and b.courseId = :courseId)
                        or t.subjectId in (select s.id from Subject s where s.tenantId = :tenantId and s.courseId = :courseId)
                        or t.moduleId in (select m.id from CurriculumModule m where m.tenantId = :tenantId and (
                            m.courseId = :courseId or m.subjectId in (select s2.id from Subject s2 where s2.tenantId = :tenantId and s2.courseId = :courseId))))
                   or (:subjectId is not null and (
                        t.subjectId = :subjectId
                        or t.moduleId in (select m2.id from CurriculumModule m2 where m2.tenantId = :tenantId and m2.subjectId = :subjectId))))
            """)
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
