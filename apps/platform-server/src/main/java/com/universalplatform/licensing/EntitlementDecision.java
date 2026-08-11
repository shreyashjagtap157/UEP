package com.universalplatform.licensing;

public record EntitlementDecision(FeatureKey feature, boolean allowed, String reason) {
    public static EntitlementDecision allow(FeatureKey feature) {
        return new EntitlementDecision(feature, true, "ENTITLED");
    }

    public static EntitlementDecision deny(FeatureKey feature, String reason) {
        return new EntitlementDecision(feature, false, reason);
    }
}
