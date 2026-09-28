package com.universalplatform.licensing;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "usage_counter")
public class UsageCounter {
    @EmbeddedId
    UsageCounterId id;
    @Column(nullable = false)
    Instant periodEnd;
    @Column(nullable = false)
    long consumed;
    @Version
    long version;

    protected UsageCounter() {
    }

    public UsageCounter(UsageCounterId id, Instant end) {
        this.id = id;
        periodEnd = end;
        consumed = 0;
    }

    public long consumed() {
        return consumed;
    }

    public void consume(long u) {
        consumed = Math.addExact(consumed, u);
    }
}
