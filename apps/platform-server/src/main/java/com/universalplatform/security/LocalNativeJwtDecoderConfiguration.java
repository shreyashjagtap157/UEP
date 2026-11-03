package com.universalplatform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Prevents the local-native backend from performing remote OIDC issuer discovery.
 * LocalDevelopmentAuthenticationFilter is the only interactive auth mechanism in this profile.
 */
@Configuration
@Profile("local-native")
class LocalNativeJwtDecoderConfiguration {
    @Bean
    JwtDecoder localNativeJwtDecoder() {
        return token -> {
            throw new BadJwtException("OIDC bearer tokens are disabled in the local-native profile.");
        };
    }
}
