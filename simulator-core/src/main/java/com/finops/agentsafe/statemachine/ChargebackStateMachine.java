package com.finops.agentsafe.statemachine;

import com.finops.agentsafe.enums.ChargebackStatus;
import com.finops.agentsafe.validator.InvariantViolationException;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * State machine for Chargeback lifecycle transitions.
 *
 * Legal transitions:
 *   OPEN         → UNDER_REVIEW
 *   UNDER_REVIEW → ACCEPTED
 *   UNDER_REVIEW → DISPUTED
 *   ACCEPTED     → RESOLVED
 *   DISPUTED     → RESOLVED
 *   RESOLVED     → CLOSED
 *
 *   Terminal state: CLOSED
 */
public class ChargebackStateMachine {

    private static final Map<ChargebackStatus, Set<ChargebackStatus>> ALLOWED_TRANSITIONS = new HashMap<>();

    static {
        ALLOWED_TRANSITIONS.put(ChargebackStatus.OPEN,
            EnumSet.of(ChargebackStatus.UNDER_REVIEW));

        ALLOWED_TRANSITIONS.put(ChargebackStatus.UNDER_REVIEW,
            EnumSet.of(ChargebackStatus.ACCEPTED, ChargebackStatus.DISPUTED));

        ALLOWED_TRANSITIONS.put(ChargebackStatus.ACCEPTED,
            EnumSet.of(ChargebackStatus.RESOLVED));

        ALLOWED_TRANSITIONS.put(ChargebackStatus.DISPUTED,
            EnumSet.of(ChargebackStatus.RESOLVED));

        ALLOWED_TRANSITIONS.put(ChargebackStatus.RESOLVED,
            EnumSet.of(ChargebackStatus.CLOSED));

        // Terminal state
        ALLOWED_TRANSITIONS.put(ChargebackStatus.CLOSED, EnumSet.noneOf(ChargebackStatus.class));
    }

    /**
     * Validates that the transition from {@code from} to {@code to} is legal.
     * Throws InvariantViolationException if the transition is not allowed.
     */
    public static void validateTransition(ChargebackStatus from, ChargebackStatus to) {
        Set<ChargebackStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new InvariantViolationException(String.format(
                "STATE_MACHINE_VIOLATION: Illegal chargeback status transition from [%s] to [%s]. " +
                "Allowed from %s: %s",
                from, to, from, allowed != null ? allowed : "[]"
            ));
        }
    }

    public static boolean isTerminal(ChargebackStatus status) {
        Set<ChargebackStatus> transitions = ALLOWED_TRANSITIONS.get(status);
        return transitions != null && transitions.isEmpty();
    }
}
