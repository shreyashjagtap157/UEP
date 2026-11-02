package com.universalplatform.recording;

import com.universalplatform.security.TenantExecutionContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class RecordingWorker {
    private static final Logger log = LoggerFactory.getLogger(RecordingWorker.class);
    private static final int BATCH_SIZE = 100;
    private static final Instant FIRST_CURSOR = Instant.parse("0001-01-01T00:00:00Z");
    private static final UUID FIRST_ID = new UUID(0L, 0L);

    private final RecordingRepository recordings;
    private final RecordingService service;
    private final TenantExecutionContext tenantExecution;

    RecordingWorker(RecordingRepository recordings, RecordingService service, TenantExecutionContext tenantExecution) {
        this.recordings = recordings;
        this.service = service;
        this.tenantExecution = tenantExecution;
    }

    @Scheduled(fixedDelayString = "${platform.recording.worker-poll-ms:2000}")
    void process() {
        Instant afterRequestedAt = FIRST_CURSOR;
        UUID afterId = FIRST_ID;
        List<RecordingProcessingStatus> statuses = List.of(
                RecordingProcessingStatus.REQUESTED,
                RecordingProcessingStatus.RECORDING,
                RecordingProcessingStatus.STARTING);

        while (true) {
            List<Recording> batch = recordings.findProcessableAfter(
                    statuses, afterRequestedAt, afterId, PageRequest.of(0, BATCH_SIZE));
            if (batch.isEmpty()) break;

            for (Recording candidate : batch) {
                // Advance by immutable ordering keys before processing; the worker may change status.
                afterRequestedAt = candidate.requestedAt();
                afterId = candidate.id();
                recordings.findById(candidate.id()).ifPresent(
                        recording -> runIsolated(recording, "processing", () -> service.processOrReconcile(recording)));
            }
            if (batch.size() < BATCH_SIZE) break;
        }
    }

    /** Keyset pagination prevents an old ARCHIVING row from starving later recordings. */
    @Scheduled(fixedDelayString = "${platform.recording.archive-poll-ms:60000}")
    void archive() {
        Instant before = Instant.now().minusSeconds(1);
        Instant afterReadyAt = FIRST_CURSOR;
        UUID afterId = FIRST_ID;
        List<RecordingProcessingStatus> statuses = List.of(
                RecordingProcessingStatus.READY,
                RecordingProcessingStatus.ARCHIVING);

        while (true) {
            List<Recording> batch = recordings.findArchiveCandidatesAfter(
                    statuses, before, afterReadyAt, afterId, PageRequest.of(0, BATCH_SIZE));
            if (batch.isEmpty()) break;

            for (Recording recording : batch) {
                afterReadyAt = recording.readyAt();
                afterId = recording.id();
                runIsolated(recording, "archive", () -> service.archiveDue(recording));
            }
            if (batch.size() < BATCH_SIZE) break;
        }
    }

    private void runIsolated(Recording recording, String operation, Runnable work) {
        try {
            tenantExecution.run(recording.tenantId(), work);
        } catch (RuntimeException failure) {
            log.error("Recording {} failed for tenant {} recording {}; later candidates will continue",
                    operation, recording.tenantId(), recording.id(), failure);
        }
    }

    @Scheduled(fixedDelayString = "${platform.recording.retention-poll-ms:3600000}")
    void purge() {
        Instant afterReadyAt = FIRST_CURSOR;
        UUID afterId = FIRST_ID;
        List<RecordingProcessingStatus> statuses = List.of(
                RecordingProcessingStatus.READY,
                RecordingProcessingStatus.ARCHIVED);

        while (true) {
            List<Recording> batch = recordings.findPurgeCandidatesAfter(
                    statuses, afterReadyAt, afterId, PageRequest.of(0, BATCH_SIZE));
            if (batch.isEmpty()) break;

            for (Recording recording : batch) {
                afterReadyAt = recording.readyAt();
                afterId = recording.id();
                runIsolated(recording, "purge", () -> service.purgeDue(recording));
            }
            if (batch.size() < BATCH_SIZE) break;
        }
    }
}
