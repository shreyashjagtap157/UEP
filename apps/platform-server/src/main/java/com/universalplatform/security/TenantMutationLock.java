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
        List<UUID> locked = jdbc.query(
                "select id from tenant where id = ? for update",
                (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class),
                tenantId);
        if (locked.isEmpty()) {
            throw new IllegalStateException("Tenant does not exist");
        }
    }
}
