package com.universalplatform.content;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(name = "Content", description = "Endpoints for managing content uploads, resources, versions, downloads, and retention policies")
@RestController
@RequestMapping("/api/v1")
class ContentController {
    private final UploadService uploads;
    private final ContentService content;

    ContentController(UploadService u, ContentService c) {
        uploads = u;
        content = c;
    }

    @Operation(
        summary = "Initiate chunked or direct upload",
        description = "Initializes an upload session. Requires InitiateUpload request body containing fileName, contentType, expectedBytes, and optional sha256 checksum."
    )
    @PostMapping("/uploads")
    UploadService.UploadView initiate(@Valid @RequestBody InitiateUpload r) throws IOException {
        return uploads.initiate(r.fileName, r.contentType, r.expectedBytes, r.sha256);
    }

    @Operation(
        summary = "Upload binary chunk",
        description = "Appends a binary chunk of data to an active upload session. Requires path variable id, Upload-Offset header, Content-Length header, and octet-stream body."
    )
    @PutMapping(value = "/uploads/{id}/content", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    UploadService.UploadView chunk(@PathVariable UUID id, @RequestHeader("Upload-Offset") long offset, @RequestHeader(HttpHeaders.CONTENT_LENGTH) long length, HttpServletRequest request) throws IOException {
        return uploads.append(id, offset, request.getInputStream(), length);
    }

    @Operation(
        summary = "Finalize upload session",
        description = "Completes and verifies an upload session identified by the upload ID path variable."
    )
    @PostMapping("/uploads/{id}/complete")
    UploadService.StorageView complete(@PathVariable UUID id) throws Exception {
        return uploads.finalizeUpload(id);
    }

    @Operation(
        summary = "Abort upload session",
        description = "Aborts and cleans up an in-progress upload session identified by the upload ID path variable."
    )
    @DeleteMapping("/uploads/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void abort(@PathVariable UUID id) throws IOException {
        uploads.abort(id);
    }

    @Operation(
        summary = "List content resources",
        description = "Retrieves a paginated list of resources. Accepts optional query parameters: page (default 0) and size (default 25)."
    )
    @GetMapping("/resources")
    ContentPage<ContentService.ResourceView> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return content.list(page, size);
    }

    @Operation(
        summary = "Create text resource",
        description = "Creates a new text resource. Requires TextResource request body with title, text, visibility, downloadPolicy, and optional course/module/session IDs, change note, language, release, and expiration dates."
    )
    @PostMapping("/resources/text")
    ContentService.ResourceView text(@Valid @RequestBody TextResource r) {
        return content.createText(new ContentService.CreateText(r.title, r.description, r.text, r.changeNote, r.courseId, r.moduleId, r.classSessionId, r.language, r.visibility, r.downloadPolicy, r.releaseAt, r.expiresAt));
    }

    @Operation(
        summary = "Create file resource",
        description = "Creates a new file resource backed by stored object. Requires FileResource request body with title, kind, storageObjectId, visibility, downloadPolicy, and optional metadata."
    )
    @PostMapping("/resources/file")
    ContentService.ResourceView file(@Valid @RequestBody FileResource r) {
        return content.createFile(new ContentService.CreateFile(r.title, r.description, r.kind, r.storageObjectId, r.changeNote, r.courseId, r.moduleId, r.classSessionId, r.language, r.visibility, r.downloadPolicy, r.releaseAt, r.expiresAt));
    }

    @Operation(
        summary = "Add text resource version",
        description = "Appends a new text revision to an existing resource. Requires resource ID path variable and TextVersion request body containing text and optional changeNote."
    )
    @PostMapping("/resources/{id}/versions/text")
    ContentService.ResourceView textVersion(@PathVariable UUID id, @RequestBody TextVersion r) {
        return content.addTextVersion(id, r.text, r.changeNote);
    }

    @Operation(
        summary = "Add file resource version",
        description = "Appends a new file revision to an existing resource. Requires resource ID path variable and FileVersion request body containing storageObjectId and optional changeNote."
    )
    @PostMapping("/resources/{id}/versions/file")
    ContentService.ResourceView fileVersion(@PathVariable UUID id, @RequestBody FileVersion r) {
        return content.addFileVersion(id, r.storageObjectId, r.changeNote);
    }

    @Operation(
        summary = "Get resource version history",
        description = "Retrieves version history for the resource identified by the resource ID path variable."
    )
    @GetMapping("/resources/{id}/versions")
    List<ContentService.VersionView> versions(@PathVariable UUID id) {
        return content.versionHistory(id);
    }

    @Operation(
        summary = "Download resource content",
        description = "Streams resource file or text content. Requires resource ID path variable and optional version query parameter. Applies streaming or attachment download policy."
    )
    @GetMapping("/resources/{id}/download")
    ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id, @RequestParam(required = false) Integer version) {
        var h = content.download(id, version);
        StreamingResponseBody body = out -> {
            try (InputStream in = h.opener().open()) {
                in.transferTo(out);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(h.contentType()))
                .contentLength(h.length())
                .header(HttpHeaders.CONTENT_DISPOSITION, (h.policy() == DownloadPolicy.STREAM_ONLY ? "inline" : "attachment") + "; filename*=UTF-8''" + java.net.URLEncoder.encode(h.fileName(), java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
    }

    @Operation(
        summary = "Update content retention policy",
        description = "Updates tenant content retention rules. Requires RetentionRequest body containing defaultRetentionDays, deletedObjectGraceDays, retainVersions, and optimistic locking expectedVersion."
    )
    @PutMapping("/content-retention")
    ContentService.RetentionView retention(@RequestBody RetentionRequest r) {
        return content.updateRetention(r.defaultRetentionDays, r.deletedObjectGraceDays, r.retainVersions, r.expectedVersion);
    }

    record InitiateUpload(@NotBlank String fileName, String contentType, @PositiveOrZero long expectedBytes, String sha256) {}
    record TextResource(@NotBlank String title, String description, @NotBlank String text, String changeNote, UUID courseId, UUID moduleId, UUID classSessionId, String language, @NotNull ResourceVisibility visibility, @NotNull DownloadPolicy downloadPolicy, Instant releaseAt, Instant expiresAt) {}
    record FileResource(@NotBlank String title, String description, @NotNull ResourceKind kind, @NotNull UUID storageObjectId, String changeNote, UUID courseId, UUID moduleId, UUID classSessionId, String language, @NotNull ResourceVisibility visibility, @NotNull DownloadPolicy downloadPolicy, Instant releaseAt, Instant expiresAt) {}
    record TextVersion(@NotBlank String text, String changeNote) {}
    record FileVersion(@NotNull UUID storageObjectId, String changeNote) {}
    record RetentionRequest(Integer defaultRetentionDays, int deletedObjectGraceDays, boolean retainVersions, long expectedVersion) {}
}
