package com.universalplatform.identity;

import com.universalplatform.security.ActorContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
class IdentityRequestInterceptor implements HandlerInterceptor {
    private final ActorContext actorContext;
    private final IdentitySecurityService identitySecurity;
    private final SessionManagementService sessions;

    IdentityRequestInterceptor(ActorContext actorContext, IdentitySecurityService identitySecurity,
                               SessionManagementService sessions) {
        this.actorContext = actorContext;
        this.identitySecurity = identitySecurity;
        this.sessions = sessions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (actorContext.currentSubject().isEmpty()) return true;
        if (actorContext.hasRealmRole(AuthorizationService.PLATFORM_SUPER_ADMIN_ROLE)) return true;
        ActiveIdentity identity = identitySecurity.requireCurrentIdentity();
        sessions.observeCurrentSession(identity);
        return true;
    }
}
