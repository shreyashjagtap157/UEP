package com.universalplatform.tenancy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants")
class TenantController {

    private final TenantDirectoryService tenantService;
    private final com.universalplatform.identity.AuthorizationService authorization;

    TenantController(TenantDirectoryService tenantService,
                     com.universalplatform.identity.AuthorizationService authorization) {
        this.tenantService = tenantService;
        this.authorization = authorization;
    }

    private void requirePlatformSuperAdmin() {
        authorization.requirePlatformSuperAdmin();
    }

    @GetMapping
    List<Tenant> listAll() {
        requirePlatformSuperAdmin();
        return tenantService.findAll();
    }

    @GetMapping("/{id}")
    Tenant details(@PathVariable UUID id) {
        requirePlatformSuperAdmin();
        return tenantService.details(id);
    }

    @PostMapping
    Tenant create(@Valid @RequestBody TenantCreateRequest request) {
        requirePlatformSuperAdmin();
        return tenantService.create(request.slug(), request.displayName(), request.subscriptionTier());
    }

    @PutMapping("/{id}")
    Tenant update(@PathVariable UUID id, @Valid @RequestBody TenantUpdateRequest request) {
        requirePlatformSuperAdmin();
        return tenantService.update(id, request.displayName(), request.subscriptionTier());
    }

    @PatchMapping("/{id}/deployment")
    Tenant updateDeployment(@PathVariable UUID id,
                            @RequestParam String platform,
                            @RequestParam String status) {
        requirePlatformSuperAdmin();
        return tenantService.updateDeployment(id, platform, status);
    }

    @PatchMapping("/{id}/licensed-users")
    Tenant incrementLicensedUsers(@PathVariable UUID id) {
        requirePlatformSuperAdmin();
        return tenantService.incrementLicensedUsers(id);
    }

    @PatchMapping("/{id}/decrement-licensed-users")
    Tenant decrementLicensedUsers(@PathVariable UUID id) {
        requirePlatformSuperAdmin();
        return tenantService.decrementLicensedUsers(id);
    }

    @GetMapping("/by-status")
    List<Tenant> findByStatus(@RequestParam(required = false) String platform,
                              @RequestParam(required = false) String status) {
        requirePlatformSuperAdmin();
        return tenantService.findByDeploymentStatus(platform, status);
    }

    @GetMapping("/license-limit")
    List<Tenant> findAtLicenseLimit() {
        requirePlatformSuperAdmin();
        return tenantService.findAtLicenseLimit();
    }

    @GetMapping("/{id}/stats")
    TenantStats stats(@PathVariable UUID id) {
        requirePlatformSuperAdmin();
        return tenantService.stats(id);
    }

    // --- Request DTOs ---

    record TenantCreateRequest(
            @NotBlank @Size(max = 80) String slug,
            @NotBlank @Size(max = 200) String displayName,
            @NotNull @Size(max = 32) String subscriptionTier
    ) {}

    record TenantUpdateRequest(
            @Size(max = 200) String displayName,
            @Size(max = 32) String subscriptionTier
    ) {}

    record TenantDetails(
            UUID id,
            String slug,
            String displayName,
            TenantStatus status,
            String deploymentPlatform,
            String deploymentStatus,
            int licensedUserCount,
            int maxLicensedUsers,
            int currentTeacherCount,
            int currentStudentCount,
            Instant onboardingDate,
            String subscriptionTier,
            String customDomain
    ) {}
}