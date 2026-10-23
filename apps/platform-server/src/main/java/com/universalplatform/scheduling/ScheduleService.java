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
import org.springframework.data.domain.Pageable;
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
        Pageable cap = PageRequest.of(0, MAX_CALENDAR_ITEMS + 1);
        List<ScheduleOccurrence> visible;
        AuthorizationService.AccessScope manageScope = authorization.currentScope(PermissionKey.SCHEDULE_MANAGE);
        if (manageScope.tenantWide()) {
            visible = occurrenceRepository.findRangeCapped(tenantId, from, to, cap);
            enforceCalendarCap(visible.size());
        } else if (!manageScope.branchIds().isEmpty()) {
            visible = occurrenceRepository.findRangeForBranchesCapped(tenantId, manageScope.branchIds(), from, to, cap);
            enforceCalendarCap(visible.size());
        } else {
            authorization.require(PermissionKey.SCHEDULE_VIEW);
            MembershipDirectory.MembershipReference current = memberships.current();
            Map<UUID, ScheduleOccurrence> unique = new LinkedHashMap<>();
            add(unique, occurrenceRepository.findRangeForTeacherCapped(tenantId, current.membershipId(), from, to, cap));
            Set<UUID> batchIds = enrollment.activeBatchIdsForMembership(current.membershipId());
            if (!batchIds.isEmpty()) add(unique, occurrenceRepository.findRangeForBatchesCapped(tenantId, batchIds, from, to, cap));
            Set<UUID> courseIds = enrollment.activeCourseIdsForMembership(current.membershipId());
            if (!courseIds.isEmpty()) add(unique, occurrenceRepository.findRangeForCoursesCapped(tenantId, courseIds, from, to, cap));
            Set<UUID> subjectIds = enrollment.activeSubjectIdsForMembership(current.membershipId());
            if (!subjectIds.isEmpty()) add(unique, occurrenceRepository.findRangeForSubjectsCapped(tenantId, subjectIds, from, to, cap));
            Set<UUID> moduleIds = enrollment.activeModuleIdsForMembership(current.membershipId());
            if (!moduleIds.isEmpty()) add(unique, occurrenceRepository.findRangeForModulesCapped(tenantId, moduleIds, from, to, cap));
            add(unique, occurrenceRepository.findPublicRangeCapped(tenantId, current.primaryBranchId(), from, to, cap));
            visible = unique.values().stream().sorted(Comparator.comparing(ScheduleOccurrence::startsAt)).toList();
            enforceCalendarCap(visible.size());
        }
        return occurrenceViews(tenantId, visible);
    }

    private static void enforceCalendarCap(int size) {
        if (size > MAX_CALENDAR_ITEMS)
            throw new IllegalArgumentException("Calendar range contains too many events; request a smaller range");
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