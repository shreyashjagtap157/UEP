package com.universalplatform.identity;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IdentityAuthorizationIntegrationTest {
    private static final UUID TENANT = UUID.fromString("01901234-1111-7000-8000-000000000001");
    private static final UUID OWNER_USER = UUID.fromString("01901234-1111-7000-8000-000000000002");
    private static final UUID OWNER_MEMBERSHIP = UUID.fromString("01901234-1111-7000-8000-000000000003");
    private static final UUID DUAL_USER = UUID.fromString("01901234-1111-7000-8000-000000000004");
    private static final UUID DUAL_MEMBERSHIP = UUID.fromString("01901234-1111-7000-8000-000000000005");
    private static final UUID OWNER_ROLE = UUID.fromString("01901234-1111-7000-8000-000000000006");
    private static final UUID TEACHER_ROLE = UUID.fromString("01901234-1111-7000-8000-000000000007");
    private static final UUID STUDENT_ROLE = UUID.fromString("01901234-1111-7000-8000-000000000008");
    private static final UUID DELEGATED_ADMIN_ROLE = UUID.fromString("01901234-1111-7000-8000-000000000009");
    private static final UUID ORGANIZATION_ADMIN_ROLE = UUID.fromString("01901234-1111-7000-8000-000000000010");
    private static final String OWNER_SUBJECT = "owner-subject";
    private static final String DUAL_SUBJECT = "dual-role-subject";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'identity-test', 'Identity Test', 'ACTIVE')", TENANT);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, email, display_name, status) VALUES (?, ?, 'owner@example.test', 'Owner', 'ACTIVE')", OWNER_USER, OWNER_SUBJECT);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, email, display_name, status) VALUES (?, ?, 'dual@example.test', 'Dual Role', 'ACTIVE')", DUAL_USER, DUAL_SUBJECT);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", OWNER_MEMBERSHIP, TENANT, OWNER_USER);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", DUAL_MEMBERSHIP, TENANT, DUAL_USER);

        insertSystemRole(OWNER_ROLE, "ORGANIZATION_OWNER", "Organization Owner");
        insertSystemRole(ORGANIZATION_ADMIN_ROLE, "ORGANIZATION_ADMINISTRATOR", "Organization Administrator");
        insertSystemRole(TEACHER_ROLE, "TEACHER", "Teacher");
        insertSystemRole(STUDENT_ROLE, "STUDENT", "Student");
        jdbc.update("INSERT INTO role_definition(id, tenant_id, name, system_managed) VALUES (?, ?, 'Delegated Administrator', false)", DELEGATED_ADMIN_ROLE, TENANT);
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'ROLES_ASSIGN')", DELEGATED_ADMIN_ROLE);
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'USERS_MANAGE')", DELEGATED_ADMIN_ROLE);
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'FEDERATION_MANAGE')", DELEGATED_ADMIN_ROLE);
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'API_MANAGE')", DELEGATED_ADMIN_ROLE);
        jdbc.update("""
                INSERT INTO identity_provider(
                    tenant_id, provider_key, issuer, authorization_endpoint, client_id, redirect_uri, scopes, enabled
                ) VALUES (
                    ?, 'trusted-test', 'https://idp.example.test', 'https://idp.example.test/authorize',
                    'uep-test-client', 'https://uep.example.test/callback', 'openid profile email', true
                )
                """, TENANT);
        for (String permission : new String[]{"ROLES_VIEW", "ROLES_MANAGE", "ROLES_ASSIGN", "USERS_VIEW", "USERS_MANAGE", "FEDERATION_MANAGE", "API_MANAGE"}) {
            jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, ?)", OWNER_ROLE, permission);
        }
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'ORGANIZATION_VIEW')", TEACHER_ROLE);
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'ORGANIZATION_VIEW')", STUDENT_ROLE);
        assign(OWNER_MEMBERSHIP, OWNER_ROLE, OWNER_SUBJECT);
        assign(DUAL_MEMBERSHIP, TEACHER_ROLE, OWNER_SUBJECT);
        assign(DUAL_MEMBERSHIP, STUDENT_ROLE, OWNER_SUBJECT);
        assign(DUAL_MEMBERSHIP, DELEGATED_ADMIN_ROLE, OWNER_SUBJECT);
    }

    @Test
    void onePersonCanHoldTeacherAndStudentRolesSimultaneously() throws Exception {
        mvc.perform(get("/api/v1/me")
                        .with(jwt().jwt(token -> token.subject(DUAL_SUBJECT)
                                .claim("tenant_id", TENANT.toString())
                                .claim("amr", java.util.List.of("pwd", "otp")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("Teacher")))
                .andExpect(jsonPath("$.roles", hasItem("Student")))
                .andExpect(jsonPath("$.authenticationAssurance.multiFactorEvidence").value(true));
    }

    @Test
    void finalActiveOrganizationOwnerCannotBeSuspended() throws Exception {
        mvc.perform(patch("/api/v1/identity/memberships/{membershipId}", OWNER_MEMBERSHIP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"expectedVersion\":0}")
                        .with(jwt().jwt(token -> token.subject(OWNER_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTITY_CONFLICT"));
    }

    @Test
    void nonOwnerWithRoleAssignmentPermissionCannotAssignOrganizationOwner() throws Exception {
        String body = """
                {"membershipId":"%s","roleId":"%s","scopeKind":"TENANT"}
                """.formatted(DUAL_MEMBERSHIP, OWNER_ROLE);
        mvc.perform(post("/api/v1/identity/roles/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(token -> token.subject(DUAL_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_ACCESS_DENIED"));
    }

    @Test
    void nonOwnerCannotProvisionMembershipWithOrganizationOwnerRole() throws Exception {
        String body = """
                {"subject":"escalation-target","email":"escalation@example.test","displayName":"Escalation Target","roleIds":["%s"]}
                """.formatted(OWNER_ROLE);
        mvc.perform(post("/api/v1/identity/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(token -> token.subject(DUAL_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_ACCESS_DENIED"));
    }

    @Test
    void nonOwnerCannotGrantOrganizationOwnerThroughFederationSync() throws Exception {
        String body = """
                {
                  "subject": "federated-escalation",
                  "email": "federated@example.test",
                  "displayName": "Federated Escalation",
                  "groups": ["owners"],
                  "roleMappings": {"owners": "ORGANIZATION_OWNER"},
                  "active": true
                }
                """;
        mvc.perform(post("/api/v1/integrations/identity-providers/trusted-test/synchronize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(token -> token.subject(DUAL_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_ACCESS_DENIED"));
    }

    @Test
    void federationSyncCannotDeactivateTheLastActiveOrganizationOwner() throws Exception {
        String body = """
                {
                  "subject": "owner-subject",
                  "email": "owner@example.test",
                  "displayName": "Owner",
                  "groups": [],
                  "roleMappings": {},
                  "active": false
                }
                """;
        mvc.perform(post("/api/v1/integrations/identity-providers/trusted-test/synchronize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(token -> token.subject(DUAL_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTITY_CONFLICT"));
    }

    @Test
    void nonOwnerCannotAssignOrganizationAdministrator() throws Exception {
        String body = """
                {"membershipId":"%s","roleId":"%s","scopeKind":"TENANT"}
                """.formatted(DUAL_MEMBERSHIP, ORGANIZATION_ADMIN_ROLE);
        mvc.perform(post("/api/v1/identity/roles/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(token -> token.subject(DUAL_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_ACCESS_DENIED"));
    }

    @Test
    void nonOwnerCannotProvisionOrganizationAdministratorMembership() throws Exception {
        String body = """
                {"subject":"escalation-admin","email":"admin-escalation@example.test","displayName":"Escalation Admin","roleIds":["%s"]}
                """.formatted(ORGANIZATION_ADMIN_ROLE);
        mvc.perform(post("/api/v1/identity/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(token -> token.subject(DUAL_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_ACCESS_DENIED"));
    }

    @Test
    void nonOwnerCannotCreateApiCredentialForAnotherMembership() throws Exception {
        String body = """
                {"membershipId":"%s","name":"owner-impersonation-key","scopes":["API_VIEW"]}
                """.formatted(OWNER_MEMBERSHIP);
        mvc.perform(post("/api/v1/integrations/api-credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(token -> token.subject(DUAL_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_ACCESS_DENIED"));
    }

    @Test
    void memberCanCreateApiCredentialForOwnActiveMembership() throws Exception {
        String body = """
                {"membershipId":"%s","name":"personal-reporting-key","scopes":["API_VIEW"]}
                """.formatted(DUAL_MEMBERSHIP);
        mvc.perform(post("/api/v1/integrations/api-credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(token -> token.subject(DUAL_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("personal-reporting-key"))
                .andExpect(jsonPath("$.scopes[0]").value("API_VIEW"))
                .andExpect(jsonPath("$.secret").value(org.hamcrest.Matchers.startsWith("upk_live_")));
    }

    @Test
    void apiCredentialCanManageCredentialsOnlyForItsOwnMembership() throws Exception {
        String createKeyBody = """
                {"membershipId":"%s","name":"api-management-key","scopes":["API_MANAGE"]}
                """.formatted(DUAL_MEMBERSHIP);
        var created = mvc.perform(post("/api/v1/integrations/api-credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createKeyBody)
                        .with(jwt().jwt(token -> token.subject(DUAL_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andReturn();
        String apiKey = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString())
                .path("secret")
                .asText();

        String ownMembershipBody = """
                {"membershipId":"%s","name":"api-key-created-key","scopes":["API_VIEW"]}
                """.formatted(DUAL_MEMBERSHIP);
        mvc.perform(post("/api/v1/integrations/api-credentials")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ownMembershipBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("api-key-created-key"));

        String otherMembershipBody = """
                {"membershipId":"%s","name":"api-key-owner-impersonation","scopes":["API_VIEW"]}
                """.formatted(OWNER_MEMBERSHIP);
        mvc.perform(post("/api/v1/integrations/api-credentials")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(otherMembershipBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_ACCESS_DENIED"));
    }

    @Test
    void ownerCanCreateApiCredentialForAnotherActiveMembership() throws Exception {
        String body = """
                {"membershipId":"%s","name":"managed-service-key","scopes":["API_VIEW"]}
                """.formatted(DUAL_MEMBERSHIP);
        mvc.perform(post("/api/v1/integrations/api-credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(token -> token.subject(OWNER_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("managed-service-key"))
                .andExpect(jsonPath("$.secret").value(org.hamcrest.Matchers.startsWith("upk_live_")));
    }

    @Test
    void ownerCanCreateCustomRoleButMemberWithoutRoleManagementCannot() throws Exception {
        String body = """
                {"name":"Lab Coordinator","description":"Lab operations","permissions":["USERS_VIEW","BRANCHES_VIEW"],"expectedVersion":0}
                """;
        mvc.perform(post("/api/v1/identity/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(token -> token.subject(OWNER_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lab Coordinator"))
                .andExpect(jsonPath("$.systemManaged").value(false))
                .andExpect(jsonPath("$.permissions", hasItem("USERS_VIEW")));

        mvc.perform(post("/api/v1/identity/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unauthorized\",\"permissions\":[]}")
                        .with(jwt().jwt(token -> token.subject(DUAL_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_ACCESS_DENIED"));
    }

    private void insertSystemRole(UUID id, String key, String name) {
        jdbc.update("INSERT INTO role_definition(id, tenant_id, system_key, name, system_managed) VALUES (?, ?, ?, ?, true)",
                id, TENANT, key, name);
    }

    private void assign(UUID membershipId, UUID roleId, String actor) {
        jdbc.update("INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject) VALUES (uuidv7(), ?, ?, ?, 'TENANT', ?)",
                TENANT, membershipId, roleId, actor);
    }
}
