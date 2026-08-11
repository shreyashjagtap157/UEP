package com.universalplatform.enrollment;

import com.universalplatform.academics.AcademicDirectory;
import com.universalplatform.audit.AuditService;
import com.universalplatform.curriculum.CurriculumDirectory;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.MembershipDirectory;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.organization.OrganizationDirectory;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EnrollmentService {
    private final TenantContext tenantContext;
    private final ActorContext actorContext;
    private final AuthorizationService authorization;
    private final MembershipDirectory memberships;
    private final OrganizationDirectory organizations;
    private final AcademicDirectory academics;
    private final CurriculumDirectory curriculum;
    private final BatchRepository batches;
    private final EnrollmentRepository enrollments;
    private final TeacherAssignmentRepository teachingAssignments;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    EnrollmentService(TenantContext tenantContext, ActorContext actorContext, AuthorizationService authorization,
                      MembershipDirectory memberships, OrganizationDirectory organizations, AcademicDirectory academics,
                      CurriculumDirectory curriculum, BatchRepository batches, EnrollmentRepository enrollments,
                      TeacherAssignmentRepository teachingAssignments, AuditService audit) {
        this.tenantContext = tenantContext;
        this.actorContext = actorContext;
        this.authorization = authorization;
        this.memberships = memberships;
        this.organizations = organizations;
        this.academics = academics;
        this.curriculum = curriculum;
        this.batches = batches;
        this.enrollments = enrollments;
        this.teachingAssignments = teachingAssignments;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    EnrollmentPage<BatchView> listBatches(UUID academicPeriodId, UUID programId, UUID courseId, UUID branchId, int page, int size) {
        authorization.require(PermissionKey.ENROLLMENTS_VIEW, branchId);
        UUID tenantId = tenantContext.requireTenantId();
        Page<Batch> result = batches.search(tenantId, academicPeriodId, programId, courseId, branchId,
                PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Direction.ASC, "displayName", "id")));
        return page(result, EnrollmentService::view);
    }

    @Transactional
    BatchView createBatch(UUID academicPeriodId, UUID programId, UUID courseId, UUID branchId,
                          String code, String displayName, String sectionCode, LocalDate startsOn, LocalDate endsOn,
                          Integer capacity) {
        authorization.require(PermissionKey.ENROLLMENTS_MANAGE, branchId);
        validateBatchReferences(academicPeriodId, programId, courseId, branchId);
        validateDates(startsOn, endsOn);
        validateCapacity(capacity);
        UUID tenantId = tenantContext.requireTenantId();
        String normalizedCode = code(code);
        if (batches.existsByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)) throw new EnrollmentConflictException("Batch code already exists");
        Batch batch = batches.save(new Batch(UUID.randomUUID(), tenantId, academicPeriodId, programId, courseId, branchId,
                normalizedCode, required(displayName, "Batch name", 200), nullable(sectionCode, 64), startsOn, endsOn, capacity, clock.instant()));
        audit.record(tenantId, actorContext.requireSubject(), "BATCH_CREATED", "batch", batch.id().toString());
        return view(batch);
    }

    @Transactional
    BatchView updateBatch(UUID id, UUID academicPeriodId, UUID programId, UUID courseId, UUID branchId,
                          String code, String displayName, String sectionCode, LocalDate startsOn, LocalDate endsOn,
                          Integer capacity, BatchStatus status, long expectedVersion) {
        authorization.require(PermissionKey.ENROLLMENTS_MANAGE, branchId);
        validateBatchReferences(academicPeriodId, programId, courseId, branchId);
        validateDates(startsOn, endsOn);
        validateCapacity(capacity);
        UUID tenantId = tenantContext.requireTenantId();
        Batch batch = batch(tenantId, id);
        if (batch.version() != expectedVersion) throw new EnrollmentConflictException("Batch was modified by another request");
        String normalizedCode = code(code);
        if (!batch.code().equalsIgnoreCase(normalizedCode) && batches.existsByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)) {
            throw new EnrollmentConflictException("Batch code already exists");
        }
        batch.update(academicPeriodId, programId, courseId, branchId, normalizedCode,
                required(displayName, "Batch name", 200), nullable(sectionCode, 64), startsOn, endsOn, capacity, status, clock.instant());
        audit.record(tenantId, actorContext.requireSubject(), "BATCH_UPDATED", "batch", batch.id().toString());
        return view(batch);
    }

    @Transactional(readOnly = true)
    EnrollmentPage<EnrollmentView> listEnrollments(int page, int size) {
        authorization.require(PermissionKey.ENROLLMENTS_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        Page<Enrollment> result = enrollments.findAllByTenantId(tenantId,
                PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Direction.DESC, "enrolledAt", "id")));
        return enrollmentPage(result);
    }

    @Transactional(readOnly = true)
    EnrollmentPage<EnrollmentView> myEnrollments(int page, int size) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = memberships.current().membershipId();
        Page<Enrollment> result = enrollments.findAllByTenantIdAndMembershipId(tenantId, membershipId,
                PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Direction.DESC, "enrolledAt", "id")));
        return enrollmentPage(result);
    }

    @Transactional
    EnrollmentView createEnrollment(UUID membershipId, UUID programId, UUID courseId, UUID batchId, String externalReference) {
        authorization.require(PermissionKey.ENROLLMENTS_MANAGE);
        validateSingleTarget(programId, courseId, batchId);
        UUID tenantId = tenantContext.requireTenantId();
        memberships.requireActive(membershipId);
        validateEnrollmentTarget(programId, courseId, batchId);
        if (enrollments.existsActive(tenantId, membershipId, programId, courseId, batchId)) {
            throw new EnrollmentConflictException("An active enrollment already exists for this learner and target");
        }
        Enrollment enrollment = enrollments.save(new Enrollment(UUID.randomUUID(), tenantId, membershipId, programId, courseId, batchId,
                nullable(externalReference, 160), clock.instant()));
        audit.record(tenantId, actorContext.requireSubject(), "ENROLLMENT_CREATED", "enrollment", enrollment.id().toString());
        return view(enrollment, memberships.requireActive(membershipId));
    }

    @Transactional
    EnrollmentView changeEnrollmentStatus(UUID id, EnrollmentStatus status, long expectedVersion) {
        authorization.require(PermissionKey.ENROLLMENTS_MANAGE);
        if (status == null) throw new IllegalArgumentException("Enrollment status is required");
        UUID tenantId = tenantContext.requireTenantId();
        Enrollment enrollment = enrollment(tenantId, id);
        if (enrollment.version() != expectedVersion) throw new EnrollmentConflictException("Enrollment was modified by another request");
        if (enrollment.status() != EnrollmentStatus.ENROLLED && status == EnrollmentStatus.ENROLLED) {
            validateEnrollmentTarget(enrollment.programId(), enrollment.courseId(), enrollment.batchId());
            if (enrollments.existsActive(tenantId, enrollment.membershipId(), enrollment.programId(), enrollment.courseId(), enrollment.batchId())) {
                throw new EnrollmentConflictException("Another active enrollment already exists for this learner and target");
            }
        }
        enrollment.changeStatus(status, clock.instant());
        audit.record(tenantId, actorContext.requireSubject(), "ENROLLMENT_STATUS_CHANGED", "enrollment", enrollment.id().toString());
        return view(enrollment, memberships.references(java.util.List.of(enrollment.membershipId())).get(enrollment.membershipId()));
    }

    @Transactional(readOnly = true)
    EnrollmentPage<TeacherAssignmentView> listTeachingAssignments(int page, int size) {
        authorization.require(PermissionKey.TEACHING_ASSIGNMENTS_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        Page<TeacherAssignment> result = teachingAssignments.findAllByTenantId(tenantId,
                PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Direction.DESC, "assignedAt", "id")));
        return teachingPage(result);
    }

    @Transactional(readOnly = true)
    EnrollmentPage<TeacherAssignmentView> myTeachingAssignments(int page, int size) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = memberships.current().membershipId();
        Page<TeacherAssignment> result = teachingAssignments.findAllByTenantIdAndMembershipId(tenantId, membershipId,
                PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Direction.DESC, "assignedAt", "id")));
        return teachingPage(result);
    }

    @Transactional
    TeacherAssignmentView createTeachingAssignment(UUID membershipId, UUID programId, UUID courseId, UUID subjectId,
                                                    UUID moduleId, UUID batchId, TeacherAssignmentRole role, LocalDate startsOn) {
        authorization.require(PermissionKey.TEACHING_ASSIGNMENTS_MANAGE);
        validateSingleScope(programId, courseId, subjectId, moduleId, batchId);
        if (role == null) throw new IllegalArgumentException("Teaching assignment role is required");
        UUID tenantId = tenantContext.requireTenantId();
        MembershipDirectory.MembershipReference member = memberships.requireActive(membershipId);
        validateTeachingScope(programId, courseId, subjectId, moduleId, batchId);
        if (teachingAssignments.existsActive(tenantId, membershipId, programId, courseId, subjectId, moduleId, batchId, role)) {
            throw new EnrollmentConflictException("An active teaching assignment already exists for this person, scope, and role");
        }
        TeacherAssignment assignment = teachingAssignments.save(new TeacherAssignment(UUID.randomUUID(), tenantId, membershipId,
                programId, courseId, subjectId, moduleId, batchId, role, startsOn, actorContext.requireSubject(), clock.instant()));
        audit.record(tenantId, actorContext.requireSubject(), "TEACHER_ASSIGNMENT_CREATED", "teacher_assignment", assignment.id().toString());
        return view(assignment, member);
    }

    @Transactional
    TeacherAssignmentView endTeachingAssignment(UUID id, LocalDate endsOn, long expectedVersion) {
        authorization.require(PermissionKey.TEACHING_ASSIGNMENTS_MANAGE);
        if (endsOn == null) endsOn = LocalDate.now(clock);
        UUID tenantId = tenantContext.requireTenantId();
        TeacherAssignment assignment = assignment(tenantId, id);
        if (assignment.version() != expectedVersion) throw new EnrollmentConflictException("Teaching assignment was modified by another request");
        if (assignment.status() == TeacherAssignmentStatus.ENDED) throw new EnrollmentConflictException("Teaching assignment is already ended");
        if (assignment.startsOn() != null && endsOn.isBefore(assignment.startsOn())) throw new IllegalArgumentException("Assignment end date cannot be before start date");
        assignment.end(endsOn);
        audit.record(tenantId, actorContext.requireSubject(), "TEACHER_ASSIGNMENT_ENDED", "teacher_assignment", assignment.id().toString());
        MembershipDirectory.MembershipReference member = memberships.references(java.util.List.of(assignment.membershipId())).get(assignment.membershipId());
        return view(assignment, member);
    }

    private void validateBatchReferences(UUID academicPeriodId, UUID programId, UUID courseId, UUID branchId) {
        AcademicDirectory.PeriodReference period = academicPeriodId == null ? null : academics.requirePeriod(academicPeriodId);
        AcademicDirectory.ProgramReference program = programId == null ? null : academics.requireProgram(programId);
        CurriculumDirectory.CourseReference course = courseId == null ? null : curriculum.requireCourse(courseId);
        OrganizationDirectory.BranchReference branch = branchId == null ? null : organizations.requireBranch(branchId);
        if (period != null && period.status() != com.universalplatform.academics.AcademicPeriodStatus.PLANNED
                && period.status() != com.universalplatform.academics.AcademicPeriodStatus.ACTIVE) {
            throw new EnrollmentConflictException("Academic period is not open for batch scheduling");
        }
        if (program != null && program.status() != com.universalplatform.academics.ProgramStatus.ACTIVE) {
            throw new EnrollmentConflictException("Program is not active for batch scheduling");
        }
        if (course != null && course.status() != com.universalplatform.curriculum.CurriculumStatus.ACTIVE) {
            throw new EnrollmentConflictException("Course is not active for batch scheduling");
        }
        if (branch != null && branch.status() != com.universalplatform.organization.BranchStatus.ACTIVE) {
            throw new EnrollmentConflictException("Branch is not active for batch scheduling");
        }
        if (program != null && period != null && program.academicPeriodId() != null && !program.academicPeriodId().equals(period.id())) {
            throw new IllegalArgumentException("Program belongs to a different academic period");
        }
        if (course != null && program != null && course.programId() != null && !course.programId().equals(program.id())) {
            throw new IllegalArgumentException("Course belongs to a different program");
        }
    }

    private void validateEnrollmentTarget(UUID programId, UUID courseId, UUID batchId) {
        if (programId != null) {
            var program = academics.requireProgram(programId);
            if (program.status() != com.universalplatform.academics.ProgramStatus.ACTIVE)
                throw new EnrollmentConflictException("Program is not active for enrollment");
        }
        if (courseId != null) {
            var course = curriculum.requireCourse(courseId);
            if (course.status() != com.universalplatform.curriculum.CurriculumStatus.ACTIVE)
                throw new EnrollmentConflictException("Course is not active for enrollment");
        }
        if (batchId != null) {
            Batch target = batchForUpdate(tenantContext.requireTenantId(), batchId);
            if (target.status() != BatchStatus.PLANNED && target.status() != BatchStatus.ACTIVE)
                throw new EnrollmentConflictException("Batch is not open for enrollment");
            if (target.capacity() != null && enrollments.countActiveByBatch(target.tenantId(), target.id()) >= target.capacity())
                throw new EnrollmentConflictException("Batch capacity has been reached");
        }
    }

    private void validateTeachingScope(UUID programId, UUID courseId, UUID subjectId, UUID moduleId, UUID batchId) {
        if (programId != null) academics.requireProgram(programId);
        if (courseId != null) curriculum.requireCourse(courseId);
        if (subjectId != null) curriculum.requireSubject(subjectId);
        if (moduleId != null) curriculum.requireModule(moduleId);
        if (batchId != null) batch(tenantContext.requireTenantId(), batchId);
    }

    private static void validateSingleTarget(UUID programId, UUID courseId, UUID batchId) {
        if ((programId == null ? 0 : 1) + (courseId == null ? 0 : 1) + (batchId == null ? 0 : 1) != 1) {
            throw new IllegalArgumentException("Enrollment must target exactly one program, course, or batch");
        }
    }

    private static void validateSingleScope(UUID programId, UUID courseId, UUID subjectId, UUID moduleId, UUID batchId) {
        int count = (programId == null ? 0 : 1) + (courseId == null ? 0 : 1) + (subjectId == null ? 0 : 1)
                + (moduleId == null ? 0 : 1) + (batchId == null ? 0 : 1);
        if (count != 1) throw new IllegalArgumentException("Teaching assignment must target exactly one academic scope");
    }

    private static void validateDates(LocalDate startsOn, LocalDate endsOn) {
        if (startsOn != null && endsOn != null && endsOn.isBefore(startsOn)) throw new IllegalArgumentException("Batch end date cannot be before start date");
    }
    private static void validateCapacity(Integer capacity) { if (capacity != null && capacity < 1) throw new IllegalArgumentException("Batch capacity must be positive"); }

    private Batch batch(UUID tenantId, UUID id) { return batches.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new EnrollmentNotFoundException("Batch not found")); }
    private Batch batchForUpdate(UUID tenantId, UUID id) { return batches.findByTenantIdAndIdForUpdate(tenantId, id).orElseThrow(() -> new EnrollmentNotFoundException("Batch not found")); }
    private Enrollment enrollment(UUID tenantId, UUID id) { return enrollments.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new EnrollmentNotFoundException("Enrollment not found")); }
    private TeacherAssignment assignment(UUID tenantId, UUID id) { return teachingAssignments.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new EnrollmentNotFoundException("Teaching assignment not found")); }

    private EnrollmentPage<EnrollmentView> enrollmentPage(Page<Enrollment> result) {
        Map<UUID, MembershipDirectory.MembershipReference> people = memberships.references(result.getContent().stream().map(Enrollment::membershipId).toList());
        return new EnrollmentPage<>(result.getContent().stream().map(item -> view(item, people.get(item.membershipId()))).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private EnrollmentPage<TeacherAssignmentView> teachingPage(Page<TeacherAssignment> result) {
        Map<UUID, MembershipDirectory.MembershipReference> people = memberships.references(result.getContent().stream().map(TeacherAssignment::membershipId).toList());
        return new EnrollmentPage<>(result.getContent().stream().map(item -> view(item, people.get(item.membershipId()))).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private static <E, V> EnrollmentPage<V> page(Page<E> page, Function<E, V> mapper) { return new EnrollmentPage<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()); }
    private static int safePage(int page) { return Math.max(0, page); }
    private static int safeSize(int size) { return size < 1 ? 25 : Math.min(size, 100); }

    private static String code(String value) {
        String normalized = required(value, "Code", 64).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9][A-Z0-9._-]{0,63}")) throw new IllegalArgumentException("Code may contain letters, numbers, dot, underscore, and hyphen");
        return normalized;
    }
    private static String required(String value, String label, int max) { if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required"); String normalized = value.trim(); if (normalized.length() > max) throw new IllegalArgumentException(label + " is too long"); return normalized; }
    private static String nullable(String value, int max) { if (value == null || value.isBlank()) return null; String normalized = value.trim(); if (normalized.length() > max) throw new IllegalArgumentException("Value is too long"); return normalized; }

    private static BatchView view(Batch batch) { return new BatchView(batch.id(), batch.academicPeriodId(), batch.programId(), batch.courseId(), batch.branchId(), batch.code(), batch.displayName(), batch.sectionCode(), batch.startsOn(), batch.endsOn(), batch.capacity(), batch.status(), batch.createdAt(), batch.version()); }
    private static EnrollmentView view(Enrollment enrollment, MembershipDirectory.MembershipReference member) {
        return new EnrollmentView(enrollment.id(), enrollment.membershipId(), member == null ? null : member.displayName(),
                enrollment.programId(), enrollment.courseId(), enrollment.batchId(), enrollment.status(), enrollment.enrolledAt(),
                enrollment.endedAt(), enrollment.externalReference(), enrollment.version());
    }
    private static TeacherAssignmentView view(TeacherAssignment assignment, MembershipDirectory.MembershipReference member) {
        return new TeacherAssignmentView(assignment.id(), assignment.membershipId(), member == null ? null : member.displayName(),
                assignment.programId(), assignment.courseId(), assignment.subjectId(), assignment.moduleId(), assignment.batchId(),
                assignment.assignmentRole(), assignment.startsOn(), assignment.endsOn(), assignment.status(), assignment.assignedAt(), assignment.version());
    }

    record BatchView(UUID id, UUID academicPeriodId, UUID programId, UUID courseId, UUID branchId, String code,
                     String displayName, String sectionCode, LocalDate startsOn, LocalDate endsOn, Integer capacity,
                     BatchStatus status, java.time.Instant createdAt, long version) {}
    record EnrollmentView(UUID id, UUID membershipId, String displayName, UUID programId, UUID courseId, UUID batchId,
                          EnrollmentStatus status, java.time.Instant enrolledAt, java.time.Instant endedAt,
                          String externalReference, long version) {}
    record TeacherAssignmentView(UUID id, UUID membershipId, String displayName, UUID programId, UUID courseId,
                                 UUID subjectId, UUID moduleId, UUID batchId, TeacherAssignmentRole assignmentRole,
                                 LocalDate startsOn, LocalDate endsOn, TeacherAssignmentStatus status,
                                 java.time.Instant assignedAt, long version) {}
}
