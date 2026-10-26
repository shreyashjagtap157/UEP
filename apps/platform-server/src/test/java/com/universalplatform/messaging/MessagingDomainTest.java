package com.universalplatform.messaging;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessagingDomainTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID MEMBERSHIP = UUID.randomUUID();

    @Test
    void messageCanBeEditedWithoutChangingIdentityOrCreationTime() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant edited = Instant.parse("2026-01-01T00:05:00Z");
        UUID id = UUID.randomUUID();
        MessageRecord message = new MessageRecord(id, UUID.randomUUID(), TENANT, MEMBERSHIP, "original", created);

        message.edit("updated", edited);

        assertEquals(id, message.id());
        assertEquals("updated", message.body());
        assertEquals(created, message.createdAt());
        assertEquals(edited, message.editedAt());
        assertNull(message.deletedAt());
    }

    @Test
    void deletingMessageIsSoftDeleteAndPreservesAuditTimestamp() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant deleted = Instant.parse("2026-01-01T00:10:00Z");
        MessageRecord message = new MessageRecord(UUID.randomUUID(), UUID.randomUUID(), TENANT, MEMBERSHIP, "secret", created);

        message.delete(deleted);

        assertEquals("secret", message.body());
        assertEquals(created, message.createdAt());
        assertEquals(deleted, message.deletedAt());
    }

    @Test
    void conversationTouchAdvancesUpdateTimestampOnly() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant updated = Instant.parse("2026-01-01T00:10:00Z");
        UUID id = UUID.randomUUID();
        MessageConversation conversation = new MessageConversation(id, TENANT, "Course discussion", MEMBERSHIP, created);

        conversation.touched(updated);

        assertEquals(id, conversation.id());
        assertEquals(created, conversation.createdAt());
        assertEquals(updated, conversation.updatedAt());
    }

    @Test
    void participantReadStateIsPerParticipant() {
        Instant joined = Instant.parse("2026-01-01T00:00:00Z");
        Instant read = Instant.parse("2026-01-01T00:20:00Z");
        MessageParticipant participant = new MessageParticipant(UUID.randomUUID(), TENANT, MEMBERSHIP, joined);

        assertNull(participant.lastReadAt());
        participant.markRead(read);
        assertEquals(read, participant.lastReadAt());
        participant.markRead(joined);
        assertEquals(read, participant.lastReadAt(), "a delayed stale read request must not move the marker backwards");
        assertEquals(MEMBERSHIP, participant.membershipId());
        assertEquals(TENANT, participant.tenantId());
    }
}
