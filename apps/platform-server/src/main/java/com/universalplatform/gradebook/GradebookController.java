package com.universalplatform.gradebook;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Gradebook", description = "Endpoints for accessing consolidated batch gradebooks and learner performance summaries")
@RestController
@RequestMapping("/api/v1/gradebook")
class GradebookController {

    private final GradebookService s;

    GradebookController(GradebookService s) {
        this.s = s;
    }

    @Operation(
            summary = "Get my batch gradebook",
            description = "Retrieves the consolidated gradebook view for the authenticated learner in the specified batch ID."
    )
    @GetMapping("/me/{batchId}")
    GradebookService.GradebookView me(@PathVariable UUID batchId) {
        return s.myBatch(batchId);
    }

    @Operation(
            summary = "Get learner gradebook",
            description = "Retrieves the gradebook view for a specific learner identified by membershipId within the specified batchId."
    )
    @GetMapping("/{batchId}/learners/{membershipId}")
    GradebookService.GradebookView learner(@PathVariable UUID batchId, @PathVariable UUID membershipId) {
        return s.learner(batchId, membershipId);
    }
}
