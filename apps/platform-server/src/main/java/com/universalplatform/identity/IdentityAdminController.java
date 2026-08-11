package com.universalplatform.identity;

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

@RestController
@RequestMapping("/api/v1/identity")
class IdentityAdminController {
    private final IdentityAdministrationService identities;

    IdentityAdminController(IdentityAdministrationService identities) {
        this.identities = identities;
    }

    @PostMapping("/bootstrap-owner")
    IdentityAdministrationService.MemberView bootstrapOwner(@Valid @RequestBody ProvisionMembershipRequest request) {
        return identities.bootstrapOwner(request.toCommand());
    }

    @GetMapping("/memberships")
    IdentityPage<IdentityAdministrationService.MemberView> memberships(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return identities.list(page, size);
    }

    @GetMapping("/memberships/{membershipId}")
    IdentityAdministrationService.MemberView membership(@PathVariable UUID membershipId) {
        return identities.get(membershipId);
    }

    @PostMapping("/memberships")
    IdentityAdministrationService.MemberView provision(@Valid @RequestBody ProvisionMembershipRequest request) {
        return identities.provision(request.toCommand());
    }

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
