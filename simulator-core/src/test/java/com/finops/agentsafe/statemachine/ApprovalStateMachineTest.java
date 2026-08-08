package com.finops.agentsafe.statemachine;

import com.finops.agentsafe.enums.ApprovalStatus;
import com.finops.agentsafe.validator.InvariantViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalStateMachineTest {

    @Test
    @DisplayName("REQUESTED → APPROVED is valid")
    void testRequestedToApproved() {
        assertDoesNotThrow(() -> ApprovalStateMachine.validateTransition(
            ApprovalStatus.REQUESTED, ApprovalStatus.APPROVED));
    }

    @Test
    @DisplayName("REQUESTED → REJECTED is valid")
    void testRequestedToRejected() {
        assertDoesNotThrow(() -> ApprovalStateMachine.validateTransition(
            ApprovalStatus.REQUESTED, ApprovalStatus.REJECTED));
    }

    @Test
    @DisplayName("REQUESTED → EXPIRED is valid")
    void testRequestedToExpired() {
        assertDoesNotThrow(() -> ApprovalStateMachine.validateTransition(
            ApprovalStatus.REQUESTED, ApprovalStatus.EXPIRED));
    }

    @Test
    @DisplayName("REQUESTED → CANCELLED is valid")
    void testRequestedToCancelled() {
        assertDoesNotThrow(() -> ApprovalStateMachine.validateTransition(
            ApprovalStatus.REQUESTED, ApprovalStatus.CANCELLED));
    }

    @Test
    @DisplayName("APPROVED → REJECTED is ILLEGAL — terminal state")
    void testApprovedToRejectedIsIllegal() {
        InvariantViolationException ex = assertThrows(InvariantViolationException.class,
            () -> ApprovalStateMachine.validateTransition(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED));
        assertTrue(ex.getMessage().contains("STATE_MACHINE_VIOLATION"));
    }

    @Test
    @DisplayName("REJECTED → APPROVED is ILLEGAL — terminal state")
    void testRejectedToApprovedIsIllegal() {
        assertThrows(InvariantViolationException.class,
            () -> ApprovalStateMachine.validateTransition(ApprovalStatus.REJECTED, ApprovalStatus.APPROVED));
    }

    @Test
    @DisplayName("EXPIRED → APPROVED is ILLEGAL — terminal state")
    void testExpiredToApprovedIsIllegal() {
        assertThrows(InvariantViolationException.class,
            () -> ApprovalStateMachine.validateTransition(ApprovalStatus.EXPIRED, ApprovalStatus.APPROVED));
    }

    @Test
    @DisplayName("APPROVED, REJECTED, EXPIRED, CANCELLED are terminal states")
    void testTerminalStates() {
        assertTrue(ApprovalStateMachine.isTerminal(ApprovalStatus.APPROVED));
        assertTrue(ApprovalStateMachine.isTerminal(ApprovalStatus.REJECTED));
        assertTrue(ApprovalStateMachine.isTerminal(ApprovalStatus.EXPIRED));
        assertTrue(ApprovalStateMachine.isTerminal(ApprovalStatus.CANCELLED));
    }

    @Test
    @DisplayName("REQUESTED is NOT a terminal state")
    void testRequestedIsNotTerminal() {
        assertFalse(ApprovalStateMachine.isTerminal(ApprovalStatus.REQUESTED));
    }
}
