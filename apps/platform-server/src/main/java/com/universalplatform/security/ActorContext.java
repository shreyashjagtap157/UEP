package com.universalplatform.security;

import java.time.Instant;
import java.util.Optional;

public interface ActorContext {
    Optional<String> currentSubject();

    default String requireSubject() {
        return currentSubject().orElseThrow(() -> new MissingActorContextException("Authenticated subject is required"));
    }

    Optional<String> currentSessionId();

    Optional<String> currentTokenId();

    Optional<String> currentEmail();

    Optional<String> currentDisplayName();

    Optional<Instant> currentTokenExpiry();

    AuthenticationAssurance authenticationAssurance();

    boolean hasRealmRole(String role);
}
