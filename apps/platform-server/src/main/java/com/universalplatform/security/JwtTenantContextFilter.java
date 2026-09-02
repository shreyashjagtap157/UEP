package com.universalplatform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.universalplatform.security.ScopedCredentialPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
final class JwtTenantContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof ScopedCredentialPrincipal api) {
                RequestTenantContext.set(api.tenantId());
            } else if (authentication instanceof JwtAuthenticationToken token) {
                Jwt jwt = token.getToken();
                String claim = jwt.getClaimAsString("tenant_id");
                if (claim != null && !claim.isBlank()) {
                    RequestTenantContext.set(UUID.fromString(claim));
                }
            }
            chain.doFilter(request, response);
        } catch (IllegalArgumentException invalidTenantClaim) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid tenant context");
        } finally {
            RequestTenantContext.clear();
        }
    }
}
