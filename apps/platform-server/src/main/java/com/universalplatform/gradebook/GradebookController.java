package com.universalplatform.gradebook;
import java.util.UUID; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/gradebook") class GradebookController { private final GradebookService s; GradebookController(GradebookService s){this.s=s;}
 @GetMapping("/me/{batchId}") GradebookService.GradebookView me(@PathVariable UUID batchId){return s.myBatch(batchId);}
 @GetMapping("/{batchId}/learners/{membershipId}") GradebookService.GradebookView learner(@PathVariable UUID batchId,@PathVariable UUID membershipId){return s.learner(batchId,membershipId);}
}
