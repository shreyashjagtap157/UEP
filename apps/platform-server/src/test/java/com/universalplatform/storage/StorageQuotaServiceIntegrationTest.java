package com.universalplatform.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class StorageQuotaServiceIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired StorageQuotaService quota;

    private UUID tenantId;

    @BeforeEach
    void createTenant() {
        tenantId = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, ?, 'Storage quota test', 'ACTIVE')",
                tenantId, "storage-quota-" + tenantId);
        jdbc.update("INSERT INTO entitlement_limit(tenant_id, limit_key, hard_limit) VALUES (?, 'STORAGE_BYTES', 1000)",
                tenantId);
        jdbc.update("""
                UPDATE storage_provider_quota
                SET writes_enabled = true, hard_limit_bytes = NULL, max_object_bytes = NULL,
                    reserved_bytes = 0, committed_bytes = 0
                WHERE provider = 'LOCAL'
                """);
    }

    @AfterEach
    void removeTenant() {
        if (tenantId != null) jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
        jdbc.update("""
                UPDATE storage_provider_quota
                SET writes_enabled = true, hard_limit_bytes = NULL, max_object_bytes = NULL,
                    reserved_bytes = 0, committed_bytes = 0
                WHERE provider = 'LOCAL'
                """);
    }

    @Test
    void reservationCommitAndReleaseAreIdempotent() {
        UUID reservation = quota.reserve(tenantId, StorageProviderType.LOCAL, "upload:" + UUID.randomUUID(),
                "lesson.pdf", 512);
        assertThat(reservedBytes()).isEqualTo(512);
        assertThat(committedBytes()).isZero();

        quota.commit(tenantId, reservation, 480, "local://objects/lesson.pdf", "lesson.pdf");
        quota.commit(tenantId, reservation, 480, "local://objects/lesson.pdf", "lesson.pdf");
        assertThat(reservedBytes()).isZero();
        assertThat(committedBytes()).isEqualTo(480);

        quota.release(tenantId, reservation);
        quota.release(tenantId, reservation);
        assertThat(reservedBytes()).isZero();
        assertThat(committedBytes()).isZero();
        assertThat(quota.reconciliationDrift()).isEmpty();
    }

    @Test
    void concurrentReservationsCannotExceedTenantEntitlement() throws Exception {
        jdbc.update("UPDATE entitlement_limit SET hard_limit = 100 WHERE tenant_id = ? AND limit_key = 'STORAGE_BYTES'",
                tenantId);
        int attempts = 20;
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                int index = i;
                tasks.add(() -> {
                    start.await();
                    try {
                        quota.reserve(tenantId, StorageProviderType.LOCAL, "parallel:" + index,
                                "object-" + index, 10);
                        return true;
                    } catch (StorageQuotaException rejected) {
                        return false;
                    }
                });
            }
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> task : tasks) futures.add(pool.submit(task));
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> future : futures) if (future.get()) accepted++;
            assertThat(accepted).isEqualTo(10);
            assertThat(reservedBytes()).isEqualTo(100);
            assertThat(committedBytes()).isZero();
            assertThat(quota.reconciliationDrift()).isEmpty();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void disabledS3AccountRejectsReservationsBeforeObjectProviderUse() {
        assertThatThrownBy(() -> quota.reserve(tenantId, StorageProviderType.S3_COMPATIBLE,
                "r2:" + UUID.randomUUID(), "r2/object", 1024))
                .isInstanceOf(StorageQuotaException.class)
                .hasMessageContaining("disabled by the deployment storage policy");
        assertThat(reservedBytes()).isZero();
        assertThat(committedBytes()).isZero();
    }

    private long reservedBytes() {
        return jdbc.queryForObject("SELECT reserved_bytes FROM storage_tenant_usage WHERE tenant_id = ?",
                Long.class, tenantId) == null ? 0 : jdbc.queryForObject(
                "SELECT reserved_bytes FROM storage_tenant_usage WHERE tenant_id = ?", Long.class, tenantId);
    }

    private long committedBytes() {
        Long value = jdbc.queryForObject("SELECT committed_bytes FROM storage_tenant_usage WHERE tenant_id = ?",
                Long.class, tenantId);
        return value == null ? 0 : value;
    }
}
