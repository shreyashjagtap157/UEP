package com.universalplatform.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Identity", description = "Current authenticated user identity, memberships, and role assignments")
@RestController
@RequestMapping("/api/v1/me")
class CurrentIdentityController {
    private final IdentityAdministrationService identities;

    CurrentIdentityController(IdentityAdministrationService identities) {
        this.identities = identities;
    }

    @Operation(summary = "Get current authenticated identity", description = "Resolves the caller identity, tenant context, active memberships, and assigned permission keys.")
    @GetMapping
    IdentityAdministrationService.CurrentIdentityView current() {
        return identities.currentIdentity();
    }
}
