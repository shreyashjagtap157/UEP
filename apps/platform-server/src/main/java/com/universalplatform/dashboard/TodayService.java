package com.universalplatform.dashboard;

import com.universalplatform.enrollment.EnrollmentDirectory;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.notification.NotificationDirectory;
import com.universalplatform.organization.OrganizationDirectory;
import com.universalplatform.scheduling.ScheduleDirectory;
import com.universalplatform.scheduling.ScheduleKind;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TodayService {
    private final OrganizationDirectory organizations;
    private final ScheduleDirectory schedules;
    private final NotificationDirectory notifications;
    private final AuthorizationService authorization;
    private final EnrollmentDirectory enrollment;
    private final Clock clock = Clock.systemUTC();

    TodayService(OrganizationDirectory organizations, ScheduleDirectory schedules, NotificationDirectory notifications,
                 AuthorizationService authorization, EnrollmentDirectory enrollment) {
        this.organizations = organizations;
        this.schedules = schedules;
        this.notifications = notifications;
        this.authorization = authorization;
        this.enrollment = enrollment;
    }

    @Transactional(readOnly = true)
    TodayView today() {
        String timezone = organizations.defaultTimezone();
        ZoneId zone = ZoneId.of(timezone);
        Instant now = clock.instant();
        LocalDate date = now.atZone(zone).toLocalDate();
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<ScheduleDirectory.ScheduleItem> items = schedules.range(from, to);
        AuthorizationService.AccessScope manageScope = authorization.currentScope(PermissionKey.SCHEDULE_MANAGE);
        boolean administrative = manageScope.tenantWide() || !manageScope.branchIds().isEmpty();
        long learners = 0;
        if (administrative) {
            LinkedHashSet<UUID> learnerIds = new LinkedHashSet<>();
            items.stream().map(ScheduleDirectory.ScheduleItem::batchId).filter(java.util.Objects::nonNull).distinct()
                    .forEach(batchId -> learnerIds.addAll(enrollment.activeLearnerMembershipIdsForBatch(batchId)));
            learners = learnerIds.size();
        }
        Summary summary = new Summary(
                items.stream().filter(item -> item.kind() == ScheduleKind.CLASS).count(),
                items.stream().filter(item -> item.kind() == ScheduleKind.EXAM).count(),
                learners,
                notifications.unreadCount());
        return new TodayView(date, timezone, now, administrative ? "ADMINISTRATIVE" : "PERSONAL", items, summary);
    }

    record TodayView(LocalDate date, String timezone, Instant generatedAt, String mode,
                     List<ScheduleDirectory.ScheduleItem> schedule, Summary summary) {}
    record Summary(long classes, long exams, long scheduledLearners, long unreadNotifications) {}
}
