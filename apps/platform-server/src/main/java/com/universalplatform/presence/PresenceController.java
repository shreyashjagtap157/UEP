package com.universalplatform.presence;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Presence", description = "Endpoints for managing participant presence heartbeats, live session departures, attendance tracking, and finalization")
@RestController
@RequestMapping("/api/v1/live-classes/{liveClassId}")
class PresenceController {

    private final PresenceService service;

    PresenceController(PresenceService service) {
        this.service = service;
    }

    @Operation(
            summary = "Send presence heartbeat",
            description = "Registers or renews active participant presence in the live class. Accepts an optional HeartbeatRequest body with clientProfile (defaults to BALANCED)."
    )
    @PostMapping("/heartbeat")
    void heartbeat(
            @PathVariable UUID liveClassId,
            @RequestBody(required = false) HeartbeatRequest r) {
        service.heartbeat(liveClassId, r == null ? "BALANCED" : r.clientProfile());
    }

    @Operation(
            summary = "Record departure from live class",
            description = "Explicitly records the departure of the authenticated participant from the specified live class ID."
    )
    @PostMapping("/leave")
    void leave(@PathVariable UUID liveClassId) {
        service.leave(liveClassId);
    }

    @Operation(
            summary = "Get live class attendance",
            description = "Retrieves the list of attendance records, duration, and status for all participants in the live class ID."
    )
    @GetMapping("/attendance")
    List<PresenceService.AttendanceView> attendance(@PathVariable UUID liveClassId) {
        return service.list(liveClassId);
    }

    @Operation(
            summary = "Finalize live class attendance",
            description = "Computes final attendance determinations and closes attendance recording for the live class ID."
    )
    @PostMapping("/attendance/finalize")
    void finalizeAttendance(@PathVariable UUID liveClassId) {
        service.finalizeAttendance(liveClassId);
    }

    @Operation(
            summary = "Set participant attendance",
            description = "Manually updates the attendance status and notes for a participant identified by membershipId. Requires liveClassId and membershipId in the path, and a SetAttendanceRequest body with status and optional notes."
    )
    @PostMapping("/attendance/{membershipId}")
    void set(
            @PathVariable UUID liveClassId,
            @PathVariable UUID membershipId,
            @RequestBody SetAttendanceRequest r) {
        service.set(liveClassId, membershipId, r.status(), r.notes());
    }

    record HeartbeatRequest(String clientProfile) {}

    record SetAttendanceRequest(AttendanceStatus status, @Size(max = 500) String notes) {}
}
