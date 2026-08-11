package com.universalplatform.notification;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
record NotificationPreferenceId(UUID membershipId, String eventType) implements Serializable {}
