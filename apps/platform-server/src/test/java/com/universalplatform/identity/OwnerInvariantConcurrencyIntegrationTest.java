package com.universalplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OwnerInvariantConcurrencyIntegrationTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID ownerRoleId = UUID.randomUUID();
    private final UUID firstUserId = UUID.randomUUID();
    private final UUID firstMembershipId = UUID.randomUUID();
    private final UUID secondUserId = UUID.randomUUID();
    private final UUID secondMembershipId = UUID.randomUUID();
    private final String firstSubject = "owner-concurrency-a-" + UUID.randomUUID();
    private final String secondSubject = "owner-concurrency-b-" + UUID.randomUUID();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedTwoActiveOwners() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, ?, 'Concurrent Owner Test', 'ACTIVE')",
                tenantId, "owner-race-" + tenantId);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, email, display_name, status) VALUES (?, ?, ?, 'Owner A', 'ACTIVE')",
                firstUserId, firstSubject, firstSubject + "@example.test");
        jdbc.update("INSERT INTO user_account(id, oidc_subject, email, display_name, status) VALUES (?, ?, ?, 'Owner B', 'ACTIVE')",
                secondUserId, secondSubject, secondSubject + "@example.test");
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')",
                firstMembershipId, tenantId, firstUserId);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')",
                secondMembershipId, tenantId, secondUserId);
        jdbc.update("INSERT INTO role_definition(id, tenant_id, system_key, name, system_managed) VALUES (?, ?, 'ORGANIZATION_OWNER', 'Organization Owner', true)",
                ownerRoleId, tenantId);
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'USERS_MANAGE')", ownerRoleId);
        jdbc.update("INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject) VALUES (uuidv7(), ?, ?, ?, 'TENANT', ?)",
                tenantId, firstMembershipId, ownerRoleId, firstSubject);
        jdbc.update("INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject) VALUES (uuidv7(), ?, ?, ?, 'TENANT', ?)",
                tenantId, secondMembershipId, ownerRoleId, secondSubject);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM audit_event WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
        jdbc.update("DELETE FROM user_account WHERE id IN (?, ?)", firstUserId, secondUserId);
    }

    @Test
    void concurrentOwnerDeactivationCannotRemoveBothLastOwners() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> {
                start.await();
                return deactivate(firstSubject, firstMembershipId);
            });
            Future<Integer> second = executor.submit(() -> {
                start.await();
                return deactivate(secondSubject, secondMembershipId);
            });

            start.countDown();
            int firstStatus = first.get(10, TimeUnit.SECONDS);
            int secondStatus = second.get(10, TimeUnit.SECONDS);

            assertThat(List.of(firstStatus, secondStatus)).containsExactlyInAnyOrder(200, 409);
            Integer activeOwners = jdbc.queryForObject("""
                    SELECT count(*)
                    FROM role_assignment ra
                    JOIN tenant_membership m
                      ON m.tenant_id = ra.tenant_id AND m.id = ra.membership_id
                    WHERE ra.tenant_id = ?
                      AND ra.role_id = ?
                      AND ra.scope_kind = 'TENANT'
                      AND m.status = 'ACTIVE'
                    """, Integer.class, tenantId, ownerRoleId);
            assertThat(activeOwners).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private int deactivate(String subject, UUID membershipId) throws Exception {
        return mvc.perform(patch("/api/v1/identity/memberships/{membershipId}", membershipId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"expectedVersion\":0}")
                        .with(jwt().jwt(token -> token.subject(subject).claim("tenant_id", tenantId.toString()))))
                .andReturn().getResponse().getStatus();
    }
}
