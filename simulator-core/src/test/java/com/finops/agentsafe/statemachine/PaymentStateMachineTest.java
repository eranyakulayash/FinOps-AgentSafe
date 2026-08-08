package com.finops.agentsafe.statemachine;

import com.finops.agentsafe.enums.TransactionStatus;
import com.finops.agentsafe.validator.InvariantViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStateMachineTest {

    @Test
    @DisplayName("SETTLED → RECONCILED is a valid transition")
    void testSettledToReconciled() {
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(
            TransactionStatus.SETTLED, TransactionStatus.RECONCILED));
    }

    @Test
    @DisplayName("SETTLED → REFUNDED is valid when fully refunded")
    void testSettledToRefunded() {
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(
            TransactionStatus.SETTLED, TransactionStatus.REFUNDED));
    }

    @Test
    @DisplayName("SETTLED → PARTIALLY_REFUNDED is valid")
    void testSettledToPartiallyRefunded() {
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(
            TransactionStatus.SETTLED, TransactionStatus.PARTIALLY_REFUNDED));
    }

    @Test
    @DisplayName("SETTLED → REVERSED is valid")
    void testSettledToReversed() {
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(
            TransactionStatus.SETTLED, TransactionStatus.REVERSED));
    }

    @Test
    @DisplayName("SETTLED → CHARGEBACK_OPEN is valid")
    void testSettledToChargebackOpen() {
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(
            TransactionStatus.SETTLED, TransactionStatus.CHARGEBACK_OPEN));
    }

    @Test
    @DisplayName("PENDING → AUTHORIZED is valid")
    void testPendingToAuthorized() {
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(
            TransactionStatus.PENDING, TransactionStatus.AUTHORIZED));
    }

    @Test
    @DisplayName("PENDING → SETTLED is ILLEGAL — must throw InvariantViolationException")
    void testPendingToSettledIsIllegal() {
        InvariantViolationException ex = assertThrows(InvariantViolationException.class,
            () -> PaymentStateMachine.validateTransition(TransactionStatus.PENDING, TransactionStatus.SETTLED));
        assertTrue(ex.getMessage().contains("STATE_MACHINE_VIOLATION"));
    }

    @Test
    @DisplayName("REFUNDED → SETTLED is ILLEGAL — terminal state cannot transition")
    void testRefundedToSettledIsIllegal() {
        InvariantViolationException ex = assertThrows(InvariantViolationException.class,
            () -> PaymentStateMachine.validateTransition(TransactionStatus.REFUNDED, TransactionStatus.SETTLED));
        assertTrue(ex.getMessage().contains("STATE_MACHINE_VIOLATION"));
    }

    @Test
    @DisplayName("REVERSED → REFUNDED is ILLEGAL — terminal state cannot transition")
    void testReversedToRefundedIsIllegal() {
        assertThrows(InvariantViolationException.class,
            () -> PaymentStateMachine.validateTransition(TransactionStatus.REVERSED, TransactionStatus.REFUNDED));
    }

    @Test
    @DisplayName("FAILED is a terminal state")
    void testFailedIsTerminal() {
        assertTrue(PaymentStateMachine.isTerminal(TransactionStatus.FAILED));
    }

    @Test
    @DisplayName("CANCELLED is a terminal state")
    void testCancelledIsTerminal() {
        assertTrue(PaymentStateMachine.isTerminal(TransactionStatus.CANCELLED));
    }

    @Test
    @DisplayName("SETTLED is NOT a terminal state")
    void testSettledIsNotTerminal() {
        assertFalse(PaymentStateMachine.isTerminal(TransactionStatus.SETTLED));
    }
}
