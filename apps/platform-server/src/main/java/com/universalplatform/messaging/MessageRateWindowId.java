package com.universalplatform.messaging;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Embeddable
record MessageRateWindowId(UUID tenantId, UUID membershipId, String windowType, Instant windowStart)
        implements Serializable {}
