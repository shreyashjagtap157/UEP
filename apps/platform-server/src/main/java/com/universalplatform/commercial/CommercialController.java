package com.universalplatform.commercial;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Commercial", description = "Endpoints for managing workspace subscription tiers, trial periods, grace periods, suspensions, and white-label branding")
@RestController
@RequestMapping("/api/v1/commercial")
class CommercialController {
    private final CommercialService s;

    CommercialController(CommercialService s) {
        this.s = s;
    }

    @Operation(
        summary = "Get current subscription details",
        description = "Retrieves current tenant subscription tier, status, recurring billing amounts, and grace/trial status."
    )
    @GetMapping("/subscription")
    CommercialService.SubscriptionView subscription() {
        return s.subscription();
    }

    @Operation(
        summary = "Configure subscription plan",
        description = "Updates workspace subscription plan and billing parameters. Requires PlanRequest body containing planCode, recurringAmount, 3-letter currency code, autoRenew flag, and expectedVersion."
    )
    @PutMapping("/subscription")
    CommercialService.SubscriptionView configure(@Valid @RequestBody PlanRequest r) {
        return s.configure(r.planCode, r.recurringAmount, r.currency, r.autoRenew, r.expectedVersion);
    }

    @Operation(
        summary = "Extend subscription trial period",
        description = "Extends the workspace subscription trial duration. Requires DaysRequest body specifying days to extend (between 1 and 365)."
    )
    @PostMapping("/subscription/trial")
    CommercialService.SubscriptionView trial(@Valid @RequestBody DaysRequest r) {
        return s.trial(r.days);
    }

    @Operation(
        summary = "Extend subscription grace period",
        description = "Extends the workspace subscription grace period. Requires DaysRequest body specifying days to extend (between 1 and 365)."
    )
    @PostMapping("/subscription/grace")
    CommercialService.SubscriptionView grace(@Valid @RequestBody DaysRequest r) {
        return s.grace(r.days);
    }

    @Operation(
        summary = "Suspend workspace subscription",
        description = "Suspends the workspace subscription and commercial access."
    )
    @PostMapping("/subscription/suspend")
    CommercialService.SubscriptionView suspend() {
        return s.suspend();
    }

    @Operation(
        summary = "Get white-label branding configuration",
        description = "Retrieves white-label branding settings including brand name, custom domain, theme colors, and assets."
    )
    @GetMapping("/white-label")
    CommercialService.WhiteLabelView whiteLabel() {
        return s.readWhiteLabel();
    }

    @Operation(
        summary = "Update white-label branding configuration",
        description = "Updates white-label branding configuration. Requires WhiteLabelRequest body containing brandName, primaryColor (hex or empty), logoUrl, faviconUrl, customDomain, enabled flag, and expectedVersion."
    )
    @PutMapping("/white-label")
    CommercialService.WhiteLabelView whiteLabel(@Valid @RequestBody WhiteLabelRequest r) {
        return s.whiteLabel(r.brandName, r.primaryColor, r.logoUrl, r.faviconUrl, r.customDomain, r.enabled, r.expectedVersion);
    }

    record PlanRequest(@NotBlank String planCode, @NotNull @DecimalMin("0.00") BigDecimal recurringAmount, @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency, boolean autoRenew, long expectedVersion) {}
    record DaysRequest(@Min(1) @Max(365) int days) {}
    record WhiteLabelRequest(@NotBlank String brandName, @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$") String primaryColor, String logoUrl, String faviconUrl, String customDomain, boolean enabled, long expectedVersion) {}
}
