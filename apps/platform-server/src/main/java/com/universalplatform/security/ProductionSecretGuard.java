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
        if (environment.getProperty(DEV_MODE_PROPERTY, Boolean.class, false)) return;
        if (runningUnderTestHarness()) return;
        List<String> offenders = new ArrayList<>();
        check(offenders, "spring.datasource.password", "DATABASE_PASSWORD", "education");
        check(offenders, "platform.recording.playback-secret", "PLATFORM_RECORDING_PLAYBACK_SECRET", "change-me-in-production");
        check(offenders, "platform.integrations.secret-key", "PLATFORM_INTEGRATION_SECRET_KEY", "change-me-in-production");
        check(offenders, "platform.live-learning.livekit.api-key", "LIVEKIT_API_KEY", "devkey");
        check(offenders, "platform.live-learning.livekit.api-secret", "LIVEKIT_API_SECRET", "secret");
        if (offenders.isEmpty()) return;
        throw new IllegalStateException(
                "Refusing to start: deployment secrets are still at their public development defaults. "
                        + "Set real values via environment variables (or set "
                        + DEV_MODE_PROPERTY + "=true / env PLATFORM_DEV_MODE=true for non-production environments only): "
                        + String.join("; ", offenders));
    }

    private void check(List<String> offenders, String property, String envVar, String devDefault) {
        String value = environment.getProperty(property);
        // A missing property is not a violation; only a value exactly equal to the published dev
        // default cannot ship to production.
        if (value != null && value.equals(devDefault)) {
            offenders.add(property + " (env " + envVar + ")");
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
