package com.universalplatform.security;

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
    private final ApiCredentialAuthorizer auth;
    private final ApiCredentialRepository repo;
    private final Clock clock = Clock.systemUTC();
    private final SecureRandom random = new SecureRandom();

    public ApiCredentialService(TenantContext tenants, ActorContext actors, ApiCredentialAuthorizer auth,
            ApiCredentialRepository repo) {
        this.tenants = tenants;
        this.actors = actors;
        this.auth = auth;
        this.repo = repo;
    }

    @Transactional
    public CredentialView create(UUID membershipId, String name, Set<String> scopes, Instant expiresAt) {
        auth.requireApiManage();
        UUID tenant = tenants.requireTenantId();
        if (membershipId == null)
            throw new IllegalArgumentException("Membership is required");
        auth.requireApiCredentialTarget(membershipId);
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 120)
            throw new IllegalArgumentException("Credential name is required");
        if (scopes == null || scopes.isEmpty()) {
            throw new IllegalArgumentException("At least one explicit API credential permission scope is required");
        }
        Set<String> effective = Set.copyOf(scopes);
        String raw = "upk_live_" + randomHex(32);
        ApiCredential saved = repo.save(new ApiCredential(UUID.randomUUID(), tenant, membershipId, normalized,
                hash(raw), effective.stream().sorted().reduce((a, b) -> a + "," + b).orElse(""),
                clock.instant(), expiresAt));
        return new CredentialView(saved.id(), saved.name(), effective, saved.expiresAt(), raw, saved.version());
    }

    @Transactional(readOnly = true)
    public List<CredentialView> list(UUID membershipId) {
        auth.requireApiView();
        UUID tenant = tenants.requireTenantId();
        auth.requireApiCredentialTarget(membershipId);
        return repo.findAllByTenantIdAndMembershipIdOrderByCreatedAtDesc(tenant, membershipId).stream()
                .map(c -> new CredentialView(c.id(), c.name(), scopeSet(c.scopes()), c.expiresAt(), null, c.version()))
                .toList();
    }

    @Transactional
    public void revoke(UUID id, long expectedVersion) {
        auth.requireApiManage();
        ApiCredential c = repo.findById(id).filter(x -> x.tenantId().equals(tenants.requireTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("API credential not found"));
        auth.requireApiCredentialTarget(c.membershipId());
        if (c.version() != expectedVersion)
            throw new IllegalArgumentException("API credential was modified by another request");
        c.revoke(clock.instant());
    }

    public Optional<ApiCredential> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank())
            return Optional.empty();
        // Fail closed for legacy credentials created before explicit scopes were required.
        // An empty scope set used to mean "inherit every current membership permission".
        return repo.findActiveByKeyHash(hash(rawKey))
                .filter(c -> c.active(clock.instant()))
                .filter(c -> !scopeSet(c.scopes()).isEmpty());
    }

    public void touch(ApiCredential c) {
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

    public static Set<String> scopeSet(String scopes) {
        if (scopes == null || scopes.isBlank())
            return Set.of();
        return Arrays.stream(scopes.split(",")).filter(x -> !x.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public record CredentialView(UUID id, String name, Set<String> scopes, Instant expiresAt, String secret, long version) {
    }
}
