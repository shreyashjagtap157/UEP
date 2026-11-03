package com.universalplatform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.universalplatform.BackgroundWorkerConfiguration;
import com.universalplatform.PlatformServerApplication;
import com.universalplatform.security.TenantMutationLock;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.profiles.active=local-sqlite",
        "UEP_SQLITE_JDBC_URL=jdbc:sqlite:target/uep-sqlite-qualification.db",
        "PLATFORM_DEV_MODE=true",
        "platform.background-workers.enabled=false"
})
class SQLiteLocalQualificationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ApplicationContext applicationContext;
    @Autowired TenantRepository tenants;
    @Autowired TenantMutationLock tenantMutationLock;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void localSqliteProfileDisablesSchedulingAndEveryBackgroundWorker() {
        assertThat(applicationContext.getEnvironment().getProperty("spring.jpa.properties.hibernate.hbm2ddl.jdbc_metadata_extraction_strategy"))
                .isEqualTo("individually");
        assertThat(applicationContext.getEnvironment().getProperty("platform.background-workers.enabled", Boolean.class))
                .isFalse();
        assertThat(applicationContext.getBeansOfType(BackgroundWorkerConfiguration.class)).isEmpty();
        assertThat(applicationContext.containsBean("announcementPublishingWorker")).isFalse();
        assertThat(applicationContext.containsBean("contentRetentionWorker")).isFalse();
        assertThat(applicationContext.containsBean("storageCleanupWorker")).isFalse();
        assertThat(applicationContext.containsBean("uploadCleanupWorker")).isFalse();
        assertThat(applicationContext.containsBean("webhookEventFanoutWorker")).isFalse();
        assertThat(applicationContext.containsBean("webhookWorker")).isFalse();
        assertThat(applicationContext.containsBean("messagingRetentionWorker")).isFalse();
        assertThat(applicationContext.containsBean("notificationEmailWorker")).isFalse();
        assertThat(applicationContext.containsBean("notificationOutboxProcessor")).isFalse();
        assertThat(applicationContext.containsBean("recordingWorker")).isFalse();
        assertThat(applicationContext.containsBean("storageQuotaReconciliationWorker")).isFalse();
    }

    @Test
    void existingSqliteSchemaSupportsPerTableColumnMetadataExtraction() throws Exception {
        var tableNames = jdbc.queryForList(
                "select name from sqlite_master where type = 'table' and name not like 'sqlite_%' order by name",
                String.class);
        assertThat(tableNames).hasSizeGreaterThan(50);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            for (String tableName : tableNames) {
                try (ResultSet columns = metadata.getColumns(null, null, tableName, null)) {
                    assertThat(columns.next()).as("SQLite column metadata for existing table %s", tableName).isTrue();
                }
            }
        }
    }

    @Test
    void existingSqliteDatabaseCanStartASecondRealApplicationContext() {
        try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(PlatformServerApplication.class)
                .run(
                        "--spring.profiles.active=local-sqlite",
                        "--spring.datasource.url=jdbc:sqlite:target/uep-sqlite-qualification.db",
                        "--platform.background-workers.enabled=false",
                        "--server.port=0")) {
            assertThat(restarted.getEnvironment().getProperty(
                    "spring.jpa.properties.hibernate.hbm2ddl.jdbc_metadata_extraction_strategy"))
                    .isEqualTo("individually");
            assertThat(restarted.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"))
                    .isEqualTo("update");
            JdbcTemplate restartedJdbc = restarted.getBean(JdbcTemplate.class);
            Integer existingTables = restartedJdbc.queryForObject(
                    "select count(*) from sqlite_master where type = 'table' and name not like 'sqlite_%'",
                    Integer.class);
            assertThat(existingTables).isGreaterThan(50);
        }
    }

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
