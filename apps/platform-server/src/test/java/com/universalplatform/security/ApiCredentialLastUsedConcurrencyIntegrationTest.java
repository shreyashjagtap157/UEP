package com.universalplatform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ApiCredentialLastUsedConcurrencyIntegrationTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID membershipId = UUID.randomUUID();
    private final UUID credentialId = UUID.randomUUID();
    private final String keyHash = "a".repeat(64);

    @Autowired JdbcTemplate jdbc;
    @Autowired ApiCredentialService credentials;

    @BeforeEach
    void seedCredential() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, ?, 'API Touch Test', 'ACTIVE')",
                tenantId, "api-touch-" + tenantId);
        jdbc.update("""
                INSERT INTO user_account(id, oidc_subject, display_name, status)
                VALUES (?, ?, 'API Touch Test User', 'ACTIVE')
                """, userId, "api-touch-" + userId);
        jdbc.update("""
                INSERT INTO tenant_membership(id, tenant_id, user_id, status)
                VALUES (?, ?, ?, 'ACTIVE')
                """, membershipId, tenantId, userId);
        jdbc.update("""
                INSERT INTO api_credential(id, tenant_id, membership_id, name, key_hash, scopes, created_at)
                VALUES (?, ?, ?, 'concurrent-touch', ?, 'API_VIEW', CURRENT_TIMESTAMP)
                """, credentialId, tenantId, membershipId, keyHash);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
    }

    @Test
    void concurrentUsageTouchesDoNotCauseOptimisticLockFailuresOrChangeCredentialVersion() throws Exception {
        int concurrency = 12;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        ApiCredential detached = new ApiCredential(credentialId, tenantId, membershipId, "concurrent-touch",
                keyHash, "API_VIEW", Instant.now(), null);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < concurrency; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent touch start");
                    }
                    credentials.touch(detached);
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            Long version = jdbc.queryForObject(
                    "SELECT version FROM api_credential WHERE tenant_id = ? AND id = ?",
                    Long.class, tenantId, credentialId);
            java.sql.Timestamp lastUsed = jdbc.queryForObject(
                    "SELECT last_used_at FROM api_credential WHERE tenant_id = ? AND id = ?",
                    java.sql.Timestamp.class, tenantId, credentialId);
            assertThat(version).isZero();
            assertThat(lastUsed).isNotNull();
            assertThat(lastUsed.toInstant()).isAfter(Instant.now().minus(Duration.ofMinutes(1)));
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
