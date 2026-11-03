package com.universalplatform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.universalplatform.security.TenantMutationLock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.profiles.active=local-sqlite",
        "UEP_SQLITE_JDBC_URL=jdbc:sqlite:target/uep-sqlite-qualification.db",
        "PLATFORM_DEV_MODE=true",
        "PLATFORM_BACKGROUND_WORKERS_ENABLED=false"
})
class SQLiteLocalQualificationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantRepository tenants;
    @Autowired TenantMutationLock tenantMutationLock;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void localProfileStartsAgainstSqliteAndPersistsJpaTenantRows() {
        String sqliteVersion = jdbc.queryForObject("select sqlite_version()", String.class);
        assertThat(sqliteVersion).isNotBlank();

        Integer tenantTableCount = jdbc.queryForObject(
                "select count(*) from sqlite_master where type='table' and name='tenant'",
                Integer.class);
        assertThat(tenantTableCount).isEqualTo(1);

        UUID tenantId = UUID.randomUUID();
        tenants.saveAndFlush(new Tenant(tenantId, "sqlite-" + tenantId.toString().substring(0, 8),
                "SQLite Local Qualification", Instant.now()));

        assertThat(jdbc.queryForObject(
                "select count(*) from tenant where slug = ?", Integer.class,
                "sqlite-" + tenantId.toString().substring(0, 8))).isEqualTo(1);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatCode(() -> transaction.executeWithoutResult(status -> tenantMutationLock.lockTenant(tenantId)))
                .doesNotThrowAnyException();
    }
}
