package com.universalplatform.identity;

import com.universalplatform.tenancy.LocalTenantBootstrapService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates/reuses the local owner as normal persisted UEP identity data.
 * The application still resolves permissions and tenant context through its
 * existing identity and authorization services.
 */
@Component
@Profile("local-native")
@ConditionalOnProperty(prefix = "platform.local-auth", name = "enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalDevelopmentIdentityBootstrapper implements ApplicationRunner {
    public static final String SUBJECT = "uep-local-demo-owner";
    public static final String EMAIL = "admin@local.uep";
    public static final String DISPLAY_NAME = "UEP Local Development Owner";

    private final LocalTenantBootstrapService tenantBootstrap;
    private final UserAccountRepository users;
    private final TenantMembershipRepository memberships;
    private final RoleAssignmentRepository assignments;
    private final DefaultRoleSeeder roleSeeder;
    private final UUID preferredTenantId;

    public LocalDevelopmentIdentityBootstrapper(
            LocalTenantBootstrapService tenantBootstrap,
            UserAccountRepository users,
            TenantMembershipRepository memberships,
            RoleAssignmentRepository assignments,
            DefaultRoleSeeder roleSeeder,
            @Value("${UEP_LOCAL_TENANT_ID:}") String preferredTenantId) {
        this.tenantBootstrap = tenantBootstrap;
        this.users = users;
        this.memberships = memberships;
        this.assignments = assignments;
        this.roleSeeder = roleSeeder;
        this.preferredTenantId = preferredTenantId == null || preferredTenantId.isBlank()
                ? null : UUID.fromString(preferredTenantId);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UUID tenantId = tenantBootstrap.ensureLocalTenant(preferredTenantId);
        Map<SystemRoleKey, RoleDefinition> systemRoles = roleSeeder.ensureSystemRoles(tenantId);
        RoleDefinition owner = systemRoles.get(SystemRoleKey.ORGANIZATION_OWNER);
        if (owner == null) {
            throw new IllegalStateException("The Organization Owner system role could not be provisioned.");
        }

        Instant now = Instant.now();
        UserAccount user = users.findByOidcSubject(SUBJECT)
                .orElseGet(() -> users.save(new UserAccount(UUID.randomUUID(), SUBJECT, EMAIL, DISPLAY_NAME, now)));
        if (user.status() != UserStatus.ACTIVE) {
            throw new IllegalStateException("The existing local owner user is disabled; refusing to enable it automatically.");
        }
        user.refreshProfile(EMAIL, DISPLAY_NAME, now);

        TenantMembership membership = memberships.findByTenantIdAndUserId(tenantId, user.id())
                .orElseGet(() -> memberships.save(new TenantMembership(
                        UUID.randomUUID(), tenantId, user.id(), null, null, now)));
        if (membership.status() != MembershipStatus.ACTIVE) {
            throw new IllegalStateException("The existing local owner membership is inactive; refusing to reactivate it automatically.");
        }

        boolean hasTenantOwner = assignments.existsByTenantIdAndMembershipIdAndRoleIdAndScopeKind(
                tenantId, membership.id(), owner.id(), AssignmentScopeKind.TENANT);
        if (!hasTenantOwner) {
            assignments.save(new RoleAssignment(
                    UUID.randomUUID(), tenantId, membership.id(), owner.id(),
                    AssignmentScopeKind.TENANT, null, SUBJECT, now));
        }
    }
}
