package com.universalplatform;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;

/**
 * One-shot, explicitly invoked database migration entry point.
 * Never run this from a request-serving Vercel API instance.
 *
 * Flyway runs as the schema-owner role. After migrations, this runner grants
 * CRUD access to a separate non-owner application role and configures default
 * privileges so subsequent migrations preserve that role boundary.
 */
public final class DatabaseMigrationRunner {
    private DatabaseMigrationRunner() {
    }

    public static void main(String[] args) throws SQLException {
        String url = required("UEP_DATABASE_MIGRATION_JDBC_URL");
        String username = required("UEP_DATABASE_MIGRATION_USERNAME");
        String password = required("UEP_DATABASE_MIGRATION_PASSWORD");
        String runtimeUsername = required("UEP_DATABASE_RUNTIME_USERNAME");
        if (!url.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException(
                    "UEP_DATABASE_MIGRATION_JDBC_URL must be a PostgreSQL JDBC URL; value is not logged.");
        }
        if (username.equals(runtimeUsername)) {
            throw new IllegalArgumentException(
                    "Migration and runtime database roles must be different identities.");
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
        grantRuntimePrivileges(url, username, password, runtimeUsername);

        var current = flyway.info().current();
        String currentVersion = current == null || current.getVersion() == null
                ? "none" : current.getVersion().getVersion();
        System.out.println("UEP Flyway migration and validation completed. Current schema version: "
                + currentVersion + ". Least-privilege runtime grants verified for role: " + runtimeUsername);
    }

    private static void grantRuntimePrivileges(
            String url, String migrationUsername, String migrationPassword, String runtimeUsername)
            throws SQLException {
        String runtimeRole = quoteIdentifier(runtimeUsername);
        String migrationRole = quoteIdentifier(migrationUsername);

        try (Connection connection = DriverManager.getConnection(url, migrationUsername, migrationPassword)) {
            validateRuntimeRole(connection, runtimeUsername);

            String database = connection.getCatalog();
            if (database == null || database.isBlank()) {
                throw new IllegalStateException("Could not determine the connected PostgreSQL database name.");
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("GRANT CONNECT ON DATABASE " + quoteIdentifier(database) + " TO " + runtimeRole);
                statement.execute("GRANT USAGE ON SCHEMA public TO " + runtimeRole);
                statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO "
                        + runtimeRole);
                statement.execute("GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO "
                        + runtimeRole);
                statement.execute("GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO " + runtimeRole);

                statement.execute("ALTER DEFAULT PRIVILEGES FOR ROLE " + migrationRole
                        + " IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO " + runtimeRole);
                statement.execute("ALTER DEFAULT PRIVILEGES FOR ROLE " + migrationRole
                        + " IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO " + runtimeRole);
                statement.execute("ALTER DEFAULT PRIVILEGES FOR ROLE " + migrationRole
                        + " IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO " + runtimeRole);
            }
        }
    }

    private static void validateRuntimeRole(Connection connection, String runtimeUsername) throws SQLException {
        String query = """
                SELECT r.rolcanlogin, r.rolsuper, r.rolcreatedb, r.rolcreaterole,
                       r.rolreplication, r.rolbypassrls,
                       EXISTS (SELECT 1 FROM pg_auth_members m WHERE m.member = r.oid) AS has_role_memberships
                FROM pg_roles r
                WHERE r.rolname = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, runtimeUsername);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                            "Runtime database role does not exist; create it before running migrations.");
                }
                boolean safe = result.getBoolean("rolcanlogin")
                        && !result.getBoolean("rolsuper")
                        && !result.getBoolean("rolcreatedb")
                        && !result.getBoolean("rolcreaterole")
                        && !result.getBoolean("rolreplication")
                        && !result.getBoolean("rolbypassrls")
                        && !result.getBoolean("has_role_memberships");
                if (!safe) {
                    throw new IllegalStateException(
                            "Runtime database role must be a standalone login without administrative or RLS-bypass privileges.");
                }
            }
        }
    }

    private static String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank() || identifier.length() > 63
                || identifier.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid PostgreSQL role or database identifier.");
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required migration environment variable is missing: " + name);
        }
        return value;
    }
}
