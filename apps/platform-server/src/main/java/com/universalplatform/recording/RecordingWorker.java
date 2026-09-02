package com.universalplatform.recording;

import com.universalplatform.security.TenantExecutionContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class RecordingWorker {
    private final RecordingRepository recordings; private final RecordingService service; private final TenantExecutionContext tenantExecution; private final Clock clock=Clock.systemUTC();
    RecordingWorker(RecordingRepository recordings,RecordingService service,TenantExecutionContext tenantExecution){this.recordings=recordings;this.service=service;this.tenantExecution=tenantExecution;}
    @Scheduled(fixedDelayString="${platform.recording.worker-poll-ms:2000}") void process(){
        for(UUID id:recordings.findTop100ByStatusInOrderByRequestedAtAsc(List.of(RecordingProcessingStatus.REQUESTED,RecordingProcessingStatus.RECORDING,RecordingProcessingStatus.STARTING)).stream().map(Recording::id).toList()){
            recordings.findById(id).ifPresent(r->tenantExecution.run(r.tenantId(),()->service.processOrReconcile(r)));
        }
    }
    @Scheduled(fixedDelayString="${platform.recording.archive-poll-ms:60000}") void archive(){
        Instant now=clock.instant(); for(Recording r:recordings.findTop100ByStatusInAndReadyAtBeforeOrderByReadyAtAsc(List.of(RecordingProcessingStatus.READY),now.minusSeconds(1))){tenantExecution.run(r.tenantId(),()->service.archiveDue(r));}
    }
    @Scheduled(fixedDelayString="${platform.recording.retention-poll-ms:3600000}") void purge(){
        for(Recording r:recordings.findTop100ByStatusInOrderByReadyAtAsc(List.of(RecordingProcessingStatus.READY,RecordingProcessingStatus.ARCHIVED))){tenantExecution.run(r.tenantId(),()->service.purgeDue(r));}
    }
}
