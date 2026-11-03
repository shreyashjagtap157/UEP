package com.universalplatform.security;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;
import com.universalplatform.tenancy.LocalTenantBootstrapService;

@Configuration
class SecurityConfiguration {
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration config = new CorsConfiguration();
        // Allow the deployed frontend pages.dev domains and localhost for development
        config.setAllowedOriginPatterns(List.of(
            "https://uep-frontend-testing.pages.dev",
            "https://*.uep-frontend-testing.pages.dev",
            "http://localhost:*",
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:8080",
            "http://127.0.0.1:*"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        config.setExposedHeaders(List.of("Location"));

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiCredentialService credentials,
            TenantContext tenantContext, com.universalplatform.tenancy.TenantDirectory tenants,
            ObjectProvider<LocalTenantBootstrapService> localTenantBootstrap,
            @Value("${platform.local-auth.enabled:false}") boolean localAuthEnabled,
            @Value("${UEP_LOCAL_AUTH_TOKEN:}") String localAuthToken,
            @Value("${UEP_LOCAL_AUTH_SESSION_ID:}") String localAuthSessionId,
            @Value("${UEP_LOCAL_AUTH_EXPIRES_AT:}") String localAuthExpiresAt) throws Exception {
        // These filters are deliberately owned by the security chain rather than registered as
        // application Filter beans. AOP-proxying a GenericFilterBean breaks its final init()
        // method because the proxy does not initialize GenericFilterBean's logger field.
        ApiCredentialFilter apiCredentialFilter = new ApiCredentialFilter(credentials);
        JwtTenantContextFilter tenantFilter = new JwtTenantContextFilter();
        TenantLifecycleFilter tenantLifecycleFilter = new TenantLifecycleFilter(tenantContext, tenants);
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/api/v1/platform/version", "/api/v1/credentials/verify/**").permitAll();
                    if (localAuthEnabled) {
                        auth.requestMatchers(
                                "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .addFilterBefore(apiCredentialFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class);

        if (localAuthEnabled) {
            LocalTenantBootstrapService bootstrap = localTenantBootstrap.getIfAvailable();
            if (bootstrap == null) {
                throw new IllegalStateException("Local API authentication is enabled outside the local-native profile.");
            }
            if (localAuthToken == null || localAuthToken.isBlank()
                    || localAuthSessionId == null || localAuthSessionId.isBlank()
                    || localAuthExpiresAt == null || localAuthExpiresAt.isBlank()) {
                throw new IllegalStateException("Local API authentication requires a launcher-issued token, session ID and expiry.");
            }
            LocalDevelopmentAuthenticationFilter localFilter = new LocalDevelopmentAuthenticationFilter(
                    localAuthToken, localAuthSessionId, Instant.parse(localAuthExpiresAt), bootstrap);
            http.addFilterBefore(localFilter, ApiCredentialFilter.class);
        }

        return http
                .addFilterAfter(tenantLifecycleFilter, JwtTenantContextFilter.class)
                .build();
    }
}
