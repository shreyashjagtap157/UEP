package com.universalplatform.identity;

import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IdentitySecurityService {
    private final TenantContext tenantContext;
    private final ActorContext actorContext;
    private final TenantMembershipRepository memberships;
    private final UserAccountRepository users;

    IdentitySecurityService(TenantContext tenantContext, ActorContext actorContext,
                            TenantMembershipRepository memberships, UserAccountRepository users) {
        this.tenantContext = tenantContext;
        this.actorContext = actorContext;
        this.memberships = memberships;
        this.users = users;
    }

    @Transactional(readOnly = true)
    ActiveIdentity requireCurrentIdentity() {
        return requireActiveIdentity(tenantContext.requireTenantId(), actorContext.requireSubject());
    }

    @Transactional(readOnly = true)
    ActiveIdentity requireActiveIdentity(UUID tenantId, String subject) {
        TenantMembership membership = memberships.findByTenantAndSubject(tenantId, subject)
                .orElseThrow(() -> new IdentityAccessDeniedException("No active membership exists for the authenticated tenant"));
        if (membership.status() != MembershipStatus.ACTIVE) {
            throw new IdentityAccessDeniedException("Tenant membership is not active");
        }
        UserAccount user = users.findById(membership.userId())
                .orElseThrow(() -> new IdentityAccessDeniedException("User account is unavailable"));
        if (user.status() != UserStatus.ACTIVE) {
            throw new IdentityAccessDeniedException("User account is disabled");
        }
        return new ActiveIdentity(user, membership);
    }
}
