package com.universalplatform.notification;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
record NotificationOutboxRecipientId(UUID outboxId, UUID membershipId) implements Serializable {}
