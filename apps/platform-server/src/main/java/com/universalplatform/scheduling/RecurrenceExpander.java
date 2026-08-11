package com.universalplatform.scheduling;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRules;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

class RecurrenceExpander {
    private static final int MAX_OCCURRENCES = 1000;

    List<ExpandedOccurrence> expand(LocalDateTime startLocal, int durationMinutes, String timezone,
                                    RecurrenceFrequency frequency, int interval, Set<DayOfWeek> days,
                                    Integer dayOfMonth, LocalDateTime untilLocal, Integer count) {
        if (startLocal == null) throw new IllegalArgumentException("Start time is required");
        if (durationMinutes <= 0 || durationMinutes > 10080) throw new IllegalArgumentException("Duration must be between 1 minute and 7 days");
        if (frequency == null) frequency = RecurrenceFrequency.NONE;
        if (interval < 1 || interval > 365) throw new IllegalArgumentException("Recurrence interval must be between 1 and 365");
        ZoneId zone = zone(timezone);
        if (frequency == RecurrenceFrequency.NONE) {
            if (count != null || untilLocal != null || (days != null && !days.isEmpty()) || dayOfMonth != null) {
                throw new IllegalArgumentException("One-time schedules cannot contain recurrence fields");
            }
            return List.of(resolve(startLocal, durationMinutes, zone));
        }
        if (count != null && untilLocal != null) throw new IllegalArgumentException("Use recurrence count or recurrence-until, not both");
        if (count == null && untilLocal == null) throw new IllegalArgumentException("Recurring schedules must have a finite count or recurrence-until value");
        if (count != null && (count < 1 || count > MAX_OCCURRENCES)) throw new IllegalArgumentException("Recurrence count must be between 1 and 1000");
        if (untilLocal != null && untilLocal.isBefore(startLocal)) throw new IllegalArgumentException("Recurrence-until cannot be before the series start");

        List<LocalDateTime> locals = switch (frequency) {
            case DAILY -> daily(startLocal, interval, untilLocal, count);
            case WEEKLY -> weekly(startLocal, interval, days, untilLocal, count);
            case MONTHLY -> monthly(startLocal, interval, dayOfMonth, untilLocal, count);
            case NONE -> throw new IllegalStateException("Unexpected NONE recurrence");
        };
        if (locals.isEmpty()) throw new IllegalArgumentException("Recurrence produced no occurrences");
        List<ExpandedOccurrence> result = locals.stream().map(local -> resolve(local, durationMinutes, zone)).toList();
        for (int i = 1; i < result.size(); i++) {
            if (result.get(i - 1).endsAt().isAfter(result.get(i).startsAt())) {
                throw new IllegalArgumentException("The recurrence duration causes occurrences to overlap each other");
            }
        }
        return result;
    }

    private static List<LocalDateTime> daily(LocalDateTime start, int interval, LocalDateTime until, Integer count) {
        List<LocalDateTime> result = new ArrayList<>();
        LocalDateTime current = start;
        while (within(current, until, count, result.size())) {
            result.add(current);
            guard(result);
            current = current.plusDays(interval);
        }
        return result;
    }

    private static List<LocalDateTime> weekly(LocalDateTime start, int interval, Set<DayOfWeek> days,
                                               LocalDateTime until, Integer count) {
        if (days == null || days.isEmpty()) throw new IllegalArgumentException("Weekly recurrence requires at least one weekday");
        List<DayOfWeek> ordered = days.stream().sorted(Comparator.comparingInt(DayOfWeek::getValue)).toList();
        LocalDate anchorWeek = start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalTime time = start.toLocalTime();
        List<LocalDateTime> result = new ArrayList<>();
        for (long week = 0; ; week += interval) {
            LocalDate weekStart = anchorWeek.plusWeeks(week);
            for (DayOfWeek day : ordered) {
                LocalDateTime candidate = LocalDateTime.of(weekStart.plusDays(day.getValue() - 1L), time);
                if (candidate.isBefore(start)) continue;
                if (!within(candidate, until, count, result.size())) return result;
                result.add(candidate);
                guard(result);
                if (count != null && result.size() >= count) return result;
            }
            if (until != null && weekStart.atStartOfDay().isAfter(until)) return result;
        }
    }

    private static List<LocalDateTime> monthly(LocalDateTime start, int interval, Integer dayOfMonth,
                                                LocalDateTime until, Integer count) {
        if (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 31) throw new IllegalArgumentException("Monthly recurrence requires dayOfMonth from 1 to 31");
        List<LocalDateTime> result = new ArrayList<>();
        YearMonth month = YearMonth.from(start);
        LocalTime time = start.toLocalTime();
        for (int index = 0; ; index += interval) {
            YearMonth current = month.plusMonths(index);
            int day = Math.min(dayOfMonth, current.lengthOfMonth());
            LocalDateTime candidate = LocalDateTime.of(current.atDay(day), time);
            if (candidate.isBefore(start)) continue;
            if (!within(candidate, until, count, result.size())) break;
            result.add(candidate);
            guard(result);
            if (count != null && result.size() >= count) break;
        }
        return result;
    }

    private static boolean within(LocalDateTime candidate, LocalDateTime until, Integer count, int currentSize) {
        if (count != null && currentSize >= count) return false;
        return until == null || !candidate.isAfter(until);
    }

    private static void guard(List<?> result) {
        if (result.size() > MAX_OCCURRENCES) throw new IllegalArgumentException("A schedule series cannot materialize more than 1000 occurrences");
    }

    private static ZoneId zone(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? "UTC" : timezone.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid IANA timezone: " + timezone);
        }
    }

    private static ExpandedOccurrence resolve(LocalDateTime local, int durationMinutes, ZoneId zone) {
        ZoneRules rules = zone.getRules();
        List<ZoneOffset> offsets = rules.getValidOffsets(local);
        if (offsets.isEmpty()) {
            throw new IllegalArgumentException("Local time does not exist because of a daylight-saving transition: " + local + " " + zone);
        }
        if (offsets.size() > 1) {
            throw new IllegalArgumentException("Local time is ambiguous because of a daylight-saving transition: " + local + " " + zone);
        }
        Instant start = local.toInstant(offsets.getFirst());
        return new ExpandedOccurrence(local, start, start.plus(Duration.ofMinutes(durationMinutes)));
    }

    record ExpandedOccurrence(LocalDateTime localStart, Instant startsAt, Instant endsAt) {}
}
