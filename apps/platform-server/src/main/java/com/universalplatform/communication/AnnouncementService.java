package com.universalplatform.communication;

import com.universalplatform.audit.AuditService;
import com.universalplatform.curriculum.CurriculumDirectory;
import com.universalplatform.enrollment.EnrollmentDirectory;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.MembershipDirectory;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.notification.NotificationEventType;
import com.universalplatform.notification.NotificationPriority;
import com.universalplatform.notification.NotificationPublisher;
import com.universalplatform.organization.OrganizationDirectory;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AnnouncementService {
    private final TenantContext tenantContext;
    private final ActorContext actorContext;
    private final AuthorizationService authorization;
    private final MembershipDirectory memberships;
    private final OrganizationDirectory organizations;
    private final CurriculumDirectory curriculum;
    private final EnrollmentDirectory enrollment;
    private final AnnouncementRepository announcements;
    private final AnnouncementTargetRepository targets;
    private final AnnouncementRecipientRepository recipients;
    private final AnnouncementAcknowledgementRepository acknowledgements;
    private final NotificationPublisher notifications;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    AnnouncementService(TenantContext tenantContext, ActorContext actorContext, AuthorizationService authorization,
                        MembershipDirectory memberships, OrganizationDirectory organizations, CurriculumDirectory curriculum,
                        EnrollmentDirectory enrollment, AnnouncementRepository announcements, AnnouncementTargetRepository targets,
                        AnnouncementRecipientRepository recipients, AnnouncementAcknowledgementRepository acknowledgements,
                        NotificationPublisher notifications, AuditService audit) {
        this.tenantContext = tenantContext;
        this.actorContext = actorContext;
        this.authorization = authorization;
        this.memberships = memberships;
        this.organizations = organizations;
        this.curriculum = curriculum;
        this.enrollment = enrollment;
        this.announcements = announcements;
        this.targets = targets;
        this.recipients = recipients;
        this.acknowledgements = acknowledgements;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    AnnouncementPage<AnnouncementView> listAdmin(int page, int size) {
        UUID tenantId = tenantContext.requireTenantId();
        AuthorizationService.AccessScope scope = authorization.currentScope(PermissionKey.ANNOUNCEMENTS_MANAGE);
        PageRequest request = PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Announcement> result;
        if (scope.tenantWide()) {
            result = announcements.findAllByTenantId(tenantId, request);
        } else {
            if (scope.branchIds().isEmpty()) authorization.require(PermissionKey.ANNOUNCEMENTS_MANAGE);
            result = announcements.findAllByTenantIdAndAuthorizationBranchIdIn(tenantId, scope.branchIds(), request);
        }
        return page(result, false, null);
    }

    @Transactional(readOnly = true)
    AnnouncementPage<AnnouncementView> mine(int page, int size) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = memberships.current().membershipId();
        Page<Announcement> result = recipients.findVisibleForMember(tenantId, membershipId, clock.instant(),
                PageRequest.of(safePage(page), safeSize(size)));
        return page(result, true, membershipId);
    }

    @Transactional
    AnnouncementView create(CreateCommand command) {
        ValidatedTargets validated = validateTargets(command.targets());
        authorization.require(PermissionKey.ANNOUNCEMENTS_MANAGE, validated.authorizationBranchId());
        Instant now = clock.instant();
        validateWindow(command.publishAt(), command.expiresAt(), command.publishNow(), now);
        UUID tenantId = tenantContext.requireTenantId();
        UUID id = UUID.randomUUID();
        Instant publishAt = command.publishNow() ? null : command.publishAt();
        Announcement announcement = announcements.save(new Announcement(id, tenantId, validated.authorizationBranchId(),
                required(command.title(), "Announcement title", 240), required(command.body(), "Announcement body", 20000),
                command.priority() == null ? AnnouncementPriority.NORMAL : command.priority(), publishAt,
                command.expiresAt(), command.acknowledgementRequired(), actorContext.requireSubject(), now));
        saveTargets(tenantId, id, validated.targets());
        audit.record(tenantId, actorContext.requireSubject(), "ANNOUNCEMENT_CREATED", "announcement", id.toString());
        if (command.publishNow()) {
            publishInternal(announcement, now, actorContext.requireSubject());
            announcements.flush();
        }
        return view(announcement, validated.targets(), null);
    }

    @Transactional
    AnnouncementView update(UUID id, UpdateCommand command) {
        UUID tenantId = tenantContext.requireTenantId();
        Announcement announcement = announcement(tenantId, id);
        authorization.require(PermissionKey.ANNOUNCEMENTS_MANAGE, announcement.authorizationBranchId());
        ValidatedTargets validated = validateTargets(command.targets());
        authorization.require(PermissionKey.ANNOUNCEMENTS_MANAGE, validated.authorizationBranchId());
        if (announcement.version() != command.expectedVersion()) throw new CommunicationConflictException("Announcement was modified by another request");
        Instant now = clock.instant();
        validateWindow(command.publishAt(), command.expiresAt(), false, now);
        announcement.update(validated.authorizationBranchId(), required(command.title(), "Announcement title", 240), required(command.body(), "Announcement body", 20000),
                command.priority() == null ? AnnouncementPriority.NORMAL : command.priority(), command.publishAt(),
                command.expiresAt(), command.acknowledgementRequired(), now);
        targets.deleteAllByTenantIdAndAnnouncementId(tenantId, id);
        saveTargets(tenantId, id, validated.targets());
        audit.record(tenantId, actorContext.requireSubject(), "ANNOUNCEMENT_UPDATED", "announcement", id.toString());
        announcements.flush();
        return view(announcement, validated.targets(), null);
    }

    @Transactional
    AnnouncementView publish(UUID id, long expectedVersion) {
        UUID tenantId = tenantContext.requireTenantId();
        Announcement announcement = announcement(tenantId, id);
        List<AnnouncementTarget.Target> targetList = targetList(tenantId, id);
        ValidatedTargets validated = validateTargets(targetList);
        authorization.require(PermissionKey.ANNOUNCEMENTS_MANAGE, validated.authorizationBranchId());
        if (announcement.version() != expectedVersion) throw new CommunicationConflictException("Announcement was modified by another request");
        publishInternal(announcement, clock.instant(), actorContext.requireSubject());
        announcements.flush();
        return view(announcement, targetList, null);
    }

    @Transactional
    AnnouncementView cancel(UUID id, String reason, long expectedVersion) {
        UUID tenantId = tenantContext.requireTenantId();
        Announcement announcement = announcement(tenantId, id);
        List<AnnouncementTarget.Target> targetList = targetList(tenantId, id);
        ValidatedTargets validated = validateTargets(targetList);
        authorization.require(PermissionKey.ANNOUNCEMENTS_MANAGE, validated.authorizationBranchId());
        if (announcement.version() != expectedVersion) throw new CommunicationConflictException("Announcement was modified by another request");
        required(reason, "Cancellation reason", 500);
        announcement.cancel(clock.instant());
        audit.record(tenantId, actorContext.requireSubject(), "ANNOUNCEMENT_CANCELLED", "announcement", id.toString());
        announcements.flush();
        return view(announcement, targetList, null);
    }

    @Transactional
    AnnouncementView acknowledge(UUID id) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = memberships.current().membershipId();
        Announcement announcement = announcement(tenantId, id);
        if (announcement.status() != AnnouncementStatus.PUBLISHED || (announcement.expiresAt() != null && !announcement.expiresAt().isAfter(clock.instant()))) {
            throw new CommunicationConflictException("Announcement is not active");
        }
        if (!recipients.existsByTenantIdAndIdAnnouncementIdAndIdMembershipId(tenantId, id, membershipId)) {
            throw new CommunicationNotFoundException("Announcement not found");
        }
        if (!announcement.acknowledgementRequired()) throw new CommunicationConflictException("Announcement does not require acknowledgement");
        AnnouncementAcknowledgement acknowledgement = acknowledgements.findByTenantIdAndIdAnnouncementIdAndIdMembershipId(tenantId, id, membershipId)
                .orElseGet(() -> acknowledgements.save(new AnnouncementAcknowledgement(tenantId, id, membershipId, clock.instant())));
        return view(announcement, targetList(tenantId, id), acknowledgement.acknowledgedAt());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishScheduled(UUID id) {
        Announcement announcement = announcements.lockById(id).orElse(null);
        if (announcement == null || announcement.status() != AnnouncementStatus.SCHEDULED) return;
        Instant now = clock.instant();
        if (announcement.publishAt() == null || announcement.publishAt().isAfter(now)) return;
        publishInternal(announcement, now, announcement.createdBySubject());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireScheduled(UUID id) {
        Announcement announcement = announcements.lockById(id).orElse(null);
        if (announcement == null || announcement.status() != AnnouncementStatus.PUBLISHED) return;
        Instant now = clock.instant();
        if (announcement.expiresAt() != null && !announcement.expiresAt().isAfter(now)) announcement.expire(now);
    }

    private void publishInternal(Announcement announcement, Instant now, String actorSubject) {
        if (announcement.expiresAt() != null && !announcement.expiresAt().isAfter(now)) throw new CommunicationConflictException("Announcement expiration must be in the future");
        UUID tenantId = announcement.tenantId();
        List<AnnouncementTarget.Target> targetList = targetList(tenantId, announcement.id());
        Set<UUID> audience = resolveAudience(targetList);
        recipients.deleteAllByTenantIdAndIdAnnouncementId(tenantId, announcement.id());
        recipients.saveAll(audience.stream().map(member -> new AnnouncementRecipient(tenantId, announcement.id(), member, now)).toList());
        announcement.publish(now);
        Map<UUID, MembershipDirectory.MembershipReference> refs = memberships.references(audience);
        Set<NotificationPublisher.Recipient> notificationRecipients = refs.values().stream()
                .map(ref -> new NotificationPublisher.Recipient(ref.membershipId(), ref.email()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        notifications.enqueue(new NotificationPublisher.NotificationRequest(tenantId, NotificationEventType.ANNOUNCEMENT_PUBLISHED,
                "announcement", announcement.id(), announcement.title(), notificationBody(announcement.body()), priority(announcement.priority()),
                "announcement", announcement.id(), "ANNOUNCEMENT_PUBLISHED:" + announcement.id() + ":" + announcement.version(),
                null, notificationRecipients));
        audit.record(tenantId, actorSubject, "ANNOUNCEMENT_PUBLISHED", "announcement", announcement.id().toString());
    }

    private Set<UUID> resolveAudience(List<AnnouncementTarget.Target> targetList) {
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        for (AnnouncementTarget.Target target : targetList) {
            switch (target.kind()) {
                case ORGANIZATION -> result.addAll(memberships.activeMembershipIds());
                case BRANCH -> result.addAll(memberships.activeMembershipIdsForBranch(target.id()));
                case COURSE -> result.addAll(enrollment.activeMembershipIdsForCourse(target.id()));
                case BATCH -> result.addAll(enrollment.activeMembershipIdsForBatch(target.id()));
                case SUBJECT -> {
                    var subject = curriculum.requireSubject(target.id());
                    result.addAll(enrollment.activeMembershipIdsForSubject(target.id(), subject.courseId()));
                }
                case ROLE -> result.addAll(memberships.activeMembershipIdsForRole(target.id()));
                case MEMBERSHIP -> result.add(memberships.requireActive(target.id()).membershipId());
            }
        }
        return Set.copyOf(result);
    }

    private ValidatedTargets validateTargets(List<AnnouncementTarget.Target> targetList) {
        if (targetList == null || targetList.isEmpty()) throw new IllegalArgumentException("At least one announcement target is required");
        if (targetList.size() > 100) throw new IllegalArgumentException("An announcement may contain at most 100 target selectors");
        LinkedHashMap<String, AnnouncementTarget.Target> unique = new LinkedHashMap<>();
        Set<UUID> branchScopes = new LinkedHashSet<>();
        boolean requiresTenantScope = false;
        boolean organizationTarget = false;
        for (AnnouncementTarget.Target target : targetList) {
            if (target == null || target.kind() == null) throw new IllegalArgumentException("Announcement target kind is required");
            if (target.kind() != AnnouncementTargetKind.ORGANIZATION && target.id() == null) throw new IllegalArgumentException(target.kind() + " target requires an id");
            UUID branchScope = null;
            switch (target.kind()) {
                case ORGANIZATION -> { organizationTarget = true; requiresTenantScope = true; }
                case BRANCH -> { organizations.requireBranch(target.id()); branchScope = target.id(); }
                case BATCH -> branchScope = enrollment.requireBatch(target.id()).branchId();
                case COURSE -> { curriculum.requireCourse(target.id()); requiresTenantScope = true; }
                case SUBJECT -> { curriculum.requireSubject(target.id()); requiresTenantScope = true; }
                case ROLE -> { memberships.requireRole(target.id()); requiresTenantScope = true; }
                case MEMBERSHIP -> {
                    var member = memberships.requireActive(target.id());
                    branchScope = member.primaryBranchId();
                    if (branchScope == null) requiresTenantScope = true;
                }
            }
            if (branchScope != null) branchScopes.add(branchScope);
            unique.putIfAbsent(target.kind() + ":" + target.id(), target);
        }
        if (organizationTarget && unique.size() > 1) throw new IllegalArgumentException("Organization-wide target cannot be combined with narrower targets");
        UUID authBranch = !requiresTenantScope && branchScopes.size() == 1 ? branchScopes.iterator().next() : null;
        if (branchScopes.size() > 1) authBranch = null;
        return new ValidatedTargets(List.copyOf(unique.values()), authBranch);
    }

    private void saveTargets(UUID tenantId, UUID announcementId, List<AnnouncementTarget.Target> items) {
        targets.saveAll(items.stream().map(target -> new AnnouncementTarget(UUID.randomUUID(), tenantId, announcementId, target)).toList());
    }

    private List<AnnouncementTarget.Target> targetList(UUID tenantId, UUID announcementId) {
        return targets.findAllByTenantIdAndAnnouncementId(tenantId, announcementId).stream()
                .map(item -> new AnnouncementTarget.Target(item.targetKind(), item.targetId())).toList();
    }

    private AnnouncementPage<AnnouncementView> page(Page<Announcement> result, boolean includeAck, UUID membershipId) {
        List<AnnouncementView> items = result.stream().map(item -> {
            Instant ack = includeAck ? acknowledgements.findByTenantIdAndIdAnnouncementIdAndIdMembershipId(item.tenantId(), item.id(), membershipId)
                    .map(AnnouncementAcknowledgement::acknowledgedAt).orElse(null) : null;
            return view(item, targetList(item.tenantId(), item.id()), ack);
        }).toList();
        return new AnnouncementPage<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private static AnnouncementView view(Announcement announcement, List<AnnouncementTarget.Target> targets, Instant acknowledgedAt) {
        return new AnnouncementView(announcement.id(), announcement.title(), announcement.body(), announcement.priority(), announcement.status(),
                announcement.publishAt(), announcement.expiresAt(), announcement.acknowledgementRequired(), announcement.publishedAt(),
                announcement.createdAt(), announcement.version(), targets, acknowledgedAt);
    }

    private Announcement announcement(UUID tenantId, UUID id) {
        return announcements.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new CommunicationNotFoundException("Announcement not found"));
    }

    private static void validateWindow(Instant publishAt, Instant expiresAt, boolean publishNow, Instant now) {
        Instant effectivePublish = publishNow ? now : publishAt;
        if (!publishNow && publishAt != null && !publishAt.isAfter(now)) throw new IllegalArgumentException("Scheduled publication must be in the future");
        if (expiresAt != null && !expiresAt.isAfter(effectivePublish == null ? now : effectivePublish)) {
            throw new IllegalArgumentException("Announcement expiration must be after publication");
        }
    }


    private static String notificationBody(String body) {
        if (body.length() <= 4000) return body;
        return body.substring(0, 3997) + "...";
    }
    private static NotificationPriority priority(AnnouncementPriority priority) {
        return NotificationPriority.valueOf(priority.name());
    }
    private static String required(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + " exceeds " + max + " characters");
        return normalized;
    }
    private static int safePage(int page) { return Math.max(0, page); }
    private static int safeSize(int size) { return Math.max(1, Math.min(size, 100)); }

    record CreateCommand(String title, String body, AnnouncementPriority priority, Instant publishAt, Instant expiresAt,
                         boolean acknowledgementRequired, boolean publishNow, List<AnnouncementTarget.Target> targets) {}
    record UpdateCommand(String title, String body, AnnouncementPriority priority, Instant publishAt, Instant expiresAt,
                         boolean acknowledgementRequired, List<AnnouncementTarget.Target> targets, long expectedVersion) {}
    record ValidatedTargets(List<AnnouncementTarget.Target> targets, UUID authorizationBranchId) {}
    record AnnouncementView(UUID id, String title, String body, AnnouncementPriority priority, AnnouncementStatus status,
                            Instant publishAt, Instant expiresAt, boolean acknowledgementRequired, Instant publishedAt,
                            Instant createdAt, long version, List<AnnouncementTarget.Target> targets, Instant acknowledgedAt) {}
    record AnnouncementPage<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
}
