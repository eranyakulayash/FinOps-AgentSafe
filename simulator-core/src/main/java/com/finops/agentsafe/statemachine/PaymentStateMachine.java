package com.finops.agentsafe.statemachine;

import com.finops.agentsafe.enums.TransactionStatus;
import com.finops.agentsafe.validator.InvariantViolationException;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * State machine for Payment and related transaction type lifecycle transitions.
 *
 * Legal transitions:
 *
 *   Payment:
 *     PENDING      → AUTHORIZED, FAILED, CANCELLED
 *     AUTHORIZED   → CAPTURED, CANCELLED
 *     CAPTURED     → SETTLED, FAILED, CANCELLED, REVERSED, PARTIALLY_REVERSED, CHARGEBACK_OPEN
 *     SETTLED      → RECONCILED, PARTIALLY_REFUNDED, REFUNDED, REVERSED, CHARGEBACK_OPEN
 *     RECONCILED   → REFUNDED (terminal settled state may still receive refund)
 *     PARTIALLY_REFUNDED → REFUNDED, PARTIALLY_REVERSED
 *     PARTIALLY_REVERSED → REVERSED
 *     CHARGEBACK_OPEN    → CHARGEBACK_RESOLVED
 *
 *   Terminal states (no further transitions): FAILED, CANCELLED, REFUNDED, REVERSED, CHARGEBACK_RESOLVED
 */
public class PaymentStateMachine {

    private static final Map<TransactionStatus, Set<TransactionStatus>> ALLOWED_TRANSITIONS = new HashMap<>();

    static {
        ALLOWED_TRANSITIONS.put(TransactionStatus.PENDING,
            EnumSet.of(TransactionStatus.AUTHORIZED, TransactionStatus.FAILED, TransactionStatus.CANCELLED));

        ALLOWED_TRANSITIONS.put(TransactionStatus.AUTHORIZED,
            EnumSet.of(TransactionStatus.CAPTURED, TransactionStatus.CANCELLED));

        ALLOWED_TRANSITIONS.put(TransactionStatus.CAPTURED,
            EnumSet.of(TransactionStatus.SETTLED, TransactionStatus.FAILED, TransactionStatus.CANCELLED,
                       TransactionStatus.REVERSED, TransactionStatus.PARTIALLY_REVERSED,
                       TransactionStatus.CHARGEBACK_OPEN));

        ALLOWED_TRANSITIONS.put(TransactionStatus.SETTLED,
            EnumSet.of(TransactionStatus.RECONCILED, TransactionStatus.PARTIALLY_REFUNDED,
                       TransactionStatus.REFUNDED, TransactionStatus.REVERSED,
                       TransactionStatus.PARTIALLY_REVERSED, TransactionStatus.CHARGEBACK_OPEN));

        ALLOWED_TRANSITIONS.put(TransactionStatus.RECONCILED,
            EnumSet.of(TransactionStatus.PARTIALLY_REFUNDED, TransactionStatus.REFUNDED));

        ALLOWED_TRANSITIONS.put(TransactionStatus.PARTIALLY_REFUNDED,
            EnumSet.of(TransactionStatus.REFUNDED, TransactionStatus.PARTIALLY_REVERSED));

        ALLOWED_TRANSITIONS.put(TransactionStatus.PARTIALLY_REVERSED,
            EnumSet.of(TransactionStatus.REVERSED));

        ALLOWED_TRANSITIONS.put(TransactionStatus.CHARGEBACK_OPEN,
            EnumSet.of(TransactionStatus.CHARGEBACK_RESOLVED));

        // Terminal states — no outgoing transitions
        ALLOWED_TRANSITIONS.put(TransactionStatus.FAILED, EnumSet.noneOf(TransactionStatus.class));
        ALLOWED_TRANSITIONS.put(TransactionStatus.CANCELLED, EnumSet.noneOf(TransactionStatus.class));
        ALLOWED_TRANSITIONS.put(TransactionStatus.REFUNDED, EnumSet.noneOf(TransactionStatus.class));
        ALLOWED_TRANSITIONS.put(TransactionStatus.REVERSED, EnumSet.noneOf(TransactionStatus.class));
        ALLOWED_TRANSITIONS.put(TransactionStatus.CHARGEBACK_RESOLVED, EnumSet.noneOf(TransactionStatus.class));
        ALLOWED_TRANSITIONS.put(TransactionStatus.DISPUTED, EnumSet.noneOf(TransactionStatus.class));
    }

    /**
     * Validates that the transition from {@code from} to {@code to} is legal.
     * Throws InvariantViolationException if the transition is not allowed.
     */
    public static void validateTransition(TransactionStatus from, TransactionStatus to) {
        Set<TransactionStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new InvariantViolationException(String.format(
                "STATE_MACHINE_VIOLATION: Illegal transaction status transition from [%s] to [%s]. " +
                "Allowed from %s: %s",
                from, to, from, allowed != null ? allowed : "[]"
            ));
        }
    }

    /**
     * Returns whether the given status is a terminal state.
     */
    public static boolean isTerminal(TransactionStatus status) {
        Set<TransactionStatus> transitions = ALLOWED_TRANSITIONS.get(status);
        return transitions != null && transitions.isEmpty();
    }
}
