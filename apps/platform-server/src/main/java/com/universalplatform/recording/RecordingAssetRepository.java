package com.universalplatform.recording;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface RecordingAssetRepository extends JpaRepository<RecordingAsset,UUID> {
    Optional<RecordingAsset> findByTenantIdAndRecordingIdAndKind(UUID tenantId, UUID recordingId, RecordingAssetKind kind);
    List<RecordingAsset> findAllByTenantIdAndRecordingId(UUID tenantId, UUID recordingId);
}
