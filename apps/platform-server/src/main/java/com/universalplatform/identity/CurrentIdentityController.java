package com.universalplatform.identity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
class CurrentIdentityController {
    private final IdentityAdministrationService identities;

    CurrentIdentityController(IdentityAdministrationService identities) {
        this.identities = identities;
    }

    @GetMapping
    IdentityAdministrationService.CurrentIdentityView current() {
        return identities.currentIdentity();
    }
}
