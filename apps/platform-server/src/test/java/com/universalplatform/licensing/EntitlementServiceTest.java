package com.universalplatform.licensing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class EntitlementServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-11T04:50:00Z");
    private static final UUID TENANT = UUID.fromString("018f6f58-3d8c-7c6f-9e1b-eef81d4af111");

    @Mock SubscriptionRepository subscriptions;
    @Mock EntitlementGrantRepository grants;
    private EntitlementService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new EntitlementService(subscriptions, grants, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void deniesWhenNoSubscriptionExists() {
        when(subscriptions.findByTenantId(TENANT)).thenReturn(Optional.empty());
        assertThat(service.decide(TENANT, FeatureKey.ASSESSMENTS).allowed()).isFalse();
        assertThat(service.decide(TENANT, FeatureKey.ASSESSMENTS).reason()).isEqualTo("NO_SUBSCRIPTION");
    }

    @Test
    void deniesSuspendedSubscriptionEvenWhenFeatureCouldBeGranted() {
        when(subscriptions.findByTenantId(TENANT))
                .thenReturn(Optional.of(new Subscription(UUID.randomUUID(), TENANT, SubscriptionStatus.SUSPENDED, NOW.minusSeconds(60))));
        assertThat(service.decide(TENANT, FeatureKey.ASSESSMENTS).reason()).isEqualTo("SUBSCRIPTION_SUSPENDED");
    }
}
