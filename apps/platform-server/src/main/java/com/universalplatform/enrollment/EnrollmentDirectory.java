package com.universalplatform.enrollment;

import com.universalplatform.security.TenantContext;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit read-only batch boundary for later scheduling and live-class modules. */
@Service
public class EnrollmentDirectory {
    private final TenantContext tenantContext;
    private final BatchRepository batches;
    private final EnrollmentRepository enrollments;
    private final TeacherAssignmentRepository teachingAssignments;

    EnrollmentDirectory(TenantContext tenantContext, BatchRepository batches, EnrollmentRepository enrollments,
                        TeacherAssignmentRepository teachingAssignments) {
        this.tenantContext = tenantContext;
        this.batches = batches;
        this.enrollments = enrollments;
        this.teachingAssignments = teachingAssignments;
    }

    @Transactional(readOnly = true)
    public BatchReference requireBatch(UUID id) {
        Batch batch = batches.findByTenantIdAndId(tenantContext.requireTenantId(), id)
                .orElseThrow(() -> new EnrollmentNotFoundException("Batch not found"));
        return new BatchReference(batch.id(), batch.academicPeriodId(), batch.programId(), batch.courseId(),
                batch.branchId(), batch.code(), batch.displayName(), batch.startsOn(), batch.endsOn(), batch.status());
    }



    @Transactional(readOnly = true)
    public java.util.Set<UUID> activeLearnerMembershipIdsForBatch(UUID batchId) {
        UUID tenantId = tenantContext.requireTenantId();
        requireBatch(batchId);
        return java.util.Set.copyOf(enrollments.findActiveMembershipIdsByBatch(tenantId, batchId));
    }

    @Transactional(readOnly = true)
    public java.util.Set<UUID> activeMembershipIdsForBatch(UUID batchId) {
        UUID tenantId = tenantContext.requireTenantId();
        requireBatch(batchId);
        java.util.LinkedHashSet<UUID> result = new java.util.LinkedHashSet<>(enrollments.findActiveMembershipIdsByBatch(tenantId, batchId));
        result.addAll(teachingAssignments.findActiveMembershipIdsForAudience(tenantId, batchId, null, null));
        return java.util.Set.copyOf(result);
    }

    @Transactional(readOnly = true)
    public java.util.Set<UUID> activeMembershipIdsForCourse(UUID courseId) {
        UUID tenantId = tenantContext.requireTenantId();
        java.util.LinkedHashSet<UUID> result = new java.util.LinkedHashSet<>(enrollments.findActiveMembershipIdsByCourse(tenantId, courseId));
        result.addAll(teachingAssignments.findActiveMembershipIdsForAudience(tenantId, null, courseId, null));
        return java.util.Set.copyOf(result);
    }

    @Transactional(readOnly = true)
    public java.util.Set<UUID> activeMembershipIdsForSubject(UUID subjectId, UUID parentCourseId) {
        UUID tenantId = tenantContext.requireTenantId();
        java.util.LinkedHashSet<UUID> result = new java.util.LinkedHashSet<>();
        if (parentCourseId != null) result.addAll(enrollments.findActiveMembershipIdsByCourse(tenantId, parentCourseId));
        result.addAll(teachingAssignments.findActiveMembershipIdsForAudience(tenantId, null, null, subjectId));
        return java.util.Set.copyOf(result);
    }

    @Transactional(readOnly = true)
    public java.util.Set<UUID> activeBatchIdsForMembership(UUID membershipId) {
        UUID tenantId = tenantContext.requireTenantId();
        return java.util.Set.copyOf(enrollments.findActiveBatchIdsByMembership(tenantId, membershipId));
    }

    public record BatchReference(UUID id, UUID academicPeriodId, UUID programId, UUID courseId, UUID branchId,
                                 String code, String displayName, LocalDate startsOn, LocalDate endsOn, BatchStatus status) {}
}
