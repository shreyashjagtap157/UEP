package com.universalplatform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.universalplatform.tenancy.TenantDirectory;
import com.universalplatform.tenancy.TenantStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Blocks API access for tenants whose lifecycle state does not permit it:
 * SUSPENDED/CLOSED -> all requests denied; READ_ONLY -> only safe methods allowed;
 * unknown tenant ids -> denied (fallback-deny). Only {@code ACTIVE} status (the hot path) is
 * cached briefly (30s); deny-decisions are looked up live so a suspended tenant never rejoins
 * traffic through a stale cache entry. Background workers that act on persisted tenant-owned
 * work wrap with TenantExecutionContext and do not traverse HTTP filters, so tenant-scoped jobs
 * remain unaffected.
 */
@Component
class TenantLifecycleFilter extends OncePerRequestFilter {
    private static final String API_PREFIX = "/api/";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final TenantContext tenantContext;
    private final TenantDirectory tenants;
    private final Clock clock = Clock.systemUTC();
    private final ConcurrentHashMap<UUID, CachedStatus> activeCache = new ConcurrentHashMap<>();

    private record CachedStatus(Instant expiresAt) {}

    TenantLifecycleFilter(TenantContext tenantContext, TenantDirectory tenants) {
        this.tenantContext = tenantContext;
        this.tenants = tenants;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith(API_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<UUID> tenantId = tenantContext.currentTenantId();
        if (tenantId.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        Optional<TenantStatus> status = effectiveStatus(tenantId.get());
        if (status.isEmpty()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant context is no longer valid");
            return;
        }
        TenantStatus resolved = status.get();
        if (resolved == TenantStatus.SUSPENDED || resolved == TenantStatus.CLOSED) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant is " + resolved.name().toLowerCase() + " and cannot process requests");
            return;
        }
        if (resolved == TenantStatus.READ_ONLY && !SAFE_METHODS.contains(request.getMethod().toUpperCase())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant is read-only and cannot accept modify requests");
            return;
        }
        chain.doFilter(request, response);
    }

    private Optional<TenantStatus> effectiveStatus(UUID tenantId) {
        Instant now = clock.instant();
        CachedStatus cached = activeCache.get(tenantId);
        if (cached != null && cached.expiresAt().isAfter(now)) return Optional.of(TenantStatus.ACTIVE);
        TenantStatus current = tenants.find(tenantId).map(TenantDirectory.TenantSummary::status).orElse(null);
        if (current == TenantStatus.ACTIVE) activeCache.put(tenantId, new CachedStatus(now.plus(CACHE_TTL)));
        return Optional.ofNullable(current);
    }
}
