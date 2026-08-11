package com.universalplatform.licensing;

import com.universalplatform.security.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/licensing/entitlements")
final class EntitlementController {
    private final TenantContext tenantContext;
    private final EntitlementService entitlements;

    EntitlementController(TenantContext tenantContext, EntitlementService entitlements) {
        this.tenantContext = tenantContext;
        this.entitlements = entitlements;
    }

    @GetMapping("/{feature}/decision")
    EntitlementDecision decision(@PathVariable FeatureKey feature) {
        return entitlements.decide(tenantContext.requireTenantId(), feature);
    }
}
