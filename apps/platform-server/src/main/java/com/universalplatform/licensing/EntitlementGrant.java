package com.universalplatform.licensing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entitlement_grant")
class EntitlementGrant {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private FeatureKey featureKey;

    @Column(nullable = false)
    private boolean enabled;

    private Instant validFrom;
    private Instant validUntil;

    protected EntitlementGrant() {}

    boolean isEffectiveAt(Instant now) {
        return enabled
                && (validFrom == null || !now.isBefore(validFrom))
                && (validUntil == null || now.isBefore(validUntil));
    }

    FeatureKey featureKey() { return featureKey; }
}
