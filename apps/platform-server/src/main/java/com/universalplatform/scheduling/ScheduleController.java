package com.universalplatform.scheduling;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Schedule Management", description = "Endpoints for managing schedule series, calendar occurrences, conflicts, rescheduling, and cancellations")
@RestController
@RequestMapping("/api/v1/schedule")
class ScheduleController {
    private final ScheduleService service;

    ScheduleController(ScheduleService service) { this.service = service; }

    @Operation(
            summary = "List schedule series",
            description = "Retrieves a paginated list of schedule recurring or single series. Accepts optional page and size query parameters."
    )
    @GetMapping("/series")
    ScheduleService.SchedulePage<ScheduleService.SeriesView> series(@RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "25") int size) {
        return service.listSeries(page, size);
    }

    @Operation(
            summary = "List occurrences calendar view",
            description = "Retrieves calendar occurrences falling within the specified time range. Requires 'from' and 'to' timestamp query parameters."
    )
    @GetMapping("/occurrences")
    List<ScheduleService.OccurrenceView> occurrences(@RequestParam Instant from, @RequestParam Instant to) {
        return service.calendar(from, to);
    }

    @Operation(
            summary = "Preflight check for schedule conflicts",
            description = "Checks for potential scheduling conflicts (teacher, room, batch) prior to creating a series. Requires a CreateSeriesRequest body."
    )
    @PostMapping("/conflicts")
    List<ScheduleConflict> conflicts(@Valid @RequestBody CreateSeriesRequest request) {
        return service.preflight(request.command());
    }

    @Operation(
            summary = "Create schedule series",
            description = "Creates a new schedule series with recurrence rules and generates occurrences. Requires a CreateSeriesRequest body."
    )
    @PostMapping("/series")
    ScheduleService.SeriesCreateResult create(@Valid @RequestBody CreateSeriesRequest request) {
        return service.create(request.command());
    }

    @Operation(
            summary = "Reschedule occurrence",
            description = "Reschedules a specific occurrence to a new start time, duration, room, or substitute teacher. Requires occurrence ID in path and RescheduleRequest body with expectedVersion."
    )
    @PostMapping("/occurrences/{id}/reschedule")
    ScheduleService.OccurrenceView reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest request) {
        return service.reschedule(id, new ScheduleService.RescheduleCommand(request.startLocal(), request.durationMinutes(),
                request.substituteTeacherMembershipId(), request.roomCode(), request.reason(), request.allowConflicts(),
                request.conflictOverrideReason(), request.expectedVersion()));
    }

    @Operation(
            summary = "Cancel occurrence",
            description = "Cancels a specific occurrence. Requires the occurrence ID in the path and a CancelRequest body with reason and expectedVersion."
    )
    @PostMapping("/occurrences/{id}/cancel")
    ScheduleService.OccurrenceView cancelOccurrence(@PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
        return service.cancelOccurrence(id, new ScheduleService.CancelOccurrenceCommand(request.reason(), request.expectedVersion()));
    }

    @Operation(
            summary = "Cancel schedule series",
            description = "Cancels an entire recurring schedule series. Requires the series ID in the path and a CancelRequest body with reason and expectedVersion."
    )
    @PostMapping("/series/{id}/cancel")
    ScheduleService.SeriesView cancelSeries(@PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
        return service.cancelSeries(id, new ScheduleService.CancelSeriesCommand(request.reason(), request.expectedVersion()));
    }

    record CreateSeriesRequest(
            @NotNull ScheduleKind kind,
            @NotBlank @Size(max = 240) String title,
            @Size(max = 4000) String description,
            @Size(max = 80) String timezone,
            DeliveryMode deliveryMode,
            UUID branchId,
            UUID batchId,
            UUID courseId,
            UUID subjectId,
            UUID moduleId,
            UUID primaryTeacherMembershipId,
            @Size(max = 120) String roomCode,
            @NotNull LocalDateTime startLocal,
            @Min(1) @Max(10080) int durationMinutes,
            RecurrenceFrequency recurrenceFrequency,
            @Min(1) @Max(365) Integer recurrenceInterval,
            Set<DayOfWeek> recurrenceDays,
            @Min(1) @Max(31) Integer recurrenceDayOfMonth,
            LocalDateTime recurrenceUntilLocal,
            @Min(1) @Max(1000) Integer recurrenceCount,
            boolean allowConflicts,
            @Size(max = 500) String conflictOverrideReason) {
        ScheduleService.CreateCommand command() {
            return new ScheduleService.CreateCommand(kind, title, description, timezone, deliveryMode, branchId, batchId, courseId,
                    subjectId, moduleId, primaryTeacherMembershipId, roomCode, startLocal, durationMinutes, recurrenceFrequency,
                    recurrenceInterval, recurrenceDays, recurrenceDayOfMonth, recurrenceUntilLocal, recurrenceCount,
                    allowConflicts, conflictOverrideReason);
        }
    }

    record RescheduleRequest(@NotNull LocalDateTime startLocal, @Min(1) @Max(10080) Integer durationMinutes,
                             UUID substituteTeacherMembershipId, @Size(max = 120) String roomCode,
                             @NotBlank @Size(max = 500) String reason, boolean allowConflicts,
                             @Size(max = 500) String conflictOverrideReason, long expectedVersion) {}

    record CancelRequest(@NotBlank @Size(max = 500) String reason, long expectedVersion) {}
}
