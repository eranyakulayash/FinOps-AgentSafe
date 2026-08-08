package com.finops.agentsafe.service;

import com.finops.agentsafe.audit.AuditChainVerifier;
import com.finops.agentsafe.clock.SimulatorClock;
import com.finops.agentsafe.domain.Chargeback;
import com.finops.agentsafe.domain.HumanApprovalRequest;
import com.finops.agentsafe.domain.Transaction;
import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.enums.ApprovalStatus;
import com.finops.agentsafe.enums.ChargebackStatus;
import com.finops.agentsafe.enums.TransactionStatus;
import com.finops.agentsafe.exception.ApprovalRequiredException;
import com.finops.agentsafe.failure.FailureInjectionContext;
import com.finops.agentsafe.identifier.IdentifierGenerator;
import com.finops.agentsafe.repository.ChargebackRepository;
import com.finops.agentsafe.repository.HumanApprovalRequestRepository;
import com.finops.agentsafe.repository.TransactionRepository;
import com.finops.agentsafe.statemachine.ChargebackStateMachine;
import com.finops.agentsafe.statemachine.PaymentStateMachine;
import com.finops.agentsafe.validator.InvariantViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the chargeback lifecycle.
 *
 * Financial invariants:
 *   - Chargebacks must reference an existing SETTLED or RECONCILED payment
 *   - Chargeback amount cannot exceed the original transaction amount
 *   - Duplicate chargebacks on the same transaction are prevented by idempotency key
 *   - Status transitions enforced by ChargebackStateMachine
 *   - High-risk resolution (ACCEPTED, RESOLVED) may require human approval
 *   - All actions are audited
 */
@Service
public class ChargebackService {

    private final ChargebackRepository chargebackRepository;
    private final TransactionRepository transactionRepository;
    private final HumanApprovalRequestRepository approvalRepository;
    private final AuditService auditService;
    private final SimulatorClock clock;
    private final IdentifierGenerator identifierGenerator;

    public ChargebackService(ChargebackRepository chargebackRepository,
                              TransactionRepository transactionRepository,
                              HumanApprovalRequestRepository approvalRepository,
                              AuditService auditService,
                              SimulatorClock clock,
                              IdentifierGenerator identifierGenerator) {
        this.chargebackRepository = chargebackRepository;
        this.transactionRepository = transactionRepository;
        this.approvalRepository = approvalRepository;
        this.auditService = auditService;
        this.clock = clock;
        this.identifierGenerator = identifierGenerator;
    }

    /**
     * Open a new chargeback against an eligible payment.
     */
    @Transactional
    public Chargeback openChargeback(String originalTransactionId, BigDecimal amount, String reasonCode,
                                     String idempotencyKey, String scenarioId, UUID runId) {
        // Idempotency
        Optional<Chargeback> existing = chargebackRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        Transaction original = transactionRepository.findById(originalTransactionId)
            .orElseThrow(() -> new IllegalArgumentException("Original transaction not found: " + originalTransactionId));

        // Only SETTLED, RECONCILED payments are eligible for chargeback
        if (original.getStatus() != TransactionStatus.SETTLED && original.getStatus() != TransactionStatus.RECONCILED) {
            throw new InvariantViolationException(
                "STATE_MACHINE_VIOLATION: Chargeback can only be opened against a SETTLED or RECONCILED payment. " +
                "Current status: " + original.getStatus());
        }

        // Amount validation
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvariantViolationException("FINANCIAL_INVARIANT_VIOLATION: Chargeback amount must be positive.");
        }
        if (amount.compareTo(original.getAmount()) > 0) {
            throw new InvariantViolationException(
                "FINANCIAL_INVARIANT_VIOLATION: Chargeback amount $" + amount +
                " exceeds original transaction amount $" + original.getAmount());
        }

        UUID resolvedRunId = runId != null ? runId : FailureInjectionContext.getRunId();
        String resolvedScenarioId = scenarioId != null ? scenarioId : FailureInjectionContext.getScenarioId();

        Chargeback chargeback = new Chargeback(
            identifierGenerator.nextUUID(),
            originalTransactionId,
            amount,
            reasonCode,
            idempotencyKey,
            ChargebackStatus.OPEN,
            resolvedScenarioId,
            resolvedRunId,
            clock.now()
        );

        Chargeback saved = chargebackRepository.save(chargeback);

        // Transition payment to CHARGEBACK_OPEN
        PaymentStateMachine.validateTransition(original.getStatus(), TransactionStatus.CHARGEBACK_OPEN);
        original.setStatus(TransactionStatus.CHARGEBACK_OPEN);
        transactionRepository.save(original);

        auditService.recordAuditEvent(
            resolvedRunId, resolvedScenarioId, "SYSTEM", "OPEN_CHARGEBACK", "CHARGEBACK",
            ActionRiskLevel.HIGH_RISK_WRITE,
            originalTransactionId + "|" + amount + "|" + reasonCode,
            "ALLOWED", "SUCCESS", "SETTLED", saved.getId().toString(), null, null,
            "Chargeback opened: reason=" + reasonCode + ", amount=$" + amount
        );

        return saved;
    }

    /**
     * Transition a chargeback to a new status.
     *
     * Transitioning to ACCEPTED or RESOLVED requires a valid APPROVED HumanApprovalRequest
     * for action "RESOLVE_CHARGEBACK" on the related transaction.
     */
    @Transactional
    public Chargeback transitionStatus(UUID chargebackId, ChargebackStatus newStatus, String actor) {
        Chargeback chargeback = chargebackRepository.findById(chargebackId)
            .orElseThrow(() -> new IllegalArgumentException("Chargeback not found: " + chargebackId));

        ChargebackStateMachine.validateTransition(chargeback.getStatus(), newStatus);

        // High-risk resolution requires human approval
        if (newStatus == ChargebackStatus.ACCEPTED || newStatus == ChargebackStatus.RESOLVED) {
            Optional<HumanApprovalRequest> approval =
                approvalRepository.findFirstByRelatedTransactionIdAndRequestedActionAndStatus(
                    chargeback.getOriginalTransactionId(), "RESOLVE_CHARGEBACK", ApprovalStatus.APPROVED);

            if (approval.isEmpty()) {
                UUID runId = chargeback.getRunId() != null ? chargeback.getRunId() : FailureInjectionContext.getRunId();
                String scenarioId = chargeback.getScenarioId() != null ? chargeback.getScenarioId() : FailureInjectionContext.getScenarioId();

                HumanApprovalRequest newApproval = new HumanApprovalRequest(
                    identifierGenerator.nextUUID(), runId, scenarioId, actor,
                    "RESOLVE_CHARGEBACK", "CHARGEBACK_RESOLUTION",
                    "Chargeback resolution to status [" + newStatus + "] requires human approval.",
                    chargeback.getOriginalTransactionId(), null,
                    ApprovalStatus.REQUESTED, null, clock.now(), clock.now().plusSeconds(86400)
                );
                approvalRepository.save(newApproval);

                throw new ApprovalRequiredException(newApproval.getId(), "RESOLVE_CHARGEBACK",
                    "Chargeback resolution to [" + newStatus + "] requires human approval.");
            }
        }

        ChargebackStatus previousStatus = chargeback.getStatus();
        chargeback.setStatus(newStatus);
        if (newStatus == ChargebackStatus.RESOLVED || newStatus == ChargebackStatus.CLOSED) {
            chargeback.setResolvedAt(clock.now());
            // Transition original payment to resolved
            transactionRepository.findById(chargeback.getOriginalTransactionId()).ifPresent(tx -> {
                if (tx.getStatus() == TransactionStatus.CHARGEBACK_OPEN) {
                    tx.setStatus(TransactionStatus.CHARGEBACK_RESOLVED);
                    transactionRepository.save(tx);
                }
            });
        }

        Chargeback saved = chargebackRepository.save(chargeback);

        UUID resolvedRunId = chargeback.getRunId() != null ? chargeback.getRunId() : FailureInjectionContext.getRunId();
        String resolvedScenarioId = chargeback.getScenarioId() != null ? chargeback.getScenarioId() : FailureInjectionContext.getScenarioId();

        auditService.recordAuditEvent(
            resolvedRunId, resolvedScenarioId, actor, "TRANSITION_CHARGEBACK", "CHARGEBACK",
            ActionRiskLevel.HIGH_RISK_WRITE,
            chargebackId.toString() + "|" + newStatus,
            "ALLOWED", "SUCCESS", previousStatus.name(), chargebackId.toString(), null, null,
            "Chargeback transitioned: " + previousStatus + " → " + newStatus
        );

        return saved;
    }

    public Optional<Chargeback> getChargeback(UUID chargebackId) {
        return chargebackRepository.findById(chargebackId);
    }
}
