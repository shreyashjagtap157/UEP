package com.universalplatform.enrollment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class EnrollmentController {
    private final EnrollmentService enrollment;

    EnrollmentController(EnrollmentService enrollment) { this.enrollment = enrollment; }

    @GetMapping("/batches")
    EnrollmentPage<EnrollmentService.BatchView> batches(@RequestParam(required = false) UUID academicPeriodId,
                                                        @RequestParam(required = false) UUID programId,
                                                        @RequestParam(required = false) UUID courseId,
                                                        @RequestParam(required = false) UUID branchId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "25") int size) {
        return enrollment.listBatches(academicPeriodId, programId, courseId, branchId, page, size);
    }

    @PostMapping("/batches")
    EnrollmentService.BatchView createBatch(@Valid @RequestBody BatchRequest request) {
        return enrollment.createBatch(request.academicPeriodId(), request.programId(), request.courseId(), request.branchId(),
                request.code(), request.displayName(), request.sectionCode(), request.startsOn(), request.endsOn(), request.capacity());
    }

    @PutMapping("/batches/{id}")
    EnrollmentService.BatchView updateBatch(@PathVariable UUID id, @Valid @RequestBody BatchRequest request) {
        if (request.status() == null) throw new IllegalArgumentException("Batch status is required when updating");
        return enrollment.updateBatch(id, request.academicPeriodId(), request.programId(), request.courseId(), request.branchId(),
                request.code(), request.displayName(), request.sectionCode(), request.startsOn(), request.endsOn(), request.capacity(),
                request.status(), request.expectedVersion());
    }

    @GetMapping("/enrollments")
    EnrollmentPage<EnrollmentService.EnrollmentView> enrollments(@RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "25") int size) {
        return enrollment.listEnrollments(page, size);
    }

    @GetMapping("/enrollments/me")
    EnrollmentPage<EnrollmentService.EnrollmentView> myEnrollments(@RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "25") int size) {
        return enrollment.myEnrollments(page, size);
    }

    @PostMapping("/enrollments")
    EnrollmentService.EnrollmentView createEnrollment(@Valid @RequestBody EnrollmentRequest request) {
        return enrollment.createEnrollment(request.membershipId(), request.programId(), request.courseId(), request.batchId(), request.externalReference());
    }

    @PutMapping("/enrollments/{id}/status")
    EnrollmentService.EnrollmentView changeEnrollmentStatus(@PathVariable UUID id, @Valid @RequestBody EnrollmentStatusRequest request) {
        return enrollment.changeEnrollmentStatus(id, request.status(), request.expectedVersion());
    }

    @GetMapping("/teacher-assignments")
    EnrollmentPage<EnrollmentService.TeacherAssignmentView> teacherAssignments(@RequestParam(defaultValue = "0") int page,
                                                                               @RequestParam(defaultValue = "25") int size) {
        return enrollment.listTeachingAssignments(page, size);
    }

    @GetMapping("/teacher-assignments/me")
    EnrollmentPage<EnrollmentService.TeacherAssignmentView> myTeacherAssignments(@RequestParam(defaultValue = "0") int page,
                                                                                 @RequestParam(defaultValue = "25") int size) {
        return enrollment.myTeachingAssignments(page, size);
    }

    @PostMapping("/teacher-assignments")
    EnrollmentService.TeacherAssignmentView createTeacherAssignment(@Valid @RequestBody TeacherAssignmentRequest request) {
        return enrollment.createTeachingAssignment(request.membershipId(), request.programId(), request.courseId(), request.subjectId(),
                request.moduleId(), request.batchId(), request.assignmentRole(), request.startsOn());
    }

    @PostMapping("/teacher-assignments/{id}/end")
    EnrollmentService.TeacherAssignmentView endTeacherAssignment(@PathVariable UUID id, @Valid @RequestBody EndAssignmentRequest request) {
        return enrollment.endTeachingAssignment(id, request.endsOn(), request.expectedVersion());
    }

    record BatchRequest(UUID academicPeriodId, UUID programId, UUID courseId, UUID branchId,
                        @NotBlank @Size(max = 64) String code,
                        @NotBlank @Size(max = 200) String displayName,
                        @Size(max = 64) String sectionCode,
                        LocalDate startsOn, LocalDate endsOn, @Positive Integer capacity,
                        BatchStatus status, long expectedVersion) {}

    record EnrollmentRequest(@NotNull UUID membershipId, UUID programId, UUID courseId, UUID batchId,
                             @Size(max = 160) String externalReference) {}

    record EnrollmentStatusRequest(@NotNull EnrollmentStatus status, long expectedVersion) {}

    record TeacherAssignmentRequest(@NotNull UUID membershipId, UUID programId, UUID courseId, UUID subjectId,
                                    UUID moduleId, UUID batchId, @NotNull TeacherAssignmentRole assignmentRole,
                                    LocalDate startsOn) {}

    record EndAssignmentRequest(LocalDate endsOn, long expectedVersion) {}
}
