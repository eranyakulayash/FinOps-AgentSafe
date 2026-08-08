package com.finops.agentsafe.validator;

import com.finops.agentsafe.enums.ActionRiskLevel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FinancialInvariantValidator {

    /**
     * Validates that monetary amounts use valid BigDecimal representations with scale <= 2 and positive values.
     */
    public void validatePositiveMonetaryAmount(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new InvariantViolationException("FINANCIAL_INVARIANT_VIOLATION: " + fieldName + " cannot be null.");
        }
        if (amount.scale() > 2) {
            throw new InvariantViolationException("FINANCIAL_INVARIANT_VIOLATION: " + fieldName + " scale cannot exceed 2 decimal places. Provided: " + amount.scale());
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvariantViolationException("FINANCIAL_INVARIANT_VIOLATION: " + fieldName + " must be strictly positive (> 0.00). Provided: " + amount);
        }
    }

    /**
     * Validates the Conservation of Balance invariant: Gross Amount - Fee Amount == Net Amount.
     */
    public void validateConservationOfBalance(BigDecimal gross, BigDecimal fee, BigDecimal net) {
        if (gross == null || fee == null || net == null) {
            throw new InvariantViolationException("FINANCIAL_INVARIANT_VIOLATION: Settlement amounts (gross, fee, net) cannot be null.");
        }
        BigDecimal calculatedNet = gross.subtract(fee).setScale(2, RoundingMode.HALF_UP);
        if (calculatedNet.compareTo(net.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new InvariantViolationException(String.format(
                "FINANCIAL_INVARIANT_VIOLATION: Conservation of balance violated. Gross (%s) - Fee (%s) = %s, but expected Net is %s",
                gross, fee, calculatedNet, net
            ));
        }
    }

    /**
     * Validates that cumulative refunds do not exceed original payment amount.
     */
    public void validateRefundCap(BigDecimal originalPaymentAmount, BigDecimal existingRefundTotal, BigDecimal requestedRefundAmount) {
        if (originalPaymentAmount == null || requestedRefundAmount == null) {
            throw new InvariantViolationException("FINANCIAL_INVARIANT_VIOLATION: Refund amounts cannot be null.");
        }
        BigDecimal existing = (existingRefundTotal != null) ? existingRefundTotal : BigDecimal.ZERO;
        BigDecimal newTotalRefund = existing.add(requestedRefundAmount).setScale(2, RoundingMode.HALF_UP);
        if (newTotalRefund.compareTo(originalPaymentAmount.setScale(2, RoundingMode.HALF_UP)) > 0) {
            throw new InvariantViolationException(String.format(
                "FINANCIAL_INVARIANT_VIOLATION: Refund cap exceeded. Original payment: %s, Existing refunds: %s, Requested refund: %s",
                originalPaymentAmount, existing, requestedRefundAmount
            ));
        }
    }

    /**
     * Validates double settlement prevention.
     */
    public void validateNotAlreadyReconciled(boolean alreadyReconciled, String transactionId) {
        if (alreadyReconciled) {
            throw new InvariantViolationException(
                "FINANCIAL_INVARIANT_VIOLATION: Double settlement attempt detected. Transaction " + transactionId + " is already reconciled."
            );
        }
    }

    /**
     * Enforces explicit authorization check for HIGH_RISK_WRITE operations.
     */
    public void validateActionAuthorization(ActionRiskLevel riskLevel, String providedToken, String expectedSupervisorToken) {
        if (riskLevel == ActionRiskLevel.HIGH_RISK_WRITE) {
            if (providedToken == null || providedToken.isBlank() || !providedToken.equals(expectedSupervisorToken)) {
                throw new InvariantViolationException(
                    "AUTHORIZATION_BOUNDARY_VIOLATION: High-risk write operation requires valid supervisor authorization token."
                );
            }
        }
    }
}
