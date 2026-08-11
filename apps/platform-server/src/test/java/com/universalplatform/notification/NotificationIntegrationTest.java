package com.universalplatform.notification;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationIntegrationTest {
    private static final UUID TENANT = UUID.fromString("01901234-8100-7000-8000-000000000001");
    private static final UUID USER = UUID.fromString("01901234-8100-7000-8000-000000000002");
    private static final UUID MEMBER = UUID.fromString("01901234-8100-7000-8000-000000000003");
    private static final UUID RESOURCE = UUID.fromString("01901234-8100-7000-8000-000000000004");
    private static final UUID OPS_ROLE = UUID.fromString("01901234-8100-7000-8000-000000000005");
    private static final UUID TENANT_2 = UUID.fromString("01901234-8100-7000-8000-000000000006");
    private static final UUID USER_2 = UUID.fromString("01901234-8100-7000-8000-000000000007");
    private static final UUID MEMBER_2 = UUID.fromString("01901234-8100-7000-8000-000000000008");
    private static final String SUBJECT = "notification-user";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationPublisher publisher;
    @Autowired NotificationOutboxProcessor processor;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'notification-test', 'Notification Test', 'ACTIVE')", TENANT);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, email, display_name, status) VALUES (?, ?, 'learner@example.test', 'Learner', 'ACTIVE')", USER, SUBJECT);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", MEMBER, TENANT, USER);
        jdbc.update("INSERT INTO role_definition(id, tenant_id, name, system_managed) VALUES (?, ?, 'Notification Operator', false)", OPS_ROLE, TENANT);
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'NOTIFICATION_OPERATIONS_VIEW')", OPS_ROLE);
        jdbc.update("INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject) VALUES (uuidv7(), ?, ?, ?, 'TENANT', ?)", TENANT, MEMBER, OPS_ROLE, SUBJECT);
    }

    @Test
    void outboxIsIdempotentAndMaterializesInAppNotification() throws Exception {
        NotificationPublisher.NotificationRequest request = new NotificationPublisher.NotificationRequest(
                TENANT, NotificationEventType.CLASS_SCHEDULED, "class_session", RESOURCE,
                "Class scheduled", "Your class is scheduled.", NotificationPriority.IMPORTANT,
                "class_session", RESOURCE, "test:class:" + RESOURCE, null,
                List.of(new NotificationPublisher.Recipient(MEMBER, "learner@example.test")));
        publisher.enqueue(request);
        publisher.enqueue(request);

        UUID outboxId = jdbc.queryForObject("SELECT id FROM notification_outbox WHERE deduplication_key = ?", UUID.class, "test:class:" + RESOURCE);
        processor.processOne(outboxId);

        mvc.perform(get("/api/v1/notifications")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].eventType").value("CLASS_SCHEDULED"));

        mvc.perform(get("/api/v1/notifications/unread-count")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        Integer outboxCount = jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE deduplication_key = ?", Integer.class, "test:class:" + RESOURCE);
        String deliveryStatus = jdbc.queryForObject("SELECT status FROM notification_delivery WHERE tenant_id = ?", String.class, TENANT);
        org.assertj.core.api.Assertions.assertThat(outboxCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(deliveryStatus).isEqualTo("SKIPPED");
    }

    @Test
    void deduplicationAndOperationsAreTenantScoped() throws Exception {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'notification-test-2', 'Notification Test 2', 'ACTIVE')", TENANT_2);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, email, display_name, status) VALUES (?, 'notification-user-2', 'two@example.test', 'Learner Two', 'ACTIVE')", USER_2);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", MEMBER_2, TENANT_2, USER_2);

        String key = "shared-key:" + RESOURCE;
        publisher.enqueue(new NotificationPublisher.NotificationRequest(
                TENANT, NotificationEventType.CLASS_SCHEDULED, "class_session", RESOURCE,
                "Tenant one", "Tenant one event", NotificationPriority.NORMAL, null, null, key, null,
                List.of(new NotificationPublisher.Recipient(MEMBER, "learner@example.test"))));
        publisher.enqueue(new NotificationPublisher.NotificationRequest(
                TENANT_2, NotificationEventType.CLASS_SCHEDULED, "class_session", RESOURCE,
                "Tenant two", "Tenant two event", NotificationPriority.NORMAL, null, null, key, null,
                List.of(new NotificationPublisher.Recipient(MEMBER_2, "two@example.test"))));

        Integer count = jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE deduplication_key = ?", Integer.class, key);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(2);

        UUID ownOutbox = jdbc.queryForObject("SELECT id FROM notification_outbox WHERE tenant_id = ? AND deduplication_key = ?", UUID.class, TENANT, key);
        processor.processOne(ownOutbox);

        mvc.perform(get("/api/v1/notifications/operations")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingOutboxEvents").value(0));
    }

    @Test
    void notificationCanBeMarkedReadOnlyByItsRecipient() throws Exception {
        publisher.enqueue(new NotificationPublisher.NotificationRequest(
                TENANT, NotificationEventType.ANNOUNCEMENT_PUBLISHED, "announcement", RESOURCE,
                "Announcement", "Read me", NotificationPriority.NORMAL, "announcement", RESOURCE,
                "test:announcement:" + RESOURCE, null,
                List.of(new NotificationPublisher.Recipient(MEMBER, "learner@example.test"))));
        UUID outboxId = jdbc.queryForObject("SELECT id FROM notification_outbox WHERE deduplication_key = ?", UUID.class, "test:announcement:" + RESOURCE);
        processor.processOne(outboxId);
        UUID notificationId = jdbc.queryForObject("SELECT id FROM notification WHERE tenant_id = ? AND membership_id = ?", UUID.class, TENANT, MEMBER);

        mvc.perform(post("/api/v1/notifications/" + notificationId + "/read")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        mvc.perform(get("/api/v1/notifications/unread-count")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }
}
