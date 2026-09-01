package com.universalplatform.assessment;

import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.time.Instant; import java.util.List; import java.util.UUID; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1") class AssessmentController {
 private final AssessmentService service; AssessmentController(AssessmentService service){this.service=service;}
 @GetMapping("/assessments") AssessmentService.PageResult<AssessmentService.AssessmentView> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size){return service.list(page,size);}
 @PostMapping("/assessments") AssessmentService.AssessmentView create(@Valid @RequestBody CreateAssessment r){return service.create(r.title());}
 @PostMapping("/assessments/{id}/versions") AssessmentService.AssessmentVersionView version(@PathVariable UUID id,@Valid @RequestBody VersionRequest r){return service.createVersion(id,new AssessmentService.VersionCommand(r.durationSeconds(),r.maxAttempts(),r.totalMarks(),r.passMarks(),r.shuffleQuestions(),r.allowBacktracking(),r.availableFrom(),r.availableUntil(),r.settingsJson()));}
 @GetMapping("/assessments/{id}/versions") List<AssessmentService.AssessmentVersionView> versions(@PathVariable UUID id){return service.versions(id);}
 @GetMapping("/assessment-versions/{id}/packet") AssessmentService.AssessmentPacket packet(@PathVariable UUID id){return service.packet(id);}
 @PostMapping("/assessment-versions/{id}/questions") AssessmentService.AssessmentVersionView question(@PathVariable UUID id,@Valid @RequestBody QuestionAttachment r){return service.addQuestion(id,r.questionVersionId(),r.ordinal());}
 @PostMapping("/assessments/{id}/publish") AssessmentService.AssessmentView publish(@PathVariable UUID id,@RequestParam long expectedVersion){return service.publish(id,expectedVersion);}
 @PostMapping("/assessment-versions/{id}/batches/{batchId}") AssessmentService.AssignmentView assign(@PathVariable UUID id,@PathVariable UUID batchId){return service.assignBatch(id,batchId);}
 @PostMapping("/assessment-versions/{id}/attempts") AssessmentService.AttemptView start(@PathVariable UUID id){return service.start(id);}
 @GetMapping("/attempts/{id}") AssessmentService.AttemptView attempt(@PathVariable UUID id){return service.getAttempt(id);}
 @PutMapping("/attempts/{attemptId}/answers/{assessmentQuestionId}") AssessmentService.AnswerView autosave(@PathVariable UUID attemptId,@PathVariable UUID assessmentQuestionId,@Valid @RequestBody AnswerRequest r){return service.autosave(attemptId,assessmentQuestionId,r.payloadJson(),r.idempotencyKey(),r.clientSequence());}
 @PostMapping("/attempts/{id}/submit") AssessmentService.AttemptView submit(@PathVariable UUID id){return service.submit(id);}
 record CreateAssessment(@NotBlank @Size(max=240) String title){}
 record VersionRequest(Integer durationSeconds,@Min(1) int maxAttempts,@Min(1) int totalMarks,@Min(0) int passMarks,boolean shuffleQuestions,boolean allowBacktracking,@NotNull Instant availableFrom,@NotNull Instant availableUntil,String settingsJson){}
 record QuestionAttachment(@NotNull UUID questionVersionId,@Positive int ordinal){}
 record AnswerRequest(@NotBlank String payloadJson,@NotBlank @Size(max=120) String idempotencyKey,long clientSequence){}
}
