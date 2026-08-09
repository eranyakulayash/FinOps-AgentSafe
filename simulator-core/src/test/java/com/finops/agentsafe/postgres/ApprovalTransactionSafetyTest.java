package com.finops.agentsafe.postgres;

import com.finops.agentsafe.AbstractPostgreSQLIntegrationTest;
import com.finops.agentsafe.domain.HumanApprovalRequest;
import com.finops.agentsafe.domain.Merchant;
import com.finops.agentsafe.domain.Transaction;
import com.finops.agentsafe.enums.ApprovalStatus;
import com.finops.agentsafe.enums.TransactionStatus;
import com.finops.agentsafe.enums.TransactionType;
import com.finops.agentsafe.exception.ApprovalRequiredException;
import com.finops.agentsafe.repository.HumanApprovalRequestRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the correctness of the REQUIRES_NEW approval-persistence contract.
 *
 * Key invariants verified:
 *   1. When APPROVAL_REQUIRED is thrown, the HumanApprovalRequest IS durably persisted.
 *   2. When APPROVAL_REQUIRED is thrown, NO reversal Transaction is created.
 *   3. When APPROVAL_REQUIRED is thrown, the original payment status is UNCHANGED.
 *   4. After approval, the reversal executes and the original payment status transitions.
 *   5. The requester cannot approve their own request (self-approval prevention).
 */
class ApprovalTransactionSafetyTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private HumanApprovalService approvalService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private HumanApprovalRequestRepository approvalRequestRepository;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = new Merchant(UUID.randomUUID(), "Safety Test Merchant", new BigDecimal("1.00"), "ACTIVE");
        merchantRepository.save(merchant);
    }

    @Test
    @DisplayName("APPROVAL_REQUIRED: HumanApprovalRequest IS durably persisted (REQUIRES_NEW commits)")
    void testApprovalRequestPersistedAfterApprovalRequired() {
        String payId = "PAY-SAFETY-PERSIST-" + UUID.randomUUID().toString().substring(0, 8);
        paymentService.processPayment(payId, "IDEMP-" + payId, merchant.getId(), new BigDecimal("100.00"), "USD");

        // First reversal attempt — must throw ApprovalRequiredException
        ApprovalRequiredException ex = assertThrows(ApprovalRequiredException.class, () ->
            paymentService.processReversal(
                "REV-SAFETY-PERSIST", "IDEMP-REV-SAFETY-PERSIST", payId,
                new BigDecimal("100.00"), "AGENT_UNDER_TEST")
        );

        UUID approvalId = ex.getApprovalRequestId();
        assertNotNull(approvalId, "ApprovalRequiredException must carry a non-null approvalRequestId");

        // THE CRITICAL ASSERTION: the approval must exist in the database
        Optional<HumanApprovalRequest> persisted = approvalRequestRepository.findById(approvalId);
        assertTrue(persisted.isPresent(),
            "HumanApprovalRequest MUST be durably persisted in PostgreSQL even though " +
            "ApprovalRequiredException was thrown. REQUIRES_NEW guarantees the save committed.");

        HumanApprovalRequest approval = persisted.get();
        assertEquals(ApprovalStatus.REQUESTED, approval.getStatus());
        assertEquals("AGENT_UNDER_TEST", approval.getRequestedBy());
        assertEquals(payId, approval.getRelatedTransactionId());
        assertEquals("EXECUTE_REVERSAL", approval.getRequestedAction());
        assertNotNull(approval.getExpiresAt());
    }

    @Test
    @DisplayName("APPROVAL_REQUIRED: NO reversal Transaction is created pre-approval")
    void testNoReversalTransactionCreatedBeforeApproval() {
        String payId = "PAY-SAFETY-NO-REV-" + UUID.randomUUID().toString().substring(0, 8);
        paymentService.processPayment(payId, "IDEMP-" + payId, merchant.getId(), new BigDecimal("200.00"), "USD");

        long txCountBefore = transactionRepository.count();

        assertThrows(ApprovalRequiredException.class, () ->
            paymentService.processReversal(
                "REV-SAFETY-NO-REV", "IDEMP-REV-SAFETY-NO-REV", payId,
                new BigDecimal("200.00"), "AGENT_UNDER_TEST")
        );

        long txCountAfter = transactionRepository.count();
        assertEquals(txCountBefore, txCountAfter,
            "No reversal Transaction must be created when APPROVAL_REQUIRED is thrown. " +
            "Financial state must remain unchanged.");

        // Verify original payment is still SETTLED
        Transaction payment = transactionRepository.findById(payId).orElseThrow();
        assertEquals(TransactionStatus.SETTLED, payment.getStatus(),
            "Original payment status must remain SETTLED when approval is required.");
    }

    @Test
    @DisplayName("APPROVAL_REQUIRED: original payment status is UNCHANGED (no state mutation pre-approval)")
    void testOriginalPaymentStatusUnchangedPreApproval() {
        String payId = "PAY-SAFETY-STATUS-" + UUID.randomUUID().toString().substring(0, 8);
        paymentService.processPayment(payId, "IDEMP-" + payId, merchant.getId(), new BigDecimal("150.00"), "USD");

        assertThrows(ApprovalRequiredException.class, () ->
            paymentService.processReversal(
                "REV-SAFETY-STATUS", "IDEMP-REV-SAFETY-STATUS", payId,
                new BigDecimal("150.00"), "AGENT_UNDER_TEST")
        );

        Transaction payment = transactionRepository.findById(payId).orElseThrow();
        assertEquals(TransactionStatus.SETTLED, payment.getStatus(),
            "Payment status MUST NOT mutate before approval is granted.");
    }

    @Test
    @DisplayName("Full HITL flow: approval persisted → approved → reversal executes → payment REVERSED")
    void testFullHITLFlowAfterApprovalPersisted() {
        String payId = "PAY-SAFETY-HITL-" + UUID.randomUUID().toString().substring(0, 8);
        paymentService.processPayment(payId, "IDEMP-" + payId, merchant.getId(), new BigDecimal("300.00"), "USD");

        // Step 1: Reversal attempt creates and durably persists an approval request
        ApprovalRequiredException ex = assertThrows(ApprovalRequiredException.class, () ->
            paymentService.processReversal(
                "REV-SAFETY-HITL", "IDEMP-REV-SAFETY-HITL", payId,
                new BigDecimal("300.00"), "AGENT_UNDER_TEST")
        );

        UUID approvalId = ex.getApprovalRequestId();

        // Step 2: Verify the approval is findable (REQUIRES_NEW committed)
        HumanApprovalRequest pending = approvalRequestRepository.findById(approvalId).orElseThrow(
            () -> new AssertionError("Approval request must be in DB after APPROVAL_REQUIRED"));
        assertEquals(ApprovalStatus.REQUESTED, pending.getStatus());

        // Step 3: Human supervisor approves (different actor — self-approval not allowed)
        HumanApprovalRequest approved = approvalService.approve(approvalId, "HUMAN_SUPERVISOR_BOB");
        assertEquals(ApprovalStatus.APPROVED, approved.getStatus());

        // Step 4: Reversal now succeeds
        Transaction reversal = paymentService.processReversal(
            "REV-SAFETY-HITL", "IDEMP-REV-SAFETY-HITL", payId,
            new BigDecimal("300.00"), "AGENT_UNDER_TEST");

        assertNotNull(reversal);
        assertEquals(TransactionType.REVERSAL, reversal.getType());
        assertEquals(new BigDecimal("300.00"), reversal.getAmount());

        // Step 5: Original payment status transitioned to REVERSED
        Transaction updatedPayment = transactionRepository.findById(payId).orElseThrow();
        assertEquals(TransactionStatus.REVERSED, updatedPayment.getStatus());
    }

    @Test
    @DisplayName("Self-approval prevention: AGENT_UNDER_TEST cannot approve its own APPROVAL_REQUIRED request")
    void testSelfApprovalBlockedForApprovalRequiredException() {
        String payId = "PAY-SAFETY-SELF-" + UUID.randomUUID().toString().substring(0, 8);
        paymentService.processPayment(payId, "IDEMP-" + payId, merchant.getId(), new BigDecimal("100.00"), "USD");

        ApprovalRequiredException ex = assertThrows(ApprovalRequiredException.class, () ->
            paymentService.processReversal(
                "REV-SAFETY-SELF", "IDEMP-REV-SAFETY-SELF", payId,
                new BigDecimal("100.00"), "AGENT_UNDER_TEST")
        );

        UUID approvalId = ex.getApprovalRequestId();

        // The requester attempts to self-approve — must be rejected
        InvariantViolationException selfApprovalEx = assertThrows(InvariantViolationException.class, () ->
            approvalService.approve(approvalId, "AGENT_UNDER_TEST")
        );

        assertTrue(selfApprovalEx.getMessage().contains("AUTHORIZATION_BOUNDARY_VIOLATION"),
            "Self-approval must throw AUTHORIZATION_BOUNDARY_VIOLATION");
        assertTrue(selfApprovalEx.getMessage().contains("AGENT_UNDER_TEST"),
            "Self-approval error must identify the requester/approver");
    }
}
