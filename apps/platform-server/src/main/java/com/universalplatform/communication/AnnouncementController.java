package com.universalplatform.communication;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Announcements", description = "Endpoints for managing institutional announcements, publication lifecycles, member acknowledgements, and background worker retries")
@RestController
@RequestMapping("/api/v1/announcements")
class AnnouncementController {
    private final AnnouncementService service;

    AnnouncementController(AnnouncementService service) { this.service = service; }

    @Operation(
        summary = "List announcements for administrators",
        description = "Retrieves a paginated list of all announcements within the workspace for administrative oversight. Accepts optional page (default 0) and size (default 25) query parameters."
    )
    @GetMapping
    AnnouncementService.AnnouncementPage<AnnouncementService.AnnouncementView> admin(@RequestParam(defaultValue = "0") int page,
                                                                                     @RequestParam(defaultValue = "25") int size) {
        return service.listAdmin(page, size);
    }

    @Operation(
        summary = "List announcements for current user",
        description = "Retrieves a paginated list of published announcements targeted to the authenticated user. Accepts optional page (default 0) and size (default 25) query parameters."
    )
    @GetMapping("/me")
    AnnouncementService.AnnouncementPage<AnnouncementService.AnnouncementView> mine(@RequestParam(defaultValue = "0") int page,
                                                                                    @RequestParam(defaultValue = "25") int size) {
        return service.mine(page, size);
    }

    @Operation(
        summary = "Create announcement",
        description = "Creates a new announcement. Requires CreateRequest body containing title, body, targets list, and optional priority, publishAt, expiresAt, acknowledgementRequired, and publishNow flags."
    )
    @PostMapping
    AnnouncementService.AnnouncementView create(@Valid @RequestBody CreateRequest request) {
        return service.create(new AnnouncementService.CreateCommand(request.title(), request.body(), request.priority(), request.publishAt(),
                request.expiresAt(), request.acknowledgementRequired(), request.publishNow(), targets(request.targets())));
    }

    @Operation(
        summary = "Update announcement",
        description = "Updates an existing draft or scheduled announcement identified by ID. Requires announcement ID path variable and UpdateRequest body containing title, body, targets list, expectedVersion, and optional priority/schedule parameters."
    )
    @PutMapping("/{id}")
    AnnouncementService.AnnouncementView update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return service.update(id, new AnnouncementService.UpdateCommand(request.title(), request.body(), request.priority(), request.publishAt(),
                request.expiresAt(), request.acknowledgementRequired(), targets(request.targets()), request.expectedVersion()));
    }

    @Operation(
        summary = "Publish announcement",
        description = "Immediately publishes a scheduled or draft announcement identified by ID. Requires announcement ID path variable and VersionRequest body containing expectedVersion."
    )
    @PostMapping("/{id}/publish")
    AnnouncementService.AnnouncementView publish(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return service.publish(id, request.expectedVersion());
    }

    @Operation(
        summary = "Cancel announcement",
        description = "Cancels an active or scheduled announcement identified by ID. Requires announcement ID path variable and CancelRequest body containing reason and expectedVersion."
    )
    @PostMapping("/{id}/cancel")
    AnnouncementService.AnnouncementView cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
        return service.cancel(id, request.reason(), request.expectedVersion());
    }

    @Operation(
        summary = "Acknowledge announcement",
        description = "Records acknowledgement of an announcement requiring confirmation by the currently authenticated user. Requires announcement ID path variable."
    )
    @PostMapping("/{id}/acknowledge")
    AnnouncementService.AnnouncementView acknowledge(@PathVariable UUID id) { return service.acknowledge(id); }

    @Operation(
        summary = "List announcement worker failures",
        description = "Retrieves a list of failed background worker jobs associated with announcement processing and dispatch."
    )
    @GetMapping("/operations/failed")
    List<AnnouncementService.AnnouncementWorkerFailureView> workerFailures() {
        return service.workerFailures();
    }

    @Operation(
        summary = "Retry announcement worker operation",
        description = "Retries a failed background worker operation for a specific announcement. Requires announcement ID path variable and WorkerRetryRequest body containing operation."
    )
    @PostMapping("/{id}/worker-retry")
    AnnouncementService.AnnouncementView retryWorkerFailure(@PathVariable UUID id,
                                                              @Valid @RequestBody WorkerRetryRequest request) {
        return service.requeueWorkerFailure(id, request.operation());
    }

    private static List<AnnouncementTarget.Target> targets(List<TargetRequest> requests) {
        return requests.stream().map(item -> new AnnouncementTarget.Target(item.kind(), item.id())).toList();
    }

    record TargetRequest(@NotNull AnnouncementTargetKind kind, UUID id) {}
    record CreateRequest(@NotBlank @Size(max = 240) String title, @NotBlank @Size(max = 20000) String body,
                         AnnouncementPriority priority, Instant publishAt, Instant expiresAt,
                         boolean acknowledgementRequired, boolean publishNow,
                         @NotEmpty @Size(max = 100) List<@Valid TargetRequest> targets) {}
    record UpdateRequest(@NotBlank @Size(max = 240) String title, @NotBlank @Size(max = 20000) String body,
                         AnnouncementPriority priority, Instant publishAt, Instant expiresAt,
                         boolean acknowledgementRequired,
                         @NotEmpty @Size(max = 100) List<@Valid TargetRequest> targets, long expectedVersion) {}
    record VersionRequest(long expectedVersion) {}
    record CancelRequest(@NotBlank @Size(max = 500) String reason, long expectedVersion) {}
    record WorkerRetryRequest(@NotNull AnnouncementWorkerOperation operation) {}
}
