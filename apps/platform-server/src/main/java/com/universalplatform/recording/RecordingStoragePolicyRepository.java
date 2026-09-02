package com.universalplatform.recording;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RecordingStoragePolicyRepository extends JpaRepository<RecordingStoragePolicy, UUID> {}
