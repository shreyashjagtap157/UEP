package com.universalplatform;

import org.flywaydb.core.Flyway;

/**
 * One-shot, explicitly invoked database migration entry point.
 * Never run this from a request-serving Vercel API instance.
 */
public final class DatabaseMigrationRunner {
    private DatabaseMigrationRunner() {
    }

    public static void main(String[] args) {
        String url = required("UEP_DATABASE_MIGRATION_JDBC_URL");
        String username = required("UEP_DATABASE_MIGRATION_USERNAME");
        String password = required("UEP_DATABASE_MIGRATION_PASSWORD");
        if (!url.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException(
                    "UEP_DATABASE_MIGRATION_JDBC_URL must be a PostgreSQL JDBC URL; value is not logged.");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .schemas("public")
                .defaultSchema("public")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .connectRetries(5)
                .load();

        flyway.migrate();
        flyway.validate();
        var current = flyway.info().current();
        String currentVersion = current == null || current.getVersion() == null
                ? "none" : current.getVersion().getVersion();
        System.out.println("UEP Flyway migration and validation completed. Current schema version: "
                + currentVersion);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required migration environment variable is missing: " + name);
        }
        return value;
    }
}
