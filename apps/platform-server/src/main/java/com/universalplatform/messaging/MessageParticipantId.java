package com.universalplatform.messaging;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
record MessageParticipantId(UUID conversationId, UUID membershipId) implements Serializable {}
