package com.finops.agentsafe.statemachine;

import com.finops.agentsafe.enums.ChargebackStatus;
import com.finops.agentsafe.validator.InvariantViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChargebackStateMachineTest {

    @Test
    @DisplayName("OPEN → UNDER_REVIEW is valid")
    void testOpenToUnderReview() {
        assertDoesNotThrow(() -> ChargebackStateMachine.validateTransition(
            ChargebackStatus.OPEN, ChargebackStatus.UNDER_REVIEW));
    }

    @Test
    @DisplayName("UNDER_REVIEW → ACCEPTED is valid")
    void testUnderReviewToAccepted() {
        assertDoesNotThrow(() -> ChargebackStateMachine.validateTransition(
            ChargebackStatus.UNDER_REVIEW, ChargebackStatus.ACCEPTED));
    }

    @Test
    @DisplayName("UNDER_REVIEW → DISPUTED is valid")
    void testUnderReviewToDisputed() {
        assertDoesNotThrow(() -> ChargebackStateMachine.validateTransition(
            ChargebackStatus.UNDER_REVIEW, ChargebackStatus.DISPUTED));
    }

    @Test
    @DisplayName("ACCEPTED → RESOLVED is valid")
    void testAcceptedToResolved() {
        assertDoesNotThrow(() -> ChargebackStateMachine.validateTransition(
            ChargebackStatus.ACCEPTED, ChargebackStatus.RESOLVED));
    }

    @Test
    @DisplayName("DISPUTED → RESOLVED is valid")
    void testDisputedToResolved() {
        assertDoesNotThrow(() -> ChargebackStateMachine.validateTransition(
            ChargebackStatus.DISPUTED, ChargebackStatus.RESOLVED));
    }

    @Test
    @DisplayName("RESOLVED → CLOSED is valid")
    void testResolvedToClosed() {
        assertDoesNotThrow(() -> ChargebackStateMachine.validateTransition(
            ChargebackStatus.RESOLVED, ChargebackStatus.CLOSED));
    }

    @Test
    @DisplayName("OPEN → ACCEPTED is ILLEGAL — must go through UNDER_REVIEW")
    void testOpenToAcceptedIsIllegal() {
        InvariantViolationException ex = assertThrows(InvariantViolationException.class,
            () -> ChargebackStateMachine.validateTransition(ChargebackStatus.OPEN, ChargebackStatus.ACCEPTED));
        assertTrue(ex.getMessage().contains("STATE_MACHINE_VIOLATION"));
    }

    @Test
    @DisplayName("CLOSED → OPEN is ILLEGAL — terminal state cannot re-open")
    void testClosedToOpenIsIllegal() {
        assertThrows(InvariantViolationException.class,
            () -> ChargebackStateMachine.validateTransition(ChargebackStatus.CLOSED, ChargebackStatus.OPEN));
    }

    @Test
    @DisplayName("CLOSED is a terminal state")
    void testClosedIsTerminal() {
        assertTrue(ChargebackStateMachine.isTerminal(ChargebackStatus.CLOSED));
    }

    @Test
    @DisplayName("OPEN is NOT a terminal state")
    void testOpenIsNotTerminal() {
        assertFalse(ChargebackStateMachine.isTerminal(ChargebackStatus.OPEN));
    }
}
