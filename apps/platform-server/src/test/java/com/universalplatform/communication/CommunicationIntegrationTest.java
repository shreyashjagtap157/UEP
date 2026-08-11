package com.universalplatform.communication;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universalplatform.security.TenantExecutionContext;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommunicationIntegrationTest {
    private static final UUID TENANT = UUID.fromString("01901234-8000-7000-8000-000000000001");
    private static final UUID BRANCH = UUID.fromString("01901234-8000-7000-8000-000000000002");
    private static final UUID COURSE = UUID.fromString("01901234-8000-7000-8000-000000000003");
    private static final UUID BATCH = UUID.fromString("01901234-8000-7000-8000-000000000004");
    private static final UUID ADMIN_USER = UUID.fromString("01901234-8000-7000-8000-000000000005");
    private static final UUID ADMIN_MEMBER = UUID.fromString("01901234-8000-7000-8000-000000000006");
    private static final UUID ADMIN_ROLE = UUID.fromString("01901234-8000-7000-8000-000000000007");
    private static final UUID LEARNER1_USER = UUID.fromString("01901234-8000-7000-8000-000000000008");
    private static final UUID LEARNER1_MEMBER = UUID.fromString("01901234-8000-7000-8000-000000000009");
    private static final UUID LEARNER2_USER = UUID.fromString("01901234-8000-7000-8000-000000000010");
    private static final UUID LEARNER2_MEMBER = UUID.fromString("01901234-8000-7000-8000-000000000011");
    private static final String ADMIN_SUBJECT = "communication-admin";
    private static final String LEARNER1_SUBJECT = "communication-learner-1";
    private static final String LEARNER2_SUBJECT = "communication-learner-2";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AnnouncementService announcements;
    @Autowired TenantExecutionContext tenantExecution;
    @Autowired EntityManager entityManager;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'communication-test', 'Communication Test', 'ACTIVE')", TENANT);
        jdbc.update("INSERT INTO branch(id, tenant_id, code, display_name, timezone, status) VALUES (?, ?, 'MAIN', 'Main', 'UTC', 'ACTIVE')", BRANCH, TENANT);
        jdbc.update("INSERT INTO course(id, tenant_id, code, display_name, status) VALUES (?, ?, 'JAVA', 'Java', 'ACTIVE')", COURSE, TENANT);
        jdbc.update("INSERT INTO batch(id, tenant_id, course_id, branch_id, code, display_name, status) VALUES (?, ?, ?, ?, 'JAVA-A', 'Java A', 'ACTIVE')", BATCH, TENANT, COURSE, BRANCH);
        seedUser(ADMIN_USER, ADMIN_MEMBER, ADMIN_SUBJECT, "Announcement Admin", "admin@example.test", BRANCH);
        seedUser(LEARNER1_USER, LEARNER1_MEMBER, LEARNER1_SUBJECT, "Learner One", "learner1@example.test", BRANCH);
        seedUser(LEARNER2_USER, LEARNER2_MEMBER, LEARNER2_SUBJECT, "Learner Two", "learner2@example.test", BRANCH);
        jdbc.update("INSERT INTO role_definition(id, tenant_id, name, system_managed) VALUES (?, ?, 'Announcement Admin', false)", ADMIN_ROLE, TENANT);
        for (String permission : new String[]{"ANNOUNCEMENTS_VIEW", "ANNOUNCEMENTS_MANAGE"}) {
            jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, ?)", ADMIN_ROLE, permission);
        }
        jdbc.update("INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject) VALUES (uuidv7(), ?, ?, ?, 'TENANT', ?)", TENANT, ADMIN_MEMBER, ADMIN_ROLE, ADMIN_SUBJECT);
        enroll(LEARNER1_MEMBER);
    }

    @Test
    void scheduledAnnouncementResolvesAudienceAtPublicationTime() throws Exception {
        String response = mvc.perform(post("/api/v1/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Tomorrow\",\"body\":\"Class information\",\"priority\":\"IMPORTANT\",\"publishAt\":\"2099-01-01T00:00:00Z\",\"acknowledgementRequired\":true,\"publishNow\":false,\"targets\":[{\"kind\":\"BATCH\",\"id\":\"" + BATCH + "\"}]}")
                        .with(jwt().jwt(token -> token.subject(ADMIN_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn().getResponse().getContentAsString();

        UUID announcementId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        enroll(LEARNER2_MEMBER);
        entityManager.flush();
        jdbc.update("UPDATE announcement SET publish_at = CURRENT_TIMESTAMP - interval '1 second' WHERE id = ?", announcementId);
        entityManager.clear();

        tenantExecution.run(TENANT, () -> announcements.publishScheduled(announcementId));

        mvc.perform(get("/api/v1/announcements/me")
                        .with(jwt().jwt(token -> token.subject(LEARNER1_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(announcementId.toString()));

        mvc.perform(get("/api/v1/announcements/me")
                        .with(jwt().jwt(token -> token.subject(LEARNER2_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(announcementId.toString()));

        Integer recipientCount = jdbc.queryForObject("SELECT count(*) FROM announcement_recipient WHERE tenant_id = ? AND announcement_id = ?", Integer.class, TENANT, announcementId);
        Integer outboxRecipientCount = jdbc.queryForObject("SELECT count(*) FROM notification_outbox_recipient r JOIN notification_outbox o ON o.id = r.outbox_id WHERE o.tenant_id = ? AND o.aggregate_id = ?", Integer.class, TENANT, announcementId);
        org.assertj.core.api.Assertions.assertThat(recipientCount).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(outboxRecipientCount).isEqualTo(2);
    }

    private void seedUser(UUID userId, UUID membershipId, String subject, String name, String email, UUID branchId) {
        jdbc.update("INSERT INTO user_account(id, oidc_subject, email, display_name, status) VALUES (?, ?, ?, ?, 'ACTIVE')", userId, subject, email, name);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, primary_branch_id, status) VALUES (?, ?, ?, ?, 'ACTIVE')", membershipId, TENANT, userId, branchId);
    }

    private void enroll(UUID membershipId) {
        jdbc.update("INSERT INTO enrollment(id, tenant_id, membership_id, batch_id, status) VALUES (uuidv7(), ?, ?, ?, 'ENROLLED')", TENANT, membershipId, BATCH);
    }
}
