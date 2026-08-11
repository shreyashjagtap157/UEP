package com.universalplatform.identity;

import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationService {
    public static final String PLATFORM_SUPER_ADMIN_ROLE = "platform_super_admin";

    private final TenantContext tenantContext;
    private final ActorContext actorContext;
    private final IdentitySecurityService identitySecurity;
    private final RolePermissionRepository rolePermissions;

    AuthorizationService(TenantContext tenantContext, ActorContext actorContext,
                         IdentitySecurityService identitySecurity, RolePermissionRepository rolePermissions) {
        this.tenantContext = tenantContext;
        this.actorContext = actorContext;
        this.identitySecurity = identitySecurity;
        this.rolePermissions = rolePermissions;
    }

    @Transactional(readOnly = true)
    public void require(PermissionKey permission) {
        require(permission, null);
    }

    @Transactional(readOnly = true)
    public void require(PermissionKey permission, UUID branchId) {
        if (actorContext.hasRealmRole(PLATFORM_SUPER_ADMIN_ROLE)) return;
        Set<PermissionKey> current = currentPermissions(branchId);
        if (!current.contains(permission)) {
            throw new IdentityAccessDeniedException("Permission required: " + permission.name());
        }
    }

    @Transactional(readOnly = true)
    public Set<PermissionKey> currentPermissions(UUID branchId) {
        if (actorContext.hasRealmRole(PLATFORM_SUPER_ADMIN_ROLE)) {
            return Set.copyOf(EnumSet.allOf(PermissionKey.class));
        }
        UUID tenantId = tenantContext.requireTenantId();
        ActiveIdentity identity = identitySecurity.requireCurrentIdentity();
        EnumSet<PermissionKey> result = EnumSet.noneOf(PermissionKey.class);
        for (String key : rolePermissions.findPermissionKeysForMembership(tenantId, identity.membershipId(), branchId)) {
            result.add(PermissionKey.valueOf(key));
        }
        return Set.copyOf(result);
    }
}
