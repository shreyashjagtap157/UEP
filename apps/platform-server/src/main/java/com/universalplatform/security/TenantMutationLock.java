package com.universalplatform.security;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serializes tenant-wide identity invariants within the caller's database transaction.
 * The lock is held until that transaction commits or rolls back.
 */
@Service
public class TenantMutationLock {
    private final JdbcTemplate jdbc;

    public TenantMutationLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockTenant(UUID tenantId) {
        boolean sqlite = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                connection -> "SQLite".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()));
        List<Integer> locked;
        if (sqlite) {
            // Hibernate's SQLite dialect stores UUID identifiers as 16-byte binary values.
            byte[] uuid = java.nio.ByteBuffer.allocate(16)
                    .putLong(tenantId.getMostSignificantBits())
                    .putLong(tenantId.getLeastSignificantBits())
                    .array();
            locked = jdbc.query("select id from tenant where id = ?",
                    (resultSet, rowNumber) -> 1, uuid);
        } else {
            locked = jdbc.query("select id from tenant where id = ? for update",
                    (resultSet, rowNumber) -> 1, tenantId);
        }
        if (locked.isEmpty()) {
            throw new IllegalStateException("Tenant does not exist");
        }
    }
}
