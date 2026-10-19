package com.universalplatform.recording;

import static org.junit.jupiter.api.Assertions.*;

import com.universalplatform.storage.StorageProviderType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Entity-level state-machine invariants hardened against audited defects C2/C1. */
class RecordingStateInvariantTest {
    private static final Instant T0 = Instant.parse("2026-09-02T08:00:00Z");

    private Recording fresh() {
        return new Recording(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "room",
                RecordingQualityPreset.BALANCED, StorageProviderType.LOCAL, T0);
    }

    @Test
    void markFinalizingIsOnlyValidFromRecordingishStates() {
        Recording ready = fresh();
        // C2: silently rewriting endedAt from any state is no longer allowed.
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ready.markFinalizing(T0.plusSeconds(60)));
        assertEquals(RecordingProcessingStatus.REQUESTED, ready.status());
        assertNull(ready.endedAt());
        assertTrue(e.getMessage().contains("REQUESTED"));

        Recording recording = fresh();
        recording.markStarting(T0.plusSeconds(1));
        recording.start("egress-1", T0.plusSeconds(2));
        recording.markFinalizing(T0.plusSeconds(100)); // RECORDING -> FINALIZING is valid
        assertEquals(RecordingProcessingStatus.FINALIZING, recording.status());
        assertEquals(T0.plusSeconds(100), recording.endedAt());
    }

    @Test
    void markArchivingOnlyClaimsReadyRowsForRecoverySafety() {
        Recording r = fresh();
        r.markStarting(T0.plusSeconds(1));
        // STARTING must not be claimable for archival (claim is the READY -> ARCHIVING transition).
        assertDoesNotThrow(r::markArchiving);
        assertEquals(RecordingProcessingStatus.STARTING, r.status());
        r.start("egress-1", T0.plusSeconds(2));
        r.markFinalizing(T0.plusSeconds(10));
        r.ready("loc", "key", "hash", 10, 8, T0.plusSeconds(20));
        r.markArchiving();
        assertEquals(RecordingProcessingStatus.ARCHIVING, r.status());
        r.restoreReady();
        assertEquals(RecordingProcessingStatus.READY, r.status());
    }
}
