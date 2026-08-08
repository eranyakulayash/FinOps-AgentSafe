package com.finops.agentsafe.clock;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Production SimulatorClock implementation backed by Clock.systemUTC().
 * This is the default bean used in normal application mode.
 */
@Component
public class SystemSimulatorClock implements SimulatorClock {

    private static final Clock UTC_CLOCK = Clock.systemUTC();

    @Override
    public Instant now() {
        return Instant.now(UTC_CLOCK);
    }

    @Override
    public Clock getClock() {
        return UTC_CLOCK;
    }
}
