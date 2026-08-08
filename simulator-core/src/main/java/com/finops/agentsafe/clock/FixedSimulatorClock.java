package com.finops.agentsafe.clock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Deterministic SimulatorClock implementation for benchmark and test use.
 * Uses Clock.fixed(...) to pin time at a specific instant.
 *
 * Example usage in tests:
 *   FixedSimulatorClock clock = new FixedSimulatorClock(Instant.parse("2025-01-01T00:00:00Z"));
 *   clock.setInstant(Instant.parse("2025-01-02T00:00:00Z")); // advance time
 *
 * This enables deterministic testing of:
 *   - approval expiration (expiresAt comparisons)
 *   - settlement windows
 *   - audit timestamps
 *   - retry behavior
 *   - chargeback/reversal event timestamps
 */
public class FixedSimulatorClock implements SimulatorClock {

    private volatile Instant fixedInstant;

    public FixedSimulatorClock(Instant fixedInstant) {
        this.fixedInstant = fixedInstant;
    }

    /**
     * Set a new fixed instant (advances or rewinds deterministic time).
     */
    public void setInstant(Instant instant) {
        this.fixedInstant = instant;
    }

    /**
     * Advance the clock by the given number of seconds.
     */
    public void advanceSeconds(long seconds) {
        this.fixedInstant = this.fixedInstant.plusSeconds(seconds);
    }

    /**
     * Advance the clock by the given number of hours.
     */
    public void advanceHours(long hours) {
        advanceSeconds(hours * 3600L);
    }

    @Override
    public Instant now() {
        return fixedInstant;
    }

    @Override
    public Clock getClock() {
        return Clock.fixed(fixedInstant, ZoneOffset.UTC);
    }
}
