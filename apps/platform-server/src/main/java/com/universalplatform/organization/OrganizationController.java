package com.universalplatform.organization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization")
class OrganizationController {
    private final OrganizationService organization;

    OrganizationController(OrganizationService organization) {
        this.organization = organization;
    }

    @GetMapping("/branches")
    OrganizationPage<OrganizationService.BranchView> branches(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return organization.listBranches(page, size);
    }

    @GetMapping("/branches/{branchId}")
    OrganizationService.BranchView branch(@PathVariable UUID branchId) {
        return organization.branch(branchId);
    }

    @PostMapping("/branches")
    OrganizationService.BranchView createBranch(@Valid @RequestBody BranchRequest request) {
        return organization.createBranch(request.code(), request.displayName(), request.timezone());
    }

    @PutMapping("/branches/{branchId}")
    OrganizationService.BranchView updateBranch(@PathVariable UUID branchId, @Valid @RequestBody BranchRequest request) {
        if (request.status() == null) throw new IllegalArgumentException("Branch status is required when updating a branch");
        return organization.updateBranch(branchId, request.code(), request.displayName(), request.timezone(),
                request.status(), request.expectedVersion());
    }

    @GetMapping("/settings")
    OrganizationService.SettingsView settings() {
        return organization.settings();
    }

    @PutMapping("/settings")
    OrganizationService.SettingsView updateSettings(@Valid @RequestBody SettingsRequest request) {
        return organization.updateSettings(request.defaultTimezone(), request.defaultLocale(), request.weekStartsOn(),
                request.supportEmail(), request.supportUrl(), request.expectedVersion());
    }

    record BranchRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 200) String displayName,
            @NotBlank @Size(max = 80) String timezone,
            BranchStatus status,
            long expectedVersion) {}

    record SettingsRequest(
            @NotBlank @Size(max = 80) String defaultTimezone,
            @NotBlank @Size(max = 35) String defaultLocale,
            @Min(1) @Max(7) short weekStartsOn,
            @Email @Size(max = 320) String supportEmail,
            @Size(max = 1000) String supportUrl,
            long expectedVersion) {}
}
