package com.universalplatform.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationOutboxRecipientRepository extends JpaRepository<NotificationOutboxRecipient, NotificationOutboxRecipientId> {
    List<NotificationOutboxRecipient> findAllByIdOutboxId(UUID outboxId);
}
