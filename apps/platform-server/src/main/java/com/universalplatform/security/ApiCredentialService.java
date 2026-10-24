package com.universalplatform.security;

import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiCredentialService {
    private final TenantContext tenants;
    private final ActorContext actors;
    private final AuthorizationService auth;
    private final ApiCredentialRepository repo;
    private final Clock clock = Clock.systemUTC();
    private final SecureRandom random = new SecureRandom();

    public ApiCredentialService(TenantContext tenants, ActorContext actors, AuthorizationService auth,
            ApiCredentialRepository repo) {
        this.tenants = tenants;
        this.actors = actors;
        this.auth = auth;
        this.repo = repo;
    }

    @Transactional
    public CredentialView create(UUID membershipId, String name, Set<PermissionKey> scopes, Instant expiresAt) {
        auth.require(PermissionKey.API_MANAGE);
        UUID tenant = tenants.requireTenantId();
        if (membershipId == null)
            throw new IllegalArgumentException("Membership is required");
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 120)
            throw new IllegalArgumentException("Credential name is required");
        Set<PermissionKey> effective = scopes == null || scopes.isEmpty() ? Set.of() : Set.copyOf(scopes);
        String raw = "upk_live_" + randomHex(32);
        ApiCredential saved = repo.save(new ApiCredential(UUID.randomUUID(), tenant, membershipId, normalized,
                hash(raw), effective.stream().map(Enum::name).sorted().reduce((a, b) -> a + "," + b).orElse(""),
                clock.instant(), expiresAt));
        return new CredentialView(saved.id(), saved.name(), effective, saved.expiresAt(), raw, saved.version());
    }

    @Transactional(readOnly = true)
    public List<CredentialView> list(UUID membershipId) {
        auth.require(PermissionKey.API_VIEW);
        UUID tenant = tenants.requireTenantId();
        return repo.findAllByTenantIdAndMembershipIdOrderByCreatedAtDesc(tenant, membershipId).stream()
                .map(c -> new CredentialView(c.id(), c.name(), scopeSet(c.scopes()), c.expiresAt(), null, c.version()))
                .toList();
    }

    @Transactional
    public void revoke(UUID id, long expectedVersion) {
        auth.require(PermissionKey.API_MANAGE);
        ApiCredential c = repo.findById(id).filter(x -> x.tenantId().equals(tenants.requireTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("API credential not found"));
        if (c.version() != expectedVersion)
            throw new IllegalArgumentException("API credential was modified by another request");
        c.revoke(clock.instant());
    }

    Optional<ApiCredential> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank())
            return Optional.empty();
        return repo.findActiveByKeyHash(hash(rawKey)).filter(c -> c.active(clock.instant()));
    }

    void touch(ApiCredential c) {
        c.used(clock.instant());
        repo.save(c);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    static Set<String> scopeSet(String scopes) {
        if (scopes == null || scopes.isBlank())
            return Set.of();
        return Arrays.stream(scopes.split(",")).filter(x -> !x.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public record CredentialView(UUID id, String name, Set<?> scopes, Instant expiresAt, String secret, long version) {
    }
}
