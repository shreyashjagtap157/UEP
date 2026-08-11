package com.universalplatform.licensing;

public enum SubscriptionStatus {
    TRIAL(true),
    ACTIVE(true),
    GRACE(true),
    SUSPENDED(false),
    EXPIRED(false),
    REVOKED(false),
    TERMINATED(false);

    private final boolean permitsNewUsage;

    SubscriptionStatus(boolean permitsNewUsage) {
        this.permitsNewUsage = permitsNewUsage;
    }

    public boolean permitsNewUsage() {
        return permitsNewUsage;
    }
}
