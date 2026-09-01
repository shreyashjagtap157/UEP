package com.universalplatform.academicreview;
import com.universalplatform.grading.GradingDirectory;
import com.universalplatform.academicreview.AcademicReviewService.AnswerRevisionView;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.util.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1") class AcademicReviewController {
 private final AcademicReviewService service; AcademicReviewController(AcademicReviewService service){this.service=service;}
 @PostMapping("/attempts/{attemptId}/reviews") AcademicReviewService.ReviewView open(@PathVariable UUID attemptId,@Valid @RequestBody OpenRequest r){return service.open(attemptId,r.answerId(),r.type(),r.subject(),r.openingArgument());}
 @GetMapping("/attempts/{attemptId}/reviews") List<AcademicReviewService.ReviewView> list(@PathVariable UUID attemptId){return service.list(attemptId);}
 @PostMapping("/reviews/{reviewId}/comments") AcademicReviewService.CommentView comment(@PathVariable UUID reviewId,@Valid @RequestBody CommentRequest r){return service.comment(reviewId,r.body());}
 @GetMapping("/reviews/{reviewId}/discussion") List<AcademicReviewService.CommentView> discussion(@PathVariable UUID reviewId){return service.discussion(reviewId);}
 @PostMapping("/reviews/{reviewId}/answer-revisions") AnswerRevisionView revise(@PathVariable UUID reviewId,@Valid @RequestBody RevisionRequest r){return service.proposeRevision(reviewId,r.answerId(),r.payloadJson());}
 @PostMapping("/answer-revisions/{revisionId}/decide") AnswerRevisionView decide(@PathVariable UUID revisionId,@Valid @RequestBody RevisionDecisionRequest r){return service.decideRevision(revisionId,r.status());}
 @GetMapping("/reviews/{reviewId}/answer-revisions") List<AnswerRevisionView> revisions(@PathVariable UUID reviewId){return service.revisions(reviewId);}
 @PostMapping("/reviews/{reviewId}/impact-analysis") AcademicReviewService.ImpactView impact(@PathVariable UUID reviewId){return service.impact(reviewId);}
 @PostMapping("/reviews/{reviewId}/resolve") AcademicReviewService.ReviewView resolve(@PathVariable UUID reviewId,@Valid @RequestBody ResolveRequest r){return service.resolve(reviewId,r.status());}
 @PostMapping("/reviews/{reviewId}/regrade") GradingDirectory.AttemptGrade regrade(@PathVariable UUID reviewId,@RequestParam(defaultValue="true") boolean publish){return service.regrade(reviewId,publish);}
 record OpenRequest(UUID answerId,@NotNull ReviewType type,@NotBlank String subject,@NotBlank String openingArgument){}
 record CommentRequest(@NotBlank String body){}
 record RevisionRequest(@NotNull UUID answerId,@NotBlank String payloadJson){}
 record RevisionDecisionRequest(@NotNull AnswerRevisionStatus status){}
 record ResolveRequest(@NotNull ReviewStatus status){}
}
