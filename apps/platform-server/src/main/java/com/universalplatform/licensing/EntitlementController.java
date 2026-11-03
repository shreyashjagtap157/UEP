package com.universalplatform.licensing;

import com.universalplatform.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Licensing Entitlements", description = "Endpoints for checking feature entitlements and licensing decisions")
@RestController
@RequestMapping("/api/v1/licensing/entitlements")
class EntitlementController {
    private final TenantContext tenantContext;
    private final EntitlementService entitlements;

    EntitlementController(TenantContext tenantContext, EntitlementService entitlements) {
        this.tenantContext = tenantContext;
        this.entitlements = entitlements;
    }

    @Operation(
            summary = "Get entitlement decision",
            description = "Evaluates whether the current tenant is entitled to use the feature key specified in the path variable."
    )
    @GetMapping("/{feature}/decision")
    EntitlementDecision decision(@PathVariable FeatureKey feature) {
        return entitlements.decide(tenantContext.requireTenantId(), feature);
    }
}
