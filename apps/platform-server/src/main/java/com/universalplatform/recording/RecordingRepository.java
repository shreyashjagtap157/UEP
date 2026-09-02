package com.universalplatform.recording;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface RecordingRepository extends JpaRepository<Recording,UUID> {
    Optional<Recording> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<Recording> findByTenantIdAndLiveClassId(UUID tenantId, UUID liveClassId);
    Page<Recording> findAllByTenantId(UUID tenantId, Pageable pageable);
    List<Recording> findTop100ByStatusInOrderByRequestedAtAsc(Collection<RecordingProcessingStatus> statuses);
    List<Recording> findTop100ByStatusInAndReadyAtBeforeOrderByReadyAtAsc(Collection<RecordingProcessingStatus> statuses, Instant before);
    List<Recording> findTop100ByStatusInOrderByReadyAtAsc(Collection<RecordingProcessingStatus> statuses);
}
