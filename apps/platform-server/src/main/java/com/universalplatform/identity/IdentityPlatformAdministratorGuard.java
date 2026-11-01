package com.universalplatform.identity;

import com.universalplatform.security.ActorContext;
import com.universalplatform.tenancy.PlatformAdministratorGuard;
import org.springframework.stereotype.Component;

/**
 * Identity-owned adapter that authorizes platform-level tenant administration.
 */
@Component
class IdentityPlatformAdministratorGuard implements PlatformAdministratorGuard {
    private final ActorContext actors;

    IdentityPlatformAdministratorGuard(ActorContext actors) {
        this.actors = actors;
    }

    @Override
    public void requirePlatformSuperAdmin() {
        if (!actors.hasRealmRole(AuthorizationService.PLATFORM_SUPER_ADMIN_ROLE)) {
            throw new IdentityAccessDeniedException("Platform super administrator role required");
        }
    }
}
