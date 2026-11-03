package com.universalplatform.assignment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Assignment", description = "Endpoints for managing assignments, rubrics, batch allocations, submissions, and grading")
@RestController
@RequestMapping("/api/v1")
class AssignmentController {

    private final AssignmentService service;

    AssignmentController(AssignmentService service) {
        this.service = service;
    }

    @Operation(
            summary = "List assignments",
            description = "Retrieves a paginated list of assignments. Accepts optional 'page' and 'size' query parameters."
    )
    @GetMapping("/assignments")
    AssignmentService.PageResult<AssignmentService.AssignmentView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.list(page, size);
    }

    @Operation(
            summary = "Create assignment",
            description = "Creates a new assignment draft. Requires a Create request body with title, optional instructions, max points, weight basis points, and due timestamp."
    )
    @PostMapping("/assignments")
    AssignmentService.AssignmentView create(@Valid @RequestBody Create r) {
        return service.create(new AssignmentService.CreateCommand(r.title, r.instructions, r.maxPoints, r.weightBasisPoints, r.dueAt));
    }

    @Operation(
            summary = "Update assignment",
            description = "Updates assignment details, scoring parameters, due date, and status. Requires the assignment ID in path and an Update request body containing expectedVersion for optimistic locking."
    )
    @PutMapping("/assignments/{id}")
    AssignmentService.AssignmentView update(@PathVariable UUID id, @Valid @RequestBody Update r) {
        return service.update(id, new AssignmentService.UpdateCommand(r.title, r.instructions, r.maxPoints, r.weightBasisPoints, r.dueAt, r.status, r.expectedVersion));
    }

    @Operation(
            summary = "Update assignment status",
            description = "Updates the lifecycle status of an assignment. Requires the assignment ID in path and a Status request body with new status and expectedVersion."
    )
    @PostMapping("/assignments/{id}/status")
    AssignmentService.AssignmentView status(@PathVariable UUID id, @Valid @RequestBody Status r) {
        return service.updateStatus(id, r.status, r.expectedVersion);
    }

    @Operation(
            summary = "Assign assignment to batch",
            description = "Allocates an assignment to a target learner batch identified by batchId."
    )
    @PostMapping("/assignments/{id}/batches/{batchId}")
    AssignmentService.AssignmentView assign(@PathVariable UUID id, @PathVariable UUID batchId) {
        return service.assignBatch(id, batchId);
    }

    @Operation(
            summary = "Upsert assignment rubric",
            description = "Creates or updates the grading rubric for the specified assignment. Requires the assignment ID in path and a Rubric request body with title, criteria JSON, and expectedVersion."
    )
    @PutMapping("/assignments/{id}/rubric")
    AssignmentService.RubricView rubric(@PathVariable UUID id, @Valid @RequestBody Rubric r) {
        return service.upsertRubric(id, new AssignmentService.RubricCommand(r.title, r.criteriaJson, r.expectedVersion));
    }

    @Operation(
            summary = "Save draft submission",
            description = "Saves a draft submission for the authenticated learner on the specified assignment. Accepts a Submission request body containing optional textBody and resourceId."
    )
    @PutMapping("/assignments/{id}/my-submission")
    AssignmentService.SubmissionView draft(@PathVariable UUID id, @RequestBody Submission r) {
        return service.saveDraft(id, r.textBody, r.resourceId);
    }

    @Operation(
            summary = "Submit assignment",
            description = "Finalizes and submits an assignment response on behalf of the authenticated learner. Accepts a Submission request body containing optional textBody and resourceId."
    )
    @PostMapping("/assignments/{id}/my-submission")
    AssignmentService.SubmissionView submit(@PathVariable UUID id, @RequestBody Submission r) {
        return service.submit(id, r.textBody, r.resourceId);
    }

    @Operation(
            summary = "List my submissions",
            description = "Retrieves a paginated list of assignment submissions made by the authenticated learner. Accepts optional 'page' and 'size' query parameters."
    )
    @GetMapping("/assignments/my-submissions")
    AssignmentService.PageResult<AssignmentService.SubmissionView> my(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listMySubmissions(page, size);
    }

    @Operation(
            summary = "List assignment submissions",
            description = "Retrieves a paginated list of submissions made by all learners for a specific assignment ID. Accepts optional 'page' and 'size' query parameters."
    )
    @GetMapping("/assignments/{id}/submissions")
    AssignmentService.PageResult<AssignmentService.SubmissionView> submissions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listSubmissions(id, page, size);
    }

    @Operation(
            summary = "Grade submission",
            description = "Assigns score, optional feedback, and rubric evaluation to a learner's submission. Requires the submission ID in path and a Grade request body with awardedPoints, feedback, rubricScoresJson, and expectedVersion."
    )
    @PutMapping("/assignment-submissions/{id}/grade")
    AssignmentService.SubmissionView grade(@PathVariable UUID id, @Valid @RequestBody Grade r) {
        return service.gradeSubmission(id, r.awardedPoints, r.feedback, r.rubricScoresJson, r.expectedVersion);
    }

    record Create(
            @NotBlank @Size(max = 240) String title,
            String instructions,
            @Min(1) int maxPoints,
            @Min(0) @Max(10000) int weightBasisPoints,
            @NotNull Instant dueAt) {}

    record Update(
            @NotBlank @Size(max = 240) String title,
            String instructions,
            @Min(1) int maxPoints,
            @Min(0) @Max(10000) int weightBasisPoints,
            @NotNull Instant dueAt,
            @NotNull AssignmentStatus status,
            long expectedVersion) {}

    record Status(@NotNull AssignmentStatus status, long expectedVersion) {}

    record Rubric(@NotBlank @Size(max = 200) String title, @NotBlank String criteriaJson, long expectedVersion) {}

    record Submission(String textBody, UUID resourceId) {}

    record Grade(@Min(0) int awardedPoints, String feedback, String rubricScoresJson, long expectedVersion) {}
}
