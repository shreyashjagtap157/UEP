package com.universalplatform.security;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
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

    private final Environment environment;

    ProductionSecretGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void rejectDevelopmentDefaults() {
        boolean developmentMode = environment.getProperty(DEV_MODE_PROPERTY, Boolean.class, false);
        boolean productionProfile = java.util.Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production"));
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
        check(offenders, environment, "spring.datasource.password", "DATABASE_PASSWORD", "education", 16);
        check(offenders, environment, "platform.recording.playback-secret",
                "PLATFORM_RECORDING_PLAYBACK_SECRET", "change-me-in-production", 32);
        check(offenders, environment, "platform.integrations.secret-key",
                "PLATFORM_INTEGRATION_SECRET_KEY", "change-me-in-production", 32);
        check(offenders, environment, "platform.live-learning.livekit.api-key", "LIVEKIT_API_KEY", "devkey", 16);
        check(offenders, environment, "platform.live-learning.livekit.api-secret", "LIVEKIT_API_SECRET", "secret", 32);
        if (offenders.isEmpty()) return;
        throw new IllegalStateException(
                "Refusing to start: one or more production secrets are missing, blank, too short, "
                        + "or still at published development defaults. Set strong values through the named "
                        + "environment variables (or set " + DEV_MODE_PROPERTY
                        + "=true / env PLATFORM_DEV_MODE=true for non-production environments only): "
                        + String.join("; ", offenders));
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
