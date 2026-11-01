package com.universalplatform.security;

import com.universalplatform.tenancy.PlatformAdministratorGuard;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Security-module adapter for the tenancy module's platform-administration port.
 * It checks the authenticated Keycloak realm role without introducing a dependency
 * from the identity module back into tenancy.
 */
@Component
class SecurityPlatformAdministratorGuard implements PlatformAdministratorGuard {
    private static final String PLATFORM_SUPER_ADMIN_ROLE = "platform_super_admin";

    private final ActorContext actors;

    SecurityPlatformAdministratorGuard(ActorContext actors) {
        this.actors = actors;
    }

    @Override
    public void requirePlatformSuperAdmin() {
        if (!actors.hasRealmRole(PLATFORM_SUPER_ADMIN_ROLE)) {
            throw new AccessDeniedException("Platform super administrator role required");
        }
    }
}
