package com.universalplatform.integration;

import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.TenantContext;
import com.universalplatform.storage.StorageAdapterRegistry;
import com.universalplatform.storage.StorageProviderType;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorageIntegrationService {
    private final TenantContext tenants;
    private final AuthorizationService auth;
    private final StorageAdapterRegistry adapters;
    private final ExternalStorageBindingRepository repo;
    private final boolean developmentMode;
    private final Clock clock = Clock.systemUTC();

    StorageIntegrationService(TenantContext tenants, AuthorizationService auth,
                              StorageAdapterRegistry adapters, ExternalStorageBindingRepository repo,
                              @Value("${platform.development-mode:false}") boolean developmentMode) {
        this.tenants = tenants;
        this.auth = auth;
        this.adapters = adapters;
        this.repo = repo;
        this.developmentMode = developmentMode;
    }

    @Transactional
    public BindingView configure(StorageProviderType type, String prefix, long expectedVersion) {
        auth.require(PermissionKey.EXTERNAL_STORAGE_MANAGE);
        if (type == null) throw new IllegalArgumentException("Storage provider is required");
        if (type == StorageProviderType.LOCAL && !developmentMode) {
            throw new IllegalArgumentException("Local filesystem storage cannot be configured outside development mode");
        }
        adapters.require(type);
        String objectPrefix = normalizePrefix(prefix);
        UUID tenantId = tenants.requireTenantId();
        ExternalStorageBinding binding = repo.findByTenantId(tenantId).orElse(null);
        if (binding == null) {
            if (expectedVersion != 0) throw new IllegalArgumentException("Storage binding version is stale");
            binding = repo.save(new ExternalStorageBinding(
                    UUID.randomUUID(), tenantId, type.name(), objectPrefix, clock.instant()));
        } else {
            if (binding.version() != expectedVersion) {
                throw new IllegalArgumentException("Storage binding was modified by another request");
            }
            binding.update(type.name(), objectPrefix, clock.instant());
        }
        return view(binding);
    }

    @Transactional(readOnly = true)
    public BindingView current() {
        auth.require(PermissionKey.EXTERNAL_STORAGE_MANAGE);
        return repo.findByTenantId(tenants.requireTenantId()).map(this::view).orElse(null);
    }

    private static String normalizePrefix(String value) {
        String prefix = value == null ? "" : value.trim();
        while (prefix.endsWith("/")) prefix = prefix.substring(0, prefix.length() - 1);
        if (prefix.length() > 200 || prefix.startsWith("/") || prefix.contains("..")
                || prefix.contains("\\") || prefix.contains("//")) {
            throw new IllegalArgumentException("Invalid object prefix");
        }
        return prefix;
    }

    private BindingView view(ExternalStorageBinding binding) {
        return new BindingView(StorageProviderType.valueOf(binding.providerType()),
                binding.objectPrefix(), binding.version());
    }

    public record BindingView(StorageProviderType providerType, String objectPrefix, long version) {}
}
