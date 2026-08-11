package com.universalplatform.licensing;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SubscriptionStatusTest {
    @Test
    void onlyTrialActiveAndGracePermitNewUsageByDefault() {
        assertThat(SubscriptionStatus.TRIAL.permitsNewUsage()).isTrue();
        assertThat(SubscriptionStatus.ACTIVE.permitsNewUsage()).isTrue();
        assertThat(SubscriptionStatus.GRACE.permitsNewUsage()).isTrue();
        assertThat(SubscriptionStatus.SUSPENDED.permitsNewUsage()).isFalse();
        assertThat(SubscriptionStatus.EXPIRED.permitsNewUsage()).isFalse();
        assertThat(SubscriptionStatus.REVOKED.permitsNewUsage()).isFalse();
        assertThat(SubscriptionStatus.TERMINATED.permitsNewUsage()).isFalse();
    }
}
