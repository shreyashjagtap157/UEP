package com.universalplatform.identity;

import com.universalplatform.tenancy.LocalKeycloakTenantBootstrapService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds exactly one disposable local OIDC owner matching the user imported by
 * infra/keycloak/education-realm.json. This is not a production JIT-provisioning path.
 */
@Component
@Profile("local-keycloak")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalKeycloakIdentityBootstrapper implements ApplicationRunner {
    public static final String OWNER_SUBJECT = "47bcafc8-b3ee-4c84-920f-0c21f4468c55";
    public static final String OWNER_EMAIL = "owner@local.uep";
    public static final String OWNER_DISPLAY_NAME = "UEP Local Keycloak Owner";
    public static final String OWNER_ASSIGNED_BY = "local-keycloak-bootstrap";

    private final LocalKeycloakTenantBootstrapService tenantBootstrap;
    private final UserAccountRepository users;
    private final TenantMembershipRepository memberships;
    private final RoleAssignmentRepository assignments;
    private final DefaultRoleSeeder roleSeeder;

    public LocalKeycloakIdentityBootstrapper(
            LocalKeycloakTenantBootstrapService tenantBootstrap,
            UserAccountRepository users,
            TenantMembershipRepository memberships,
            RoleAssignmentRepository assignments,
            DefaultRoleSeeder roleSeeder) {
        this.tenantBootstrap = tenantBootstrap;
        this.users = users;
        this.memberships = memberships;
        this.assignments = assignments;
        this.roleSeeder = roleSeeder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UUID tenantId = tenantBootstrap.ensureTenant();
        Map<SystemRoleKey, RoleDefinition> systemRoles = roleSeeder.ensureSystemRoles(tenantId);
        RoleDefinition ownerRole = systemRoles.get(SystemRoleKey.ORGANIZATION_OWNER);
        if (ownerRole == null) {
            throw new IllegalStateException("The Organization Owner system role could not be provisioned for local Keycloak.");
        }

        Instant now = Instant.now();
        UserAccount owner = users.findByOidcSubject(OWNER_SUBJECT)
                .orElseGet(() -> users.save(new UserAccount(
                        UUID.randomUUID(), OWNER_SUBJECT, OWNER_EMAIL, OWNER_DISPLAY_NAME, now)));
        if (owner.status() != UserStatus.ACTIVE) {
            throw new IllegalStateException("The local Keycloak owner is disabled; refusing to enable it automatically.");
        }
        owner.refreshProfile(OWNER_EMAIL, OWNER_DISPLAY_NAME, now);

        TenantMembership membership = memberships.findByTenantIdAndUserId(tenantId, owner.id()).orElse(null);
        if (membership == null) {
            membership = memberships.save(new TenantMembership(
                    UUID.randomUUID(), tenantId, owner.id(), null, null, now));
        } else if (membership.status() != MembershipStatus.ACTIVE) {
            throw new IllegalStateException(
                    "The existing local Keycloak owner membership is inactive; refusing to reactivate it automatically.");
        }

        if (!assignments.existsByTenantIdAndMembershipIdAndRoleIdAndScopeKind(
                tenantId, membership.id(), ownerRole.id(), AssignmentScopeKind.TENANT)) {
            assignments.save(new RoleAssignment(
                    UUID.randomUUID(), tenantId, membership.id(), ownerRole.id(),
                    AssignmentScopeKind.TENANT, null, OWNER_ASSIGNED_BY, now));
        }
    }
}
