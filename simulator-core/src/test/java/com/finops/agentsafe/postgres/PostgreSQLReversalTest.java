package com.finops.agentsafe.postgres;

import com.finops.agentsafe.AbstractPostgreSQLIntegrationTest;
import com.finops.agentsafe.domain.Merchant;
import com.finops.agentsafe.domain.Transaction;
import com.finops.agentsafe.enums.TransactionStatus;
import com.finops.agentsafe.exception.ApprovalRequiredException;
import com.finops.agentsafe.repository.MerchantRepository;
import com.finops.agentsafe.repository.TransactionRepository;
import com.finops.agentsafe.service.HumanApprovalService;
import com.finops.agentsafe.service.PaymentService;
import com.finops.agentsafe.validator.InvariantViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PostgreSQLReversalTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private HumanApprovalService approvalService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = new Merchant(UUID.randomUUID(), "Reversal Test Merchant", new BigDecimal("1.00"), "ACTIVE");
        merchantRepository.save(merchant);
    }

    private void approveReversalFor(String paymentId) {
        try {
            paymentService.processReversal("PROBE-" + paymentId, "PROBE-IDEMP-" + paymentId, paymentId, new BigDecimal("0.01"), "AGENT");
        } catch (ApprovalRequiredException e) {
            approvalService.approve(e.getApprovalRequestId(), "HUMAN_SUPERVISOR");
        } catch (InvariantViolationException ignored) {
            // May fail on $0.01 — we only needed the approval to be created and approved
        }
    }

    @Test
    @DisplayName("Full reversal: $100 payment → $100 reversal with approval → payment status REVERSED")
    void testFullReversalChangesStatusToReversed() {
        String payId = "PAY-REVERSAL-FULL-" + UUID.randomUUID().toString().substring(0, 6);
        paymentService.processPayment(payId, "IDEMP-" + payId, merchant.getId(), new BigDecimal("100.00"), "USD");

        approveReversalFor(payId);

        Transaction reversal = paymentService.processReversal("REV-FULL-" + payId, "REV-IDEMP-" + payId, payId, new BigDecimal("100.00"), "AGENT");
        assertNotNull(reversal);

        Transaction payment = transactionRepository.findById(payId).orElseThrow();
        assertEquals(TransactionStatus.REVERSED, payment.getStatus());
    }

    @Test
    @DisplayName("Partial reversal: $100 payment → $60 reversal → payment status PARTIALLY_REVERSED")
    void testPartialReversalChangesStatusToPartiallyReversed() {
        String payId = "PAY-REVERSAL-PARTIAL-" + UUID.randomUUID().toString().substring(0, 6);
        paymentService.processPayment(payId, "IDEMP-" + payId, merchant.getId(), new BigDecimal("100.00"), "USD");

        approveReversalFor(payId);

        paymentService.processReversal("REV-PARTIAL-" + payId, "REV-PARTIAL-IDEMP-" + payId, payId, new BigDecimal("60.00"), "AGENT");

        Transaction payment = transactionRepository.findById(payId).orElseThrow();
        assertEquals(TransactionStatus.PARTIALLY_REVERSED, payment.getStatus());
    }

    @Test
    @DisplayName("Reversal cap: $100 payment → $70 reversal + $40 reversal attempt must fail")
    void testReversalCapEnforced() {
        String payId = "PAY-REVERSAL-CAP-" + UUID.randomUUID().toString().substring(0, 6);
        paymentService.processPayment(payId, "IDEMP-" + payId, merchant.getId(), new BigDecimal("100.00"), "USD");

        approveReversalFor(payId);

        paymentService.processReversal("REV-CAP-A-" + payId, "REV-CAP-A-IDEMP-" + payId, payId, new BigDecimal("70.00"), "AGENT");

        // Second reversal exceeds cap
        InvariantViolationException ex = assertThrows(InvariantViolationException.class,
            () -> paymentService.processReversal("REV-CAP-B-" + payId, "REV-CAP-B-IDEMP-" + payId, payId, new BigDecimal("40.00"), "AGENT"));
        assertTrue(ex.getMessage().contains("Reversal cap exceeded"));
    }

    @Test
    @DisplayName("Reversal idempotency: duplicate reversal key returns same transaction")
    void testReversalIdempotency() {
        String payId = "PAY-REVERSAL-IDEMP-" + UUID.randomUUID().toString().substring(0, 6);
        paymentService.processPayment(payId, "IDEMP-" + payId, merchant.getId(), new BigDecimal("100.00"), "USD");

        approveReversalFor(payId);

        String reversalId = "REV-IDEMP-" + payId;
        String idemp = "IDEMP-REV-" + payId;
        Transaction r1 = paymentService.processReversal(reversalId, idemp, payId, new BigDecimal("100.00"), "AGENT");
        Transaction r2 = paymentService.processReversal(reversalId + "-dup", idemp, payId, new BigDecimal("100.00"), "AGENT");

        assertEquals(r1.getId(), r2.getId(), "Duplicate idempotency key must return the same reversal transaction");
    }
}
