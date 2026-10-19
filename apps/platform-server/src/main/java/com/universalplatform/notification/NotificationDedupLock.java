package com.universalplatform.notification;

import java.sql.PreparedStatement;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Serializes concurrent enqueues of the same deduplication key inside the caller's transaction.
 * Mirrors {@code com.universalplatform.scheduling.ScheduleWriteLock}: a PostgreSQL transactional
 * advisory lock keyed by tenant + key is taken only after a fast-path miss, closing the
 * read-then-insert race without relying on catching a unique-constraint violation (which would
 * poison the joined business transaction). The lock is released automatically at commit/rollback,
 * and the {@code uq_notification_outbox_tenant_dedup} unique constraint remains the final backstop.
 */
@Component
class NotificationDedupLock {
    private final JdbcTemplate jdbc;

    NotificationDedupLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void acquire(UUID tenantId, String deduplicationKey) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, tenantId + ":" + deduplicationKey);
                statement.execute();
            }
            return null;
        });
    }
}
