package com.universalplatform.tenancy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Tenants", description = "Endpoints for platform administrators to manage tenants, deployments, and licensing")
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

    @Operation(
            summary = "List all tenants",
            description = "Retrieves all tenants across the platform. Requires platform super administrator privileges."
    )
    @GetMapping
    List<TenantDetails> listAll() {
        requirePlatformSuperAdmin();
        return tenantService.findAll().stream().map(TenantController::toDetails).toList();
    }

    @Operation(
            summary = "Get tenant details",
            description = "Retrieves detailed information for a specific tenant identified by ID. Requires platform super administrator privileges."
    )
    @GetMapping("/{id}")
    TenantDetails details(@PathVariable UUID id) {
        requirePlatformSuperAdmin();
        return toDetails(tenantService.details(id));
    }

    @Operation(
            summary = "Create tenant",
            description = "Provisions a new tenant. Requires slug, displayName, and subscriptionTier in the request body. Requires platform super administrator privileges."
    )
    @PostMapping
    TenantDetails create(@Valid @RequestBody TenantCreateRequest request) {
        requirePlatformSuperAdmin();
        return toDetails(tenantService.create(request.slug(), request.displayName(), request.subscriptionTier()));
    }

    @Operation(
            summary = "Update tenant",
            description = "Updates display name and subscription tier for a tenant identified by ID. Requires platform super administrator privileges."
    )
    @PutMapping("/{id}")
    TenantDetails update(@PathVariable UUID id, @Valid @RequestBody TenantUpdateRequest request) {
        requirePlatformSuperAdmin();
        return toDetails(tenantService.update(id, request.displayName(), request.subscriptionTier()));
    }

    @Operation(
            summary = "Update tenant deployment",
            description = "Updates the deployment platform and status for a tenant identified by ID. Requires platform and status query parameters. Requires platform super administrator privileges."
    )
    @PatchMapping("/{id}/deployment")
    TenantDetails updateDeployment(@PathVariable UUID id,
                                    @RequestParam String platform,
                                    @RequestParam String status) {
        requirePlatformSuperAdmin();
        return toDetails(tenantService.updateDeployment(id, platform, status));
    }

    @Operation(
            summary = "Increment licensed users",
            description = "Increments the licensed user count for a tenant identified by ID. Requires platform super administrator privileges."
    )
    @PatchMapping("/{id}/licensed-users")
    TenantDetails incrementLicensedUsers(@PathVariable UUID id) {
        requirePlatformSuperAdmin();
        return toDetails(tenantService.incrementLicensedUsers(id));
    }

    @Operation(
            summary = "Decrement licensed users",
            description = "Decrements the licensed user count for a tenant identified by ID. Requires platform super administrator privileges."
    )
    @PatchMapping("/{id}/decrement-licensed-users")
    TenantDetails decrementLicensedUsers(@PathVariable UUID id) {
        requirePlatformSuperAdmin();
        return toDetails(tenantService.decrementLicensedUsers(id));
    }

    @Operation(
            summary = "Find tenants by deployment status",
            description = "Retrieves tenants filtered by optional platform and status query parameters. Requires platform super administrator privileges."
    )
    @GetMapping("/by-status")
    List<TenantDetails> findByStatus(@RequestParam(required = false) String platform,
                                      @RequestParam(required = false) String status) {
        requirePlatformSuperAdmin();
        return tenantService.findByDeploymentStatus(platform, status).stream()
                .map(TenantController::toDetails)
                .toList();
    }

    @Operation(
            summary = "Find tenants at license limit",
            description = "Retrieves all tenants that have reached their licensed user limit. Requires platform super administrator privileges."
    )
    @GetMapping("/license-limit")
    List<TenantDetails> findAtLicenseLimit() {
        requirePlatformSuperAdmin();
        return tenantService.findAtLicenseLimit().stream().map(TenantController::toDetails).toList();
    }

    @Operation(
            summary = "Get tenant statistics",
            description = "Retrieves usage statistics and metrics for a tenant identified by ID. Requires platform super administrator privileges."
    )
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
