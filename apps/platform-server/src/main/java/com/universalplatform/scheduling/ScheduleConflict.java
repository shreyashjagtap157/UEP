package com.universalplatform.scheduling;

import java.time.Instant;
import java.util.UUID;

public record ScheduleConflict(
        ScheduleConflictType type,
        UUID existingSeriesId,
        UUID existingOccurrenceId,
        String existingTitle,
        Instant startsAt,
        Instant endsAt,
        String message) {}
