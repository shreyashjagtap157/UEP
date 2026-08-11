package com.universalplatform.communication;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
record AnnouncementRecipientId(UUID announcementId, UUID membershipId) implements Serializable {}
