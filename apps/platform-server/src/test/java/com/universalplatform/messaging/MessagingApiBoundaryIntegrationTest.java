package com.universalplatform.messaging;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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
class MessagingApiBoundaryIntegrationTest {
    private static final UUID TENANT_A = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af711");
    private static final UUID TENANT_B = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af722");
    private static final UUID USER_A = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af733");
    private static final UUID USER_A_INACTIVE = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af744");
    private static final UUID USER_B = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af755");
    private static final UUID USER_A_PEER = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af799");
    private static final UUID MEMBERSHIP_A = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af766");
    private static final UUID MEMBERSHIP_A_INACTIVE = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af777");
    private static final UUID MEMBERSHIP_B = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af788");
    private static final UUID MEMBERSHIP_A_PEER = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af7aa");
    private static final UUID USER_MODERATOR = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af7bb");
    private static final UUID MEMBERSHIP_MODERATOR = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af7cc");
    private static final UUID ROLE_MODERATOR = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af7dd");
    private static final UUID ROLE_STUDENT = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af7ee");
    private static final String SUBJECT = "messaging-api-owner";
    private static final String MODERATOR_SUBJECT = "messaging-api-moderator";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EntityManager entityManager;
    @Autowired MessagingRetentionService retention;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'messaging-api-a', 'Messaging API A', 'ACTIVE')", TENANT_A);
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'messaging-api-b', 'Messaging API B', 'ACTIVE')", TENANT_B);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, display_name, status) VALUES (?, ?, 'Messaging API Owner', 'ACTIVE')", USER_A, SUBJECT);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, display_name, status) VALUES (?, 'messaging-api-inactive', 'Inactive Participant', 'ACTIVE')", USER_A_INACTIVE);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, display_name, status) VALUES (?, 'messaging-api-foreign', 'Foreign Participant', 'ACTIVE')", USER_B);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, email, display_name, status) VALUES (?, 'messaging-api-peer', 'peer@example.test', 'Messaging Peer', 'ACTIVE')", USER_A_PEER);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", MEMBERSHIP_A, TENANT_A, USER_A);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'SUSPENDED')", MEMBERSHIP_A_INACTIVE, TENANT_A, USER_A_INACTIVE);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", MEMBERSHIP_B, TENANT_B, USER_B);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", MEMBERSHIP_A_PEER, TENANT_A, USER_A_PEER);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, display_name, status) VALUES (?, ?, 'Messaging Moderator', 'ACTIVE')", USER_MODERATOR, MODERATOR_SUBJECT);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", MEMBERSHIP_MODERATOR, TENANT_A, USER_MODERATOR);
        jdbc.update("INSERT INTO role_definition(id, tenant_id, name, system_managed) VALUES (?, ?, 'Messaging Moderator', false)", ROLE_MODERATOR, TENANT_A);
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'MESSAGING_MODERATE')", ROLE_MODERATOR);
        jdbc.update("""
                INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject)
                VALUES (uuidv7(), ?, ?, ?, 'TENANT', ?)
                """, TENANT_A, MEMBERSHIP_MODERATOR, ROLE_MODERATOR, SUBJECT);
    }

    @Test
    void crossTenantParticipantIsReturnedAsAClientErrorWithoutLeakingIdentityDetails() throws Exception {
        mvc.perform(post("/api/v1/messaging/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Cross-tenant conversation","participantMembershipIds":["%s"]}
                                """.formatted(MEMBERSHIP_B))
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_PARTICIPANT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("A participant membership was not found in the authenticated tenant"));
    }

    @Test
    void sendingMessageEnqueuesANonSenderNotificationWithoutLeakingMessageBody() throws Exception {
        String conversationResponse = mvc.perform(post("/api/v1/messaging/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Project discussion","participantMembershipIds":["%s"]}
                                """.formatted(MEMBERSHIP_A_PEER))
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID conversationId = UUID.fromString(objectMapper.readTree(conversationResponse).get("id").asText());

        String messageResponse = mvc.perform(post("/api/v1/messaging/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Confidential message content\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID messageId = UUID.fromString(objectMapper.readTree(messageResponse).get("id").asText());

        UUID outboxId = jdbc.queryForObject("""
                select id from notification_outbox
                where tenant_id = ? and event_type = 'MESSAGE_RECEIVED' and aggregate_id = ?
                """, UUID.class, TENANT_A, messageId);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select count(*) from notification_outbox_recipient
                where tenant_id = ? and outbox_id = ? and membership_id = ?
                """, Integer.class, TENANT_A, outboxId, MEMBERSHIP_A_PEER)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select count(*) from notification_outbox_recipient
                where tenant_id = ? and outbox_id = ? and membership_id = ?
                """, Integer.class, TENANT_A, outboxId, MEMBERSHIP_A)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select body from notification_outbox where tenant_id = ? and id = ?
                """, String.class, TENANT_A, outboxId))
                .isEqualTo("A participant sent you a message. Sign in to UEP to read it.");

        mvc.perform(get("/api/v1/messaging/conversations")
                        .with(jwt().jwt(token -> token.subject("messaging-api-peer").claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unreadCount").value(1));

        mvc.perform(post("/api/v1/messaging/conversations/" + conversationId + "/read")
                        .with(jwt().jwt(token -> token.subject("messaging-api-peer").claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/messaging/conversations")
                        .with(jwt().jwt(token -> token.subject("messaging-api-peer").claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unreadCount").value(0));
    }

    @Test
    void blockingPreventsDirectMessagesUntilTheBlockIsRemoved() throws Exception {
        String response = mvc.perform(post("/api/v1/messaging/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Blocked direct thread","participantMembershipIds":["%s"]}
                                """.formatted(MEMBERSHIP_A_PEER))
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID conversationId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mvc.perform(put("/api/v1/messaging/blocks/" + MEMBERSHIP_A_PEER)
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/messaging/blocks")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].membershipId").value(MEMBERSHIP_A_PEER.toString()));

        mvc.perform(post("/api/v1/messaging/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Must be blocked\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MESSAGE_CONFLICT"));

        mvc.perform(post("/api/v1/messaging/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Blocked direct thread","participantMembershipIds":["%s"]}
                                """.formatted(MEMBERSHIP_A_PEER))
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MESSAGE_CONFLICT"));

        mvc.perform(delete("/api/v1/messaging/blocks/" + MEMBERSHIP_A_PEER)
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/messaging/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Allowed after unblock\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk());
    }

    @Test
    void reportCanBeSubmittedAndModeratorActionRedactsTheReportedMessage() throws Exception {
        String conversationResponse = mvc.perform(post("/api/v1/messaging/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Report test","participantMembershipIds":["%s"]}
                                """.formatted(MEMBERSHIP_A_PEER))
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID conversationId = UUID.fromString(objectMapper.readTree(conversationResponse).get("id").asText());

        String messageResponse = mvc.perform(post("/api/v1/messaging/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Message to be moderated\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID messageId = UUID.fromString(objectMapper.readTree(messageResponse).get("id").asText());

        String reportResponse = mvc.perform(post("/api/v1/messaging/messages/" + messageId + "/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Harassing and abusive content\"}")
                        .with(jwt().jwt(token -> token.subject("messaging-api-peer").claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        UUID reportId = UUID.fromString(objectMapper.readTree(reportResponse).get("id").asText());

        mvc.perform(post("/api/v1/messaging/messages/" + messageId + "/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Duplicate report attempt\"}")
                        .with(jwt().jwt(token -> token.subject("messaging-api-peer").claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/messaging/messages/" + messageId + "/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"I am the message author\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/messaging/reports")
                        .with(jwt().jwt(token -> token.subject(MODERATOR_SUBJECT).claim("tenant_id", TENANT_B.toString()))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/messaging/reports")
                        .with(jwt().jwt(token -> token.subject("messaging-api-peer").claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/messaging/reports")
                        .with(jwt().jwt(token -> token.subject(MODERATOR_SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("OPEN"));

        mvc.perform(post("/api/v1/messaging/reports/" + reportId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACTIONED","resolution":"Confirmed policy violation; removed message content","expectedVersion":0}
                                """)
                        .with(jwt().jwt(token -> token.subject(MODERATOR_SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIONED"))
                .andExpect(jsonPath("$.reportedMessageBody").value("Message to be moderated"))
                .andExpect(jsonPath("$.messageSenderMembershipId").value(MEMBERSHIP_A.toString()));

        mvc.perform(post("/api/v1/messaging/reports/" + reportId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISMISSED\",\"resolution\":\"Stale duplicate action\",\"expectedVersion\":0}")
                        .with(jwt().jwt(token -> token.subject(MODERATOR_SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select body from message_record where tenant_id = ? and id = ?", String.class, TENANT_A, messageId))
                .isEqualTo("[removed by moderation]");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select deleted_at is not null from message_record where tenant_id = ? and id = ?", Boolean.class, TENANT_A, messageId))
                .isTrue();
    }

    @Test
    void tenantPolicyCanRestrictStudentMessagingAndDisableMessaging() throws Exception {
        // The policy endpoint is permission-gated, not merely hidden in the UI.
        mvc.perform(get("/api/v1/messaging/policy")
                        .with(jwt().jwt(token -> token.subject("messaging-api-peer").claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isForbidden());

        // Give both participants the system Student role for this scenario.
        jdbc.update("""
                INSERT INTO role_definition(id, tenant_id, system_key, name, system_managed)
                VALUES (?, ?, 'STUDENT', 'Student', true)
                ON CONFLICT (tenant_id, system_key) DO NOTHING
                """, ROLE_STUDENT, TENANT_A);
        UUID studentRoleId = jdbc.queryForObject(
                "SELECT id FROM role_definition WHERE tenant_id = ? AND system_key = 'STUDENT'",
                UUID.class, TENANT_A);
        jdbc.update("""
                INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject)
                VALUES (uuidv7(), ?, ?, ?, 'TENANT', ?)
                ON CONFLICT DO NOTHING
                """, TENANT_A, MEMBERSHIP_A, studentRoleId, SUBJECT);
        jdbc.update("""
                INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject)
                VALUES (uuidv7(), ?, ?, ?, 'TENANT', ?)
                ON CONFLICT DO NOTHING
                """, TENANT_A, MEMBERSHIP_A_PEER, studentRoleId, SUBJECT);

        mvc.perform(get("/api/v1/messaging/policy")
                        .with(jwt().jwt(token -> token.subject(MODERATOR_SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messagingEnabled").value(true))
                .andExpect(jsonPath("$.allowStudentToStudent").value(true))
                .andExpect(jsonPath("$.messageRetentionDays").value(365))
                .andExpect(jsonPath("$.reportRetentionDays").value(1095));

        mvc.perform(put("/api/v1/messaging/policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messagingEnabled":true,"allowStudentToStudent":false,
                                 "messageRetentionDays":30,"reportRetentionDays":90,
                                 "maxMessagesPerMinute":30,"maxReportsPerHour":5,"expectedVersion":0}
                                """)
                        .with(jwt().jwt(token -> token.subject(MODERATOR_SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowStudentToStudent").value(false))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(post("/api/v1/messaging/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Student direct chat","participantMembershipIds":["%s"]}
                                """.formatted(MEMBERSHIP_A_PEER))
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MESSAGE_CONFLICT"));

        mvc.perform(put("/api/v1/messaging/policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messagingEnabled":false,"allowStudentToStudent":false,
                                 "messageRetentionDays":30,"reportRetentionDays":90,
                                 "maxMessagesPerMinute":30,"maxReportsPerHour":5,"expectedVersion":1}
                                """)
                        .with(jwt().jwt(token -> token.subject(MODERATOR_SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messagingEnabled").value(false));

        mvc.perform(post("/api/v1/messaging/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Disabled chat","participantMembershipIds":["%s"]}
                                """.formatted(MEMBERSHIP_A_PEER))
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MESSAGING_DISABLED"));
    }

    @Test
    void messageSendRateLimitIsEnforcedAtomically() throws Exception {
        jdbc.update("""
                INSERT INTO message_tenant_policy(tenant_id, max_messages_per_minute)
                VALUES (?, 1)
                ON CONFLICT (tenant_id) DO UPDATE SET max_messages_per_minute = 1
                """, TENANT_A);
        String response = mvc.perform(post("/api/v1/messaging/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Rate limit test","participantMembershipIds":["%s"]}
                                """.formatted(MEMBERSHIP_A_PEER))
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID conversationId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mvc.perform(post("/api/v1/messaging/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"First message\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/messaging/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Second message should be limited\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("MESSAGING_RATE_LIMITED"));
    }

    @Test
    void reportRateLimitIsEnforcedForDistinctMessages() throws Exception {
        jdbc.update("""
                INSERT INTO message_tenant_policy(tenant_id, max_reports_per_hour)
                VALUES (?, 1)
                ON CONFLICT (tenant_id) DO UPDATE SET max_reports_per_hour = 1
                """, TENANT_A);
        String conversationResponse = mvc.perform(post("/api/v1/messaging/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Report rate limit","participantMembershipIds":["%s"]}
                                """.formatted(MEMBERSHIP_A_PEER))
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID conversationId = UUID.fromString(objectMapper.readTree(conversationResponse).get("id").asText());

        String firstResponse = mvc.perform(post("/api/v1/messaging/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"First reportable message\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID firstMessageId = UUID.fromString(objectMapper.readTree(firstResponse).get("id").asText());

        String secondResponse = mvc.perform(post("/api/v1/messaging/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Second reportable message\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID secondMessageId = UUID.fromString(objectMapper.readTree(secondResponse).get("id").asText());

        mvc.perform(post("/api/v1/messaging/messages/" + firstMessageId + "/reports")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"First report\"}")
                        .with(jwt().jwt(token -> token.subject("messaging-api-peer").claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/messaging/messages/" + secondMessageId + "/reports")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Second report exceeds quota\"}")
                        .with(jwt().jwt(token -> token.subject("messaging-api-peer").claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("MESSAGING_RATE_LIMITED"));
    }

    @Test
    void retentionPreservesReportedEvidenceUntilItsLongerRetentionExpires() throws Exception {
        jdbc.update("""
                INSERT INTO message_tenant_policy(
                    tenant_id, message_retention_days, report_retention_days,
                    max_messages_per_minute, max_reports_per_hour
                ) VALUES (?, 1, 90, 30, 5)
                ON CONFLICT (tenant_id) DO UPDATE SET
                    message_retention_days = 1,
                    report_retention_days = 90,
                    max_messages_per_minute = 30,
                    max_reports_per_hour = 5
                """, TENANT_A);
        String conversationResponse = mvc.perform(post("/api/v1/messaging/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Retention test","participantMembershipIds":["%s"]}
                                """.formatted(MEMBERSHIP_A_PEER))
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID conversationId = UUID.fromString(objectMapper.readTree(conversationResponse).get("id").asText());

        String unreportedResponse = mvc.perform(post("/api/v1/messaging/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Expired unreported message\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID unreportedMessageId = UUID.fromString(objectMapper.readTree(unreportedResponse).get("id").asText());

        String reportedResponse = mvc.perform(post("/api/v1/messaging/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Evidence must be retained\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID reportedMessageId = UUID.fromString(objectMapper.readTree(reportedResponse).get("id").asText());

        String reportResponse = mvc.perform(post("/api/v1/messaging/messages/" + reportedMessageId + "/reports")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Retention evidence test\"}")
                        .with(jwt().jwt(token -> token.subject("messaging-api-peer").claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID reportId = UUID.fromString(objectMapper.readTree(reportResponse).get("id").asText());

        entityManager.flush();
        jdbc.update("UPDATE message_record SET created_at = current_timestamp - interval '3 days' WHERE tenant_id = ?", TENANT_A);
        entityManager.clear();

        org.assertj.core.api.Assertions.assertThat(retention.purgeTenant(TENANT_A, Instant.now())).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM message_record WHERE tenant_id = ? AND id = ?", Integer.class, TENANT_A, unreportedMessageId)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM message_record WHERE tenant_id = ? AND id = ?", Integer.class, TENANT_A, reportedMessageId)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT reported_message_body FROM message_report WHERE tenant_id = ? AND id = ?", String.class, TENANT_A, reportId))
                .isEqualTo("Evidence must be retained");

        jdbc.update("UPDATE message_report SET created_at = current_timestamp - interval '120 days' WHERE tenant_id = ? AND id = ?", TENANT_A, reportId);
        entityManager.clear();
        org.assertj.core.api.Assertions.assertThat(retention.purgeTenant(TENANT_A, Instant.now())).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM message_report WHERE tenant_id = ? AND id = ?", Integer.class, TENANT_A, reportId)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM message_record WHERE tenant_id = ? AND id = ?", Integer.class, TENANT_A, reportedMessageId)).isZero();
    }

@Test
    void inactiveParticipantIsReturnedAsForbidden() throws Exception {
        mvc.perform(post("/api/v1/messaging/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Inactive participant conversation","participantMembershipIds":["%s"]}
                                """.formatted(MEMBERSHIP_A_INACTIVE))
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MESSAGING_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Messaging requires active memberships in the authenticated tenant"));
    }
}
