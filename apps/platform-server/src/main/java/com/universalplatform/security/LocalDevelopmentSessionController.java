package com.universalplatform.security;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Revokes the one launcher-managed local-native session in this Spring process. */
@RestController
@Profile("local-native")
@RequestMapping("/api/v1/local/session")
class LocalDevelopmentSessionController {
    private final LocalDevelopmentSessionControl session;

    LocalDevelopmentSessionController(LocalDevelopmentSessionControl session) {
        this.session = session;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !session.revoke(jwt.getToken().getSubject(), jwt.getToken().getClaimAsString("sid"))) {
            throw new AccessDeniedException("The current request is not the active local-native session.");
        }
    }
}
