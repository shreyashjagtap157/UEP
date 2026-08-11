package com.universalplatform.licensing;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EntitlementService {
    private final SubscriptionRepository subscriptions;
    private final EntitlementGrantRepository grants;
    private final Clock clock;

    EntitlementService(SubscriptionRepository subscriptions, EntitlementGrantRepository grants) {
        this(subscriptions, grants, Clock.systemUTC());
    }

    EntitlementService(SubscriptionRepository subscriptions, EntitlementGrantRepository grants, Clock clock) {
        this.subscriptions = subscriptions;
        this.grants = grants;
        this.clock = clock;
    }

    public EntitlementDecision decide(UUID tenantId, FeatureKey feature) {
        Subscription subscription = subscriptions.findByTenantId(tenantId).orElse(null);
        if (subscription == null) {
            return EntitlementDecision.deny(feature, "NO_SUBSCRIPTION");
        }

        Instant now = clock.instant();
        if (!permitsNewUsage(subscription, now)) {
            return EntitlementDecision.deny(feature, "SUBSCRIPTION_" + subscription.status().name());
        }

        return grants.findByTenantIdAndFeatureKey(tenantId, feature)
                .filter(grant -> grant.isEffectiveAt(now))
                .map(grant -> EntitlementDecision.allow(feature))
                .orElseGet(() -> EntitlementDecision.deny(feature, "FEATURE_NOT_ENTITLED"));
    }

    private boolean permitsNewUsage(Subscription subscription, Instant now) {
        if (!subscription.status().permitsNewUsage()) {
            return false;
        }
        if (subscription.status() == SubscriptionStatus.GRACE
                && subscription.graceEndsAt() != null
                && !now.isBefore(subscription.graceEndsAt())) {
            return false;
        }
        return subscription.expiresAt() == null || now.isBefore(subscription.expiresAt());
    }
}
