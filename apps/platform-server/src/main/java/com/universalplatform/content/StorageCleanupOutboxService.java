package com.universalplatform.content;

import com.universalplatform.storage.StorageProviderType;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorageCleanupOutboxService {
    private final StorageCleanupRepository repository;
    private final Clock clock = Clock.systemUTC();

    StorageCleanupOutboxService(StorageCleanupRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(UUID tenantId, StorageProviderType provider, String locator,
                        String objectKey, Throwable reason) {
        if (tenantId == null || provider == null || locator == null || locator.isBlank()
                || objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Complete storage cleanup identity is required");
        }
        Instant now = clock.instant();
        // Persist only the exception class, not its message: provider errors may contain URLs or
        // response content that should not be retained in the operational outbox.
        String error = reason == null ? "Upload transaction rolled back"
                : reason.getClass().getSimpleName();
        repository.enqueueIfAbsent(UUID.randomUUID(), tenantId, provider.name(), locator,
                objectKey, now, now);
    }
}
