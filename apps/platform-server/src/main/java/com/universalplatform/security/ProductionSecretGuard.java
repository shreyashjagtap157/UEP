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
        boolean productionProfile = isProductionProfile(environment);
        if (developmentMode && productionProfile) {
            throw new IllegalStateException(
                    "Refusing to start: " + DEV_MODE_PROPERTY
                            + " cannot be enabled with the prod/production Spring profile");
        }
        if (developmentMode) return;
        if (runningUnderTestHarness()) return;
        validateProductionSecrets(environment);
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
        }

        if (offenders.isEmpty()) return;
        throw new IllegalStateException(
                "Refusing to start: one or more production secrets or connection settings are missing, "
                        + "blank, insecure, too short, or still at published development defaults. Set strong "
                        + "values through the named environment variables (or set " + DEV_MODE_PROPERTY
                        + "=true / env PLATFORM_DEV_MODE=true for non-production environments only): "
                        + String.join("; ", offenders));
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
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getUserInfo() != null) {
                offenders.add(label + " must be an absolute HTTPS URL without embedded credentials");
            }
        } catch (IllegalArgumentException exception) {
            offenders.add(label + " must be a valid absolute HTTPS URL");
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
