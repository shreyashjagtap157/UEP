package com.universalplatform.assignment;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.time.Instant; import java.util.UUID; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1") class AssignmentController {
 private final AssignmentService service; AssignmentController(AssignmentService service){this.service=service;}
 @GetMapping("/assignments") AssignmentService.PageResult<AssignmentService.AssignmentView> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size){return service.list(page,size);}
 @PostMapping("/assignments") AssignmentService.AssignmentView create(@Valid @RequestBody Create r){return service.create(new AssignmentService.CreateCommand(r.title,r.instructions,r.maxPoints,r.weightBasisPoints,r.dueAt));}
 @PutMapping("/assignments/{id}") AssignmentService.AssignmentView update(@PathVariable UUID id,@Valid @RequestBody Update r){return service.update(id,new AssignmentService.UpdateCommand(r.title,r.instructions,r.maxPoints,r.weightBasisPoints,r.dueAt,r.status,r.expectedVersion));}
 @PostMapping("/assignments/{id}/status") AssignmentService.AssignmentView status(@PathVariable UUID id,@Valid @RequestBody Status r){return service.updateStatus(id,r.status,r.expectedVersion);}
 @PostMapping("/assignments/{id}/batches/{batchId}") AssignmentService.AssignmentView assign(@PathVariable UUID id,@PathVariable UUID batchId){return service.assignBatch(id,batchId);}
 @PutMapping("/assignments/{id}/rubric") AssignmentService.RubricView rubric(@PathVariable UUID id,@Valid @RequestBody Rubric r){return service.upsertRubric(id,new AssignmentService.RubricCommand(r.title,r.criteriaJson,r.expectedVersion));}
 @PutMapping("/assignments/{id}/my-submission") AssignmentService.SubmissionView draft(@PathVariable UUID id,@RequestBody Submission r){return service.saveDraft(id,r.textBody,r.resourceId);}
 @PostMapping("/assignments/{id}/my-submission") AssignmentService.SubmissionView submit(@PathVariable UUID id,@RequestBody Submission r){return service.submit(id,r.textBody,r.resourceId);}
 @GetMapping("/assignments/my-submissions") AssignmentService.PageResult<AssignmentService.SubmissionView> my(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size){return service.listMySubmissions(page,size);}
 @GetMapping("/assignments/{id}/submissions") AssignmentService.PageResult<AssignmentService.SubmissionView> submissions(@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size){return service.listSubmissions(id,page,size);}
 @PutMapping("/assignment-submissions/{id}/grade") AssignmentService.SubmissionView grade(@PathVariable UUID id,@Valid @RequestBody Grade r){return service.gradeSubmission(id,r.awardedPoints,r.feedback,r.rubricScoresJson,r.expectedVersion);}
 record Create(@NotBlank @Size(max=240) String title,String instructions,@Min(1) int maxPoints,@Min(0) @Max(10000) int weightBasisPoints,@NotNull Instant dueAt){}
 record Update(@NotBlank @Size(max=240) String title,String instructions,@Min(1) int maxPoints,@Min(0) @Max(10000) int weightBasisPoints,@NotNull Instant dueAt,@NotNull AssignmentStatus status,long expectedVersion){}
 record Status(@NotNull AssignmentStatus status,long expectedVersion){}
 record Rubric(@NotBlank @Size(max=200) String title,@NotBlank String criteriaJson,long expectedVersion){}
 record Submission(String textBody,UUID resourceId){}
 record Grade(@Min(0) int awardedPoints,String feedback,String rubricScoresJson,long expectedVersion){}
}
