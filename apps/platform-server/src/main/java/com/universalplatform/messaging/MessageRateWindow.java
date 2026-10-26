package com.universalplatform.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "message_rate_window")
class MessageRateWindow {
    @EmbeddedId private MessageRateWindowId id;
    @Column(nullable = false) private int messageCount;
    @Column(nullable = false) private int reportCount;

    protected MessageRateWindow() {}
}
