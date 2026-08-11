package com.universalplatform.communication;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
record AnnouncementAcknowledgementId(UUID announcementId, UUID membershipId) implements Serializable {}
