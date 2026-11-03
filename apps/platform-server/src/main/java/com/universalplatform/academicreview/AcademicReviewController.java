package com.universalplatform.academicreview;

import com.universalplatform.grading.GradingDirectory;
import com.universalplatform.academicreview.AcademicReviewService.AnswerRevisionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Academic Review", description = "Endpoints for handling academic reviews, regrade requests, answer revisions, discussions, and impact analysis")
@RestController
@RequestMapping("/api/v1")
class AcademicReviewController {

    private final AcademicReviewService service;

    AcademicReviewController(AcademicReviewService service) {
        this.service = service;
    }

    @Operation(
            summary = "Open academic review",
            description = "Opens a new academic review or dispute for a specific assessment attempt. Requires attemptId in the path and an OpenRequest body specifying optional answerId, review type, subject, and opening argument."
    )
    @PostMapping("/attempts/{attemptId}/reviews")
    AcademicReviewService.ReviewView open(
            @PathVariable UUID attemptId,
            @Valid @RequestBody OpenRequest r) {
        return service.open(attemptId, r.answerId(), r.type(), r.subject(), r.openingArgument());
    }

    @Operation(
            summary = "List attempt reviews",
            description = "Retrieves all academic reviews opened for the specified attempt ID."
    )
    @GetMapping("/attempts/{attemptId}/reviews")
    List<AcademicReviewService.ReviewView> list(@PathVariable UUID attemptId) {
        return service.list(attemptId);
    }

    @Operation(
            summary = "Post review comment",
            description = "Adds a discussion comment to an existing academic review. Requires reviewId in the path and a CommentRequest body containing the non-blank comment body."
    )
    @PostMapping("/reviews/{reviewId}/comments")
    AcademicReviewService.CommentView comment(
            @PathVariable UUID reviewId,
            @Valid @RequestBody CommentRequest r) {
        return service.comment(reviewId, r.body());
    }

    @Operation(
            summary = "Get review discussion",
            description = "Retrieves the entire chronological discussion thread and comments for the specified review ID."
    )
    @GetMapping("/reviews/{reviewId}/discussion")
    List<AcademicReviewService.CommentView> discussion(@PathVariable UUID reviewId) {
        return service.discussion(reviewId);
    }

    @Operation(
            summary = "Propose answer revision",
            description = "Proposes a revised answer payload under an active academic review. Requires reviewId in the path and a RevisionRequest body containing answerId and replacement payloadJson."
    )
    @PostMapping("/reviews/{reviewId}/answer-revisions")
    AnswerRevisionView revise(
            @PathVariable UUID reviewId,
            @Valid @RequestBody RevisionRequest r) {
        return service.proposeRevision(reviewId, r.answerId(), r.payloadJson());
    }

    @Operation(
            summary = "Decide answer revision",
            description = "Accepts or rejects a proposed answer revision. Requires revisionId in the path and a RevisionDecisionRequest body containing the decision status."
    )
    @PostMapping("/answer-revisions/{revisionId}/decide")
    AnswerRevisionView decide(
            @PathVariable UUID revisionId,
            @Valid @RequestBody RevisionDecisionRequest r) {
        return service.decideRevision(revisionId, r.status());
    }

    @Operation(
            summary = "List review answer revisions",
            description = "Retrieves all answer revisions proposed under the specified review ID."
    )
    @GetMapping("/reviews/{reviewId}/answer-revisions")
    List<AnswerRevisionView> revisions(@PathVariable UUID reviewId) {
        return service.revisions(reviewId);
    }

    @Operation(
            summary = "Analyze review impact",
            description = "Calculates the grading and score impact of the changes proposed within the academic review identified by reviewId."
    )
    @PostMapping("/reviews/{reviewId}/impact-analysis")
    AcademicReviewService.ImpactView impact(@PathVariable UUID reviewId) {
        return service.impact(reviewId);
    }

    @Operation(
            summary = "Resolve academic review",
            description = "Resolves and closes an academic review. Requires reviewId in the path and a ResolveRequest body with the final ReviewStatus."
    )
    @PostMapping("/reviews/{reviewId}/resolve")
    AcademicReviewService.ReviewView resolve(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ResolveRequest r) {
        return service.resolve(reviewId, r.status());
    }

    @Operation(
            summary = "Regrade attempt from review",
            description = "Triggers an automatic regrading of the attempt associated with the review. Requires reviewId in the path and accepts an optional 'publish' query parameter (defaults to true)."
    )
    @PostMapping("/reviews/{reviewId}/regrade")
    GradingDirectory.AttemptGrade regrade(
            @PathVariable UUID reviewId,
            @RequestParam(defaultValue = "true") boolean publish) {
        return service.regrade(reviewId, publish);
    }

    record OpenRequest(UUID answerId, @NotNull ReviewType type, @NotBlank String subject, @NotBlank String openingArgument) {}

    record CommentRequest(@NotBlank String body) {}

    record RevisionRequest(@NotNull UUID answerId, @NotBlank String payloadJson) {}

    record RevisionDecisionRequest(@NotNull AnswerRevisionStatus status) {}

    record ResolveRequest(@NotNull ReviewStatus status) {}
}
