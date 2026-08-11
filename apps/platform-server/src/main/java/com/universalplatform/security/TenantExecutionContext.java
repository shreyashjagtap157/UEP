package com.universalplatform.security;

import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Establishes a bounded tenant context for trusted background jobs that operate from persisted tenant-owned work. */
@Component
public class TenantExecutionContext {
    private final TenantContext tenantContext;

    TenantExecutionContext(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    public void run(UUID tenantId, Runnable action) {
        run(tenantId, () -> { action.run(); return null; });
    }

    public <T> T run(UUID tenantId, Supplier<T> action) {
        UUID previous = tenantContext.currentTenantId().orElse(null);
        try {
            RequestTenantContext.set(tenantId);
            return action.get();
        } finally {
            if (previous == null) RequestTenantContext.clear();
            else RequestTenantContext.set(previous);
        }
    }
}
