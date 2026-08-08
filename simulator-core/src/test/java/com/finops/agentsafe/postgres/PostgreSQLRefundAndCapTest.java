package com.finops.agentsafe.postgres;

import com.finops.agentsafe.AbstractPostgreSQLIntegrationTest;
import com.finops.agentsafe.domain.Merchant;
import com.finops.agentsafe.domain.Transaction;
import com.finops.agentsafe.enums.TransactionStatus;
import com.finops.agentsafe.repository.MerchantRepository;
import com.finops.agentsafe.repository.TransactionRepository;
import com.finops.agentsafe.service.PaymentService;
import com.finops.agentsafe.validator.InvariantViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PostgreSQLRefundAndCapTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private final String supervisorToken = "SUP-SECRET-AUTH-TOKEN-9988";
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = new Merchant(UUID.randomUUID(), "Refund Test Merchant", new BigDecimal("1.50"), "ACTIVE");
        merchantRepository.save(merchant);
    }

    @Test
    @DisplayName("Verify Refund Limits: Create $100 payment, refund $60, attempt another $50 refund, verify rejection")
    void testRefundCapEnforcementAgainstPostgreSQL() {
        String paymentId = "PAY-100-CAP";
        paymentService.processPayment(paymentId, "IDEMP-PAY-100", merchant.getId(), new BigDecimal("100.00"), "USD");

        // First refund of $60.00
        Transaction refund1 = paymentService.processRefund(
            "REFUND-60",
            "IDEMP-REFUND-60",
            paymentId,
            new BigDecimal("60.00"),
            supervisorToken
        );
        assertNotNull(refund1);
        assertEquals(new BigDecimal("60.00"), refund1.getAmount());

        Transaction paymentAfterRefund1 = transactionRepository.findById(paymentId).orElseThrow();
        assertEquals(TransactionStatus.PARTIALLY_REFUNDED, paymentAfterRefund1.getStatus());

        // Attempt second refund of $50.00 ($60 + $50 = $110 > $100 original payment)
        assertThrows(InvariantViolationException.class, () ->
            paymentService.processRefund(
                "REFUND-50",
                "IDEMP-REFUND-50",
                paymentId,
                new BigDecimal("50.00"),
                supervisorToken
            ),
            "Expected InvariantViolationException when cumulative refund exceeds original payment"
        );

        // Verify total refunded amount in PostgreSQL database remains $60.00
        BigDecimal totalRefunded = transactionRepository.findTotalRefundedForPayment(paymentId);
        assertEquals(new BigDecimal("60.00"), totalRefunded);
    }
}
