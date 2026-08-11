package com.universalplatform.identity;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IdentityBootstrapIntegrationTest {
    private static final UUID TENANT = UUID.fromString("01901234-4444-7000-8000-000000000001");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'bootstrap-test', 'Bootstrap Test', 'ACTIVE')", TENANT);
    }

    @Test
    void platformAdministratorCanBootstrapExactlyOneOwnerAndSystemRoleCatalog() throws Exception {
        var platformAdmin = jwt().jwt(token -> token.subject("platform-operator")
                .claim("tenant_id", TENANT.toString())
                .claim("realm_access", Map.of("roles", List.of("platform_super_admin"))));
        String body = """
                {"subject":"initial-owner","email":"owner@example.test","displayName":"Initial Owner"}
                """;

        mvc.perform(post("/api/v1/identity/bootstrap-owner")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(platformAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[*].name", hasItem("Organization Owner")));

        Integer systemRoleCount = jdbc.queryForObject(
                "SELECT count(*) FROM role_definition WHERE tenant_id = ? AND system_managed = true", Integer.class, TENANT);
        org.assertj.core.api.Assertions.assertThat(systemRoleCount).isEqualTo(SystemRoleKey.values().length);

        mvc.perform(post("/api/v1/identity/bootstrap-owner")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(platformAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTITY_CONFLICT"));
    }
}
