package com.universalplatform.organization;

import com.universalplatform.security.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit read-only organization boundary for other domain modules. */
@Service
public class OrganizationDirectory {
    private final TenantContext tenantContext;
    private final BranchRepository branches;
    private final OrganizationSettingsRepository settings;

    OrganizationDirectory(TenantContext tenantContext, BranchRepository branches, OrganizationSettingsRepository settings) {
        this.tenantContext = tenantContext;
        this.branches = branches;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public BranchReference requireBranch(UUID branchId) {
        Branch branch = branches.findByTenantIdAndId(tenantContext.requireTenantId(), branchId)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found"));
        return new BranchReference(branch.id(), branch.code(), branch.displayName(), branch.timezone(), branch.status());
    }


    @Transactional(readOnly = true)
    public String defaultTimezone() {
        UUID tenantId = tenantContext.requireTenantId();
        return settings.findById(tenantId).map(OrganizationSettings::defaultTimezone).orElse("UTC");
    }

    public record BranchReference(UUID id, String code, String displayName, String timezone, BranchStatus status) {}
}
