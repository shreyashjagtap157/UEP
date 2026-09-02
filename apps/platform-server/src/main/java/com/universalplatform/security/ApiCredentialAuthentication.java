package com.universalplatform.security;
import java.util.*;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
public final class ApiCredentialAuthentication extends AbstractAuthenticationToken {
    private final ScopedCredentialPrincipal context;
    public ApiCredentialAuthentication(ScopedCredentialPrincipal context, Collection<? extends GrantedAuthority> authorities){super(authorities);this.context=context;setAuthenticated(true);}
    public ScopedCredentialPrincipal context(){return context;}
    @Override public Object getCredentials(){return null;}
    @Override public Object getPrincipal(){return context;}
}
