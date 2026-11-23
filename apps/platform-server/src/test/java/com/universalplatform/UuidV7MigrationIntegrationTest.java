package com.universalplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class UuidV7MigrationIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void databaseUuidGeneratorProducesRfc9562VersionSevenValues() {
        String value = jdbc.queryForObject("SELECT uuidv7()::text", String.class);

        UUID generated = UUID.fromString(value);
        assertThat(generated.version()).isEqualTo(7);
        assertThat(generated.variant()).isEqualTo(2);

        String hexadecimal = value.replace("-", "");
        long timestampMillis = Long.parseUnsignedLong(hexadecimal.substring(0, 12), 16);
        long nowMillis = Instant.now().toEpochMilli();
        assertThat(timestampMillis).isBetween(nowMillis - 60_000, nowMillis + 60_000);
    }
}
