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
    private final PlatformAdministratorGuard platformAdministrator;

    TenantController(TenantDirectoryService tenantService,
                     PlatformAdministratorGuard platformAdministrator) {
        this.tenantService = tenantService;
        this.platformAdministrator = platformAdministrator;
    }

    private void requirePlatformSuperAdmin() {
        platformAdministrator.requirePlatformSuperAdmin();
    }

    @GetMapping
    List<TenantDetails> listAll() {
        requirePlatformSuperAdmin();
        return tenantService.findAll().stream().map(TenantController::toDetails).toList();
    }

    @GetMapping("/{id}")
    TenantDetails details(@PathVariable UUID id) {
        requirePlatformSuperAdmin();
        return toDetails(tenantService.details(id));
    }

    @PostMapping
    TenantDetails create(@Valid @RequestBody TenantCreateRequest request) {
        requirePlatformSuperAdmin();
        return toDetails(tenantService.create(request.slug(), request.displayName(), request.subscriptionTier()));
    }

    @PutMapping("/{id}")
    TenantDetails update(@PathVariable UUID id, @Valid @RequestBody TenantUpdateRequest request) {
        requirePlatformSuperAdmin();
        return toDetails(tenantService.update(id, request.displayName(), request.subscriptionTier()));
    }

    @PatchMapping("/{id}/deployment")
    TenantDetails updateDeployment(@PathVariable UUID id,
                                    @RequestParam String platform,
                                    @RequestParam String status) {
        requirePlatformSuperAdmin();
        return toDetails(tenantService.updateDeployment(id, platform, status));
    }

    @PatchMapping("/{id}/licensed-users")
    TenantDetails incrementLicensedUsers(@PathVariable UUID id) {
        requirePlatformSuperAdmin();
        return toDetails(tenantService.incrementLicensedUsers(id));
    }

    @PatchMapping("/{id}/decrement-licensed-users")
    TenantDetails decrementLicensedUsers(@PathVariable UUID id) {
        requirePlatformSuperAdmin();
        return toDetails(tenantService.decrementLicensedUsers(id));
    }

    @GetMapping("/by-status")
    List<TenantDetails> findByStatus(@RequestParam(required = false) String platform,
                                      @RequestParam(required = false) String status) {
        requirePlatformSuperAdmin();
        return tenantService.findByDeploymentStatus(platform, status).stream()
                .map(TenantController::toDetails)
                .toList();
    }

    @GetMapping("/license-limit")
    List<TenantDetails> findAtLicenseLimit() {
        requirePlatformSuperAdmin();
        return tenantService.findAtLicenseLimit().stream().map(TenantController::toDetails).toList();
    }

    @GetMapping("/{id}/stats")
    TenantStats stats(@PathVariable UUID id) {
        requirePlatformSuperAdmin();
        return tenantService.stats(id);
    }

    private static TenantDetails toDetails(Tenant tenant) {
        return new TenantDetails(
                tenant.id(),
                tenant.slug(),
                tenant.displayName(),
                tenant.status(),
                tenant.deploymentPlatform(),
                tenant.deploymentStatus(),
                tenant.licensedUserCount(),
                tenant.maxLicensedUsers(),
                0,
                0,
                tenant.onboardingDate(),
                tenant.subscriptionTier(),
                tenant.customDomain()
        );
    }

    // --- Request and response DTOs ---

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