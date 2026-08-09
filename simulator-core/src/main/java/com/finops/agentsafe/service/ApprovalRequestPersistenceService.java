package com.finops.agentsafe.service;

import com.finops.agentsafe.domain.HumanApprovalRequest;
import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.enums.ApprovalStatus;
import com.finops.agentsafe.repository.HumanApprovalRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Responsible for durably persisting a {@link HumanApprovalRequest} in its own isolated
 * transaction ({@code REQUIRES_NEW}), independent of the caller's transaction context.
 *
 * <h3>Why REQUIRES_NEW?</h3>
 * When {@code PaymentService.processReversal()} determines that human approval is required,
 * it needs to:
 * <ol>
 *   <li>Save the approval request to PostgreSQL so the caller can look it up later.</li>
 *   <li>Throw {@link com.finops.agentsafe.exception.ApprovalRequiredException} to halt
 *       the financial operation.</li>
 * </ol>
 * Under the default {@code @Transactional} propagation, the exception would cause Spring
 * to roll back the entire transaction, including the just-saved approval row.  The caller
 * would then receive an approval ID that does not exist in the database — violating the
 * HITL (Human-In-The-Loop) contract.
 *
 * <p>By using {@code REQUIRES_NEW} here, the approval row and its accompanying audit event
 * are committed atomically <em>before</em> control returns to the calling transaction.
 * The outer transaction may still roll back (it contains no financial mutations in the
 * APPROVAL_REQUIRED branch), but the approval record survives.
 *
 * <h3>Financial safety proof</h3>
 * This service is called only when {@code approval.isEmpty()} — i.e., before any reversal
 * {@link com.finops.agentsafe.domain.Transaction} is created and before any payment-status
 * state-machine transition executes.  Rolling back the outer transaction therefore commits
 * zero financial side-effects.
 */
@Service
public class ApprovalRequestPersistenceService {

    private final HumanApprovalRequestRepository approvalRepository;
    private final AuditService auditService;

    public ApprovalRequestPersistenceService(HumanApprovalRequestRepository approvalRepository,
                                             AuditService auditService) {
        this.approvalRepository = approvalRepository;
        this.auditService = auditService;
    }

    /**
     * Persist a new {@link HumanApprovalRequest} in an isolated transaction that commits
     * immediately, regardless of the caller's transaction outcome.
     *
     * <p>The approval record and its audit event are written atomically within this
     * {@code REQUIRES_NEW} transaction.  The caller receives the persisted entity with
     * a valid database-assigned state, ready to be referenced by ID.
     *
     * @param id                   pre-generated UUID for the approval request
     * @param runId                benchmark run context
     * @param scenarioId           scenario context
     * @param requestedBy          actor who triggered the blocked operation
     * @param requestedAction      the action requiring approval (e.g. "EXECUTE_REVERSAL")
     * @param resourceType         resource type label (e.g. "REVERSAL")
     * @param approvalReason       human-readable reason for the approval request
     * @param relatedTransactionId the original payment transaction ID
     * @param createdAt            creation timestamp from the caller's clock
     * @param expiresAt            expiry timestamp
     * @return the persisted {@link HumanApprovalRequest}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public HumanApprovalRequest persistApprovalRequest(
            UUID id,
            UUID runId,
            String scenarioId,
            String requestedBy,
            String requestedAction,
            String resourceType,
            String approvalReason,
            String relatedTransactionId,
            Instant createdAt,
            Instant expiresAt) {

        HumanApprovalRequest newApproval = new HumanApprovalRequest(
            id,
            runId,
            scenarioId,
            requestedBy,
            requestedAction,
            resourceType,
            approvalReason,
            relatedTransactionId,
            null,                    // relatedSettlementId — not applicable for reversals
            ApprovalStatus.REQUESTED,
            null,                    // decidedBy — not yet decided
            createdAt,
            expiresAt
        );

        HumanApprovalRequest saved = approvalRepository.save(newApproval);

        // Record the audit event inside the same REQUIRES_NEW transaction so it
        // commits with the approval or not at all.
        auditService.recordAuditEvent(
            runId,
            scenarioId,
            requestedBy,
            requestedAction,
            "REQUEST_" + requestedAction,
            ActionRiskLevel.HIGH_RISK_WRITE,
            relatedTransactionId + "|APPROVAL_REQUIRED",
            "APPROVAL_REQUIRED",
            "BLOCKED",
            null,
            null,
            saved.getId().toString(),
            null,
            "Financial operation blocked — human approval required for: " + requestedAction
        );

        return saved;
    }
}
