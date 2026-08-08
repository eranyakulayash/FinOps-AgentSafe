package com.finops.agentsafe.statemachine;

import com.finops.agentsafe.enums.ApprovalStatus;
import com.finops.agentsafe.validator.InvariantViolationException;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * State machine for HumanApprovalRequest lifecycle transitions.
 *
 * Legal transitions:
 *   REQUESTED → APPROVED
 *   REQUESTED → REJECTED
 *   REQUESTED → EXPIRED
 *   REQUESTED → CANCELLED
 *
 *   Terminal states: APPROVED, REJECTED, EXPIRED, CANCELLED
 */
public class ApprovalStateMachine {

    private static final Map<ApprovalStatus, Set<ApprovalStatus>> ALLOWED_TRANSITIONS = new HashMap<>();

    static {
        ALLOWED_TRANSITIONS.put(ApprovalStatus.REQUESTED,
            EnumSet.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED,
                       ApprovalStatus.EXPIRED, ApprovalStatus.CANCELLED));

        // Terminal states
        ALLOWED_TRANSITIONS.put(ApprovalStatus.APPROVED, EnumSet.noneOf(ApprovalStatus.class));
        ALLOWED_TRANSITIONS.put(ApprovalStatus.REJECTED, EnumSet.noneOf(ApprovalStatus.class));
        ALLOWED_TRANSITIONS.put(ApprovalStatus.EXPIRED, EnumSet.noneOf(ApprovalStatus.class));
        ALLOWED_TRANSITIONS.put(ApprovalStatus.CANCELLED, EnumSet.noneOf(ApprovalStatus.class));
    }

    /**
     * Validates that the transition from {@code from} to {@code to} is legal.
     * Throws InvariantViolationException if the transition is not allowed.
     */
    public static void validateTransition(ApprovalStatus from, ApprovalStatus to) {
        Set<ApprovalStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new InvariantViolationException(String.format(
                "STATE_MACHINE_VIOLATION: Illegal approval status transition from [%s] to [%s]. " +
                "Allowed from %s: %s",
                from, to, from, allowed != null ? allowed : "[]"
            ));
        }
    }

    public static boolean isTerminal(ApprovalStatus status) {
        Set<ApprovalStatus> transitions = ALLOWED_TRANSITIONS.get(status);
        return transitions != null && transitions.isEmpty();
    }
}
