package com.universalplatform.engagement;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.time.Instant; import java.util.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/engagement") class EngagementController { private final EngagementService s; EngagementController(EngagementService s){this.s=s;}
@GetMapping("/mentoring") List<EngagementService.MentorshipView> mentoring(){return s.mentoring();}
@PostMapping("/mentoring") EngagementService.MentorshipView mentor(@Valid @RequestBody MentorRequest r){return s.createMentor(r.mentorMembershipId,r.menteeMembershipId,r.goal);}
@PostMapping("/mentoring/{id}") void update(@PathVariable UUID id,@Valid @RequestBody MentorUpdateRequest r){s.updateMentor(id,r.status,r.goal,r.expectedVersion);}
@GetMapping("/surveys") List<EngagementService.SurveyView> surveys(){return s.surveys();}
@GetMapping("/surveys/{id}/questions") List<EngagementService.SurveyQuestionView> questions(@PathVariable UUID id){return s.questions(id);}
@PostMapping("/surveys/{id}/questions") EngagementService.SurveyQuestionView question(@PathVariable UUID id,@Valid @RequestBody SurveyQuestionRequest r){return s.addQuestion(id,r.sequence,r.questionType,r.prompt,r.required,r.optionsJson);}
@PostMapping("/surveys") EngagementService.SurveyView survey(@Valid @RequestBody SurveyRequest r){return s.createSurvey(r.title,r.description,r.anonymous,r.opensAt,r.closesAt);}
@PostMapping("/surveys/{id}/publish") void publish(@PathVariable UUID id,@Valid @RequestBody VersionRequest r){s.publishSurvey(id,r.expectedVersion);}
@PostMapping("/surveys/{id}/responses") void response(@PathVariable UUID id,@Valid @RequestBody ResponseRequest r){s.respond(id,r.answersJson,r.membershipId);}
@PostMapping("/feedback") void feedback(@Valid @RequestBody FeedbackRequest r){s.feedback(r.targetId,r.targetType,r.rating,r.comments);}
@GetMapping("/feedback") List<EngagementService.FeedbackView> feedback(@RequestParam UUID targetId,@RequestParam String targetType){return s.feedbackFor(targetId,targetType);}
record MentorRequest(@NotNull UUID mentorMembershipId,@NotNull UUID menteeMembershipId,@NotBlank @Size(max=2000) String goal){}
record MentorUpdateRequest(@NotBlank String status,@NotBlank @Size(max=2000) String goal,long expectedVersion){}
record SurveyRequest(@NotBlank @Size(max=240) String title,@Size(max=4000) String description,boolean anonymous,Instant opensAt,Instant closesAt){}
record SurveyQuestionRequest(@Min(1) int sequence,@NotBlank @Pattern(regexp="[A-Z_]{2,32}") String questionType,@NotBlank @Size(max=2000) String prompt,boolean required,String optionsJson){}
record VersionRequest(long expectedVersion){} record ResponseRequest(@NotBlank String answersJson,UUID membershipId){}
record FeedbackRequest(@NotNull UUID targetId,@NotBlank @Pattern(regexp="[A-Z_]{2,32}") String targetType,@Min(1) @Max(5) int rating,@NotBlank @Size(max=4000) String comments){}
}
