package com.universalplatform.enrollment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Enrollment Management", description = "Endpoints for managing batches, student enrollments, and teacher assignments")
@RestController
@RequestMapping("/api/v1")
class EnrollmentController {
    private final EnrollmentService enrollment;

    EnrollmentController(EnrollmentService enrollment) { this.enrollment = enrollment; }

    @Operation(
            summary = "List batches",
            description = "Retrieves a paginated list of batches filtered optionally by academicPeriodId, programId, courseId, or branchId. Accepts page and size parameters."
    )
    @GetMapping("/batches")
    EnrollmentPage<EnrollmentService.BatchView> batches(@RequestParam(required = false) UUID academicPeriodId,
                                                        @RequestParam(required = false) UUID programId,
                                                        @RequestParam(required = false) UUID courseId,
                                                        @RequestParam(required = false) UUID branchId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "25") int size) {
        return enrollment.listBatches(academicPeriodId, programId, courseId, branchId, page, size);
    }

    @Operation(
            summary = "Create batch",
            description = "Creates a new batch under an academic period/program/course. Requires a BatchRequest body with code, displayName, and optional scheduling/capacity details."
    )
    @PostMapping("/batches")
    EnrollmentService.BatchView createBatch(@Valid @RequestBody BatchRequest request) {
        return enrollment.createBatch(request.academicPeriodId(), request.programId(), request.courseId(), request.branchId(),
                request.code(), request.displayName(), request.sectionCode(), request.startsOn(), request.endsOn(), request.capacity());
    }

    @Operation(
            summary = "Update batch",
            description = "Updates an existing batch's details, schedule, or status. Requires the batch ID in the path and a BatchRequest body containing status and expectedVersion for optimistic locking."
    )
    @PutMapping("/batches/{id}")
    EnrollmentService.BatchView updateBatch(@PathVariable UUID id, @Valid @RequestBody BatchRequest request) {
        if (request.status() == null) throw new IllegalArgumentException("Batch status is required when updating");
        return enrollment.updateBatch(id, request.academicPeriodId(), request.programId(), request.courseId(), request.branchId(),
                request.code(), request.displayName(), request.sectionCode(), request.startsOn(), request.endsOn(), request.capacity(),
                request.status(), request.expectedVersion());
    }

    @Operation(
            summary = "List all enrollments",
            description = "Retrieves a paginated list of all student enrollments across the organization. Accepts optional page and size query parameters."
    )
    @GetMapping("/enrollments")
    EnrollmentPage<EnrollmentService.EnrollmentView> enrollments(@RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "25") int size) {
        return enrollment.listEnrollments(page, size);
    }

    @Operation(
            summary = "List current user enrollments",
            description = "Retrieves a paginated list of enrollments belonging to the currently authenticated user. Accepts optional page and size query parameters."
    )
    @GetMapping("/enrollments/me")
    EnrollmentPage<EnrollmentService.EnrollmentView> myEnrollments(@RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "25") int size) {
        return enrollment.myEnrollments(page, size);
    }

    @Operation(
            summary = "Create enrollment",
            description = "Enrolls a member into a program, course, or batch. Requires an EnrollmentRequest body with membershipId and optional programId, courseId, batchId, and externalReference."
    )
    @PostMapping("/enrollments")
    EnrollmentService.EnrollmentView createEnrollment(@Valid @RequestBody EnrollmentRequest request) {
        return enrollment.createEnrollment(request.membershipId(), request.programId(), request.courseId(), request.batchId(), request.externalReference());
    }

    @Operation(
            summary = "Change enrollment status",
            description = "Updates the status of an enrollment (e.g. active, completed, dropped). Requires the enrollment ID in the path and an EnrollmentStatusRequest body with status and expectedVersion."
    )
    @PutMapping("/enrollments/{id}/status")
    EnrollmentService.EnrollmentView changeEnrollmentStatus(@PathVariable UUID id, @Valid @RequestBody EnrollmentStatusRequest request) {
        return enrollment.changeEnrollmentStatus(id, request.status(), request.expectedVersion());
    }

    @Operation(
            summary = "List teaching assignments",
            description = "Retrieves a paginated list of all teacher assignments across the organization. Accepts optional page and size query parameters."
    )
    @GetMapping("/teacher-assignments")
    EnrollmentPage<EnrollmentService.TeacherAssignmentView> teacherAssignments(@RequestParam(defaultValue = "0") int page,
                                                                               @RequestParam(defaultValue = "25") int size) {
        return enrollment.listTeachingAssignments(page, size);
    }

    @Operation(
            summary = "List current user teaching assignments",
            description = "Retrieves a paginated list of teaching assignments for the currently authenticated teacher. Accepts optional page and size query parameters."
    )
    @GetMapping("/teacher-assignments/me")
    EnrollmentPage<EnrollmentService.TeacherAssignmentView> myTeacherAssignments(@RequestParam(defaultValue = "0") int page,
                                                                                 @RequestParam(defaultValue = "25") int size) {
        return enrollment.myTeachingAssignments(page, size);
    }

    @Operation(
            summary = "Create teacher assignment",
            description = "Assigns a teacher to a batch, program, course, subject, or module. Requires a TeacherAssignmentRequest body with membershipId, assignmentRole, and scope identifiers."
    )
    @PostMapping("/teacher-assignments")
    EnrollmentService.TeacherAssignmentView createTeacherAssignment(@Valid @RequestBody TeacherAssignmentRequest request) {
        return enrollment.createTeachingAssignment(request.membershipId(), request.programId(), request.courseId(), request.subjectId(),
                request.moduleId(), request.batchId(), request.assignmentRole(), request.startsOn());
    }

    @Operation(
            summary = "End teacher assignment",
            description = "Ends an active teaching assignment by setting an end date. Requires the assignment ID in the path and an EndAssignmentRequest body with endsOn and expectedVersion."
    )
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
