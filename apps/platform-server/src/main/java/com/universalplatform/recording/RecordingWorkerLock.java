package com.universalplatform.recording;

import java.sql.PreparedStatement;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Cross-instance worker coordination for recording jobs, mirroring scheduling's ScheduleWriteLock:
 * a transaction-scoped advisory lock keyed by tenant id. Must be acquired inside an active
 * transaction (the lock is released when that transaction commits or rolls back).
 */
@Component
class RecordingWorkerLock {
    private final JdbcTemplate jdbc;

    RecordingWorkerLock(JdbcTemplate jdbc) {
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
