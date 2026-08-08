package com.finops.agentsafe.clock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicClockTest {

    @Test
    @DisplayName("FixedSimulatorClock always returns the same pinned instant")
    void testFixedClockReturnsPinnedInstant() {
        Instant pinned = Instant.parse("2025-01-01T00:00:00Z");
        FixedSimulatorClock clock = new FixedSimulatorClock(pinned);

        assertEquals(pinned, clock.now());
        assertEquals(pinned, clock.now()); // idempotent
        assertEquals(pinned, clock.now()); // still same
    }

    @Test
    @DisplayName("FixedSimulatorClock.advanceSeconds advances time deterministically")
    void testClockAdvancesSeconds() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        FixedSimulatorClock clock = new FixedSimulatorClock(start);

        clock.advanceSeconds(3600);
        assertEquals(Instant.parse("2025-01-01T01:00:00Z"), clock.now());
    }

    @Test
    @DisplayName("FixedSimulatorClock.advanceHours advances time by hours")
    void testClockAdvancesHours() {
        Instant start = Instant.parse("2025-06-15T10:00:00Z");
        FixedSimulatorClock clock = new FixedSimulatorClock(start);

        clock.advanceHours(25); // advance past 24h
        Instant expected = start.plusSeconds(25 * 3600L);
        assertEquals(expected, clock.now());
    }

    @Test
    @DisplayName("FixedSimulatorClock can simulate approval expiration: clock past expiresAt")
    void testApprovalExpirationSimulation() {
        Instant createdAt = Instant.parse("2025-01-01T00:00:00Z");
        Instant expiresAt = createdAt.plusSeconds(3600); // 1h TTL
        FixedSimulatorClock clock = new FixedSimulatorClock(createdAt);

        // Before expiration
        assertFalse(clock.now().isAfter(expiresAt));

        // Advance clock past expiration
        clock.advanceHours(2);
        assertTrue(clock.now().isAfter(expiresAt));
    }

    @Test
    @DisplayName("FixedSimulatorClock.setInstant allows rewinding to specific instant")
    void testSetInstant() {
        Instant initial = Instant.parse("2025-03-01T12:00:00Z");
        Instant target = Instant.parse("2025-01-01T00:00:00Z");
        FixedSimulatorClock clock = new FixedSimulatorClock(initial);

        clock.setInstant(target);
        assertEquals(target, clock.now());
    }

    @Test
    @DisplayName("SystemSimulatorClock returns non-null Instant and advances with wall clock")
    void testSystemClockReturnsNonNull() {
        SystemSimulatorClock clock = new SystemSimulatorClock();
        Instant t1 = clock.now();
        assertNotNull(t1);
        // Wall clock always moves forward or equals
        Instant t2 = clock.now();
        assertFalse(t2.isBefore(t1));
    }
}
