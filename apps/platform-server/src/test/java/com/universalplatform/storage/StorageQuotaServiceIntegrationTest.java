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
        jdbc.update("DELETE FROM storage_provider_operation_counter WHERE provider = 'S3_COMPATIBLE'");
        jdbc.update("DELETE FROM storage_tenant_operation_counter WHERE provider = 'S3_COMPATIBLE'");
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
        jdbc.update("DELETE FROM storage_provider_operation_counter WHERE provider = 'S3_COMPATIBLE'");
        jdbc.update("DELETE FROM storage_tenant_operation_counter WHERE provider = 'S3_COMPATIBLE'");
        resetS3OperationLimits();
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
        assertThat(releasedBytes(reservation)).isEqualTo(480);
        assertThat(quota.reconciliationDrift()).isEmpty();
    }

    @Test
    void releasedReservationKeyCanBeReusedWithoutLosingReleaseHistory() {
        String key = "recording:" + UUID.randomUUID() + ":MASTER";
        UUID first = quota.reserve(tenantId, StorageProviderType.LOCAL, key, "master.mp4", 200);
        quota.commit(tenantId, first, 180, "local://first/master.mp4", "master.mp4");
        quota.release(tenantId, first);

        UUID second = quota.reserve(tenantId, StorageProviderType.LOCAL, key, "master.mp4", 200);

        assertThat(second).isNotEqualTo(first);
        assertThat(reservedBytes()).isEqualTo(200);
        assertThat(committedBytes()).isZero();
        assertThat(releasedBytes(first)).isEqualTo(180);
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

    @Test
    void providerOperationChargeRechecksReadAndWriteAvailability() {
        setS3OperationLimits(100, 1000, 100, 1000, 100, 1000);

        jdbc.update("""
                UPDATE storage_provider_quota
                SET reads_enabled = false, writes_enabled = true
                WHERE provider = 'S3_COMPATIBLE'
                """);
        assertThatThrownBy(() -> quota.consumeProviderOperations(tenantId, StorageProviderType.S3_COMPATIBLE,
                StorageQuotaService.OperationClass.CLASS_B, 3))
                .isInstanceOf(StorageQuotaException.class)
                .hasMessageContaining("disabled by the deployment storage policy");
        assertThat(providerOperations("CLASS_B", "DAILY")).isZero();

        jdbc.update("""
                UPDATE storage_provider_quota
                SET reads_enabled = true, writes_enabled = false
                WHERE provider = 'S3_COMPATIBLE'
                """);
        assertThatThrownBy(() -> quota.consumeProviderOperations(tenantId, StorageProviderType.S3_COMPATIBLE,
                StorageQuotaService.OperationClass.CLASS_A, 3))
                .isInstanceOf(StorageQuotaException.class)
                .hasMessageContaining("disabled by the deployment storage policy");
        assertThat(providerOperations("CLASS_A", "DAILY")).isZero();
    }

    @Test
    void providerDeleteOperationsRemainBudgetedWhenReadsAndWritesAreDisabled() {
        setS3OperationLimits(3, 3, 30, 300, 30, 300);
        jdbc.update("""
                UPDATE storage_provider_quota
                SET reads_enabled = false, writes_enabled = false
                WHERE provider = 'S3_COMPATIBLE'
                """);

        quota.consumeProviderDeleteOperations(tenantId, StorageProviderType.S3_COMPATIBLE, 3);

        assertThat(providerOperations("CLASS_A", "DAILY")).isEqualTo(3);
        assertThat(providerOperations("CLASS_A", "MONTHLY")).isEqualTo(3);
        assertThat(tenantOperations("CLASS_A")).isEqualTo(3);
        assertThatThrownBy(() -> quota.consumeProviderDeleteOperations(
                tenantId, StorageProviderType.S3_COMPATIBLE, 3))
                .isInstanceOf(StorageQuotaException.class)
                .hasMessageContaining("daily operation budget");
    }

    @Test
    void providerDailyOperationBudgetIsEnforcedAtomically() {
        setS3OperationLimits(6, 90, 100, 1000, 90, 900);

        quota.consumeProviderOperations(tenantId, StorageProviderType.S3_COMPATIBLE,
                StorageQuotaService.OperationClass.CLASS_A, 3);
        quota.consumeProviderOperations(tenantId, StorageProviderType.S3_COMPATIBLE,
                StorageQuotaService.OperationClass.CLASS_A, 3);

        assertThatThrownBy(() -> quota.consumeProviderOperations(tenantId, StorageProviderType.S3_COMPATIBLE,
                StorageQuotaService.OperationClass.CLASS_A, 3))
                .isInstanceOf(StorageQuotaException.class)
                .hasMessageContaining("daily operation budget");
        assertThat(providerOperations("CLASS_A", "DAILY")).isEqualTo(6);
        assertThat(providerOperations("CLASS_A", "MONTHLY")).isEqualTo(6);
    }

    @Test
    void providerMonthlyOperationBudgetIsEnforcedIndependentlyOfDailyBudget() {
        setS3OperationLimits(100, 6, 100, 1000, 100, 1000);

        quota.consumeProviderOperations(tenantId, StorageProviderType.S3_COMPATIBLE,
                StorageQuotaService.OperationClass.CLASS_A, 3);
        quota.consumeProviderOperations(tenantId, StorageProviderType.S3_COMPATIBLE,
                StorageQuotaService.OperationClass.CLASS_A, 3);

        assertThatThrownBy(() -> quota.consumeProviderOperations(tenantId, StorageProviderType.S3_COMPATIBLE,
                StorageQuotaService.OperationClass.CLASS_A, 3))
                .isInstanceOf(StorageQuotaException.class)
                .hasMessageContaining("monthly operation budget");
        assertThat(providerOperations("CLASS_A", "MONTHLY")).isEqualTo(6);
    }

    @Test
    void tenantDailyOperationBudgetPreventsOneTenantExhaustingProviderAllowance() {
        setS3OperationLimits(1000, 30000, 1000, 30000, 6, 1000);

        quota.consumeProviderOperations(tenantId, StorageProviderType.S3_COMPATIBLE,
                StorageQuotaService.OperationClass.CLASS_A, 3);
        quota.consumeProviderOperations(tenantId, StorageProviderType.S3_COMPATIBLE,
                StorageQuotaService.OperationClass.CLASS_A, 3);

        assertThatThrownBy(() -> quota.consumeProviderOperations(tenantId, StorageProviderType.S3_COMPATIBLE,
                StorageQuotaService.OperationClass.CLASS_A, 3))
                .isInstanceOf(StorageQuotaException.class)
                .hasMessageContaining("tenant CLASS_A daily operation budget");
        assertThat(tenantOperations("CLASS_A")).isEqualTo(6);
    }

    @Test
    void concurrentProviderOperationChargesCannotExceedDailyBudget() throws Exception {
        setS3OperationLimits(12, 120, 100, 1000, 12, 1000);
        int attempts = 20;
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                tasks.add(() -> {
                    start.await();
                    try {
                        quota.consumeProviderOperations(tenantId, StorageProviderType.S3_COMPATIBLE,
                                StorageQuotaService.OperationClass.CLASS_A, 3);
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
            assertThat(accepted).isEqualTo(4);
            assertThat(providerOperations("CLASS_A", "DAILY")).isEqualTo(12);
            assertThat(tenantOperations("CLASS_A")).isEqualTo(12);
        } finally {
            pool.shutdownNow();
        }
    }

    private void setS3OperationLimits(long classADaily, long classAMonthly,
                                      long classBDaily, long classBMonthly,
                                      long tenantClassADaily, long tenantClassBDaily) {
        jdbc.update("""
                UPDATE storage_provider_quota
                SET reads_enabled = true, writes_enabled = true,
                    class_a_daily_limit = ?, class_a_monthly_limit = ?,
                    class_b_daily_limit = ?, class_b_monthly_limit = ?,
                    tenant_class_a_daily_limit = ?, tenant_class_b_daily_limit = ?
                WHERE provider = 'S3_COMPATIBLE'
                """, classADaily, classAMonthly, classBDaily, classBMonthly,
                tenantClassADaily, tenantClassBDaily);
    }

    private void resetS3OperationLimits() {
        jdbc.update("""
                UPDATE storage_provider_quota
                SET reads_enabled = false, writes_enabled = false,
                    hard_limit_bytes = 8000000000, max_object_bytes = 1000000000,
                    class_a_daily_limit = 25000, class_a_monthly_limit = 750000,
                    class_b_daily_limit = 250000, class_b_monthly_limit = 7500000,
                    tenant_class_a_daily_limit = 3000, tenant_class_b_daily_limit = 30000,
                    reserved_bytes = 0, committed_bytes = 0
                WHERE provider = 'S3_COMPATIBLE'
                """);
    }

    private long providerOperations(String operationClass, String periodKind) {
        return jdbc.queryForObject("""
                SELECT COALESCE((SELECT consumed FROM storage_provider_operation_counter
                WHERE provider = 'S3_COMPATIBLE' AND operation_class = ? AND period_kind = ?
                AND period_start = CASE WHEN ? = 'DAILY' THEN CURRENT_DATE
                  ELSE date_trunc('month', CURRENT_DATE)::date END), 0)
                """, Long.class, operationClass, periodKind, periodKind);
    }

    private long tenantOperations(String operationClass) {
        return jdbc.queryForObject("""
                SELECT COALESCE((SELECT consumed FROM storage_tenant_operation_counter
                WHERE tenant_id = ? AND provider = 'S3_COMPATIBLE' AND operation_class = ?
                  AND period_start = CURRENT_DATE), 0)
                """, Long.class, tenantId, operationClass);
    }

    private long reservedBytes() {
        return jdbc.queryForObject("""
                SELECT COALESCE((SELECT reserved_bytes FROM storage_tenant_usage WHERE tenant_id = ?), 0)
                """, Long.class, tenantId);
    }

    private long releasedBytes(UUID reservationId) {
        return jdbc.queryForObject("""
                SELECT released_bytes FROM storage_quota_reservation WHERE id = ?
                """, Long.class, reservationId);
    }

    private long committedBytes() {
        return jdbc.queryForObject("""
                SELECT COALESCE((SELECT committed_bytes FROM storage_tenant_usage WHERE tenant_id = ?), 0)
                """, Long.class, tenantId);
    }
}
