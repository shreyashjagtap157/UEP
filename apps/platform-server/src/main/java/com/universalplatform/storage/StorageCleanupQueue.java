package com.universalplatform.storage;

import java.util.UUID;

/** Cross-module boundary for durable deletion of provider objects. */
public interface StorageCleanupQueue {
    void enqueue(UUID tenantId, StorageProviderType provider, String locator,
                 String objectKey, UUID reservationId, Throwable reason);
}
