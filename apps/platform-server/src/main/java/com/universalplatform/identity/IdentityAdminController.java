package com.universalplatform.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Identity Administration", description = "Endpoints for managing identities, memberships, and tenant owner bootstrapping")
@RestController
@RequestMapping("/api/v1/identity")
class IdentityAdminController {
    private final IdentityAdministrationService identities;

    IdentityAdminController(IdentityAdministrationService identities) {
        this.identities = identities;
    }

    @Operation(
            summary = "Bootstrap tenant owner",
            description = "Bootstraps the initial owner membership for the tenant. Requires a ProvisionMembershipRequest body containing subject and displayName."
    )
    @PostMapping("/bootstrap-owner")
    IdentityAdministrationService.MemberView bootstrapOwner(@Valid @RequestBody ProvisionMembershipRequest request) {
        return identities.bootstrapOwner(request.toCommand());
    }

    @Operation(
            summary = "List memberships",
            description = "Retrieves a paginated list of memberships for the tenant. Accepts optional page and size query parameters."
    )
    @GetMapping("/memberships")
    IdentityPage<IdentityAdministrationService.MemberView> memberships(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return identities.list(page, size);
    }

    @Operation(
            summary = "Get membership by ID",
            description = "Retrieves details of a specific membership by its unique identifier passed in the path variable."
    )
    @GetMapping("/memberships/{membershipId}")
    IdentityAdministrationService.MemberView membership(@PathVariable UUID membershipId) {
        return identities.get(membershipId);
    }

    @Operation(
            summary = "Provision membership",
            description = "Provisions a new membership within the organization. Requires a ProvisionMembershipRequest body with subject, displayName, and optional roles/branch."
    )
    @PostMapping("/memberships")
    IdentityAdministrationService.MemberView provision(@Valid @RequestBody ProvisionMembershipRequest request) {
        return identities.provision(request.toCommand());
    }

    @Operation(
            summary = "Update membership",
            description = "Updates an existing membership's status, primary branch, or external reference. Requires the membership ID in the path and an UpdateMembershipRequest body with expectedVersion for optimistic locking."
    )
    @PatchMapping("/memberships/{membershipId}")
    IdentityAdministrationService.MemberView update(
            @PathVariable UUID membershipId,
            @Valid @RequestBody UpdateMembershipRequest request) {
        return identities.update(membershipId, new IdentityAdministrationService.UpdateMembershipCommand(
                request.status(), request.primaryBranchId(), request.externalReference(), request.expectedVersion()));
    }

    record ProvisionMembershipRequest(
            @NotBlank @Size(max = 160) String subject,
            @Size(max = 320) String email,
            @NotBlank @Size(max = 200) String displayName,
            UUID primaryBranchId,
            @Size(max = 160) String externalReference,
            List<UUID> roleIds) {
        IdentityAdministrationService.ProvisionMembershipCommand toCommand() {
            return new IdentityAdministrationService.ProvisionMembershipCommand(
                    subject, email, displayName, primaryBranchId, externalReference,
                    roleIds == null ? List.of() : List.copyOf(roleIds));
        }
    }

    record UpdateMembershipRequest(
            @NotNull MembershipStatus status,
            UUID primaryBranchId,
            @Size(max = 160) String externalReference,
            long expectedVersion) {}
}
