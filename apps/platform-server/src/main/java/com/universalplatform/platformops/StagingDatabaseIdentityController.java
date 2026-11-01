package com.universalplatform.platformops;

import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Staging-only guard that proves the running API is connected to the operator-allowlisted
 * PostgreSQL host and database before an automated staging seeder is permitted to write.
 * It deliberately exposes only non-secret connection identity and is permission-gated.
 */
@RestController
@RequestMapping("/api/v1/platform")
@ConditionalOnProperty(name = "platform.staging.database-identity.enabled", havingValue = "true")
class StagingDatabaseIdentityController {
    private final DataSource dataSource;
    private final AuthorizationService authorization;
    private final String expectedHost;
    private final String expectedDatabase;

    StagingDatabaseIdentityController(
            DataSource dataSource,
            AuthorizationService authorization,
            @Value("${platform.staging.database-identity.expected-host:}") String expectedHost,
            @Value("${platform.staging.database-identity.expected-database:}") String expectedDatabase) {
        this.dataSource = dataSource;
        this.authorization = authorization;
        this.expectedHost = required(expectedHost, "expected database host").toLowerCase(Locale.ROOT);
        this.expectedDatabase = required(expectedDatabase, "expected database name");
    }

    @GetMapping("/staging-database-identity")
    Map<String, Object> verifyDatabaseIdentity() {
        authorization.require(PermissionKey.OPERATIONS_MANAGE);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseIdentity actual = parse(connection.getMetaData().getURL());
            if (!expectedHost.equals(actual.host().toLowerCase(Locale.ROOT))
                    || !expectedDatabase.equals(actual.database())) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Connected database does not match the configured staging identity");
            }
            return Map.of(
                    "verified", true,
                    "databaseHost", actual.host(),
                    "databaseName", actual.database());
        } catch (SQLException | IllegalArgumentException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to verify the configured staging database identity");
        }
    }

    private static DatabaseIdentity parse(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("A PostgreSQL JDBC URL is required");
        }
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        String host = uri.getHost();
        String path = uri.getPath();
        if (host == null || path == null || path.length() < 2 || path.substring(1).contains("/")) {
            throw new IllegalArgumentException("PostgreSQL JDBC URL does not identify one database");
        }
        return new DatabaseIdentity(host, path.substring(1));
    }

    private static String required(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException("Staging database identity " + label + " must be configured");
        }
        return normalized;
    }

    private record DatabaseIdentity(String host, String database) {}
}
