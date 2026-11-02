package com.universalplatform.storage;

import java.util.UUID;

/** Cross-module boundary for durable deletion of provider objects. */
public interface StorageCleanupQueue {
    void enqueue(UUID tenantId, StorageProviderType provider, String locator,
                 String objectKey, UUID reservationId, Throwable reason);

    /**
     * Enlist cleanup in the caller's transaction so metadata retirement and the
     * durable provider-deletion request commit or roll back together.
     */
    void enqueueWithinCurrentTransaction(UUID tenantId, StorageProviderType provider, String locator,
                                         String objectKey, UUID reservationId, Throwable reason);
}
