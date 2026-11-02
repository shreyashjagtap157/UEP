package com.universalplatform.content;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/storage-cleanup")
class StorageCleanupOperationsController {
    private final StorageCleanupOperationsService service;

    StorageCleanupOperationsController(StorageCleanupOperationsService service) {
        this.service = service;
    }

    @GetMapping("/dead-letter")
    List<StorageCleanupOperationsService.FailureView> deadLettered() {
        return service.deadLettered();
    }

    @PostMapping("/{id}/requeue")
    StorageCleanupOperationsService.FailureView requeue(@PathVariable UUID id) {
        return service.requeue(id);
    }
}
