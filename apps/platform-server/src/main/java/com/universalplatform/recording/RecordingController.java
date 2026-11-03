package com.universalplatform.recording;

import com.universalplatform.storage.StorageProviderType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(name = "Recording", description = "Endpoints for managing session recordings, video streaming playback, capture requests, and storage retention policies")
@RestController
@RequestMapping("/api/v1")
class RecordingController {

    private final RecordingService service;

    RecordingController(RecordingService service) {
        this.service = service;
    }

    @Operation(
            summary = "List recordings",
            description = "Retrieves a paginated list of recordings. Accepts optional 'page' and 'size' query parameters."
    )
    @GetMapping("/recordings")
    RecordingService.PageResult<RecordingService.RecordingView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.list(page, size);
    }

    @Operation(
            summary = "Get recording by ID",
            description = "Retrieves metadata and status details for a specific recording by its unique identifier."
    )
    @GetMapping("/recordings/{id}")
    RecordingService.RecordingView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(
            summary = "Request live class recording",
            description = "Initiates recording capture for the specified live class ID. Requires liveClassId in the path and a Request body with qualityPreset."
    )
    @PostMapping("/live-classes/{liveClassId}/recordings")
    RecordingService.RecordingView request(
            @PathVariable UUID liveClassId,
            @Valid @RequestBody Request r) {
        return service.request(liveClassId, r.qualityPreset);
    }

    @Operation(
            summary = "Stop recording",
            description = "Stops an active recording session identified by ID."
    )
    @PostMapping("/recordings/{id}/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void stop(@PathVariable UUID id) {
        service.stop(id);
    }

    @Operation(
            summary = "Get recording playback information",
            description = "Retrieves playback metadata and secure streaming credentials/tokens for the specified recording ID."
    )
    @PostMapping("/recordings/{id}/playback")
    RecordingService.PlaybackView playback(@PathVariable UUID id) {
        return service.playback(id);
    }

    @Operation(
            summary = "Stream recording media",
            description = "Streams the MP4 video content for the specified recording ID. Requires a valid playback 'token' query parameter."
    )
    @GetMapping("/recordings/{id}/stream")
    ResponseEntity<StreamingResponseBody> stream(
            @PathVariable UUID id,
            @RequestParam String token) throws Exception {
        InputStream in = service.openStream(id, token);
        StreamingResponseBody body = out -> {
            try (in) {
                in.transferTo(out);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("video/mp4"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(body);
    }

    @Operation(
            summary = "Get recording storage policy",
            description = "Retrieves the current storage lifecycle and retention policy configuration for recordings."
    )
    @GetMapping("/recording-storage-policy")
    RecordingService.StoragePolicyView getPolicy() {
        return service.policy();
    }

    @Operation(
            summary = "Update recording storage policy",
            description = "Updates the storage lifecycle and retention policy for recordings. Requires a Policy request body with cache days, retention periods, storage providers, grace days, and expectedVersion."
    )
    @PutMapping("/recording-storage-policy")
    RecordingService.StoragePolicyView policy(@Valid @RequestBody Policy r) {
        return service.updatePolicy(
                r.hotCacheDays, r.cacheDays, r.retentionDays,
                r.hotProvider, r.cacheProvider, r.archiveProvider,
                r.deletedObjectGraceDays, r.expectedVersion);
    }

    record Request(@NotNull RecordingQualityPreset qualityPreset) {}

    record Policy(
            @Min(0) int hotCacheDays,
            @Min(0) int cacheDays,
            Integer retentionDays,
            @NotNull StorageProviderType hotProvider,
            @NotNull StorageProviderType cacheProvider,
            @NotNull StorageProviderType archiveProvider,
            @Min(0) int deletedObjectGraceDays,
            long expectedVersion) {}
}
