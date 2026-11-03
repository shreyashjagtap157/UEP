package com.universalplatform.content;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Storage Cleanup Operations", description = "Endpoints for inspecting dead-lettered storage cleanup failures and requeueing jobs")
@RestController
@RequestMapping("/api/v1/storage-cleanup")
class StorageCleanupOperationsController {
    private final StorageCleanupOperationsService service;

    StorageCleanupOperationsController(StorageCleanupOperationsService service) {
        this.service = service;
    }

    @Operation(
        summary = "List dead-lettered storage cleanup failures",
        description = "Retrieves a list of failed storage cleanup operations in dead-letter state."
    )
    @GetMapping("/dead-letter")
    List<StorageCleanupOperationsService.FailureView> deadLettered() {
        return service.deadLettered();
    }

    @Operation(
        summary = "Requeue dead-lettered storage cleanup failure",
        description = "Requeues a specific dead-lettered cleanup operation for reprocessing using its unique failure ID path variable."
    )
    @PostMapping("/{id}/requeue")
    StorageCleanupOperationsService.FailureView requeue(@PathVariable UUID id) {
        return service.requeue(id);
    }
}
