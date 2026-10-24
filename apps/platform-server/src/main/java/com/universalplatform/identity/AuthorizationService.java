package com.universalplatform.identity;

import com.universalplatform.security.ActorContext;
import com.universalplatform.security.ApiCredentialAuthorizer;
import com.universalplatform.security.ScopedCredentialPrincipal;
import com.universalplatform.security.TenantContext;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationService implements ApiCredentialAuthorizer {
    public static final String PLATFORM_SUPER_ADMIN_ROLE = "platform_super_admin";

    private final TenantContext tenantContext;
    private final ActorContext actorContext;
    private final IdentitySecurityService identitySecurity;
    private final RolePermissionRepository rolePermissions;
    private final MembershipDirectory membershipDirectory;
    private final OwnershipGuard ownershipGuard;

    AuthorizationService(TenantContext tenantContext, ActorContext actorContext,
                         IdentitySecurityService identitySecurity, RolePermissionRepository rolePermissions,
                         MembershipDirectory membershipDirectory, OwnershipGuard ownershipGuard) {
        this.tenantContext = tenantContext;
        this.actorContext = actorContext;
        this.identitySecurity = identitySecurity;
        this.rolePermissions = rolePermissions;
        this.membershipDirectory = membershipDirectory;
        this.ownershipGuard = ownershipGuard;
    }

    @Override
    public void requireApiManage() {
        require(PermissionKey.API_MANAGE);
    }

    @Override
    public void requireApiView() {
        require(PermissionKey.API_VIEW);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireApiCredentialTarget(UUID membershipId) {
        UUID tenantId = tenantContext.requireTenantId();
        if (actorContext.hasRealmRole(PLATFORM_SUPER_ADMIN_ROLE)) {
            membershipDirectory.requireActive(membershipId);
            return;
        }

        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        UUID actorMembershipId = authentication != null
                && authentication.getPrincipal() instanceof ScopedCredentialPrincipal credential
                ? credential.membershipId()
                : identitySecurity.requireCurrentIdentity().membershipId();
        if (actorMembershipId.equals(membershipId)) {
            membershipDirectory.requireActive(membershipId);
            return;
        }

        ownershipGuard.assertCanManageCredentialsForAnotherMembership(tenantId, actorMembershipId, false);
        membershipDirectory.requireActive(membershipId);
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
    public boolean has(PermissionKey permission) {
        return has(permission, null);
    }

    @Transactional(readOnly = true)
    public boolean has(PermissionKey permission, UUID branchId) {
        if (actorContext.hasRealmRole(PLATFORM_SUPER_ADMIN_ROLE)) return true;
        return currentPermissions(branchId).contains(permission);
    }

    @Transactional(readOnly = true)
    public AccessScope currentScope(PermissionKey permission) {
        if (actorContext.hasRealmRole(PLATFORM_SUPER_ADMIN_ROLE)) return new AccessScope(true, Set.of());
        UUID tenantId = tenantContext.requireTenantId();
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        UUID membershipId = authentication != null && authentication.getPrincipal() instanceof ScopedCredentialPrincipal api
                ? api.membershipId() : identitySecurity.requireCurrentIdentity().membershipId();
        if (currentPermissions(null).contains(permission)) return new AccessScope(true, Set.of());
        Set<UUID> branchIds = Set.copyOf(rolePermissions.findBranchScopeIdsForMembershipPermission(
                tenantId, membershipId, permission.name()));
        return new AccessScope(false, branchIds);
    }

    @Transactional(readOnly = true)
    public Set<PermissionKey> currentPermissions(UUID branchId) {
        if (actorContext.hasRealmRole(PLATFORM_SUPER_ADMIN_ROLE)) {
            return Set.copyOf(EnumSet.allOf(PermissionKey.class));
        }
        UUID tenantId = tenantContext.requireTenantId();
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        UUID membershipId;
        Set<String> scopes;
        boolean scopedCredential = authentication != null
                && authentication.getPrincipal() instanceof ScopedCredentialPrincipal;
        if (scopedCredential) {
            ScopedCredentialPrincipal api = (ScopedCredentialPrincipal) authentication.getPrincipal();
            membershipId = api.membershipId();
            scopes = Set.copyOf(api.scopes());
        } else {
            membershipId = identitySecurity.requireCurrentIdentity().membershipId();
            scopes = Set.of();
        }
        EnumSet<PermissionKey> result = EnumSet.noneOf(PermissionKey.class);
        for (String key : rolePermissions.findPermissionKeysForMembership(tenantId, membershipId, branchId)) {
            result.add(PermissionKey.valueOf(key));
        }
        // Empty scope sets must never be interpreted as unlimited access for an API credential.
        if (scopedCredential) result.removeIf(permission -> !scopes.contains(permission.name()));
        return Set.copyOf(result);
    }

    public record AccessScope(boolean tenantWide, Set<UUID> branchIds) {}
}
