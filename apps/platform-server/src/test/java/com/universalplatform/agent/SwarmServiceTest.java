package com.universalplatform.agent;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SwarmServiceTest {
    @Test void swarmRunsBoundedSpecialistsAndSynthesizes() {
        AtomicInteger calls = new AtomicInteger();
        SwarmProvider provider = (system, user) -> {
            calls.incrementAndGet();
            return system.contains("Synthesize") ? "final" : "finding:" + user.substring(0, Math.min(20, user.length()));
        };
        var service = new SwarmService(provider, 4);
        var result = service.run("Design a secure course assistant", 3);
        assertEquals(3, result.findings().size());
        assertEquals("final", result.finalAnswer());
        assertEquals(4, calls.get());
    }

    @Test void swarmRejectsOversizedTaskAndInvalidAgentCount() {
        var service = new SwarmService((s, u) -> "ok", 4);
        assertThrows(IllegalArgumentException.class, () -> service.run("x".repeat(12_001), 2));
        assertThrows(IllegalArgumentException.class, () -> service.run("task", 0));
        assertThrows(IllegalArgumentException.class, () -> service.run("task", 5));
    }
}
