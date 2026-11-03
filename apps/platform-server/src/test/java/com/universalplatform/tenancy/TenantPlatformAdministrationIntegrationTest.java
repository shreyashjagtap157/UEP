package com.universalplatform.tenancy;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantPlatformAdministrationIntegrationTest {
    private static final UUID TENANT = UUID.fromString("01901234-4444-7000-8000-000000000002");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedTenant() {
        jdbc.update(
                "INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'platform-admin-test', 'Platform Admin Test', 'ACTIVE') ON CONFLICT DO NOTHING",
                TENANT);
    }

    @Test
    void platformSuperAdministratorCanUseCrossTenantDirectoryWithoutTenantClaim() throws Exception {
        mvc.perform(get("/api/v1/tenants")
                        .with(jwt().jwt(token -> token.subject("platform-operator")
                                .claim("realm_access", Map.of("roles", List.of("platform_super_admin"))))))
                .andExpect(status().isOk());
    }

    @Test
    void platformSuperAdministratorReceivesTenantIdWhenCreatingTenant() throws Exception {
        mvc.perform(post("/api/v1/tenants")
                        .with(jwt().jwt(token -> token.subject("platform-operator")
                                .claim("realm_access", Map.of("roles", List.of("platform_super_admin")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "tenant-create-response-test",
                                  "displayName": "Tenant Create Response Test",
                                  "subscriptionTier": "free"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.slug").value("tenant-create-response-test"));
    }

    @Test
    void tenantUserCannotUseCrossTenantDirectoryEvenWithTenantClaim() throws Exception {
        mvc.perform(get("/api/v1/tenants")
                        .with(jwt().jwt(token -> token.subject("tenant-user")
                                .claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isForbidden());
    }
}
