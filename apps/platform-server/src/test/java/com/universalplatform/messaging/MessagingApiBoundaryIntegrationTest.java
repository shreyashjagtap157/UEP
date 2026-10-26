package com.universalplatform.messaging;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private static final UUID MEMBERSHIP_A = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af766");
    private static final UUID MEMBERSHIP_A_INACTIVE = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af777");
    private static final UUID MEMBERSHIP_B = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af788");
    private static final String SUBJECT = "messaging-api-owner";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'messaging-api-a', 'Messaging API A', 'ACTIVE')", TENANT_A);
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'messaging-api-b', 'Messaging API B', 'ACTIVE')", TENANT_B);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, display_name, status) VALUES (?, ?, 'Messaging API Owner', 'ACTIVE')", USER_A, SUBJECT);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, display_name, status) VALUES (?, 'messaging-api-inactive', 'Inactive Participant', 'ACTIVE')", USER_A_INACTIVE);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, display_name, status) VALUES (?, 'messaging-api-foreign', 'Foreign Participant', 'ACTIVE')", USER_B);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", MEMBERSHIP_A, TENANT_A, USER_A);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'SUSPENDED')", MEMBERSHIP_A_INACTIVE, TENANT_A, USER_A_INACTIVE);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", MEMBERSHIP_B, TENANT_B, USER_B);
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
