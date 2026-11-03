package com.universalplatform.security;

import com.universalplatform.tenancy.LocalTenantBootstrapService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Local-only authentication for a Vite frontend connected to the actual API.
 *
 * The launcher supplies a random, short-lived token. It is accepted only for
 * loopback requests in the explicit local-native Spring profile; it is not a
 * JWT validation bypass and is never enabled by the default/prod profiles.
 */
final class LocalDevelopmentAuthenticationFilter extends OncePerRequestFilter {
    static final String HEADER = "X-UEP-Local-Auth";
    private static final String SUBJECT = "uep-local-demo-owner";
    private static final Duration MAX_SESSION = Duration.ofHours(12);

    private final byte[] expectedToken;
    private final String sessionId;
    private final Instant sessionExpiresAt;
    private final LocalTenantBootstrapService tenantBootstrap;
    private final LocalDevelopmentSessionControl sessionControl;

    LocalDevelopmentAuthenticationFilter(
            String token,
            String sessionId,
            Instant sessionExpiresAt,
            LocalTenantBootstrapService tenantBootstrap,
            LocalDevelopmentSessionControl sessionControl) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Local API token must not be blank.");
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("Local session ID must not be blank.");
        if (sessionExpiresAt == null) throw new IllegalArgumentException("Local session expiry is required.");
        if (sessionExpiresAt.isAfter(Instant.now().plus(MAX_SESSION))) {
            throw new IllegalArgumentException("Local API sessions cannot exceed twelve hours.");
        }
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
        this.sessionId = sessionId;
        this.sessionExpiresAt = sessionExpiresAt;
        this.tenantBootstrap = tenantBootstrap;
        this.sessionControl = sessionControl;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        if (supplied == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!request.getRequestURI().startsWith("/api/")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "The local development credential is valid only for API routes.");
            return;
        }
        if (request.getHeader("Authorization") != null || request.getHeader("X-API-Key") != null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Use only one authentication mechanism per request.");
            return;
        }
        if (!isLoopback(request.getRemoteAddr())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Local development authentication is limited to loopback requests.");
            return;
        }
        byte[] suppliedBytes = supplied.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedToken, suppliedBytes)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "The local development session is invalid.");
            return;
        }

        if (!sessionControl.isActive()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "The local development session has been revoked; restart the native launcher to reconnect.");
            return;
        }

        Instant now = Instant.now();
        if (!now.isBefore(sessionExpiresAt)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "The local development session expired; restart the native launcher.");
            return;
        }

        Jwt jwt = Jwt.withTokenValue("local-development-session")
                .header("alg", "none")
                .subject(LocalDevelopmentSessionControl.LOCAL_SUBJECT)
                .issuedAt(now)
                .expiresAt(sessionExpiresAt)
                .claim("tenant_id", tenantBootstrap.requireTenantId().toString())
                .claim("email", "admin@local.uep")
                .claim("name", "UEP Local Development Owner")
                .claim("sid", sessionId)
                .claim("jti", sessionId)
                .claim("acr", "urn:uep:local-development")
                .claim("amr", List.of("local-development"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        chain.doFilter(request, response);
    }

    private static boolean isLoopback(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) return false;
        try {
            return InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException ignored) {
            return false;
        }
    }
}
