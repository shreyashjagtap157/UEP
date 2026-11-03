package com.universalplatform.engagement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Engagement", description = "Endpoints for mentorships, surveys, survey questions, responses, and feedback")
@RestController
@RequestMapping("/api/v1/engagement")
class EngagementController {
    private final EngagementService s;

    EngagementController(EngagementService s) {
        this.s = s;
    }

    @Operation(
            summary = "List mentorships",
            description = "Retrieves all mentorship relationships in the current tenant."
    )
    @GetMapping("/mentoring")
    List<EngagementService.MentorshipView> mentoring() {
        return s.mentoring();
    }

    @Operation(
            summary = "Create mentorship",
            description = "Creates a new mentorship pairing. Requires mentorMembershipId, menteeMembershipId, and goal in the request body."
    )
    @PostMapping("/mentoring")
    EngagementService.MentorshipView mentor(@Valid @RequestBody MentorRequest r) {
        return s.createMentor(r.mentorMembershipId, r.menteeMembershipId, r.goal);
    }

    @Operation(
            summary = "Update mentorship",
            description = "Updates the status and goal of a mentorship identified by ID. Requires status, goal, and expectedVersion in the request body."
    )
    @PostMapping("/mentoring/{id}")
    void update(@PathVariable UUID id, @Valid @RequestBody MentorUpdateRequest r) {
        s.updateMentor(id, r.status, r.goal, r.expectedVersion);
    }

    @Operation(
            summary = "List surveys",
            description = "Retrieves all surveys configured in the current tenant."
    )
    @GetMapping("/surveys")
    List<EngagementService.SurveyView> surveys() {
        return s.surveys();
    }

    @Operation(
            summary = "List survey questions",
            description = "Retrieves all questions for the survey identified by the path variable ID."
    )
    @GetMapping("/surveys/{id}/questions")
    List<EngagementService.SurveyQuestionView> questions(@PathVariable UUID id) {
        return s.questions(id);
    }

    @Operation(
            summary = "Add survey question",
            description = "Adds a question to the specified survey. Requires sequence, questionType, prompt, required flag, and optionsJson in the request body."
    )
    @PostMapping("/surveys/{id}/questions")
    EngagementService.SurveyQuestionView question(@PathVariable UUID id, @Valid @RequestBody SurveyQuestionRequest r) {
        return s.addQuestion(id, r.sequence, r.questionType, r.prompt, r.required, r.optionsJson);
    }

    @Operation(
            summary = "Create survey",
            description = "Creates a new survey. Requires title, description, anonymous flag, opensAt, and closesAt in the request body."
    )
    @PostMapping("/surveys")
    EngagementService.SurveyView survey(@Valid @RequestBody SurveyRequest r) {
        return s.createSurvey(r.title, r.description, r.anonymous, r.opensAt, r.closesAt);
    }

    @Operation(
            summary = "Publish survey",
            description = "Publishes a survey identified by ID to make it available for responses. Requires expectedVersion in the request body."
    )
    @PostMapping("/surveys/{id}/publish")
    void publish(@PathVariable UUID id, @Valid @RequestBody VersionRequest r) {
        s.publishSurvey(id, r.expectedVersion);
    }

    @Operation(
            summary = "Submit survey response",
            description = "Submits responses to a survey identified by ID. Requires answersJson in the request body."
    )
    @PostMapping("/surveys/{id}/responses")
    void response(@PathVariable UUID id, @Valid @RequestBody ResponseRequest r) {
        s.respond(id, r.answersJson);
    }

    @Operation(
            summary = "Submit feedback",
            description = "Submits feedback for a target entity. Requires targetId, targetType, rating (1-5), and comments in the request body."
    )
    @PostMapping("/feedback")
    void feedback(@Valid @RequestBody FeedbackRequest r) {
        s.feedback(r.targetId, r.targetType, r.rating, r.comments);
    }

    @Operation(
            summary = "Get feedback for target",
            description = "Retrieves feedback entries for a specific target entity filtered by targetId and targetType query parameters."
    )
    @GetMapping("/feedback")
    List<EngagementService.FeedbackView> feedback(@RequestParam UUID targetId, @RequestParam String targetType) {
        return s.feedbackFor(targetId, targetType);
    }

    record MentorRequest(
            @NotNull UUID mentorMembershipId,
            @NotNull UUID menteeMembershipId,
            @NotBlank @Size(max = 2000) String goal
    ) {}

    record MentorUpdateRequest(
            @NotBlank String status,
            @NotBlank @Size(max = 2000) String goal,
            long expectedVersion
    ) {}

    record SurveyRequest(
            @NotBlank @Size(max = 240) String title,
            @Size(max = 4000) String description,
            boolean anonymous,
            Instant opensAt,
            Instant closesAt
    ) {}

    record SurveyQuestionRequest(
            @Min(1) int sequence,
            @NotBlank @Pattern(regexp = "[A-Z_]{2,32}") String questionType,
            @NotBlank @Size(max = 2000) String prompt,
            boolean required,
            String optionsJson
    ) {}

    record VersionRequest(long expectedVersion) {}

    record ResponseRequest(@NotBlank String answersJson) {}

    record FeedbackRequest(
            @NotNull UUID targetId,
            @NotBlank @Pattern(regexp = "[A-Z_]{2,32}") String targetType,
            @Min(1) @Max(5) int rating,
            @NotBlank @Size(max = 4000) String comments
    ) {}
}
