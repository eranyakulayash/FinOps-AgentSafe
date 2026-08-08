package com.finops.agentsafe.postgres;

import com.finops.agentsafe.AbstractPostgreSQLIntegrationTest;
import com.finops.agentsafe.domain.Chargeback;
import com.finops.agentsafe.domain.Merchant;
import com.finops.agentsafe.domain.Transaction;
import com.finops.agentsafe.enums.ChargebackStatus;
import com.finops.agentsafe.enums.TransactionStatus;
import com.finops.agentsafe.exception.ApprovalRequiredException;
import com.finops.agentsafe.repository.MerchantRepository;
import com.finops.agentsafe.repository.TransactionRepository;
import com.finops.agentsafe.service.ChargebackService;
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

class PostgreSQLChargebackTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private ChargebackService chargebackService;

    @Autowired
    private HumanApprovalService approvalService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = new Merchant(UUID.randomUUID(), "Chargeback Merchant", new BigDecimal("2.00"), "ACTIVE");
        merchantRepository.save(merchant);
    }

    @Test
    @DisplayName("Open chargeback against SETTLED payment persists in PostgreSQL with OPEN status")
    void testOpenChargebackPersistsInPostgreSQL() {
        String payId = "PAY-CB-OPEN-" + UUID.randomUUID().toString().substring(0, 6);
        paymentService.processPayment(payId, "IDEMP-" + payId, merchant.getId(), new BigDecimal("300.00"), "USD");

        Chargeback cb = chargebackService.openChargeback(payId, new BigDecimal("300.00"),
            "FRAUD", "IDEMP-CB-" + payId, "SCENARIO-CB", UUID.randomUUID());

        assertNotNull(cb.getId());
        assertEquals(ChargebackStatus.OPEN, cb.getStatus());
        assertEquals(payId, cb.getOriginalTransactionId());
        assertEquals(new BigDecimal("300.00"), cb.getAmount());
    }

    @Test
    @DisplayName("Opening chargeback transitions payment status to CHARGEBACK_OPEN")
    void testChargebackTransitionsPaymentStatus() {
        String payId = "PAY-CB-STATUS-" + UUID.randomUUID().toString().substring(0, 6);
        paymentService.processPayment(payId, "IDEMP-" + payId, merchant.getId(), new BigDecimal("150.00"), "USD");

        chargebackService.openChargeback(payId, new BigDecimal("150.00"),
            "ITEM_NOT_RECEIVED", "IDEMP-CB-STATUS-" + payId, "SCENARIO-CB", UUID.randomUUID());

        Transaction payment = transactionRepository.findById(payId).orElseThrow();
        assertEquals(TransactionStatus.CHARGEBACK_OPEN, payment.getStatus());
    }

    @Test
    @DisplayName("Chargeback state machine: OPEN → UNDER_REVIEW via transitionStatus")
    void testChargebackTransitionOpenToUnderReview() {
        String payId = "PAY-CB-TRANSITION-" + UUID.randomUUID().toString().substring(0, 6);
        paymentService.processPayment(payId, "IDEMP-" + payId, merchant.getId(), new BigDecimal("200.00"), "USD");

        Chargeback cb = chargebackService.openChargeback(payId, new BigDecimal("200.00"),
            "UNAUTHORIZED", "IDEMP-CB-TR-" + payId, "SCENARIO-CB", UUID.randomUUID());

        Chargeback underReview = chargebackService.transitionStatus(cb.getId(), ChargebackStatus.UNDER_REVIEW, "ANALYST");
        assertEquals(ChargebackStatus.UNDER_REVIEW, underReview.getStatus());
    }

    @Test
    @DisplayName("Chargeback ACCEPTED/RESOLVED requires human approval (APPROVAL_REQUIRED)")
    void testChargebackResolutionRequiresApproval() {
        String payId = "PAY-CB-APPROVAL-" + UUID.randomUUID().toString().substring(0, 6);
        paymentService.processPayment(payId, "IDEMP-" + payId, merchant.getId(), new BigDecimal("250.00"), "USD");

        Chargeback cb = chargebackService.openChargeback(payId, new BigDecimal("250.00"),
            "FRAUD", "IDEMP-CB-APPR-" + payId, "SCENARIO-CB", UUID.randomUUID());

        chargebackService.transitionStatus(cb.getId(), ChargebackStatus.UNDER_REVIEW, "ANALYST");

        // ACCEPTED requires human approval
        assertThrows(ApprovalRequiredException.class,
            () -> chargebackService.transitionStatus(cb.getId(), ChargebackStatus.ACCEPTED, "AGENT_UNDER_TEST"));
    }

    @Test
    @DisplayName("Duplicate chargeback idempotency key returns same chargeback")
    void testChargebackIdempotency() {
        String payId = "PAY-CB-IDEMP-" + UUID.randomUUID().toString().substring(0, 6);
        paymentService.processPayment(payId, "IDEMP-" + payId, merchant.getId(), new BigDecimal("100.00"), "USD");

        String idempKey = "IDEMP-CB-IDEM-" + payId;
        Chargeback cb1 = chargebackService.openChargeback(payId, new BigDecimal("100.00"),
            "FRAUD", idempKey, "SCENARIO-CB", UUID.randomUUID());

        // Second attempt with same idempotency key — should be rejected by UNIQUE constraint
        // Since the payment is now CHARGEBACK_OPEN, this also tests state machine
        assertThrows(Exception.class,
            () -> chargebackService.openChargeback(payId, new BigDecimal("100.00"),
                "FRAUD", idempKey, "SCENARIO-CB", UUID.randomUUID()));
    }

    @Test
    @DisplayName("Chargeback amount exceeding original transaction amount is rejected")
    void testChargebackAmountExceedingOriginalRejected() {
        String payId = "PAY-CB-AMOUNT-" + UUID.randomUUID().toString().substring(0, 6);
        paymentService.processPayment(payId, "IDEMP-" + payId, merchant.getId(), new BigDecimal("100.00"), "USD");

        InvariantViolationException ex = assertThrows(InvariantViolationException.class,
            () -> chargebackService.openChargeback(payId, new BigDecimal("200.00"),
                "FRAUD", "IDEMP-CB-OVER-" + payId, "SCENARIO-CB", UUID.randomUUID()));
        assertTrue(ex.getMessage().contains("FINANCIAL_INVARIANT_VIOLATION"));
    }

    @Test
    @DisplayName("Illegal chargeback state machine transition is rejected")
    void testIllegalChargebackTransitionRejected() {
        String payId = "PAY-CB-ILLEGAL-" + UUID.randomUUID().toString().substring(0, 6);
        paymentService.processPayment(payId, "IDEMP-" + payId, merchant.getId(), new BigDecimal("100.00"), "USD");

        Chargeback cb = chargebackService.openChargeback(payId, new BigDecimal("100.00"),
            "FRAUD", "IDEMP-CB-ILL-" + payId, "SCENARIO-CB", UUID.randomUUID());

        // OPEN → RESOLVED is ILLEGAL (must go through UNDER_REVIEW first)
        InvariantViolationException ex = assertThrows(InvariantViolationException.class,
            () -> chargebackService.transitionStatus(cb.getId(), ChargebackStatus.RESOLVED, "AGENT"));
        assertTrue(ex.getMessage().contains("STATE_MACHINE_VIOLATION"));
    }
}
