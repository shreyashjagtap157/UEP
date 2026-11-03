package com.universalplatform.grading;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Grading", description = "Endpoints for managing assessment grading, grade items, revisions, overrides, and score publishing")
@RestController
@RequestMapping("/api/v1")
class GradingController {

    private final GradingService service;

    GradingController(GradingService service) {
        this.service = service;
    }

    @Operation(
            summary = "List attempt grade items",
            description = "Retrieves all individual question grade items and awarded marks for the specified attempt ID."
    )
    @GetMapping("/attempts/{attemptId}/grade-items")
    List<GradingService.GradeItemView> items(@PathVariable UUID attemptId) {
        return service.items(attemptId);
    }

    @Operation(
            summary = "Grade attempt",
            description = "Triggers evaluation and calculates scores for an assessment attempt. Accepts optional 'publish' query parameter (defaults to true) indicating whether the grade should be published immediately."
    )
    @PostMapping("/attempts/{attemptId}/grade")
    GradingDirectory.AttemptGrade grade(
            @PathVariable UUID attemptId,
            @RequestParam(defaultValue = "true") boolean publish) {
        return service.gradeAttempt(attemptId, publish);
    }

    @Operation(
            summary = "Get current grade",
            description = "Retrieves the current published or latest recorded grade for the specified attempt ID."
    )
    @GetMapping("/attempts/{attemptId}/grade")
    GradingDirectory.AttemptGrade current(@PathVariable UUID attemptId) {
        return service.currentGrade(attemptId);
    }

    @Operation(
            summary = "Get grade revision history",
            description = "Retrieves the full revision history of grade evaluations and changes for the specified attempt ID."
    )
    @GetMapping("/attempts/{attemptId}/grade-history")
    List<GradingService.GradeRevisionView> history(@PathVariable UUID attemptId) {
        return service.history(attemptId);
    }

    @Operation(
            summary = "Publish latest grade revision",
            description = "Publishes the latest grade revision for the specified attempt ID, updating the official learner record."
    )
    @PostMapping("/attempts/{attemptId}/grade-revisions/publish")
    GradingDirectory.AttemptGrade publish(@PathVariable UUID attemptId) {
        return service.publishLatest(attemptId);
    }

    @Operation(
            summary = "Override question grade item",
            description = "Overrides the score and evaluation for a specific answer in an attempt. Requires attemptId and answerId in the path, and an OverrideRequest body with finalScore, explanationJson, optional rubricJson, and publish flag."
    )
    @PostMapping("/attempts/{attemptId}/grade-items/{answerId}/override")
    GradingService.GradeItemView override(
            @PathVariable UUID attemptId,
            @PathVariable UUID answerId,
            @Valid @RequestBody OverrideRequest r) {
        return service.override(attemptId, answerId, r.finalScore(), r.explanationJson(), r.rubricJson(), r.publish());
    }

    record OverrideRequest(int finalScore, @NotBlank String explanationJson, String rubricJson, boolean publish) {}
}
