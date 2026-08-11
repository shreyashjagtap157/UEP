package com.universalplatform.communication;

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

@RestController
@RequestMapping("/api/v1/announcements")
class AnnouncementController {
    private final AnnouncementService service;

    AnnouncementController(AnnouncementService service) { this.service = service; }

    @GetMapping
    AnnouncementService.AnnouncementPage<AnnouncementService.AnnouncementView> admin(@RequestParam(defaultValue = "0") int page,
                                                                                     @RequestParam(defaultValue = "25") int size) {
        return service.listAdmin(page, size);
    }

    @GetMapping("/me")
    AnnouncementService.AnnouncementPage<AnnouncementService.AnnouncementView> mine(@RequestParam(defaultValue = "0") int page,
                                                                                    @RequestParam(defaultValue = "25") int size) {
        return service.mine(page, size);
    }

    @PostMapping
    AnnouncementService.AnnouncementView create(@Valid @RequestBody CreateRequest request) {
        return service.create(new AnnouncementService.CreateCommand(request.title(), request.body(), request.priority(), request.publishAt(),
                request.expiresAt(), request.acknowledgementRequired(), request.publishNow(), targets(request.targets())));
    }

    @PutMapping("/{id}")
    AnnouncementService.AnnouncementView update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return service.update(id, new AnnouncementService.UpdateCommand(request.title(), request.body(), request.priority(), request.publishAt(),
                request.expiresAt(), request.acknowledgementRequired(), targets(request.targets()), request.expectedVersion()));
    }

    @PostMapping("/{id}/publish")
    AnnouncementService.AnnouncementView publish(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return service.publish(id, request.expectedVersion());
    }

    @PostMapping("/{id}/cancel")
    AnnouncementService.AnnouncementView cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
        return service.cancel(id, request.reason(), request.expectedVersion());
    }

    @PostMapping("/{id}/acknowledge")
    AnnouncementService.AnnouncementView acknowledge(@PathVariable UUID id) { return service.acknowledge(id); }

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
}
