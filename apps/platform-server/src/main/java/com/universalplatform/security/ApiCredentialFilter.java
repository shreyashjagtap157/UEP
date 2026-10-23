package com.universalplatform.security;
import jakarta.servlet.*; import jakarta.servlet.http.*; import java.io.IOException; import java.util.*; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
@Component
public final class ApiCredentialFilter extends OncePerRequestFilter {
    private final ApiCredentialService credentials;
    public ApiCredentialFilter(ApiCredentialService credentials){this.credentials=credentials;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String raw=request.getHeader("X-API-Key");
        if(raw!=null&&!raw.isBlank() && request.getHeader("Authorization")!=null){ response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Use either X-API-Key or Authorization, not both"); return; }
        if(raw!=null&&!raw.isBlank() && SecurityContextHolder.getContext().getAuthentication()==null){
            var candidate=credentials.authenticate(raw);
            if(candidate.isPresent()){
                var c=candidate.get(); Set<String> scopes=ApiCredentialService.scopeSet(c.scopes());
                SecurityContextHolder.getContext().setAuthentication(new ApiCredentialAuthentication(new ApiCredentialPrincipal(c.id(),c.tenantId(),c.membershipId(),scopes),List.of()));
                RequestTenantContext.set(c.tenantId()); credentials.touch(c);
            }
        }
        chain.doFilter(request,response);
    }
}
