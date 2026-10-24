package com.universalplatform.content;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Marks staging-file cleanup after the filesystem operation succeeds. */
@Service
class UploadStagingCleanupService {
    private final JdbcTemplate jdbc;

    UploadStagingCleanupService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCleaned(UUID tenantId, UUID uploadId) {
        jdbc.update("""
                UPDATE upload_session
                SET staging_cleaned_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND id = ? AND staging_cleaned_at IS NULL
                """, tenantId, uploadId);
    }
}
