package com.universalplatform.security;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails application startup when a deployment still runs any secret at its public
 * development-default value (from apps/platform-server/src/main/resources/application.yaml).
 * Two escapes exist and both are intentional: {@code platform.development-mode} (env
 * {@code PLATFORM_DEV_MODE}) for local development/CI, and test-harness detection so
 * {@code @SpringBootTest} boots (which intentionally boot the shipped dev defaults) complete.
 * The detection fails closed: a real production boot never has JUnit/Spring-Test frames on the
 * startup stack, so it is always guarded regardless of the dev default being publicly documented.
 */
@Component
final class ProductionSecretGuard {
    static final String DEV_MODE_PROPERTY = "platform.development-mode";
    private static final Pattern POSTGRES_SSL_MODE = Pattern.compile(
            "(?i)(?:[?&])sslmode=(?:require|verify-ca|verify-full)(?:[&#]|$)");

    private final Environment environment;

    ProductionSecretGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void rejectDevelopmentDefaults() {
        boolean developmentMode = environment.getProperty(DEV_MODE_PROPERTY, Boolean.class, false);
        boolean productionProfile = java.util.Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production"));
        boolean localNativeProfile = java.util.Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("local-native"));
        if (localNativeProfile && productionProfile) {
            throw new IllegalStateException(
                    "Refusing to start: the local-native profile cannot be combined with the prod/production Spring profile");
        }
        if (developmentMode && productionProfile) {
            throw new IllegalStateException(
                    "Refusing to start: " + DEV_MODE_PROPERTY
                            + " cannot be enabled with the prod/production Spring profile");
        }
        if (developmentMode) return;
        if (runningUnderTestHarness()) return;
        validateProductionSecrets(environment);
        if (productionProfile) validateProductionOidcConfiguration(environment);
    }

    /**
     * Validates without the test-harness bypass so the fail-closed contract can be unit-tested.
     * Values are never included in errors; only property/environment variable names are reported.
     */
    static void validateProductionSecrets(Environment environment) {
        List<String> offenders = new ArrayList<>();
        check(offenders, environment, "spring.datasource.password", "SUPABASE_DB_PASS", "education", 16);
        check(offenders, environment, "platform.recording.playback-secret",
                "PLATFORM_RECORDING_PLAYBACK_SECRET", "change-me-in-production", 32);
        check(offenders, environment, "platform.integrations.secret-key",
                "PLATFORM_INTEGRATION_SECRET_KEY", "change-me-in-production", 32);
        check(offenders, environment, "platform.live-learning.livekit.api-key", "LIVEKIT_API_KEY", "devkey", 16);
        check(offenders, environment, "platform.live-learning.livekit.api-secret", "LIVEKIT_API_SECRET", "secret", 32);

        // The production image explicitly activates this profile. Keep local CI's ephemeral
        // PostgreSQL smoke usable without TLS while making the deployed product fail closed.
        if (isProductionProfile(environment)) {
            checkPostgresTransport(offenders, environment);
            checkHttpsEndpoint(offenders, environment,
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri", "OIDC_ISSUER_URI");
            checkHttpsEndpoint(offenders, environment,
                    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri", "OIDC_JWK_SET_URI");
            checkSecureEndpoint(offenders, environment,
                    "platform.live-learning.livekit.url", "LIVEKIT_URL", "wss");
            checkSecureEndpoint(offenders, environment,
                    "platform.live-learning.livekit.api-url", "LIVEKIT_API_URL", "https");
        }
        if (offenders.isEmpty()) return;
        throw new IllegalStateException(
                "Refusing to start: one or more production secrets or connection settings are missing, "
                        + "blank, insecure, too short, or still at published development defaults. Set strong "
                        + "values through the named environment variables (or set " + DEV_MODE_PROPERTY
                        + "=true / env PLATFORM_DEV_MODE=true for non-production environments only): "
                        + String.join("; ", offenders));
    }

    /**
     * The resource-server issuer and JWKS endpoint are trust anchors. A production
     * deployment must never fall back to the sample localhost HTTP endpoints.
     * Values are deliberately excluded from diagnostics to avoid leaking URLs
     * that may contain deployment-specific routing information.
     */
    static void validateProductionOidcConfiguration(Environment environment) {
        String issuerValue = environment.getProperty(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri");
        String jwksValue = environment.getProperty(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri");
        List<String> offenders = new ArrayList<>();
        java.net.URI issuer = safeHttpsUri(issuerValue);
        java.net.URI jwks = safeHttpsUri(jwksValue);

        if (issuer == null || !"/realms/education".equals(issuer.getPath())) {
            offenders.add("OIDC_ISSUER_URI must be an absolute HTTPS issuer ending in /realms/education");
        }
        if (jwks == null) {
            offenders.add("OIDC_JWK_SET_URI must be an absolute HTTPS JWKS endpoint");
        } else if (issuer != null) {
            String expectedPath = issuer.getPath() + "/protocol/openid-connect/certs";
            if (!sameOrigin(issuer, jwks) || !expectedPath.equals(jwks.getPath())) {
                offenders.add("OIDC_JWK_SET_URI must use the issuer origin and /protocol/openid-connect/certs path");
            }
        }

        if (!offenders.isEmpty()) {
            throw new IllegalStateException("Refusing to start: production OIDC configuration is invalid: "
                    + String.join("; ", offenders));
        }
    }

    private static java.net.URI safeHttpsUri(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            java.net.URI uri = java.net.URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) return null;
            String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
            if ("localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host)) return null;
            return uri;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean sameOrigin(java.net.URI left, java.net.URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && left.getPort() == right.getPort();
    }

    private static boolean isProductionProfile(Environment environment) {
        return java.util.Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production"));
    }

    private static void checkPostgresTransport(List<String> offenders, Environment environment) {
        final String property = "spring.datasource.url";
        final String envVar = "SUPABASE_DB_URL";
        final String value = environment.getProperty(property);
        final String label = property + " (env " + envVar + ")";
        if (value == null || value.isBlank()) {
            offenders.add(label + " is missing or blank");
        } else if (!value.regionMatches(true, 0, "jdbc:postgresql://", 0, "jdbc:postgresql://".length())) {
            offenders.add(label + " must be a PostgreSQL JDBC URL");
        } else if (!POSTGRES_SSL_MODE.matcher(value).find()) {
            offenders.add(label + " must set sslmode=require, verify-ca, or verify-full");
        }

        final String username = environment.getProperty("spring.datasource.username");
        if (username == null || username.isBlank()) {
            offenders.add("spring.datasource.username (env SUPABASE_DB_USER) is missing or blank");
        }
    }

    private static void checkHttpsEndpoint(List<String> offenders, Environment environment,
                                           String property, String envVar) {
        final String value = environment.getProperty(property);
        final String label = property + " (env " + envVar + ")";
        if (value == null || value.isBlank()) {
            offenders.add(label + " is missing or blank");
            return;
        }
        try {
            java.net.URI uri = java.net.URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getUserInfo() != null) {
                offenders.add(label + " must be an absolute HTTPS URL without embedded credentials");
            }
        } catch (IllegalArgumentException exception) {
            offenders.add(label + " must be a valid absolute HTTPS URL");
        }
    }

    private static void checkSecureEndpoint(List<String> offenders, Environment environment,
                                            String property, String envVar, String requiredScheme) {
        final String value = environment.getProperty(property);
        final String label = property + " (env " + envVar + ")";
        if (value == null || value.isBlank()) {
            offenders.add(label + " is missing or blank");
            return;
        }
        try {
            java.net.URI uri = java.net.URI.create(value);
            if (!requiredScheme.equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getHost().isBlank() || uri.getUserInfo() != null) {
                offenders.add(label + " must be an absolute " + requiredScheme.toUpperCase(Locale.ROOT)
                        + " URL without embedded credentials");
            }
        } catch (IllegalArgumentException exception) {
            offenders.add(label + " must be a valid absolute " + requiredScheme.toUpperCase(Locale.ROOT) + " URL");
        }
    }
    }

    private static void check(List<String> offenders, Environment environment, String property,
                              String envVar, String devDefault, int minimumLength) {
        String value = environment.getProperty(property);
        String label = property + " (env " + envVar + ")";
        if (value == null || value.isBlank()) {
            offenders.add(label + " is missing or blank");
        } else if (value.equals(devDefault)) {
            offenders.add(label + " uses the development default");
        } else if (value.trim().length() < minimumLength) {
            offenders.add(label + " must contain at least " + minimumLength + " characters");
        }
    }

    /**
     * Test contexts boot the main application.yaml on purpose (no separate test properties file),
     * so defaults like {@code education} are the intended development state there.
     */
    private static boolean runningUnderTestHarness() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String name = frame.getClassName();
            if (name.startsWith("org.junit.") || name.startsWith("org.testng.")
                    || name.startsWith("org.springframework.test.")
                    || name.startsWith("org.springframework.boot.test.")) {
                return true;
            }
        }
        return false;
    }
}
