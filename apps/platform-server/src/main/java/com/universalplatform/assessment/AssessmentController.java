package com.universalplatform.assessment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Assessment", description = "Endpoints for managing assessments, versions, question attachments, batch assignments, and learner attempts")
@RestController
@RequestMapping("/api/v1")
class AssessmentController {

    private final AssessmentService service;

    AssessmentController(AssessmentService service) {
        this.service = service;
    }

    @Operation(
            summary = "List assessments",
            description = "Retrieves a paginated list of assessments. Accepts optional 'page' and 'size' query parameters."
    )
    @GetMapping("/assessments")
    AssessmentService.PageResult<AssessmentService.AssessmentView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.list(page, size);
    }

    @Operation(
            summary = "Create assessment",
            description = "Creates a new assessment draft. Requires a CreateAssessment request body with a non-blank title."
    )
    @PostMapping("/assessments")
    AssessmentService.AssessmentView create(@Valid @RequestBody CreateAssessment r) {
        return service.create(r.title());
    }

    @Operation(
            summary = "Create assessment version",
            description = "Creates a new version configuration for the specified assessment ID. Requires a VersionRequest body with duration, attempt limits, marks, availability window, and optional settings."
    )
    @PostMapping("/assessments/{id}/versions")
    AssessmentService.AssessmentVersionView version(@PathVariable UUID id, @Valid @RequestBody VersionRequest r) {
        return service.createVersion(id, new AssessmentService.VersionCommand(
                r.durationSeconds(), r.maxAttempts(), r.totalMarks(), r.passMarks(),
                r.shuffleQuestions(), r.allowBacktracking(), r.availableFrom(), r.availableUntil(), r.settingsJson()));
    }

    @Operation(
            summary = "List assessment versions",
            description = "Retrieves all versions associated with the specified assessment ID."
    )
    @GetMapping("/assessments/{id}/versions")
    List<AssessmentService.AssessmentVersionView> versions(@PathVariable UUID id) {
        return service.versions(id);
    }

    @Operation(
            summary = "Get assessment version packet",
            description = "Retrieves the complete assessment packet containing delivery metadata and assembled questions for the specified version ID."
    )
    @GetMapping("/assessment-versions/{id}/packet")
    AssessmentService.AssessmentPacket packet(@PathVariable UUID id) {
        return service.packet(id);
    }

    @Operation(
            summary = "Attach question to version",
            description = "Attaches a question version at a specific ordinal position to the specified assessment version ID. Requires a QuestionAttachment request body."
    )
    @PostMapping("/assessment-versions/{id}/questions")
    AssessmentService.AssessmentVersionView question(@PathVariable UUID id, @Valid @RequestBody QuestionAttachment r) {
        return service.addQuestion(id, r.questionVersionId(), r.ordinal());
    }

    @Operation(
            summary = "Publish assessment",
            description = "Publishes the assessment identified by ID, making its latest version active. Requires the 'expectedVersion' query parameter for optimistic concurrency control."
    )
    @PostMapping("/assessments/{id}/publish")
    AssessmentService.AssessmentView publish(@PathVariable UUID id, @RequestParam long expectedVersion) {
        return service.publish(id, expectedVersion);
    }

    @Operation(
            summary = "Assign assessment version to batch",
            description = "Assigns an assessment version to a target learner batch identified by batchId."
    )
    @PostMapping("/assessment-versions/{id}/batches/{batchId}")
    AssessmentService.AssignmentView assign(@PathVariable UUID id, @PathVariable UUID batchId) {
        return service.assignBatch(id, batchId);
    }

    @Operation(
            summary = "List version attempts",
            description = "Retrieves a paginated list of all learner attempts for the specified assessment version ID. Accepts optional 'page' and 'size' query parameters."
    )
    @GetMapping("/assessment-versions/{id}/attempts")
    AssessmentService.PageResult<AssessmentService.AttemptAdminView> attempts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listAttempts(id, page, size);
    }

    @Operation(
            summary = "List my attempts",
            description = "Retrieves a paginated list of assessment attempts undertaken by the currently authenticated learner. Accepts optional 'page' and 'size' query parameters."
    )
    @GetMapping("/attempts/me")
    AssessmentService.PageResult<AssessmentService.AttemptAdminView> myAttempts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listMyAttempts(page, size);
    }

    @Operation(
            summary = "Start assessment attempt",
            description = "Starts a new attempt on the specified assessment version ID for the authenticated learner."
    )
    @PostMapping("/assessment-versions/{id}/attempts")
    AssessmentService.AttemptView start(@PathVariable UUID id) {
        return service.start(id);
    }

    @Operation(
            summary = "Get attempt details",
            description = "Retrieves detailed information and current status of an assessment attempt by its unique attempt ID."
    )
    @GetMapping("/attempts/{id}")
    AssessmentService.AttemptView attempt(@PathVariable UUID id) {
        return service.getAttempt(id);
    }

    @Operation(
            summary = "Autosave answer",
            description = "Saves an answer draft for a specific question within an active attempt. Requires attemptId and assessmentQuestionId in the path, and an AnswerRequest body with payload JSON, idempotency key, and client sequence number."
    )
    @PutMapping("/attempts/{attemptId}/answers/{assessmentQuestionId}")
    AssessmentService.AnswerView autosave(
            @PathVariable UUID attemptId,
            @PathVariable UUID assessmentQuestionId,
            @Valid @RequestBody AnswerRequest r) {
        return service.autosave(attemptId, assessmentQuestionId, r.payloadJson(), r.idempotencyKey(), r.clientSequence());
    }

    @Operation(
            summary = "Submit assessment attempt",
            description = "Submits the specified assessment attempt for grading and completion."
    )
    @PostMapping("/attempts/{id}/submit")
    AssessmentService.AttemptView submit(@PathVariable UUID id) {
        return service.submit(id);
    }

    record CreateAssessment(@NotBlank @Size(max = 240) String title) {}

    record VersionRequest(
            Integer durationSeconds,
            @Min(1) int maxAttempts,
            @Min(1) int totalMarks,
            @Min(0) int passMarks,
            boolean shuffleQuestions,
            boolean allowBacktracking,
            @NotNull Instant availableFrom,
            @NotNull Instant availableUntil,
            String settingsJson) {}

    record QuestionAttachment(@NotNull UUID questionVersionId, @Positive int ordinal) {}

    record AnswerRequest(@NotBlank String payloadJson, @NotBlank @Size(max = 120) String idempotencyKey, long clientSequence) {}
}
