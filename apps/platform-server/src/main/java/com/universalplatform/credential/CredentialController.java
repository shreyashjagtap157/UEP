package com.universalplatform.credential;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Credentials", description = "Endpoints for managing credential templates, issuing credentials, revoking credentials, and public verification")
@RestController
@RequestMapping("/api/v1/credentials")
class CredentialController {
    private final CredentialService s;

    CredentialController(CredentialService s) {
        this.s = s;
    }

    @Operation(
            summary = "List credentials",
            description = "Retrieves credentials, optionally filtered by the membership ID query parameter."
    )
    @GetMapping
    List<CredentialService.CredentialView> credentials(@RequestParam(required = false) UUID membershipId) {
        return s.credentials(membershipId);
    }

    @Operation(
            summary = "List credential templates",
            description = "Retrieves all available credential templates in the tenant."
    )
    @GetMapping("/templates")
    List<CredentialService.TemplateView> templates() {
        return s.templates();
    }

    @Operation(
            summary = "Create credential template",
            description = "Creates a new credential template. Requires code, name, description, and credential type in the request body."
    )
    @PostMapping("/templates")
    CredentialService.TemplateView template(@Valid @RequestBody TemplateRequest r) {
        return s.createTemplate(r.code, r.name, r.description, r.credentialType);
    }

    @Operation(
            summary = "Issue credential",
            description = "Issues a credential to a member. Requires templateId, membershipId, reason, and optional sourceId in the request body."
    )
    @PostMapping
    CredentialService.CredentialView issue(@Valid @RequestBody IssueRequest r) {
        return s.issue(r.templateId, r.membershipId, r.reason, r.sourceId);
    }

    @Operation(
            summary = "Revoke credential",
            description = "Revokes an issued credential identified by ID. Requires reason and expectedVersion in the request body."
    )
    @PostMapping("/{id}/revoke")
    void revoke(@PathVariable UUID id, @Valid @RequestBody RevokeRequest r) {
        s.revoke(id, r.reason, r.expectedVersion);
    }

    @Operation(
            summary = "Verify credential publicly",
            description = "Publicly verifies a credential using its unique verification code passed as a path variable."
    )
    @GetMapping("/verify/{code}")
    CredentialService.PublicVerification verify(@PathVariable String code) {
        return s.publicVerify(code);
    }

    record TemplateRequest(
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotBlank @Pattern(regexp = "[A-Z_]{2,32}") String credentialType
    ) {}

    record IssueRequest(
            @NotNull UUID templateId,
            @NotNull UUID membershipId,
            @NotBlank @Size(max = 500) String reason,
            UUID sourceId
    ) {}

    record RevokeRequest(
            @NotBlank @Size(max = 500) String reason,
            long expectedVersion
    ) {}
}
