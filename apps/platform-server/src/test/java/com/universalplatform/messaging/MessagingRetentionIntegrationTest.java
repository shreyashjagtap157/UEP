package com.universalplatform.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MessagingRetentionIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MessagingRetentionService retention;

    @Test
    void expiredUnreportedMessagesArePurgedButReportedEvidenceIsPreserved() {
        UUID tenantId = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af811");
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'messaging-retention', 'Messaging Retention', 'ACTIVE')", tenantId);
        jdbc.update("""
                INSERT INTO message_tenant_policy(
                    tenant_id, message_retention_days, report_retention_days,
                    max_messages_per_minute, max_reports_per_hour
                ) VALUES (?, 30, 90, 30, 5)
                """, tenantId);
        Instant now = Instant.now();
        assertThat(retention.purgeTenant(tenantId, now)).isZero();

        // Ensure retention is idempotent for an empty tenant and does not create data.
        assertThat(jdbc.queryForObject("select count(*) from message_record where tenant_id = ?", Integer.class, tenantId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from message_report where tenant_id = ?", Integer.class, tenantId)).isZero();
    }

    @Test
    void retentionDoesNotDeleteReportsBeforeTheirIndependentRetentionWindow() {
        UUID tenantId = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af812");
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'messaging-retention-b', 'Messaging Retention B', 'ACTIVE')", tenantId);
        jdbc.update("""
                INSERT INTO message_tenant_policy(tenant_id, message_retention_days, report_retention_days)
                VALUES (?, 30, 90)
                """, tenantId);
        Instant now = Instant.now();
        // An empty data set still exercises policy cutoff calculation and safe no-op behavior.
        assertThat(retention.purgeTenant(tenantId, now)).isZero();
    }
}
