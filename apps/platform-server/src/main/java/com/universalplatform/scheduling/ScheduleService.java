package com.universalplatform.scheduling;

import com.universalplatform.audit.AuditService;
import com.universalplatform.curriculum.CurriculumDirectory;
import com.universalplatform.curriculum.CurriculumStatus;
import com.universalplatform.enrollment.BatchStatus;
import com.universalplatform.enrollment.EnrollmentDirectory;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.MembershipDirectory;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.notification.NotificationEventType;
import com.universalplatform.notification.NotificationPriority;
import com.universalplatform.notification.NotificationPublisher;
import com.universalplatform.organization.BranchStatus;
import com.universalplatform.organization.OrganizationDirectory;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ScheduleService {
    private static final int MAX_CALENDAR_DAYS = 62;
    private static final int MAX_CALENDAR_ITEMS = 5000;

    private final TenantContext tenantContext;
    private final ActorContext actorContext;
    private final AuthorizationService authorization;
    private final MembershipDirectory memberships;
    private final OrganizationDirectory organizations;
    private final CurriculumDirectory curriculum;
    private final EnrollmentDirectory enrollment;
    private final ScheduleSeriesRepository seriesRepository;
    private final ScheduleOccurrenceRepository occurrenceRepository;
    private final ClassSessionRepository classSessions;
    private final ScheduleConflictOverrideRepository conflictOverrides;
    private final ScheduleWriteLock writeLock;
    private final ScheduleConflictDetector conflictDetector;
    private final NotificationPublisher notifications;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();
    private final RecurrenceExpander recurrence = new RecurrenceExpander();

    ScheduleService(TenantContext tenantContext, ActorContext actorContext, AuthorizationService authorization,
                    MembershipDirectory memberships, OrganizationDirectory organizations, CurriculumDirectory curriculum,
                    EnrollmentDirectory enrollment, ScheduleSeriesRepository seriesRepository,
                    ScheduleOccurrenceRepository occurrenceRepository, ClassSessionRepository classSessions,
                    ScheduleConflictOverrideRepository conflictOverrides, ScheduleWriteLock writeLock, ScheduleConflictDetector conflictDetector,
                    NotificationPublisher notifications, AuditService audit) {
        this.tenantContext = tenantContext;
        this.actorContext = actorContext;
        this.authorization = authorization;
        this.memberships = memberships;
        this.organizations = organizations;
        this.curriculum = curriculum;
        this.enrollment = enrollment;
        this.seriesRepository = seriesRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.classSessions = classSessions;
        this.conflictOverrides = conflictOverrides;
        this.writeLock = writeLock;
        this.conflictDetector = conflictDetector;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    SchedulePage<SeriesView> listSeries(int page, int size) {
        UUID tenantId = tenantContext.requireTenantId();
        AuthorizationService.AccessScope scope = authorization.currentScope(PermissionKey.SCHEDULE_MANAGE);
        PageRequest request = PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<ScheduleSeries> result;
        if (scope.tenantWide()) {
            result = seriesRepository.findAllByTenantId(tenantId, request);
        } else {
            if (scope.branchIds().isEmpty()) authorization.require(PermissionKey.SCHEDULE_MANAGE);
            result = seriesRepository.findAllByTenantIdAndBranchIdIn(tenantId, scope.branchIds(), request);
        }
        return new SchedulePage<>(result.stream().map(ScheduleService::seriesView).toList(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    List<OccurrenceView> calendar(Instant from, Instant to) {
        validateRange(from, to);
        UUID tenantId = tenantContext.requireTenantId();
        List<ScheduleOccurrence> visible;
        AuthorizationService.AccessScope manageScope = authorization.currentScope(PermissionKey.SCHEDULE_MANAGE);
        if (manageScope.tenantWide()) {
            visible = occurrenceRepository.findRange(tenantId, from, to);
        } else if (!manageScope.branchIds().isEmpty()) {
            visible = occurrenceRepository.findRangeForBranches(tenantId, manageScope.branchIds(), from, to);
        } else {
            authorization.require(PermissionKey.SCHEDULE_VIEW);
            MembershipDirectory.MembershipReference current = memberships.current();
            Map<UUID, ScheduleOccurrence> unique = new LinkedHashMap<>();
            add(unique, occurrenceRepository.findRangeForTeacher(tenantId, current.membershipId(), from, to));
            Set<UUID> batchIds = enrollment.activeBatchIdsForMembership(current.membershipId());
            if (!batchIds.isEmpty()) add(unique, occurrenceRepository.findRangeForBatches(tenantId, batchIds, from, to));
            add(unique, occurrenceRepository.findPublicRange(tenantId, current.primaryBranchId(), from, to));
            visible = unique.values().stream().sorted(Comparator.comparing(ScheduleOccurrence::startsAt)).toList();
        }
        if (visible.size() > MAX_CALENDAR_ITEMS) throw new IllegalArgumentException("Calendar range contains too many events; request a smaller range");
        return occurrenceViews(tenantId, visible);
    }

    @Transactional(readOnly = true)
    List<ScheduleConflict> preflight(CreateCommand command) {
        ResolvedCommand resolved = resolve(command);
        authorization.require(PermissionKey.SCHEDULE_MANAGE, resolved.branchId());
        UUID tenantId = tenantContext.requireTenantId();
        List<RecurrenceExpander.ExpandedOccurrence> candidates = expand(resolved);
        return conflictDetector.detect(tenantId, subject(resolved, resolved.primaryTeacherMembershipId(), resolved.roomCode()), candidates, null);
    }

    @Transactional
    SeriesCreateResult create(CreateCommand command) {
        ResolvedCommand resolved = resolve(command);
        authorization.require(PermissionKey.SCHEDULE_MANAGE, resolved.branchId());
        UUID tenantId = tenantContext.requireTenantId();
        List<RecurrenceExpander.ExpandedOccurrence> candidates = expand(resolved);
        writeLock.acquire(tenantId);
        List<ScheduleConflict> conflicts = conflictDetector.detect(tenantId,
                subject(resolved, resolved.primaryTeacherMembershipId(), resolved.roomCode()), candidates, null);
        validateConflictOverride(command.allowConflicts(), command.conflictOverrideReason(), resolved.branchId(), conflicts);

        Instant now = clock.instant();
        UUID seriesId = UUID.randomUUID();
        ScheduleSeries series = seriesRepository.save(new ScheduleSeries(seriesId, tenantId, resolved.kind(), resolved.title(),
                resolved.description(), resolved.timezone(), resolved.deliveryMode(), resolved.branchId(), resolved.batchId(),
                resolved.courseId(), resolved.subjectId(), resolved.moduleId(), resolved.primaryTeacherMembershipId(),
                resolved.roomCode(), resolved.startLocal(), resolved.durationMinutes(), resolved.recurrenceFrequency(),
                resolved.recurrenceInterval(), recurrenceDays(resolved.recurrenceDays()), resolved.recurrenceDayOfMonth(),
                resolved.recurrenceUntilLocal(), resolved.recurrenceCount(), actorContext.requireSubject(), now));

        List<ScheduleOccurrence> occurrences = candidates.stream()
                .map(item -> new ScheduleOccurrence(UUID.randomUUID(), tenantId, seriesId, item.startsAt(), item.endsAt(), now))
                .toList();
        occurrenceRepository.saveAll(occurrences);
        if (resolved.kind() == ScheduleKind.CLASS) {
            classSessions.saveAll(occurrences.stream().map(item -> new ClassSession(UUID.randomUUID(), tenantId, item.id(),
                    resolved.batchId(), resolved.courseId(), resolved.subjectId(), resolved.moduleId(), now)).toList());
        }

        audit.record(tenantId, actorContext.requireSubject(), "SCHEDULE_SERIES_CREATED", "schedule_series", seriesId.toString());
        if (!conflicts.isEmpty()) {
            persistConflictOverride(tenantId, seriesId, null, command.conflictOverrideReason(), conflicts);
            audit.record(tenantId, actorContext.requireSubject(), "SCHEDULE_CONFLICT_OVERRIDE", "schedule_series", seriesId.toString());
        }
        enqueueScheduledNotification(series, occurrences, false);
        return new SeriesCreateResult(seriesView(series), occurrenceViews(tenantId, occurrences), conflicts);
    }

    @Transactional
    OccurrenceView reschedule(UUID occurrenceId, RescheduleCommand command) {
        if (command == null || command.startLocal() == null) throw new IllegalArgumentException("New local start time is required");
        UUID tenantId = tenantContext.requireTenantId();
        ScheduleOccurrence occurrence = occurrence(tenantId, occurrenceId);
        ScheduleSeries series = series(tenantId, occurrence.seriesId());
        authorization.require(PermissionKey.SCHEDULE_MANAGE, series.branchId());
        if (occurrence.version() != command.expectedVersion()) throw new SchedulingConflictException("Occurrence was modified by another request");
        if (series.status() != ScheduleSeriesStatus.ACTIVE) throw new SchedulingConflictException("Cancelled schedule series cannot be rescheduled");
        if (command.substituteTeacherMembershipId() != null) memberships.requireActive(command.substituteTeacherMembershipId());
        int duration = command.durationMinutes() == null ? Math.toIntExact(Duration.between(occurrence.startsAt(), occurrence.endsAt()).toMinutes()) : command.durationMinutes();
        List<RecurrenceExpander.ExpandedOccurrence> expanded = recurrence.expand(command.startLocal(), duration, series.timezone(),
                RecurrenceFrequency.NONE, 1, Set.of(), null, null, null);
        RecurrenceExpander.ExpandedOccurrence candidate = expanded.getFirst();
        UUID effectiveTeacher = command.substituteTeacherMembershipId() != null ? command.substituteTeacherMembershipId() : series.primaryTeacherMembershipId();
        String room = command.roomCode() == null ? effectiveRoom(series, occurrence) : normalize(command.roomCode(), 120);
        validateRoom(series.deliveryMode(), series.branchId(), room);
        writeLock.acquire(tenantId);
        List<ScheduleConflict> conflicts = conflictDetector.detect(tenantId,
                new ScheduleConflictDetector.ConflictSubject(series.kind(), series.branchId(), series.batchId(), effectiveTeacher, room),
                expanded, occurrence.id());
        validateConflictOverride(command.allowConflicts(), command.conflictOverrideReason(), series.branchId(), conflicts);
        String reason = required(command.reason(), "Reschedule reason", 500);
        occurrence.reschedule(candidate.startsAt(), candidate.endsAt(), command.substituteTeacherMembershipId(),
                command.roomCode() == null ? null : room, reason, clock.instant());
        if (!conflicts.isEmpty()) {
            persistConflictOverride(tenantId, null, occurrence.id(), command.conflictOverrideReason(), conflicts);
            audit.record(tenantId, actorContext.requireSubject(), "SCHEDULE_CONFLICT_OVERRIDE", "schedule_occurrence", occurrence.id().toString());
        }
        audit.record(tenantId, actorContext.requireSubject(), "SCHEDULE_OCCURRENCE_RESCHEDULED", "schedule_occurrence", occurrence.id().toString());
        enqueueOccurrenceChange(series, occurrence, "rescheduled", eventType(series.kind(), "RESCHEDULED"));
        occurrenceRepository.flush();
        return occurrenceViews(tenantId, List.of(occurrence)).getFirst();
    }

    @Transactional
    OccurrenceView cancelOccurrence(UUID occurrenceId, CancelOccurrenceCommand command) {
        UUID tenantId = tenantContext.requireTenantId();
        ScheduleOccurrence occurrence = occurrence(tenantId, occurrenceId);
        ScheduleSeries series = series(tenantId, occurrence.seriesId());
        authorization.require(PermissionKey.SCHEDULE_MANAGE, series.branchId());
        if (occurrence.version() != command.expectedVersion()) throw new SchedulingConflictException("Occurrence was modified by another request");
        String reason = required(command.reason(), "Cancellation reason", 500);
        occurrence.cancel(reason, clock.instant());
        classSessions.findByTenantIdAndScheduleOccurrenceId(tenantId, occurrence.id()).ifPresent(ClassSession::cancel);
        audit.record(tenantId, actorContext.requireSubject(), "SCHEDULE_OCCURRENCE_CANCELLED", "schedule_occurrence", occurrence.id().toString());
        enqueueOccurrenceChange(series, occurrence, "cancelled", eventType(series.kind(), "CANCELLED"));
        occurrenceRepository.flush();
        return occurrenceViews(tenantId, List.of(occurrence)).getFirst();
    }

    @Transactional
    SeriesView cancelSeries(UUID seriesId, CancelSeriesCommand command) {
        UUID tenantId = tenantContext.requireTenantId();
        ScheduleSeries series = series(tenantId, seriesId);
        authorization.require(PermissionKey.SCHEDULE_MANAGE, series.branchId());
        if (series.version() != command.expectedVersion()) throw new SchedulingConflictException("Schedule series was modified by another request");
        String reason = required(command.reason(), "Cancellation reason", 500);
        if (series.status() == ScheduleSeriesStatus.CANCELLED) return seriesView(series);
        Instant now = clock.instant();
        series.cancel(now);
        List<ScheduleOccurrence> occurrences = occurrenceRepository.findAllByTenantIdAndSeriesIdOrderByStartsAtAsc(tenantId, seriesId);
        for (ScheduleOccurrence occurrence : occurrences) {
            if (occurrence.status() == ScheduleOccurrenceStatus.SCHEDULED) occurrence.cancel(reason, now);
        }
        Map<UUID, ClassSession> sessions = classSessions.findAllByTenantIdAndScheduleOccurrenceIdIn(tenantId,
                        occurrences.stream().map(ScheduleOccurrence::id).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(ClassSession::scheduleOccurrenceId, item -> item));
        sessions.values().forEach(ClassSession::cancel);
        audit.record(tenantId, actorContext.requireSubject(), "SCHEDULE_SERIES_CANCELLED", "schedule_series", seriesId.toString());
        enqueueSeriesChange(series, series.title() + " was cancelled.", eventType(series.kind(), "CANCELLED"), seriesId + ":cancel:" + series.version());
        seriesRepository.flush();
        occurrenceRepository.flush();
        return seriesView(series);
    }

    private ResolvedCommand resolve(CreateCommand command) {
        if (command == null) throw new IllegalArgumentException("Schedule request is required");
        ScheduleKind kind = command.kind() == null ? ScheduleKind.CLASS : command.kind();
        UUID branchId = command.branchId();
        UUID batchId = command.batchId();
        UUID courseId = command.courseId();
        UUID subjectId = command.subjectId();
        UUID moduleId = command.moduleId();

        if (batchId != null) {
            EnrollmentDirectory.BatchReference batch = enrollment.requireBatch(batchId);
            if (batch.status() == BatchStatus.CLOSED || batch.status() == BatchStatus.ARCHIVED) throw new IllegalArgumentException("Closed or archived batches cannot receive new schedules");
            branchId = merge(branchId, batch.branchId(), "Branch does not match the selected batch");
            courseId = merge(courseId, batch.courseId(), "Course does not match the selected batch");
        }
        if (subjectId != null) {
            CurriculumDirectory.SubjectReference subject = curriculum.requireSubject(subjectId);
            if (subject.status() != CurriculumStatus.ACTIVE) throw new IllegalArgumentException("Inactive subjects cannot receive new schedules");
            courseId = merge(courseId, subject.courseId(), "Course does not match the selected subject");
        }
        if (courseId != null && curriculum.requireCourse(courseId).status() != CurriculumStatus.ACTIVE) {
            throw new IllegalArgumentException("Inactive courses cannot receive new schedules");
        }
        if (moduleId != null && curriculum.requireModule(moduleId).status() != CurriculumStatus.ACTIVE) {
            throw new IllegalArgumentException("Inactive modules cannot receive new schedules");
        }
        String timezone = command.timezone();
        if (branchId != null) {
            OrganizationDirectory.BranchReference branch = organizations.requireBranch(branchId);
            if (branch.status() != BranchStatus.ACTIVE) throw new IllegalArgumentException("Inactive branches cannot receive new schedules");
            if (timezone == null || timezone.isBlank()) timezone = branch.timezone();
        }
        if (timezone == null || timezone.isBlank()) timezone = organizations.defaultTimezone();
        try { ZoneId.of(timezone); } catch (RuntimeException exception) { throw new IllegalArgumentException("Invalid IANA timezone: " + timezone); }

        if (command.primaryTeacherMembershipId() != null) memberships.requireActive(command.primaryTeacherMembershipId());
        DeliveryMode deliveryMode = command.deliveryMode() == null
                ? (kind == ScheduleKind.HOLIDAY ? DeliveryMode.NOT_APPLICABLE : DeliveryMode.OFFLINE) : command.deliveryMode();
        String room = normalize(command.roomCode(), 120);
        validateKind(kind, batchId, courseId, subjectId, moduleId, deliveryMode);
        validateRoom(deliveryMode, branchId, room);
        RecurrenceFrequency frequency = command.recurrenceFrequency() == null ? RecurrenceFrequency.NONE : command.recurrenceFrequency();
        int interval = command.recurrenceInterval() == null ? 1 : command.recurrenceInterval();
        Set<DayOfWeek> days = command.recurrenceDays() == null ? Set.of() : Set.copyOf(command.recurrenceDays());
        return new ResolvedCommand(kind, required(command.title(), "Schedule title", 240), normalize(command.description(), 4000),
                timezone, deliveryMode, branchId, batchId, courseId, subjectId, moduleId, command.primaryTeacherMembershipId(), room,
                command.startLocal(), command.durationMinutes(), frequency, interval, days, command.recurrenceDayOfMonth(),
                command.recurrenceUntilLocal(), command.recurrenceCount());
    }

    private List<RecurrenceExpander.ExpandedOccurrence> expand(ResolvedCommand command) {
        return recurrence.expand(command.startLocal(), command.durationMinutes(), command.timezone(), command.recurrenceFrequency(),
                command.recurrenceInterval(), command.recurrenceDays(), command.recurrenceDayOfMonth(),
                command.recurrenceUntilLocal(), command.recurrenceCount());
    }

    private void persistConflictOverride(UUID tenantId, UUID seriesId, UUID occurrenceId, String reason, List<ScheduleConflict> conflicts) {
        String normalizedReason = required(reason, "Conflict override reason", 500);
        String summary = conflicts.stream()
                .map(conflict -> conflict.type() + "|" + conflict.existingSeriesId() + "|" + conflict.existingOccurrenceId()
                        + "|" + conflict.startsAt() + "|" + conflict.endsAt() + "|" + conflict.message())
                .collect(java.util.stream.Collectors.joining("\n"));
        conflictOverrides.save(new ScheduleConflictOverride(UUID.randomUUID(), tenantId, seriesId, occurrenceId, normalizedReason,
                summary, actorContext.requireSubject(), clock.instant()));
    }

    private void validateConflictOverride(boolean allow, String reason, UUID branchId, List<ScheduleConflict> conflicts) {
        if (conflicts.isEmpty()) return;
        if (!allow) throw new ScheduleConflictDetectedException(conflicts);
        authorization.require(PermissionKey.SCHEDULE_CONFLICT_OVERRIDE, branchId);
        required(reason, "Conflict override reason", 500);
    }

    private void enqueueScheduledNotification(ScheduleSeries series, List<ScheduleOccurrence> occurrences, boolean rescheduled) {
        String verb = rescheduled ? "updated" : "scheduled";
        NotificationEventType type = eventType(series.kind(), rescheduled ? "RESCHEDULED" : "SCHEDULED");
        String body = series.title() + " was " + verb + ". First occurrence: " + occurrences.getFirst().startsAt() + ".";
        enqueueSeriesChange(series, body, type, series.id() + ":" + type + ":" + series.version());
    }

    private void enqueueOccurrenceChange(ScheduleSeries series, ScheduleOccurrence occurrence, String verb, NotificationEventType type) {
        Set<NotificationPublisher.Recipient> recipients = audience(series);
        if (recipients.isEmpty()) return;
        notifications.enqueue(new NotificationPublisher.NotificationRequest(series.tenantId(), type, "schedule_occurrence", occurrence.id(),
                series.title(), series.title() + " was " + verb + ". New state starts at " + occurrence.startsAt() + ".",
                series.kind() == ScheduleKind.EXAM ? NotificationPriority.IMPORTANT : NotificationPriority.NORMAL,
                "schedule_occurrence", occurrence.id(), type + ":" + occurrence.id() + ":" + occurrence.version(), null, recipients));
    }

    private void enqueueSeriesChange(ScheduleSeries series, String body, NotificationEventType type, String deduplicationKey) {
        Set<NotificationPublisher.Recipient> recipients = audience(series);
        if (recipients.isEmpty()) return;
        notifications.enqueue(new NotificationPublisher.NotificationRequest(series.tenantId(), type, "schedule_series", series.id(),
                series.title(), body, series.kind() == ScheduleKind.EXAM ? NotificationPriority.IMPORTANT : NotificationPriority.NORMAL,
                "schedule_series", series.id(), deduplicationKey, null, recipients));
    }

    private Set<NotificationPublisher.Recipient> audience(ScheduleSeries series) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        if (series.batchId() != null) ids.addAll(enrollment.activeMembershipIdsForBatch(series.batchId()));
        else if (series.courseId() != null) ids.addAll(enrollment.activeMembershipIdsForCourse(series.courseId()));
        if (series.primaryTeacherMembershipId() != null) ids.add(series.primaryTeacherMembershipId());
        Map<UUID, MembershipDirectory.MembershipReference> refs = memberships.references(ids);
        return refs.values().stream().map(ref -> new NotificationPublisher.Recipient(ref.membershipId(), ref.email()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private List<OccurrenceView> occurrenceViews(UUID tenantId, Collection<ScheduleOccurrence> occurrences) {
        if (occurrences.isEmpty()) return List.of();
        Set<UUID> seriesIds = occurrences.stream().map(ScheduleOccurrence::seriesId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, ScheduleSeries> bySeries = seriesRepository.findAllByTenantIdAndIdIn(tenantId, seriesIds).stream()
                .collect(java.util.stream.Collectors.toMap(ScheduleSeries::id, item -> item));
        Map<UUID, ClassSession> byOccurrence = classSessions.findAllByTenantIdAndScheduleOccurrenceIdIn(tenantId,
                        occurrences.stream().map(ScheduleOccurrence::id).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(ClassSession::scheduleOccurrenceId, item -> item));
        return occurrences.stream().map(occurrence -> occurrenceView(occurrence, bySeries.get(occurrence.seriesId()), byOccurrence.get(occurrence.id())))
                .filter(java.util.Objects::nonNull).sorted(Comparator.comparing(OccurrenceView::startsAt)).toList();
    }

    private static OccurrenceView occurrenceView(ScheduleOccurrence occurrence, ScheduleSeries series, ClassSession classSession) {
        if (series == null) return null;
        UUID effectiveTeacher = occurrence.substituteTeacherMembershipId() != null ? occurrence.substituteTeacherMembershipId() : series.primaryTeacherMembershipId();
        return new OccurrenceView(occurrence.id(), series.id(), classSession == null ? null : classSession.id(), series.kind(), series.title(),
                series.description(), series.timezone(), series.deliveryMode(), series.branchId(), series.batchId(), series.courseId(),
                series.subjectId(), series.moduleId(), series.primaryTeacherMembershipId(), effectiveTeacher,
                effectiveRoom(series, occurrence), occurrence.originalStartsAt(), occurrence.startsAt(), occurrence.endsAt(), occurrence.status(),
                occurrence.exceptionReason(), occurrence.version());
    }

    private static SeriesView seriesView(ScheduleSeries series) {
        return new SeriesView(series.id(), series.kind(), series.title(), series.description(), series.timezone(), series.deliveryMode(),
                series.branchId(), series.batchId(), series.courseId(), series.subjectId(), series.moduleId(), series.primaryTeacherMembershipId(),
                series.roomCode(), series.startLocal(), series.durationMinutes(), series.recurrenceFrequency(), series.recurrenceInterval(),
                parseDays(series.recurrenceDays()), series.recurrenceDayOfMonth(), series.recurrenceUntilLocal(), series.recurrenceCount(),
                series.status(), series.createdAt(), series.version());
    }

    private ScheduleSeries series(UUID tenantId, UUID id) {
        return seriesRepository.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new SchedulingNotFoundException("Schedule series not found"));
    }

    private ScheduleOccurrence occurrence(UUID tenantId, UUID id) {
        return occurrenceRepository.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new SchedulingNotFoundException("Schedule occurrence not found"));
    }

    private static void validateRange(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) throw new IllegalArgumentException("Calendar range must have from < to");
        if (Duration.between(from, to).toDays() > MAX_CALENDAR_DAYS) throw new IllegalArgumentException("Calendar ranges are limited to 62 days");
    }

    private static void validateKind(ScheduleKind kind, UUID batchId, UUID courseId, UUID subjectId, UUID moduleId, DeliveryMode mode) {
        if ((kind == ScheduleKind.CLASS || kind == ScheduleKind.EXAM)
                && batchId == null && courseId == null && subjectId == null && moduleId == null) {
            throw new IllegalArgumentException(kind + " schedules require an academic target");
        }
        if (kind == ScheduleKind.HOLIDAY && mode != DeliveryMode.NOT_APPLICABLE) throw new IllegalArgumentException("Holidays must use NOT_APPLICABLE delivery mode");
        if (kind != ScheduleKind.HOLIDAY && mode == DeliveryMode.NOT_APPLICABLE) throw new IllegalArgumentException("Only holidays may use NOT_APPLICABLE delivery mode");
    }

    private static void validateRoom(DeliveryMode mode, UUID branchId, String room) {
        if (mode == DeliveryMode.ONLINE && room != null) throw new IllegalArgumentException("Online schedules cannot reserve a physical room");
        if ((mode == DeliveryMode.OFFLINE || mode == DeliveryMode.HYBRID) && room != null && branchId == null) {
            throw new IllegalArgumentException("A physical room requires a branch");
        }
    }

    private static UUID merge(UUID explicit, UUID derived, String message) {
        if (explicit != null && derived != null && !explicit.equals(derived)) throw new IllegalArgumentException(message);
        return explicit != null ? explicit : derived;
    }

    private static String recurrenceDays(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) return null;
        return days.stream().sorted(Comparator.comparingInt(DayOfWeek::getValue)).map(DayOfWeek::name)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static Set<DayOfWeek> parseDays(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return java.util.Arrays.stream(value.split(",")).map(DayOfWeek::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String effectiveRoom(ScheduleSeries series, ScheduleOccurrence occurrence) {
        return occurrence.roomCodeOverride() != null ? occurrence.roomCodeOverride() : series.roomCode();
    }

    private static ScheduleConflictDetector.ConflictSubject subject(ResolvedCommand command, UUID teacher, String room) {
        return new ScheduleConflictDetector.ConflictSubject(command.kind(), command.branchId(), command.batchId(), teacher, room);
    }

    private static String required(String value, String label, int max) {
        String normalized = normalize(value, max);
        if (normalized == null) throw new IllegalArgumentException(label + " is required");
        return normalized;
    }

    private static String normalize(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("Value exceeds " + max + " characters");
        return normalized;
    }

    private static int safePage(int page) { return Math.max(0, page); }
    private static int safeSize(int size) { return Math.max(1, Math.min(size, 100)); }
    private static void add(Map<UUID, ScheduleOccurrence> target, Collection<ScheduleOccurrence> source) { source.forEach(item -> target.putIfAbsent(item.id(), item)); }

    record CreateCommand(ScheduleKind kind, String title, String description, String timezone, DeliveryMode deliveryMode,
                         UUID branchId, UUID batchId, UUID courseId, UUID subjectId, UUID moduleId,
                         UUID primaryTeacherMembershipId, String roomCode, LocalDateTime startLocal, int durationMinutes,
                         RecurrenceFrequency recurrenceFrequency, Integer recurrenceInterval, Set<DayOfWeek> recurrenceDays,
                         Integer recurrenceDayOfMonth, LocalDateTime recurrenceUntilLocal, Integer recurrenceCount,
                         boolean allowConflicts, String conflictOverrideReason) {}
    record RescheduleCommand(LocalDateTime startLocal, Integer durationMinutes, UUID substituteTeacherMembershipId,
                             String roomCode, String reason, boolean allowConflicts, String conflictOverrideReason, long expectedVersion) {}
    record CancelOccurrenceCommand(String reason, long expectedVersion) {}
    record CancelSeriesCommand(String reason, long expectedVersion) {}
    record ResolvedCommand(ScheduleKind kind, String title, String description, String timezone, DeliveryMode deliveryMode,
                           UUID branchId, UUID batchId, UUID courseId, UUID subjectId, UUID moduleId,
                           UUID primaryTeacherMembershipId, String roomCode, LocalDateTime startLocal, int durationMinutes,
                           RecurrenceFrequency recurrenceFrequency, int recurrenceInterval, Set<DayOfWeek> recurrenceDays,
                           Integer recurrenceDayOfMonth, LocalDateTime recurrenceUntilLocal, Integer recurrenceCount) {}

    record SeriesView(UUID id, ScheduleKind kind, String title, String description, String timezone, DeliveryMode deliveryMode,
                      UUID branchId, UUID batchId, UUID courseId, UUID subjectId, UUID moduleId, UUID primaryTeacherMembershipId,
                      String roomCode, LocalDateTime startLocal, int durationMinutes, RecurrenceFrequency recurrenceFrequency,
                      int recurrenceInterval, Set<DayOfWeek> recurrenceDays, Integer recurrenceDayOfMonth,
                      LocalDateTime recurrenceUntilLocal, Integer recurrenceCount, ScheduleSeriesStatus status,
                      Instant createdAt, long version) {}
    public record OccurrenceView(UUID id, UUID seriesId, UUID classSessionId, ScheduleKind kind, String title, String description,
                                 String timezone, DeliveryMode deliveryMode, UUID branchId, UUID batchId, UUID courseId,
                                 UUID subjectId, UUID moduleId, UUID primaryTeacherMembershipId, UUID effectiveTeacherMembershipId,
                                 String roomCode, Instant originalStartsAt, Instant startsAt, Instant endsAt,
                                 ScheduleOccurrenceStatus status, String exceptionReason, long version) {}
    record SeriesCreateResult(SeriesView series, List<OccurrenceView> occurrences, List<ScheduleConflict> overriddenConflicts) {}
    record SchedulePage<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
}
