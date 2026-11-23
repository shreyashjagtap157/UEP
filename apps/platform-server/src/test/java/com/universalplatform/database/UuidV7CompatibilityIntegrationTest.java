package com.universalplatform.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises the database UUID generator used by the V001-V046 schema.
 *
 * The CI database intentionally runs PostgreSQL 17, matching the current
 * managed staging major version. PostgreSQL 17 has no built-in uuidv7(),
 * so Flyway connection initialization must install the RFC 9562 fallback
 * before the first migration is evaluated.
 */
@SpringBootTest
class UuidV7CompatibilityIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayConnectionInitializationProvidesRfc9562UuidV7OnPostgresql17() {
        long lowerTimestamp = Instant.now().minusSeconds(10).toEpochMilli();
        long upperTimestamp = Instant.now().plusSeconds(10).toEpochMilli();
        Set<String> identifiers = new HashSet<>();

        for (int index = 0; index < 128; index++) {
            String identifier = jdbc.queryForObject("SELECT uuidv7()::text", String.class);
            assertThat(identifier).isNotNull();
            String normalized = identifier.toLowerCase(Locale.ROOT);
            assertThat(normalized).matches(
                    "^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

            long timestampMillis = Long.parseLong(normalized.substring(0, 8)
                    + normalized.substring(9, 13), 16);
            assertThat(timestampMillis).isBetween(lowerTimestamp, upperTimestamp);
            identifiers.add(normalized);
        }

        assertThat(identifiers).hasSize(128);
    }
}
