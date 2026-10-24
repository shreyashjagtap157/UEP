package com.universalplatform.finance;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FinancePaymentAuthorizationIntegrationTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID membershipId = UUID.randomUUID();
    private final UUID financeRoleId = UUID.randomUUID();
    private final String subject = "finance-only-" + UUID.randomUUID();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedFinanceOnlyMember() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, ?, 'Finance Authorization Test', 'ACTIVE')",
                tenantId, "finance-payment-auth-" + tenantId);
        jdbc.update("""
                INSERT INTO user_account(id, oidc_subject, display_name, status)
                VALUES (?, ?, 'Finance-Only Operator', 'ACTIVE')
                """, userId, subject);
        jdbc.update("""
                INSERT INTO tenant_membership(id, tenant_id, user_id, status)
                VALUES (?, ?, ?, 'ACTIVE')
                """, membershipId, tenantId, userId);
        jdbc.update("""
                INSERT INTO role_definition(id, tenant_id, name, system_managed)
                VALUES (?, ?, 'Finance Manager Without Payment Authority', false)
                """, financeRoleId, tenantId);
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'FINANCE_MANAGE')", financeRoleId);
        jdbc.update("""
                INSERT INTO role_assignment(
                    id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject
                ) VALUES (uuidv7(), ?, ?, ?, 'TENANT', ?)
                """, tenantId, membershipId, financeRoleId, subject);
    }

    @Test
    void financeManagerWithoutPaymentsManageCannotRecordSettlement() throws Exception {
        String body = """
                {
                  "amount": 25.00,
                  "currency": "USD",
                  "settlementReference": "bank-transfer-reference-20261024",
                  "confirmSettlement": true
                }
                """;

        mvc.perform(post("/api/v1/finance/invoices/{id}/payments", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(token -> token.subject(subject).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_ACCESS_DENIED"));
    }
}
