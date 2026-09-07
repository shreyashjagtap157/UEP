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
        insertSystemRole(TEACHER_ROLE, "TEACHER", "Teacher");
        insertSystemRole(STUDENT_ROLE, "STUDENT", "Student");
        for (String permission : new String[]{"ROLES_VIEW", "ROLES_MANAGE", "ROLES_ASSIGN", "USERS_VIEW", "USERS_MANAGE", "FEDERATION_MANAGE"}) {
            jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, ?)", OWNER_ROLE, permission);
        }
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'ORGANIZATION_VIEW')", TEACHER_ROLE);
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'ORGANIZATION_VIEW')", STUDENT_ROLE);
        assign(OWNER_MEMBERSHIP, OWNER_ROLE, OWNER_SUBJECT);
        assign(DUAL_MEMBERSHIP, TEACHER_ROLE, OWNER_SUBJECT);
        assign(DUAL_MEMBERSHIP, STUDENT_ROLE, OWNER_SUBJECT);
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
    void ownerCanCreateCustomRoleButUnprivilegedMemberCannot() throws Exception {
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
