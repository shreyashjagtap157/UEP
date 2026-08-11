package com.universalplatform.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private static final String SUBJECT = "session-user";
    private static final String SESSION_ID = "browser-session-1";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'session-test', 'Session Test', 'ACTIVE')", TENANT);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, display_name, status) VALUES (?, ?, 'Session User', 'ACTIVE')", USER, SUBJECT);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", MEMBERSHIP, TENANT, USER);
    }

    @Test
    void revokedOidcSessionIsDeniedOnSubsequentRequests() throws Exception {
        mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token.subject(SUBJECT)
                        .claim("tenant_id", TENANT.toString()).claim("sid", SESSION_ID).claim("jti", "token-1"))))
                .andExpect(status().isOk());

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
