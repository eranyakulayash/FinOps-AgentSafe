package com.finops.agentsafe;

import com.finops.agentsafe.audit.AuditChainVerifier;
import com.finops.agentsafe.domain.*;
import com.finops.agentsafe.enums.*;
import com.finops.agentsafe.exception.ApprovalRequiredException;
import com.finops.agentsafe.repository.*;
import com.finops.agentsafe.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test demonstrating the complete human escalation workflow:
 *
 *   1. Synthetic Payment processed
 *   2. Financial Exception (discrepancy) logged via ReconciliationService
 *   3. High-risk action attempted (Reversal) → blocks with APPROVAL_REQUIRED (HTTP 409)
 *   4. HumanApprovalRequest created in REQUESTED status
 *   5. Different human actor (SUPERVISOR) approves the request
 *   6. Financial action (Reversal) re-attempted → succeeds
 *   7. Tamper-evident AuditEvent created for all steps
 *   8. AuditChainVerifier verifies 100% audit chain integrity
 */
class EndToEndWorkflowIntegrationTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private HumanApprovalService approvalService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private SettlementBatchRepository batchRepository;

    @Autowired
    private SettlementLineItemRepository lineItemRepository;

    private Merchant merchant;
    private UUID runId;
    private String scenarioId;

    @BeforeEach
    void setUp() {
        runId = UUID.randomUUID();
        scenarioId = "SCENARIO-E2E-ESCALATION-001";
        com.finops.agentsafe.failure.FailureInjectionContext.setRunAndScenario(runId, scenarioId);
        merchant = new Merchant(UUID.randomUUID(), "End-To-End Enterprise Inc", new BigDecimal("2.00"), "ACTIVE");
        merchantRepository.save(merchant);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        com.finops.agentsafe.failure.FailureInjectionContext.clear();
    }

    @Test
    @DisplayName("Complete Workflow Demonstration: Payment → Discrepancy → High-Risk Attempt → APPROVAL_REQUIRED → Human Approval → Reversal Executed → Audit Chain Verified")
    void testCompleteHumanEscalationWorkflow() {
        // Step 1: Process Synthetic Payment ($500.00)
        String paymentId = "PAY-E2E-" + UUID.randomUUID().toString().substring(0, 8);
        Transaction payment = paymentService.processPayment(
            paymentId, "IDEMP-" + paymentId, merchant.getId(), new BigDecimal("500.00"), "USD");

        assertNotNull(payment);
        assertEquals(TransactionStatus.SETTLED, payment.getStatus());

        // Step 2: Create external settlement batch with line item showing discrepancy ($450.00 vs $500.00)
        UUID batchId = UUID.randomUUID();
        SettlementBatch batch = new SettlementBatch(
            batchId, merchant.getId(), "settlement.csv", "E2E Batch",
            new BigDecimal("450.00"), new BigDecimal("10.00"), new BigDecimal("440.00"),
            SettlementStatus.UNPROCESSED
        );
        batchRepository.save(batch);

        UUID lineItemId = UUID.randomUUID();
        SettlementLineItem lineItem = new SettlementLineItem(
            lineItemId, batch, "EXT-" + paymentId,
            new BigDecimal("450.00"), new BigDecimal("10.00"), new BigDecimal("440.00")
        );
        lineItemRepository.save(lineItem);

        ReconciliationRecord reconRecord = reconciliationService.reconcileTransaction(paymentId, lineItemId);
        assertEquals(MatchStatus.AMOUNT_MISMATCH, reconRecord.getMatchStatus());
        assertEquals(new BigDecimal("50.00"), reconRecord.getDiscrepancyAmount());

        // Step 3: High-risk financial action attempted (Reversal of $500.00) by AGENT_UNDER_TEST
        // Must fail with APPROVAL_REQUIRED because no prior human approval exists
        String reversalTxId = "REV-E2E-" + UUID.randomUUID().toString().substring(0, 8);
        ApprovalRequiredException approvalEx = assertThrows(ApprovalRequiredException.class, () ->
            paymentService.processReversal(
                reversalTxId,
                "IDEMP-" + reversalTxId,
                paymentId,
                new BigDecimal("500.00"),
                "AGENT_UNDER_TEST"
            )
        );

        assertNotNull(approvalEx.getApprovalRequestId());
        assertEquals("EXECUTE_REVERSAL", approvalEx.getRequestedAction());

        // Step 4: Verify HumanApprovalRequest created in REQUESTED status
        UUID approvalId = approvalEx.getApprovalRequestId();
        HumanApprovalRequest pendingApproval = approvalService.getApprovalRequest(approvalId).orElseThrow();
        assertEquals(ApprovalStatus.REQUESTED, pendingApproval.getStatus());
        assertEquals("AGENT_UNDER_TEST", pendingApproval.getRequestedBy());
        assertEquals(paymentId, pendingApproval.getRelatedTransactionId());

        // Step 5: Different human actor (HUMAN_SUPERVISOR_ALICE) approves the request
        HumanApprovalRequest approvedReq = approvalService.approve(approvalId, "HUMAN_SUPERVISOR_ALICE");
        assertEquals(ApprovalStatus.APPROVED, approvedReq.getStatus());
        assertEquals("HUMAN_SUPERVISOR_ALICE", approvedReq.getDecidedBy());
        assertNotNull(approvedReq.getDecidedAt());

        // Step 6: Reversal re-attempted by AGENT_UNDER_TEST — now succeeds because APPROVED approval exists
        Transaction reversal = paymentService.processReversal(
            reversalTxId,
            "IDEMP-" + reversalTxId,
            paymentId,
            new BigDecimal("500.00"),
            "AGENT_UNDER_TEST"
        );

        assertNotNull(reversal);
        assertEquals(TransactionType.REVERSAL, reversal.getType());
        assertEquals(new BigDecimal("500.00"), reversal.getAmount());

        // Verify original payment status transitioned to REVERSED in PostgreSQL
        Transaction updatedPayment = transactionRepository.findById(paymentId).orElseThrow();
        assertEquals(TransactionStatus.REVERSED, updatedPayment.getStatus());

        // Step 7 & 8: Query audit trail and verify tamper-evident chain integrity
        List<AuditEvent> auditTrail = auditService.getAuditTrailByScenarioId(scenarioId);
        assertFalse(auditTrail.isEmpty(), "Audit trail must record all workflow actions");

        AuditChainVerifier.AuditChainVerificationResult verification = AuditChainVerifier.verifyChain(
            auditTrail, auditService::hashString);

        assertTrue(verification.isValid(), "Audit chain verification MUST pass: " + verification.getBrokenLinks());
        assertTrue(verification.getEventsChecked() >= 4, "Must verify all logged workflow events");
    }
}
