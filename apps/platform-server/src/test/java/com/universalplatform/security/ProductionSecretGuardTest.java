package com.universalplatform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSecretGuardTest {
    @Test
    void rejectsMissingAndBlankProductionSecrets() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.password", " ")
                .withProperty("platform.recording.playback-secret", "")
                .withProperty("platform.integrations.secret-key", "integration-secret-that-is-long-enough")
                .withProperty("platform.live-learning.livekit.api-key", "livekit-production-key-123")
                .withProperty("platform.live-learning.livekit.api-secret",
                        "livekit-production-secret-which-is-long-enough");

        assertThatThrownBy(() -> ProductionSecretGuard.validateProductionSecrets(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATABASE_PASSWORD")
                .hasMessageContaining("PLATFORM_RECORDING_PLAYBACK_SECRET")
                .satisfies(exception -> assertThat(exception.getMessage())
                        .doesNotContain("integration-secret-that-is-long-enough"));
    }

    @Test
    void rejectsDevelopmentDefaultsAndTooShortSecrets() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.password", "education")
                .withProperty("platform.recording.playback-secret", "short")
                .withProperty("platform.integrations.secret-key", "change-me-in-production")
                .withProperty("platform.live-learning.livekit.api-key", "devkey")
                .withProperty("platform.live-learning.livekit.api-secret", "secret");

        assertThatThrownBy(() -> ProductionSecretGuard.validateProductionSecrets(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("uses the development default")
                .hasMessageContaining("must contain at least 32 characters")
                .hasMessageContaining("PLATFORM_INTEGRATION_SECRET_KEY")
                .hasMessageContaining("LIVEKIT_API_KEY");
    }

    @Test
    void acceptsStrongNonDefaultProductionSecrets() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.password", "S3cure-database-password-2026")
                .withProperty("platform.recording.playback-secret", "playback-key-2026-0123456789abcdef")
                .withProperty("platform.integrations.secret-key", "integration-key-2026-0123456789abcdef")
                .withProperty("platform.live-learning.livekit.api-key", "livekit-production-key-2026")
                .withProperty("platform.live-learning.livekit.api-secret", "livekit-secret-2026-0123456789abcdef");

        ProductionSecretGuard.validateProductionSecrets(environment);
        assertThat(environment.getProperty("spring.datasource.password")).isNotBlank();
    }
}
