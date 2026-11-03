package com.universalplatform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
        "spring.profiles.active=local-sqlite,local-native",
        "UEP_SQLITE_JDBC_URL=jdbc:sqlite:target/uep-local-auth-qualification.db",
        "UEP_LOCAL_TENANT_ID=01901234-4444-7000-8000-000000000001",
        "UEP_LOCAL_AUTH_TOKEN=local-auth-integration-test-token",
        "UEP_LOCAL_AUTH_SESSION_ID=01901234-4444-7000-8000-000000000002",
        "UEP_LOCAL_AUTH_EXPIRES_AT=2099-01-01T00:00:00Z",
        "platform.background-workers.enabled=false"
})
@AutoConfigureMockMvc
class LocalDevelopmentAuthenticationIntegrationTest {
    private static final String TOKEN_HEADER = LocalDevelopmentAuthenticationFilter.HEADER;
    private static final String TOKEN = "local-auth-integration-test-token";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void localOwnerUsesTheRealPersistedMembershipAndRole() throws Exception {
        mvc.perform(get("/api/v1/me").header(TOKEN_HEADER, TOKEN).with(loopback()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oidcSubject").value("uep-local-demo-owner"))
                .andExpect(jsonPath("$.displayName").value("UEP Local Development Owner"))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.hasItem("Organization Owner")));

        assertThat(count("select count(*) from tenant where slug = 'uep-local'")).isEqualTo(1);
        assertThat(count("select count(*) from user_account where oidc_subject = 'uep-local-demo-owner'")).isEqualTo(1);
        assertThat(count("""
                select count(*) from tenant_membership m
                join user_account u on u.id = m.user_id
                where u.oidc_subject = 'uep-local-demo-owner' and m.status = 'ACTIVE'
                """)).isEqualTo(1);
        assertThat(count("""
                select count(*) from role_assignment a
                join tenant_membership m on m.id = a.membership_id
                join role_definition r on r.id = a.role_id
                where r.system_key = 'ORGANIZATION_OWNER'
                  and a.scope_kind = 'TENANT'
                  and m.status = 'ACTIVE'
                """)).isEqualTo(1);
    }

    @Test
    void missingOrInvalidLocalTokenDoesNotAuthenticate() throws Exception {
        mvc.perform(get("/api/v1/me").with(loopback()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header(TOKEN_HEADER, "not-the-launcher-token").with(loopback()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void localTokenIsRejectedForNonLoopbackRequests() throws Exception {
        mvc.perform(get("/api/v1/me")
                        .header(TOKEN_HEADER, TOKEN)
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.15");
                            return request;
                        }))
                .andExpect(status().isForbidden());
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static RequestPostProcessor loopback() {
        return request -> {
            request.setRemoteAddr("127.0.0.1");
            return request;
        };
    }
}
