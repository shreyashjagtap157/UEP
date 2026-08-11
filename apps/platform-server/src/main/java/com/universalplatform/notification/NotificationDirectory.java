package com.universalplatform.notification;

import com.universalplatform.identity.MembershipDirectory;
import com.universalplatform.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only notification boundary for dashboard composition. */
@Service
public class NotificationDirectory {
    private final TenantContext tenantContext;
    private final MembershipDirectory memberships;
    private final PlatformNotificationRepository notifications;

    NotificationDirectory(TenantContext tenantContext, MembershipDirectory memberships, PlatformNotificationRepository notifications) {
        this.tenantContext = tenantContext;
        this.memberships = memberships;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notifications.countByTenantIdAndMembershipIdAndVisibleInAppTrueAndReadAtIsNull(
                tenantContext.requireTenantId(), memberships.current().membershipId());
    }
}
