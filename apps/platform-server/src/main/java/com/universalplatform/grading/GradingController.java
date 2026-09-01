package com.universalplatform.grading;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.util.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1") class GradingController {
 private final GradingService service; GradingController(GradingService service){this.service=service;}
 @GetMapping("/attempts/{attemptId}/grade-items") List<GradingService.GradeItemView> items(@PathVariable UUID attemptId){return service.items(attemptId);}
 @PostMapping("/attempts/{attemptId}/grade") GradingDirectory.AttemptGrade grade(@PathVariable UUID attemptId,@RequestParam(defaultValue="true") boolean publish){return service.gradeAttempt(attemptId,publish);}
 @GetMapping("/attempts/{attemptId}/grade") GradingDirectory.AttemptGrade current(@PathVariable UUID attemptId){return service.currentGrade(attemptId);}
 @GetMapping("/attempts/{attemptId}/grade-history") List<GradingService.GradeRevisionView> history(@PathVariable UUID attemptId){return service.history(attemptId);}
 @PostMapping("/attempts/{attemptId}/grade-revisions/publish") GradingDirectory.AttemptGrade publish(@PathVariable UUID attemptId){return service.publishLatest(attemptId);}
 @PostMapping("/attempts/{attemptId}/grade-items/{answerId}/override") GradingService.GradeItemView override(@PathVariable UUID attemptId,@PathVariable UUID answerId,@Valid @RequestBody OverrideRequest r){return service.override(attemptId,answerId,r.finalScore(),r.explanationJson(),r.rubricJson(),r.publish());}
 record OverrideRequest(int finalScore,@NotBlank String explanationJson,String rubricJson,boolean publish){}
}
