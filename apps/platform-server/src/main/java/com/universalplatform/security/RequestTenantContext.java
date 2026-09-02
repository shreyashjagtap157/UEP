package com.universalplatform.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class RequestTenantContext implements TenantContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    public static void set(UUID tenantId) { CURRENT.set(tenantId); }
    public static void clear() { CURRENT.remove(); }

    @Override
    public Optional<UUID> currentTenantId() {
        return Optional.ofNullable(CURRENT.get());
    }
}
