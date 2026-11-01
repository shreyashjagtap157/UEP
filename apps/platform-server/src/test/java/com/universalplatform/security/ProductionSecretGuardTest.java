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
                .hasMessageContaining("SUPABASE_DB_PASS")
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
    void developmentModeCannotBypassChecksUnderProductionProfile() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(ProductionSecretGuard.DEV_MODE_PROPERTY, "true");
        environment.setActiveProfiles("production");
        ProductionSecretGuard guard = new ProductionSecretGuard(environment);

        assertThatThrownBy(guard::rejectDevelopmentDefaults)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be enabled with the prod/production Spring profile");
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
    @Test
    void allowsMissingLiveKitCredentialsOnlyWhenIntegrationIsExplicitlyDisabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.password", "S3cure-database-password-2026")
                .withProperty("platform.recording.playback-secret", "playback-key-2026-0123456789abcdef")
                .withProperty("platform.integrations.secret-key", "integration-key-2026-0123456789abcdef")
                .withProperty("platform.live-learning.livekit.enabled", "false");

        ProductionSecretGuard.validateProductionSecrets(environment);
    }

    @Test
    void productionOidcRequiresMatchingHttpsIssuerAndJwksOnSameOrigin() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                        "https://identity.example.test/realms/education")
                .withProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                        "https://identity.example.test/realms/education/protocol/openid-connect/certs");

        ProductionSecretGuard.validateProductionOidcConfiguration(environment);
    }

    @Test
    void productionOidcRejectsHttpLocalhostAndMismatchedJwksOrigins() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                        "http://localhost:8081/realms/education")
                .withProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                        "https://other.example.test/realms/education/protocol/openid-connect/certs");

        assertThatThrownBy(() -> ProductionSecretGuard.validateProductionOidcConfiguration(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OIDC_ISSUER_URI");
    }

    @Test
    void productionOidcRejectsJwksFromDifferentOrigin() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                        "https://identity.example.test/realms/education")
                .withProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                        "https://other.example.test/realms/education/protocol/openid-connect/certs");

        assertThatThrownBy(() -> ProductionSecretGuard.validateProductionOidcConfiguration(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OIDC_JWK_SET_URI");
    }

    @Test
    void productionOidcRejectsIncorrectIssuerRealmPath() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                        "https://identity.example.test/realms/not-education")
                .withProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                        "https://identity.example.test/realms/not-education/protocol/openid-connect/certs");

        assertThatThrownBy(() -> ProductionSecretGuard.validateProductionOidcConfiguration(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OIDC_ISSUER_URI");
    }


    @Test
    void vercelProfileCannotRunWithoutProductionProfile() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.password", "not-used-by-this-profile-check");
        environment.setActiveProfiles("vercel");
        ProductionSecretGuard guard = new ProductionSecretGuard(environment);

        assertThatThrownBy(guard::rejectDevelopmentDefaults)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vercel Spring profile requires the production profile");
    }


}
