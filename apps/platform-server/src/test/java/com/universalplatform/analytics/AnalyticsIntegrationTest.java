package com.universalplatform.analytics;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalyticsIntegrationTest {
    private static final UUID TENANT = UUID.fromString("01901234-8200-7000-8000-000000000001");
    private static final UUID USER = UUID.fromString("01901234-8200-7000-8000-000000000002");
    private static final UUID MEMBER = UUID.fromString("01901234-8200-7000-8000-000000000003");
    private static final UUID ROLE = UUID.fromString("01901234-8200-7000-8000-000000000004");
    private static final String SUBJECT = "analytics-operator";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'analytics-test', 'Analytics Test', 'ACTIVE')", TENANT);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, display_name, status) VALUES (?, ?, 'Analytics Operator', 'ACTIVE')", USER, SUBJECT);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", MEMBER, TENANT, USER);
        jdbc.update("INSERT INTO role_definition(id, tenant_id, name, system_managed) VALUES (?, ?, 'Analytics Operator', false)", ROLE, TENANT);
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'OPERATIONS_VIEW')", ROLE);
        jdbc.update("INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject) VALUES (uuidv7(), ?, ?, ?, 'TENANT', ?)", TENANT, MEMBER, ROLE, SUBJECT);
        jdbc.update("""
                INSERT INTO notification_outbox(
                    id, tenant_id, event_type, aggregate_type, aggregate_id,
                    title, body, priority, deduplication_key, dead_lettered_at
                ) VALUES (
                    uuidv7(), ?, 'CLASS_SCHEDULED', 'class_session', uuidv7(),
                    'Dead-letter test', 'Delivery failed', 'NORMAL', 'analytics-dead-letter-test', CURRENT_TIMESTAMP
                )
                """, TENANT);
    }

    @Test
    void operationsReportCountsDeadLetteredOutboxEvents() throws Exception {
        mvc.perform(get("/api/v1/analytics/operations")
                        .param("from", "2026-10-01T00:00:00Z")
                        .param("to", "2026-11-01T00:00:00Z")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationDeadLetters").value(1));
    }
}
