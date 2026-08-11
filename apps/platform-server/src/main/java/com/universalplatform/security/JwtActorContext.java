package com.universalplatform.security;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
final class JwtActorContext implements ActorContext {
    @Override
    public Optional<String> currentSubject() {
        return currentJwt().map(Jwt::getSubject).filter(value -> !value.isBlank());
    }

    @Override
    public Optional<String> currentSessionId() {
        return currentJwt().flatMap(jwt -> firstClaim(jwt, "sid", "session_state"));
    }

    @Override
    public Optional<String> currentTokenId() {
        return currentJwt().flatMap(jwt -> firstClaim(jwt, "jti"));
    }

    @Override
    public Optional<String> currentEmail() {
        return currentJwt().flatMap(jwt -> firstClaim(jwt, "email"));
    }

    @Override
    public Optional<String> currentDisplayName() {
        return currentJwt().flatMap(jwt -> firstClaim(jwt, "name", "preferred_username", "email"));
    }

    @Override
    public Optional<Instant> currentTokenExpiry() {
        return currentJwt().map(Jwt::getExpiresAt);
    }

    @Override
    public AuthenticationAssurance authenticationAssurance() {
        return currentJwt()
                .map(jwt -> new AuthenticationAssurance(
                        jwt.getClaimAsString("acr"),
                        stringSet(jwt.getClaim("amr"))))
                .orElseGet(() -> new AuthenticationAssurance("", Set.of()));
    }

    @Override
    public boolean hasRealmRole(String role) {
        if (role == null || role.isBlank()) return false;
        return currentJwt()
                .map(jwt -> realmRoles(jwt).contains(role))
                .orElse(false);
    }

    private Optional<Jwt> currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token && token.isAuthenticated()) {
            return Optional.of(token.getToken());
        }
        return Optional.empty();
    }

    private static Optional<String> firstClaim(Jwt jwt, String... names) {
        for (String name : names) {
            String value = jwt.getClaimAsString(name);
            if (value != null && !value.isBlank()) return Optional.of(value);
        }
        return Optional.empty();
    }

    private static Set<String> realmRoles(Jwt jwt) {
        Object claim = jwt.getClaim("realm_access");
        if (!(claim instanceof Map<?, ?> realmAccess)) return Set.of();
        return stringSet(realmAccess.get("roles"));
    }

    private static Set<String> stringSet(Object value) {
        if (!(value instanceof Collection<?> collection)) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (Object item : collection) {
            if (item instanceof String text && !text.isBlank()) result.add(text);
        }
        return Set.copyOf(result);
    }
}
