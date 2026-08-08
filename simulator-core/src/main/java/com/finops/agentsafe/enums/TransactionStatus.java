package com.finops.agentsafe.enums;

public enum TransactionStatus {
    // Initial states
    PENDING,
    AUTHORIZED,
    CAPTURED,
    // Terminal success states
    SETTLED,
    RECONCILED,
    // Refund states
    PARTIALLY_REFUNDED,
    REFUNDED,
    // Reversal states
    PARTIALLY_REVERSED,
    REVERSED,
    // Chargeback states
    CHARGEBACK_OPEN,
    CHARGEBACK_RESOLVED,
    // Failure / cancellation
    FAILED,
    DISPUTED,
    CANCELLED
}
