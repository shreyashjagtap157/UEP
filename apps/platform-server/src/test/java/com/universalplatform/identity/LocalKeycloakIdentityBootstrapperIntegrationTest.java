package com.universalplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.universalplatform.tenancy.LocalKeycloakTenantBootstrapService;
import com.universalplatform.tenancy.TenantDirectory;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.profiles.active=local-sqlite,local-keycloak",
        "UEP_SQLITE_JDBC_URL=jdbc:sqlite:target/uep-local-keycloak-identity.db",
        "PLATFORM_DEV_MODE=true",
        "PLATFORM_BACKGROUND_WORKERS_ENABLED=false",
        "server.port=0"
})
class LocalKeycloakIdentityBootstrapperIntegrationTest {
    @Autowired TenantDirectory tenants;
    @Autowired UserAccountRepository users;
    @Autowired TenantMembershipRepository memberships;
    @Autowired RoleDefinitionRepository roles;
    @Autowired RoleAssignmentRepository assignments;

    @Test
    void localKeycloakProfileBootstrapsTheRealmOwnerIntoTheSameTenantAndRoleModel() {
        UUID tenantId = LocalKeycloakTenantBootstrapService.TENANT_ID;
        UserAccount owner = users.findByOidcSubject(LocalKeycloakIdentityBootstrapper.OWNER_SUBJECT).orElseThrow();
        var membership = memberships.findByTenantIdAndUserId(tenantId, owner.id()).orElseThrow();
        RoleDefinition ownerRole = roles.findByTenantIdAndSystemKey(tenantId, SystemRoleKey.ORGANIZATION_OWNER.name())
                .orElseThrow();

        assertThat(tenants.find(tenantId)).isPresent();
        assertThat(tenants.find(tenantId).orElseThrow().slug())
                .isEqualTo(LocalKeycloakTenantBootstrapService.TENANT_SLUG);
        assertThat(owner.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(owner.email()).isEqualTo(LocalKeycloakIdentityBootstrapper.OWNER_EMAIL);
        assertThat(membership.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(assignments.existsByTenantIdAndMembershipIdAndRoleIdAndScopeKind(
                tenantId, membership.id(), ownerRole.id(), AssignmentScopeKind.TENANT)).isTrue();
        var tenant = tenants.find(tenantId).orElseThrow();
        assertThat(tenant.deploymentPlatform()).isEqualTo("local");
        assertThat(tenant.deploymentStatus()).isEqualTo("active");
    }

    @Test
    void keycloakProfileDoesNotBootstrapTheLocalNativeOwner() {
        assertThat(users.findByOidcSubject("uep-local-demo-owner")).isEmpty();
        assertThat(users.findByOidcSubject(LocalKeycloakIdentityBootstrapper.OWNER_SUBJECT)).isPresent();
    }
}
