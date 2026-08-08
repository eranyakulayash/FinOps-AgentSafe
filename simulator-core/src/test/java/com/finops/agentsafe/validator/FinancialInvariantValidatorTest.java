package com.finops.agentsafe.validator;

import com.finops.agentsafe.enums.ActionRiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class FinancialInvariantValidatorTest {

    private FinancialInvariantValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FinancialInvariantValidator();
    }

    @Test
    @DisplayName("Should accept positive monetary amount with scale <= 2")
    void testValidMonetaryAmount() {
        assertDoesNotThrow(() -> validator.validatePositiveMonetaryAmount(new BigDecimal("100.50"), "Amount"));
    }

    @Test
    @DisplayName("Should reject negative or zero monetary amount")
    void testNegativeMonetaryAmount() {
        assertThrows(InvariantViolationException.class, () -> validator.validatePositiveMonetaryAmount(new BigDecimal("-10.00"), "Amount"));
        assertThrows(InvariantViolationException.class, () -> validator.validatePositiveMonetaryAmount(BigDecimal.ZERO, "Amount"));
    }

    @Test
    @DisplayName("Should reject monetary scale greater than 2 decimal places")
    void testInvalidScaleMonetaryAmount() {
        assertThrows(InvariantViolationException.class, () -> validator.validatePositiveMonetaryAmount(new BigDecimal("10.505"), "Amount"));
    }

    @Test
    @DisplayName("Should validate Conservation of Balance (Gross - Fee = Net)")
    void testConservationOfBalanceSuccess() {
        BigDecimal gross = new BigDecimal("100.00");
        BigDecimal fee = new BigDecimal("2.50");
        BigDecimal net = new BigDecimal("97.50");
        assertDoesNotThrow(() -> validator.validateConservationOfBalance(gross, fee, net));
    }

    @Test
    @DisplayName("Should throw InvariantViolationException when Conservation of Balance is violated")
    void testConservationOfBalanceFailure() {
        BigDecimal gross = new BigDecimal("100.00");
        BigDecimal fee = new BigDecimal("2.50");
        BigDecimal invalidNet = new BigDecimal("98.00"); // Incorrect net
        assertThrows(InvariantViolationException.class, () -> validator.validateConservationOfBalance(gross, fee, invalidNet));
    }

    @Test
    @DisplayName("Should enforce Refund Cap invariant (Cumulative Refunds <= Original Payment)")
    void testRefundCapEnforcement() {
        BigDecimal original = new BigDecimal("100.00");
        BigDecimal existingRefunds = new BigDecimal("60.00");
        BigDecimal validRefund = new BigDecimal("40.00");
        BigDecimal invalidRefund = new BigDecimal("40.01");

        assertDoesNotThrow(() -> validator.validateRefundCap(original, existingRefunds, validRefund));
        assertThrows(InvariantViolationException.class, () -> validator.validateRefundCap(original, existingRefunds, invalidRefund));
    }

    @Test
    @DisplayName("Should enforce Authorization Token check for HIGH_RISK_WRITE operations")
    void testAuthorizationTokenValidation() {
        String supervisorToken = "SUP-SECRET-1234";

        assertDoesNotThrow(() -> validator.validateActionAuthorization(ActionRiskLevel.HIGH_RISK_WRITE, supervisorToken, supervisorToken));
        assertThrows(InvariantViolationException.class, () -> validator.validateActionAuthorization(ActionRiskLevel.HIGH_RISK_WRITE, "INVALID_TOKEN", supervisorToken));
        assertThrows(InvariantViolationException.class, () -> validator.validateActionAuthorization(ActionRiskLevel.HIGH_RISK_WRITE, null, supervisorToken));
    }
}
