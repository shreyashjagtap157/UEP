package com.universalplatform.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ConcurrentInvoicePaymentIntegrationTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID membershipId = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();
    private final UUID invoiceId = UUID.randomUUID();
    private final String subject = "payment-race-" + UUID.randomUUID();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedPayableInvoice() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, ?, 'Payment Race Test', 'ACTIVE')",
                tenantId, "payment-race-" + tenantId);
        jdbc.update("""
                INSERT INTO user_account(id, oidc_subject, display_name, status)
                VALUES (?, ?, 'Payment Race Operator', 'ACTIVE')
                """, userId, subject);
        jdbc.update("""
                INSERT INTO tenant_membership(id, tenant_id, user_id, status)
                VALUES (?, ?, ?, 'ACTIVE')
                """, membershipId, tenantId, userId);
        jdbc.update("""
                INSERT INTO role_definition(id, tenant_id, name, system_managed)
                VALUES (?, ?, 'Payment Race Operator', false)
                """, roleId, tenantId);
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'PAYMENTS_MANAGE')", roleId);
        jdbc.update("""
                INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject)
                VALUES (uuidv7(), ?, ?, ?, 'TENANT', ?)
                """, tenantId, membershipId, roleId, subject);
        jdbc.update("""
                INSERT INTO subscription(id, tenant_id, status, starts_at)
                VALUES (uuidv7(), ?, 'ACTIVE', CURRENT_TIMESTAMP)
                """, tenantId);
        jdbc.update("""
                INSERT INTO entitlement_grant(id, tenant_id, feature_key, enabled)
                VALUES (uuidv7(), ?, 'PAYMENTS', true)
                ON CONFLICT (tenant_id, feature_key) DO UPDATE SET enabled = EXCLUDED.enabled
                """, tenantId);
        jdbc.update("""
                INSERT INTO invoice(
                    id, tenant_id, membership_id, invoice_number, total_amount, paid_amount, currency, status, due_on
                ) VALUES (?, ?, ?, ?, 100.00, 0.00, 'USD', 'ISSUED', CURRENT_DATE + 7)
                """, invoiceId, tenantId, membershipId, "INV-RACE-" + invoiceId);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
        jdbc.update("DELETE FROM user_account WHERE id = ?", userId);
    }

    @Test
    void concurrentPaymentsCannotCollectMoreThanTheInvoiceBalance() throws Exception {
        int concurrency = 2;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        try {
            results.add(submitPayment(executor, ready, start, "settlement-race-a"));
            results.add(submitPayment(executor, ready, start, "settlement-race-b"));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(20, TimeUnit.SECONDS));
            }

            assertThat(statuses).containsExactlyInAnyOrder(200, 400);
            BigDecimal paid = jdbc.queryForObject(
                    "SELECT paid_amount FROM invoice WHERE tenant_id = ? AND id = ?",
                    BigDecimal.class, tenantId, invoiceId);
            Integer capturedCount = jdbc.queryForObject("""
                    SELECT count(*) FROM payment
                    WHERE tenant_id = ? AND invoice_id = ? AND status = 'CAPTURED'
                    """, Integer.class, tenantId, invoiceId);
            BigDecimal capturedTotal = jdbc.queryForObject("""
                    SELECT COALESCE(sum(amount), 0) FROM payment
                    WHERE tenant_id = ? AND invoice_id = ? AND status = 'CAPTURED'
                    """, BigDecimal.class, tenantId, invoiceId);
            Integer receiptCount = jdbc.queryForObject("""
                    SELECT count(*) FROM payment_receipt
                    WHERE tenant_id = ? AND payment_id IN (
                        SELECT id FROM payment WHERE tenant_id = ? AND invoice_id = ? AND status = 'CAPTURED'
                    )
                    """, Integer.class, tenantId, tenantId, invoiceId);

            assertThat(paid).isEqualByComparingTo("60.00");
            assertThat(capturedCount).isEqualTo(1);
            assertThat(capturedTotal).isEqualByComparingTo("60.00");
            assertThat(receiptCount).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Future<Integer> submitPayment(ExecutorService executor, CountDownLatch ready,
                                          CountDownLatch start, String reference) {
        return executor.submit(() -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent payment start");
            }
            String body = """
                    {
                      "amount": 60.00,
                      "currency": "USD",
                      "settlementReference": "%s",
                      "confirmSettlement": true
                    }
                    """.formatted(reference);
            return mvc.perform(post("/api/v1/finance/invoices/{id}/payments", invoiceId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(jwt().jwt(token -> token.subject(subject).claim("tenant_id", tenantId.toString()))))
                    .andReturn().getResponse().getStatus();
        });
    }
}
