package com.universalplatform.licensing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "entitlement_limit")
public class EntitlementLimit {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private LimitKey limitKey;

    @Column(nullable = false)
    private long hardLimit;

    protected EntitlementLimit() {}
    public long hardLimit() { return hardLimit; }
}
