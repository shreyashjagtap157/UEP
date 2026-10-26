package com.universalplatform.messaging;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final String SUBJECT = "messaging-api-owner";
    private static final String MODERATOR_SUBJECT = "messaging-api-moderator";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

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
                .andExpect(jsonPath("$[0].unreadCount").value(1));

        mvc.perform(post("/api/v1/messaging/conversations/" + conversationId + "/read")
                        .with(jwt().jwt(token -> token.subject("messaging-api-peer").claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/messaging/conversations")
                        .with(jwt().jwt(token -> token.subject("messaging-api-peer").claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unreadCount").value(0));
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
                .andExpect(jsonPath("$.status").value("ACTIONED"));

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
