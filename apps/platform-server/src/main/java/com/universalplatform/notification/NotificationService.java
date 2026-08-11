package com.universalplatform.notification;

import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.MembershipDirectory;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.TenantContext;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class NotificationService {
    private final TenantContext tenantContext;
    private final MembershipDirectory memberships;
    private final AuthorizationService authorization;
    private final PlatformNotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final NotificationOutboxRepository outbox;
    private final NotificationDeliveryRepository deliveries;
    private final Clock clock = Clock.systemUTC();

    NotificationService(TenantContext tenantContext, MembershipDirectory memberships, AuthorizationService authorization,
                        PlatformNotificationRepository notifications, NotificationPreferenceRepository preferences,
                        NotificationOutboxRepository outbox, NotificationDeliveryRepository deliveries) {
        this.tenantContext = tenantContext;
        this.memberships = memberships;
        this.authorization = authorization;
        this.notifications = notifications;
        this.preferences = preferences;
        this.outbox = outbox;
        this.deliveries = deliveries;
    }

    @Transactional(readOnly = true)
    NotificationPage<NotificationView> mine(int page, int size) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = memberships.current().membershipId();
        Page<PlatformNotification> result = notifications.findAllByTenantIdAndMembershipIdAndVisibleInAppTrue(
                tenantId, membershipId, PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new NotificationPage<>(result.stream().map(NotificationService::view).toList(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    UnreadCount unreadCount() {
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = memberships.current().membershipId();
        return new UnreadCount(notifications.countByTenantIdAndMembershipIdAndVisibleInAppTrueAndReadAtIsNull(tenantId, membershipId));
    }

    @Transactional
    NotificationView markRead(UUID notificationId) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = memberships.current().membershipId();
        PlatformNotification notification = notifications.findByTenantIdAndMembershipIdAndIdAndVisibleInAppTrue(tenantId, membershipId, notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        notification.markRead(clock.instant());
        notifications.flush();
        return view(notification);
    }

    @Transactional(readOnly = true)
    List<PreferenceView> preferences() {
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = memberships.current().membershipId();
        var explicit = preferences.findAllByTenantIdAndIdMembershipId(tenantId, membershipId).stream()
                .collect(java.util.stream.Collectors.toMap(NotificationPreference::eventType, item -> item));
        return java.util.Arrays.stream(NotificationEventType.values())
                .map(type -> {
                    NotificationPreference preference = explicit.get(type.name());
                    return preference == null ? new PreferenceView(type, true, true, 0)
                            : new PreferenceView(type, preference.inAppEnabled(), preference.emailEnabled(), preference.version());
                })
                .sorted(Comparator.comparing(item -> item.eventType().name()))
                .toList();
    }

    @Transactional
    PreferenceView updatePreference(NotificationEventType eventType, boolean inAppEnabled, boolean emailEnabled, Long expectedVersion) {
        if (eventType == null) throw new IllegalArgumentException("Notification event type is required");
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = memberships.current().membershipId();
        NotificationPreference preference = preferences.findByTenantIdAndIdMembershipIdAndIdEventType(tenantId, membershipId, eventType.name())
                .orElse(null);
        if (preference == null) {
            if (expectedVersion != null && expectedVersion != 0) throw new IllegalArgumentException("Preference version is stale");
            preference = preferences.save(new NotificationPreference(tenantId, membershipId, eventType.name(), inAppEnabled, emailEnabled, clock.instant()));
        } else {
            if (expectedVersion != null && preference.version() != expectedVersion) throw new IllegalArgumentException("Preference was modified by another request");
            preference.update(inAppEnabled, emailEnabled, clock.instant());
            preferences.flush();
        }
        return new PreferenceView(eventType, preference.inAppEnabled(), preference.emailEnabled(), preference.version());
    }

    @Transactional(readOnly = true)
    OperationsView operations() {
        authorization.require(PermissionKey.NOTIFICATION_OPERATIONS_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        return new OperationsView(outbox.countByTenantIdAndProcessedAtIsNullAndDeadLetteredAtIsNull(tenantId),
                outbox.countByTenantIdAndDeadLetteredAtIsNotNull(tenantId),
                deliveries.countByTenantIdAndStatusIn(tenantId, List.of(NotificationDeliveryStatus.PENDING, NotificationDeliveryStatus.FAILED)));
    }

    private static NotificationView view(PlatformNotification notification) {
        return new NotificationView(notification.id(), NotificationEventType.valueOf(notification.eventType()), notification.title(),
                notification.body(), notification.resourceType(), notification.resourceId(),
                NotificationPriority.valueOf(notification.priority()), notification.createdAt(), notification.readAt(), notification.version());
    }

    private static int safePage(int page) { return Math.max(0, page); }
    private static int safeSize(int size) { return Math.max(1, Math.min(size, 100)); }

    record NotificationView(UUID id, NotificationEventType eventType, String title, String body,
                            String resourceType, UUID resourceId, NotificationPriority priority,
                            java.time.Instant createdAt, java.time.Instant readAt, long version) {}
    record UnreadCount(long count) {}
    record PreferenceView(NotificationEventType eventType, boolean inAppEnabled, boolean emailEnabled, long version) {}
    record OperationsView(long pendingOutboxEvents, long deadLetteredOutboxEvents, long pendingOrFailedEmailDeliveries) {}
}
