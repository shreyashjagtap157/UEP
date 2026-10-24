package com.universalplatform.liveclass;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LiveClassParticipantInvariantTest {
    @Test
    void rejoiningStartsANewPresenceIntervalWithoutDiscardingTheFirst() {
        Instant t0 = Instant.parse("2026-09-02T08:00:00Z");
        LiveClassParticipant p = new LiveClassParticipant(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ParticipantRole.PARTICIPANT, t0);

        p.join(t0, "BALANCED");
        p.leave(t0.plusSeconds(120));
        assertEquals(120, p.totalPresentSeconds());
        assertEquals(ParticipantStatus.LEFT, p.status());

        p.join(t0.plusSeconds(600), "LOW_BANDWIDTH");
        assertNull(p.leftAt());
        p.leave(t0.plusSeconds(780));

        assertEquals(300, p.totalPresentSeconds());
        assertEquals(ParticipantStatus.LEFT, p.status());
    }

    @Test
    void kickedParticipantCannotRejoin() {
        Instant t0 = Instant.parse("2026-09-02T08:00:00Z");
        LiveClassParticipant p = new LiveClassParticipant(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ParticipantRole.PARTICIPANT, t0);
        p.join(t0, "BALANCED");
        p.moderateKick("policy", t0.plusSeconds(10));
        assertEquals(ParticipantStatus.KICKED, p.status());
        assertThrows(IllegalStateException.class, () -> p.join(t0.plusSeconds(20), "BALANCED"));
    }

    @Test
    void moderatorMutePersistsAcrossReconnectionUntilExplicitUnmute() {
        Instant t0 = Instant.parse("2026-09-02T08:00:00Z");
        LiveClassParticipant p = new LiveClassParticipant(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ParticipantRole.PARTICIPANT, t0);
        p.join(t0, "BALANCED");
        p.moderateMute();
        assertEquals(ParticipantStatus.MUTED, p.status());

        // C5: reconnecting must not silently lift the moderator mute.
        p.join(t0.plusSeconds(60), "BALANCED");
        assertEquals(ParticipantStatus.MUTED, p.status());
        assertNotNull(p.joinedAt());
        assertNull(p.leftAt());

        p.leave(t0.plusSeconds(90));
        assertEquals(ParticipantStatus.MUTED, p.status()); // mute survives a leave too
        p.join(t0.plusSeconds(120), "LOW_BANDWIDTH");
        assertEquals(ParticipantStatus.MUTED, p.status());

        // Only an explicit moderator action restores voice.
        p.moderateUnmute();
        assertEquals(ParticipantStatus.JOINED, p.status());
    }
}
