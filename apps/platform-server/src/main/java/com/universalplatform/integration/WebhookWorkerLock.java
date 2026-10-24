package com.universalplatform.integration;

import java.sql.PreparedStatement;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Cross-instance worker coordination for webhook deliveries, mirroring scheduling's
 * ScheduleWriteLock: a transaction-scoped advisory lock keyed by tenant id. Must be acquired
 * inside an active transaction.
 */
@Component
class WebhookWorkerLock {
    private final JdbcTemplate jdbc;

    WebhookWorkerLock(JdbcTemplate jdbc) {
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
