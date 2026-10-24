package com.universalplatform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiCredentialService credentials,
            TenantContext tenantContext, com.universalplatform.tenancy.TenantDirectory tenants) throws Exception {
        // These filters are deliberately owned by the security chain rather than registered as
        // application Filter beans. AOP-proxying a GenericFilterBean breaks its final init()
        // method because the proxy does not initialize GenericFilterBean's logger field.
        ApiCredentialFilter apiCredentialFilter = new ApiCredentialFilter(credentials);
        JwtTenantContextFilter tenantFilter = new JwtTenantContextFilter();
        TenantLifecycleFilter tenantLifecycleFilter = new TenantLifecycleFilter(tenantContext, tenants);
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/api/v1/platform/version", "/api/v1/credentials/verify/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                 .addFilterBefore(apiCredentialFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(tenantLifecycleFilter, JwtTenantContextFilter.class)
                .build();
    }
}
