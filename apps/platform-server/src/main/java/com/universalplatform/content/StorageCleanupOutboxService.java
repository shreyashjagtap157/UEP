package com.universalplatform.content;

import com.universalplatform.storage.StorageProviderType;
import com.universalplatform.storage.StorageCleanupQueue;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorageCleanupOutboxService implements StorageCleanupQueue {
    private final StorageCleanupRepository repository;
    private final Clock clock = Clock.systemUTC();

    StorageCleanupOutboxService(StorageCleanupRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(UUID tenantId, StorageProviderType provider, String locator,
                        String objectKey, UUID reservationId, Throwable reason) {
        if (tenantId == null || provider == null || locator == null || locator.isBlank()
                || objectKey == null || objectKey.isBlank() || reservationId == null) {
            throw new IllegalArgumentException("Complete storage cleanup identity is required");
        }
        Instant now = clock.instant();
        // Persist only the exception class, not its message: provider errors may contain URLs or
        // response content that should not be retained in the operational outbox.
        repository.enqueueIfAbsent(UUID.randomUUID(), tenantId, provider.name(), locator,
                objectKey, reservationId, now, now);
    }
}
