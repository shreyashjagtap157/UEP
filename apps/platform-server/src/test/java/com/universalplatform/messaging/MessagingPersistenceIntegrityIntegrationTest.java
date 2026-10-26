package com.universalplatform.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MessagingPersistenceIntegrityIntegrationTest {
    private static final UUID TENANT_A = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af611");
    private static final UUID TENANT_B = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af622");
    private static final UUID USER_A = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af633");
    private static final UUID USER_A_OTHER = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af644");
    private static final UUID USER_B = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af655");
    private static final UUID MEMBERSHIP_A = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af666");
    private static final UUID MEMBERSHIP_A_OTHER = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af677");
    private static final UUID MEMBERSHIP_B = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af688");
    private static final UUID CONVERSATION = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af699");
    private static final UUID MESSAGE = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af6aa");

    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        insertTenant(TENANT_A, "messaging-tenant-a", "Messaging Tenant A");
        insertTenant(TENANT_B, "messaging-tenant-b", "Messaging Tenant B");
        insertUser(USER_A, "messaging-user-a", "Messaging User A");
        insertUser(USER_A_OTHER, "messaging-user-a-other", "Messaging User A Other");
        insertUser(USER_B, "messaging-user-b", "Messaging User B");
        insertMembership(MEMBERSHIP_A, TENANT_A, USER_A);
        insertMembership(MEMBERSHIP_A_OTHER, TENANT_A, USER_A_OTHER);
        insertMembership(MEMBERSHIP_B, TENANT_B, USER_B);
    }

    @Test
    void conversationCreatorMustBelongToTheConversationTenant() {
        assertThrows(DataIntegrityViolationException.class,
                () -> insertConversation(CONVERSATION, TENANT_A, MEMBERSHIP_B));
    }

    @Test
    void participantMembershipMustBelongToTheConversationTenant() {
        insertConversation(CONVERSATION, TENANT_A, MEMBERSHIP_A);

        assertThrows(DataIntegrityViolationException.class,
                () -> insertParticipant(CONVERSATION, TENANT_A, MEMBERSHIP_B));
    }

    @Test
    void messageSenderMustAlreadyBeParticipantInTheConversation() {
        insertConversation(CONVERSATION, TENANT_A, MEMBERSHIP_A);
        insertParticipant(CONVERSATION, TENANT_A, MEMBERSHIP_A);

        assertThrows(DataIntegrityViolationException.class,
                () -> insertMessage(MESSAGE, CONVERSATION, TENANT_A, MEMBERSHIP_A_OTHER));
    }

    @Test
    void validTenantScopedParticipantsCanSendMessages() {
        insertConversation(CONVERSATION, TENANT_A, MEMBERSHIP_A);
        insertParticipant(CONVERSATION, TENANT_A, MEMBERSHIP_A);
        insertParticipant(CONVERSATION, TENANT_A, MEMBERSHIP_A_OTHER);
        insertMessage(MESSAGE, CONVERSATION, TENANT_A, MEMBERSHIP_A_OTHER);

        assertEquals(2, jdbc.queryForObject(
                "select count(*) from message_participant where conversation_id = ?", Integer.class, CONVERSATION));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from message_record where id = ?", Integer.class, MESSAGE));
    }

    private void insertTenant(UUID id, String slug, String name) {
        jdbc.update("""
                insert into tenant (id, slug, display_name, status)
                values (?, ?, ?, 'ACTIVE')
                """, id, slug, name);
    }

    private void insertUser(UUID id, String subject, String name) {
        jdbc.update("""
                insert into user_account (id, oidc_subject, display_name, status)
                values (?, ?, ?, 'ACTIVE')
                """, id, subject, name);
    }

    private void insertMembership(UUID id, UUID tenantId, UUID userId) {
        jdbc.update("""
                insert into tenant_membership (id, tenant_id, user_id, status)
                values (?, ?, ?, 'ACTIVE')
                """, id, tenantId, userId);
    }

    private void insertConversation(UUID id, UUID tenantId, UUID creatorMembershipId) {
        jdbc.update("""
                insert into message_conversation (
                    id, tenant_id, subject, created_by_membership_id, created_at, updated_at
                )
                values (?, ?, 'Integrity test', ?, current_timestamp, current_timestamp)
                """, id, tenantId, creatorMembershipId);
    }

    private void insertParticipant(UUID conversationId, UUID tenantId, UUID membershipId) {
        jdbc.update("""
                insert into message_participant (conversation_id, tenant_id, membership_id, joined_at)
                values (?, ?, ?, current_timestamp)
                """, conversationId, tenantId, membershipId);
    }

    private void insertMessage(UUID id, UUID conversationId, UUID tenantId, UUID senderMembershipId) {
        jdbc.update("""
                insert into message_record (
                    id, conversation_id, tenant_id, sender_membership_id, body, created_at
                )
                values (?, ?, ?, ?, 'Integrity test message', current_timestamp)
                """, id, conversationId, tenantId, senderMembershipId);
    }
}
