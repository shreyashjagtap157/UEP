package com.universalplatform.storage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
@Component
public class StorageAdapterRegistry {
    private final Map<StorageProviderType, StorageAdapter> adapters = new EnumMap<>(StorageProviderType.class);
    public StorageAdapterRegistry(List<StorageAdapter> configured) { configured.forEach(a -> adapters.put(a.type(), a)); }
    public StorageAdapter require(StorageProviderType type) {
        StorageAdapter adapter = adapters.get(type);
        if (adapter == null) throw new IllegalStateException("Storage provider is not configured: " + type);
        return adapter;
    }
}
