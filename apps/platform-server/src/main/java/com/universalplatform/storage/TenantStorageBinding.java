package com.universalplatform.storage;

/** Immutable storage provider and object prefix snapshot selected for an upload session. */
public record TenantStorageBinding(StorageProviderType providerType, String objectPrefix) {
    public TenantStorageBinding {
        if (providerType == null) throw new IllegalArgumentException("Storage provider is required");
        objectPrefix = objectPrefix == null ? "" : objectPrefix;
    }
}
