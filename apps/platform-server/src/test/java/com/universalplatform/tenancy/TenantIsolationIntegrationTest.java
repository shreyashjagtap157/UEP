package com.universalplatform.tenancy;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
class TenantIsolationIntegrationTest {
    private static final UUID TENANT_A = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af111");
    private static final UUID TENANT_B = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af222");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'tenant-a', 'Tenant A', 'ACTIVE') ON CONFLICT DO NOTHING", TENANT_A);
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'tenant-b', 'Tenant B', 'ACTIVE') ON CONFLICT DO NOTHING", TENANT_B);
        jdbc.update("INSERT INTO subscription(id, tenant_id, status, starts_at) VALUES (uuidv7(), ?, 'ACTIVE', CURRENT_TIMESTAMP) ON CONFLICT (tenant_id) DO NOTHING", TENANT_A);
        jdbc.update("INSERT INTO subscription(id, tenant_id, status, starts_at) VALUES (uuidv7(), ?, 'ACTIVE', CURRENT_TIMESTAMP) ON CONFLICT (tenant_id) DO NOTHING", TENANT_B);
        jdbc.update("INSERT INTO entitlement_grant(id, tenant_id, feature_key, enabled) VALUES (uuidv7(), ?, 'ASSESSMENTS', true) ON CONFLICT (tenant_id, feature_key) DO UPDATE SET enabled = EXCLUDED.enabled", TENANT_A);
        jdbc.update("INSERT INTO entitlement_grant(id, tenant_id, feature_key, enabled) VALUES (uuidv7(), ?, 'ASSESSMENTS', false) ON CONFLICT (tenant_id, feature_key) DO UPDATE SET enabled = EXCLUDED.enabled", TENANT_B);
    }

    @Test
    void entitlementDecisionUsesAuthenticatedTenantNotClientSelectedTenant() throws Exception {
        mvc.perform(get("/api/v1/licensing/entitlements/ASSESSMENTS/decision")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        mvc.perform(get("/api/v1/licensing/entitlements/ASSESSMENTS/decision")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", TENANT_B.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    void missingTenantClaimIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/licensing/entitlements/ASSESSMENTS/decision").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_CONTEXT_REQUIRED"));
    }
}
