package com.universalplatform.messaging;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MessageRateWindowRepository extends JpaRepository<MessageRateWindow, MessageRateWindowId> {
    @Modifying
    @Query(value = "delete from message_rate_window where window_start < :cutoff", nativeQuery = true)
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
