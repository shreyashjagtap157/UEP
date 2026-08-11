package com.universalplatform.scheduling;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class ScheduleConflictDetector {
    private final ScheduleOccurrenceRepository occurrences;
    private final ScheduleSeriesRepository series;

    ScheduleConflictDetector(ScheduleOccurrenceRepository occurrences, ScheduleSeriesRepository series) {
        this.occurrences = occurrences;
        this.series = series;
    }

    List<ScheduleConflict> detect(UUID tenantId, ConflictSubject subject,
                                  Collection<RecurrenceExpander.ExpandedOccurrence> proposed,
                                  UUID ignoredOccurrenceId) {
        if (subject.kind() == ScheduleKind.HOLIDAY || proposed.isEmpty()) return List.of();
        Instant from = proposed.stream().map(RecurrenceExpander.ExpandedOccurrence::startsAt).min(Instant::compareTo).orElseThrow();
        Instant to = proposed.stream().map(RecurrenceExpander.ExpandedOccurrence::endsAt).max(Instant::compareTo).orElseThrow();
        List<ScheduleOccurrence> existing = occurrences.findRange(tenantId, from, to).stream()
                .filter(item -> ignoredOccurrenceId == null || !item.id().equals(ignoredOccurrenceId))
                .toList();
        Map<UUID, ScheduleSeries> byId = series.findAllByTenantIdAndIdIn(tenantId,
                        existing.stream().map(ScheduleOccurrence::seriesId).collect(java.util.stream.Collectors.toSet()))
                .stream().collect(java.util.stream.Collectors.toMap(ScheduleSeries::id, item -> item));

        Map<String, ScheduleConflict> unique = new LinkedHashMap<>();
        for (RecurrenceExpander.ExpandedOccurrence candidate : proposed) {
            for (ScheduleOccurrence existingOccurrence : existing) {
                if (!overlaps(candidate.startsAt(), candidate.endsAt(), existingOccurrence.startsAt(), existingOccurrence.endsAt())) continue;
                ScheduleSeries existingSeries = byId.get(existingOccurrence.seriesId());
                if (existingSeries == null || existingSeries.status() != ScheduleSeriesStatus.ACTIVE) continue;
                addConflicts(unique, subject, candidate, existingSeries, existingOccurrence);
            }
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(ScheduleConflict::startsAt).thenComparing(item -> item.type().name()))
                .toList();
    }

    private static void addConflicts(Map<String, ScheduleConflict> result, ConflictSubject proposed,
                                     RecurrenceExpander.ExpandedOccurrence candidate, ScheduleSeries existingSeries,
                                     ScheduleOccurrence existingOccurrence) {
        if (existingSeries.kind() == ScheduleKind.HOLIDAY && holidayApplies(proposed.branchId(), existingSeries.branchId())) {
            put(result, ScheduleConflictType.HOLIDAY, existingSeries, existingOccurrence,
                    "The occurrence overlaps an institutional holiday");
        }
        UUID existingTeacher = existingOccurrence.substituteTeacherMembershipId() != null
                ? existingOccurrence.substituteTeacherMembershipId() : existingSeries.primaryTeacherMembershipId();
        if (proposed.teacherMembershipId() != null && proposed.teacherMembershipId().equals(existingTeacher)) {
            put(result, ScheduleConflictType.TEACHER, existingSeries, existingOccurrence,
                    "The assigned teacher is already scheduled");
        }
        if (proposed.batchId() != null && proposed.batchId().equals(existingSeries.batchId())) {
            ScheduleConflictType type = proposed.kind() == ScheduleKind.EXAM || existingSeries.kind() == ScheduleKind.EXAM
                    ? ScheduleConflictType.EXAM : ScheduleConflictType.BATCH;
            put(result, type, existingSeries, existingOccurrence,
                    type == ScheduleConflictType.EXAM ? "The batch has an overlapping exam or scheduled activity" : "The batch is already scheduled");
        }
        String proposedRoom = normalizeRoom(proposed.roomCode());
        String existingRoom = normalizeRoom(existingOccurrence.roomCodeOverride() != null
                ? existingOccurrence.roomCodeOverride() : existingSeries.roomCode());
        if (proposedRoom != null && proposedRoom.equals(existingRoom)
                && java.util.Objects.equals(proposed.branchId(), existingSeries.branchId())) {
            put(result, ScheduleConflictType.ROOM, existingSeries, existingOccurrence,
                    "The room/resource is already scheduled");
        }
    }

    private static boolean holidayApplies(UUID proposedBranch, UUID holidayBranch) {
        return holidayBranch == null || java.util.Objects.equals(proposedBranch, holidayBranch);
    }

    private static String normalizeRoom(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean overlaps(Instant startA, Instant endA, Instant startB, Instant endB) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }

    private static void put(Map<String, ScheduleConflict> result, ScheduleConflictType type,
                            ScheduleSeries series, ScheduleOccurrence occurrence, String message) {
        String key = type + ":" + occurrence.id();
        result.putIfAbsent(key, new ScheduleConflict(type, series.id(), occurrence.id(), series.title(),
                occurrence.startsAt(), occurrence.endsAt(), message));
    }

    record ConflictSubject(ScheduleKind kind, UUID branchId, UUID batchId, UUID teacherMembershipId, String roomCode) {}
}
