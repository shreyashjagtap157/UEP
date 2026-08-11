package com.universalplatform.organization;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class OrganizationIsolationIntegrationTest {
    private static final UUID TENANT_A = UUID.fromString("01901234-3333-7000-8000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("01901234-3333-7000-8000-000000000002");
    private static final UUID USER = UUID.fromString("01901234-3333-7000-8000-000000000003");
    private static final UUID MEMBERSHIP = UUID.fromString("01901234-3333-7000-8000-000000000004");
    private static final UUID OWNER_ROLE = UUID.fromString("01901234-3333-7000-8000-000000000005");
    private static final UUID FOREIGN_BRANCH = UUID.fromString("01901234-3333-7000-8000-000000000006");
    private static final String SUBJECT = "organization-owner";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'org-a', 'Org A', 'ACTIVE')", TENANT_A);
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'org-b', 'Org B', 'ACTIVE')", TENANT_B);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, display_name, status) VALUES (?, ?, 'Org Owner', 'ACTIVE')", USER, SUBJECT);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", MEMBERSHIP, TENANT_A, USER);
        jdbc.update("INSERT INTO role_definition(id, tenant_id, system_key, name, system_managed) VALUES (?, ?, 'ORGANIZATION_OWNER', 'Organization Owner', true)", OWNER_ROLE, TENANT_A);
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'BRANCHES_VIEW'), (?, 'BRANCHES_MANAGE')", OWNER_ROLE, OWNER_ROLE);
        jdbc.update("INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject) VALUES (uuidv7(), ?, ?, ?, 'TENANT', ?)", TENANT_A, MEMBERSHIP, OWNER_ROLE, SUBJECT);
        jdbc.update("INSERT INTO branch(id, tenant_id, code, display_name, timezone, status) VALUES (?, ?, 'FOREIGN', 'Foreign Branch', 'UTC', 'ACTIVE')", FOREIGN_BRANCH, TENANT_B);
    }

    @Test
    void branchQueriesAndLookupsRemainTenantScoped() throws Exception {
        mvc.perform(get("/api/v1/organization/branches")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        mvc.perform(post("/api/v1/organization/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MAIN\",\"displayName\":\"Main Campus\",\"timezone\":\"Asia/Kolkata\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MAIN"));

        mvc.perform(get("/api/v1/organization/branches/{branchId}", FOREIGN_BRANCH)
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isNotFound());
    }
}
