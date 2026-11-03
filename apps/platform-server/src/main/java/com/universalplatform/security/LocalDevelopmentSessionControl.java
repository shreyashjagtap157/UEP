package com.universalplatform.security;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Process-local revocation state for the single launcher-issued local-native session.
 * The browser never receives the launcher token; logout only flips this server-side guard.
 */
@Component
@Profile("local-native")
final class LocalDevelopmentSessionControl {
    static final String LOCAL_SUBJECT = "uep-local-demo-owner";

    private final String sessionId;
    private final AtomicBoolean active = new AtomicBoolean(true);

    LocalDevelopmentSessionControl(@Value("${UEP_LOCAL_AUTH_SESSION_ID:}") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Local API session ID must not be blank.");
        }
        this.sessionId = sessionId;
    }

    boolean isActive() {
        return active.get();
    }

    boolean revoke(String subject, String candidateSessionId) {
        return LOCAL_SUBJECT.equals(subject)
                && sessionId.equals(candidateSessionId)
                && active.compareAndSet(true, false);
    }

    void resetForTests() {
        active.set(true);
    }
}
