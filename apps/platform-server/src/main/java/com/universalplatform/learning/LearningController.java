package com.universalplatform.learning;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Learning", description = "Endpoints for learning outcomes, competencies, learning paths, outcome mappings, progress, and mastery tracking")
@RestController
@RequestMapping("/api/v1/learning")
class LearningController {
    private final LearningService s;

    LearningController(LearningService s) {
        this.s = s;
    }

    @Operation(
            summary = "List learning outcomes",
            description = "Retrieves all learning outcomes configured in the current tenant."
    )
    @GetMapping("/outcomes")
    List<LearningService.OutcomeView> outcomes() {
        return s.outcomes();
    }

    @Operation(
            summary = "Create learning outcome",
            description = "Creates a new learning outcome. Requires code, name, and optional description in the request body."
    )
    @PostMapping("/outcomes")
    LearningService.OutcomeView outcome(@Valid @RequestBody OutcomeRequest r) {
        return s.createOutcome(r.code, r.name, r.description);
    }

    @Operation(
            summary = "List competencies for outcome",
            description = "Retrieves all competencies associated with a specific learning outcome ID passed as a path variable."
    )
    @GetMapping("/outcomes/{outcomeId}/competencies")
    List<LearningService.CompetencyView> competencies(@PathVariable UUID outcomeId) {
        return s.competencies(outcomeId);
    }

    @Operation(
            summary = "Create competency",
            description = "Creates a new competency under a learning outcome. Requires outcomeId, code, name, and optional description in the request body."
    )
    @PostMapping("/competencies")
    LearningService.CompetencyView competency(@Valid @RequestBody CompetencyRequest r) {
        return s.createCompetency(r.outcomeId, r.code, r.name, r.description);
    }

    @Operation(
            summary = "List learning paths",
            description = "Retrieves all learning paths in the current tenant."
    )
    @GetMapping("/paths")
    List<LearningService.LearningPathView> paths() {
        return s.paths();
    }

    @Operation(
            summary = "Create learning path",
            description = "Creates a new learning path. Requires code, name, and optional description in the request body."
    )
    @PostMapping("/paths")
    LearningService.LearningPathView path(@Valid @RequestBody PathRequest r) {
        return s.createPath(r.code, r.name, r.description);
    }

    @Operation(
            summary = "Add item to learning path",
            description = "Adds a sequential item to a learning path identified by ID. Requires sequence, itemType, itemId, title, and required flag in the request body."
    )
    @PostMapping("/paths/{pathId}/items")
    LearningService.PathItemView item(@PathVariable UUID pathId, @Valid @RequestBody PathItemRequest r) {
        return s.addPathItem(pathId, r.sequence, r.itemType, r.itemId, r.title, r.required);
    }

    @Operation(
            summary = "Map outcome",
            description = "Maps an entity to a learning outcome with a specific weight. Requires targetType, targetId, outcomeId, and weight (0.000001 - 1.0) in the request body."
    )
    @PostMapping("/mappings")
    void mapping(@Valid @RequestBody MappingRequest r) {
        s.mapOutcome(r.targetType, r.targetId, r.outcomeId, r.weight);
    }

    @Operation(
            summary = "Record learning path progress",
            description = "Records progress along a learning path for a member. Requires pathId, membershipId, and progress (0.0 - 1.0) in the request body."
    )
    @PostMapping("/progress")
    void progress(@Valid @RequestBody ProgressRequest r) {
        s.recordProgress(r.pathId, r.membershipId, r.progress);
    }

    @Operation(
            summary = "Record competency mastery",
            description = "Records competency mastery level for a member. Requires outcomeId, competencyId, membershipId, mastery score (0.0 - 1.0), and optional evidence text in the request body."
    )
    @PostMapping("/mastery")
    void masteryWrite(@Valid @RequestBody MasteryRequest r) {
        s.recordMastery(r.outcomeId, r.competencyId, r.membershipId, r.mastery, r.evidence);
    }

    @Operation(
            summary = "Get competency mastery",
            description = "Retrieves mastery details for a member and learning outcome specified by outcomeId and membershipId query parameters."
    )
    @GetMapping("/mastery")
    LearningService.MasteryView mastery(@RequestParam UUID outcomeId, @RequestParam UUID membershipId) {
        return s.mastery(outcomeId, membershipId);
    }

    record OutcomeRequest(
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description
    ) {}

    record CompetencyRequest(
            @NotNull UUID outcomeId,
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description
    ) {}

    record PathRequest(
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description
    ) {}

    record PathItemRequest(
            @Min(1) int sequence,
            @NotBlank @Pattern(regexp = "[A-Z_]{2,32}") String itemType,
            @NotNull UUID itemId,
            @NotBlank @Size(max = 300) String title,
            boolean required
    ) {}

    record MappingRequest(
            @NotBlank @Pattern(regexp = "[A-Z_]{2,32}") String targetType,
            @NotNull UUID targetId,
            @NotNull UUID outcomeId,
            @DecimalMin("0.000001") @DecimalMax("1.0") double weight
    ) {}

    record ProgressRequest(
            @NotNull UUID pathId,
            @NotNull UUID membershipId,
            @DecimalMin("0") @DecimalMax("1") double progress
    ) {}

    record MasteryRequest(
            @NotNull UUID outcomeId,
            @NotNull UUID competencyId,
            @NotNull UUID membershipId,
            @DecimalMin("0") @DecimalMax("1") double mastery,
            @Size(max = 4000) String evidence
    ) {}
}
