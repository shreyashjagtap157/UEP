package com.universalplatform.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionRevocationIntegrationTest {
    private static final UUID TENANT = UUID.fromString("01901234-2222-7000-8000-000000000001");
    private static final UUID USER = UUID.fromString("01901234-2222-7000-8000-000000000002");
    private static final UUID MEMBERSHIP = UUID.fromString("01901234-2222-7000-8000-000000000003");
    private static final UUID ADMIN_USER = UUID.fromString("01901234-2222-7000-8000-000000000004");
    private static final UUID ADMIN_MEMBERSHIP = UUID.fromString("01901234-2222-7000-8000-000000000005");
    private static final UUID ADMIN_ROLE = UUID.fromString("01901234-2222-7000-8000-000000000006");
    private static final String ADMIN_SUBJECT = "session-admin";
    private static final String SUBJECT = "session-user";
    private static final String SESSION_ID = "browser-session-1";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'session-test', 'Session Test', 'ACTIVE')", TENANT);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, display_name, status) VALUES (?, ?, 'Session User', 'ACTIVE')", USER, SUBJECT);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", MEMBERSHIP, TENANT, USER);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, display_name, status) VALUES (?, ?, 'Session Administrator', 'ACTIVE')", ADMIN_USER, ADMIN_SUBJECT);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", ADMIN_MEMBERSHIP, TENANT, ADMIN_USER);
        jdbc.update("INSERT INTO role_definition(id, tenant_id, name, system_managed) VALUES (?, ?, 'Session Administrator', false)", ADMIN_ROLE, TENANT);
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'SESSIONS_VIEW')", ADMIN_ROLE);
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'SESSIONS_MANAGE')", ADMIN_ROLE);
        jdbc.update("""
                INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject)
                VALUES (uuidv7(), ?, ?, ?, 'TENANT', 'session-test-bootstrap')
                """, TENANT, ADMIN_MEMBERSHIP, ADMIN_ROLE);
    }

    @Test
    void sessionAdministratorCanListAndRevokeAnotherMembersSession() throws Exception {
        mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token.subject(SUBJECT)
                        .claim("tenant_id", TENANT.toString()).claim("sid", "target-browser-session"))))
                .andExpect(status().isOk());

        entityManager.flush();
        UUID sessionRecordId = jdbc.queryForObject(
                "SELECT id FROM platform_session WHERE tenant_id = ? AND membership_id = ? AND oidc_session_id = ?",
                UUID.class, TENANT, MEMBERSHIP, "target-browser-session");

        mvc.perform(get("/api/v1/identity/sessions/memberships/{membershipId}", MEMBERSHIP)
                        .with(jwt().jwt(token -> token.subject(ADMIN_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(sessionRecordId.toString()));

        mvc.perform(delete("/api/v1/identity/sessions/{sessionId}", sessionRecordId)
                        .contentType("application/json")
                        .content("{\"reason\":\"Security incident investigation\"}")
                        .with(jwt().jwt(token -> token.subject(ADMIN_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isNoContent());

        entityManager.flush();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT revocation_reason FROM platform_session WHERE id = ?", String.class, sessionRecordId))
                .isEqualTo("Security incident investigation");

        mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token.subject(SUBJECT)
                        .claim("tenant_id", TENANT.toString()).claim("sid", "target-browser-session"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_ACCESS_DENIED"));
    }

    @Test
    void memberCannotUseAdministrativeSessionListOrRevokeEndpoints() throws Exception {
        mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token.subject(SUBJECT)
                        .claim("tenant_id", TENANT.toString()).claim("sid", "member-owned-session"))))
                .andExpect(status().isOk());
        entityManager.flush();
        UUID sessionRecordId = jdbc.queryForObject(
                "SELECT id FROM platform_session WHERE tenant_id = ? AND membership_id = ? AND oidc_session_id = ?",
                UUID.class, TENANT, MEMBERSHIP, "member-owned-session");

        mvc.perform(get("/api/v1/identity/sessions/memberships/{membershipId}", MEMBERSHIP)
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_ACCESS_DENIED"));

        mvc.perform(delete("/api/v1/identity/sessions/{sessionId}", sessionRecordId)
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_ACCESS_DENIED"));

        entityManager.flush();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT revoked_at IS NULL FROM platform_session WHERE id = ?", Boolean.class, sessionRecordId))
                .isTrue();
    }

    @Test
    void revokedOidcSessionIsDeniedOnSubsequentRequests() throws Exception {
        mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token.subject(SUBJECT)
                        .claim("tenant_id", TENANT.toString()).claim("sid", SESSION_ID).claim("jti", "token-1"))))
                .andExpect(status().isOk());

        entityManager.flush();
        UUID sessionRecordId = jdbc.queryForObject(
                "SELECT id FROM platform_session WHERE tenant_id = ? AND membership_id = ? AND oidc_session_id = ?",
                UUID.class, TENANT, MEMBERSHIP, SESSION_ID);

        mvc.perform(delete("/api/v1/identity/sessions/me/{sessionId}", sessionRecordId)
                        .with(jwt().jwt(token -> token.subject(SUBJECT)
                                .claim("tenant_id", TENANT.toString()).claim("sid", SESSION_ID).claim("jti", "token-2"))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token.subject(SUBJECT)
                        .claim("tenant_id", TENANT.toString()).claim("sid", SESSION_ID).claim("jti", "token-3"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_ACCESS_DENIED"));
    }
}
