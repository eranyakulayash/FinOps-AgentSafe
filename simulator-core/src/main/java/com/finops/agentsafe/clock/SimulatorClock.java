package com.finops.agentsafe.clock;

import java.time.Clock;
import java.time.Instant;

/**
 * Central clock abstraction used by all financial and domain logic.
 * Application code MUST NOT use Instant.now(), LocalDateTime.now(), or System.currentTimeMillis()
 * directly in financial/domain logic. Inject and use this interface instead.
 *
 * Default mode:     SystemSimulatorClock (Clock.systemUTC())
 * Benchmark/test:   FixedSimulatorClock  (Clock.fixed(...)) for deterministic time control
 */
public interface SimulatorClock {
    /**
     * Returns the current instant from this clock.
     */
    Instant now();

    /**
     * Returns the underlying java.time.Clock for use with Instant.now(clock).
     */
    Clock getClock();
}
