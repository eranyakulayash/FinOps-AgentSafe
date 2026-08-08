package com.finops.agentsafe.identifier;

import java.util.UUID;

/**
 * Central identifier generation abstraction.
 * Inject this interface instead of calling UUID.randomUUID() directly
 * in benchmark-visible financial domain objects (transactions, refunds,
 * reversals, chargebacks, approvals, audit events).
 *
 * Default:     RandomIdentifierGenerator  (UUID.randomUUID())
 * Benchmark:   SeededIdentifierGenerator  (deterministic, seed-based)
 */
public interface IdentifierGenerator {

    /**
     * Generate the next UUID.
     */
    UUID nextUUID();

    /**
     * Generate a prefixed transaction-style string ID.
     * Example: nextTransactionId("PAY") -> "PAY-550e8400-e29b-41d4-a716-446655440000"
     */
    default String nextTransactionId(String prefix) {
        return prefix + "-" + nextUUID().toString().toUpperCase();
    }
}
