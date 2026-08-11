package com.universalplatform.scheduling;

import java.sql.PreparedStatement;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ScheduleWriteLock {
    private final JdbcTemplate jdbc;

    ScheduleWriteLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void acquire(UUID tenantId) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, tenantId.toString());
                statement.execute();
            }
            return null;
        });
    }
}
