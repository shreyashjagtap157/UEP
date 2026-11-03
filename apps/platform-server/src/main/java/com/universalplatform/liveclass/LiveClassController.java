package com.universalplatform.liveclass;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Live Class", description = "Endpoints for scheduling, initiating, joining, moderating, and managing live interactive classroom sessions")
@RestController
@RequestMapping("/api/v1/live-classes")
class LiveClassController {

    private final LiveClassService service;

    LiveClassController(LiveClassService service) {
        this.service = service;
    }

    @Operation(
            summary = "List live classes",
            description = "Retrieves a paginated list of live classes. Accepts optional 'page' and 'size' query parameters."
    )
    @GetMapping
    LiveClassService.PageResult<LiveClassService.LiveClassView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.list(page, size);
    }

    @Operation(
            summary = "Create live class",
            description = "Creates a new live class room associated with a class session. Requires a CreateRequest body specifying classSessionId, attendance policy, thresholds, bandwidth mode, and chat settings."
    )
    @PostMapping
    LiveClassService.LiveClassView create(@RequestBody CreateRequest r) {
        return service.create(r.classSessionId(), r.attendancePolicy(), r.minimumAttendanceSeconds(), r.attendanceThresholdBasisPoints(), r.lowBandwidth(), r.chatEnabled());
    }

    @Operation(
            summary = "Start live class",
            description = "Starts the live classroom session identified by ID, opening it for participants."
    )
    @PostMapping("/{id}/start")
    LiveClassService.LiveClassView start(@PathVariable UUID id) {
        return service.start(id);
    }

    @Operation(
            summary = "End live class",
            description = "Ends the live classroom session identified by ID, closing rooms and disconnecting active participants."
    )
    @PostMapping("/{id}/end")
    LiveClassService.LiveClassView end(@PathVariable UUID id) {
        return service.end(id);
    }

    @Operation(
            summary = "Join live class",
            description = "Joins a live class session and generates access tokens for real-time media. Accepts an optional JoinRequest body containing clientProfile (defaults to BALANCED)."
    )
    @PostMapping("/{id}/join")
    LiveClassService.TokenView join(@PathVariable UUID id, @RequestBody(required = false) JoinRequest r) {
        return service.join(id, r == null ? "BALANCED" : r.clientProfile());
    }

    @Operation(
            summary = "Leave live class",
            description = "Disconnects the authenticated participant from the live classroom session identified by ID."
    )
    @PostMapping("/{id}/leave")
    void leave(@PathVariable UUID id) {
        service.leave(id);
    }

    @Operation(
            summary = "List live class participants",
            description = "Retrieves a paginated list of current and past participants for the live class ID. Accepts optional 'page' and 'size' query parameters."
    )
    @GetMapping("/{id}/participants")
    LiveClassService.PageResult<LiveClassService.ParticipantView> participants(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.participants(id, page, size);
    }

    @Operation(
            summary = "Mute participant",
            description = "Mutes the audio of a specific participant identified by membershipId in the live class ID."
    )
    @PostMapping("/{id}/participants/{membershipId}/mute")
    void mute(@PathVariable UUID id, @PathVariable UUID membershipId) {
        service.mute(id, membershipId);
    }

    @Operation(
            summary = "Unmute participant",
            description = "Unmutes or allows unmuting for a specific participant identified by membershipId in the live class ID."
    )
    @PostMapping("/{id}/participants/{membershipId}/unmute")
    void unmute(@PathVariable UUID id, @PathVariable UUID membershipId) {
        service.unmute(id, membershipId);
    }

    @Operation(
            summary = "Kick participant",
            description = "Removes a participant identified by membershipId from the live class ID. Accepts an optional ReasonRequest body providing an explanation reason."
    )
    @PostMapping("/{id}/participants/{membershipId}/kick")
    void kick(@PathVariable UUID id, @PathVariable UUID membershipId, @RequestBody(required = false) ReasonRequest r) {
        service.kick(id, membershipId, r == null ? null : r.reason());
    }

    record CreateRequest(
            UUID classSessionId,
            AttendancePolicy attendancePolicy,
            @Min(0) @Max(86400) Integer minimumAttendanceSeconds,
            @Min(0) @Max(10000) Integer attendanceThresholdBasisPoints,
            boolean lowBandwidth,
            boolean chatEnabled) {}

    record JoinRequest(String clientProfile) {}

    record ReasonRequest(@Size(max = 500) String reason) {}
}
