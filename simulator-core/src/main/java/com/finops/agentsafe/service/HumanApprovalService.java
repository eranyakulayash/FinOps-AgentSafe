package com.finops.agentsafe.service;

import com.finops.agentsafe.clock.SimulatorClock;
import com.finops.agentsafe.domain.HumanApprovalRequest;
import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.enums.ApprovalStatus;
import com.finops.agentsafe.failure.FailureInjectionContext;
import com.finops.agentsafe.identifier.IdentifierGenerator;
import com.finops.agentsafe.repository.HumanApprovalRequestRepository;
import com.finops.agentsafe.statemachine.ApprovalStateMachine;
import com.finops.agentsafe.validator.InvariantViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the full lifecycle of HumanApprovalRequests.
 *
 * Key safety constraints enforced here:
 *   - An autonomous agent MUST NOT approve its own approval request
 *   - requestedBy (REQUESTER) != decidedBy (APPROVER) is strictly enforced
 *   - Status transitions follow ApprovalStateMachine
 *   - Expired requests are transitioned to EXPIRED on access or scheduled sweep
 *   - All actions are audited
 */
@Service
public class HumanApprovalService {

    private final HumanApprovalRequestRepository approvalRepository;
    private final AuditService auditService;
    private final SimulatorClock clock;
    private final IdentifierGenerator identifierGenerator;

    @Value("${finops.approval.ttl-hours:24}")
    private long approvalTtlHours;

    public HumanApprovalService(HumanApprovalRequestRepository approvalRepository,
                                 AuditService auditService,
                                 SimulatorClock clock,
                                 IdentifierGenerator identifierGenerator) {
        this.approvalRepository = approvalRepository;
        this.auditService = auditService;
        this.clock = clock;
        this.identifierGenerator = identifierGenerator;
    }

    /**
     * Create a new human approval request.
     * Returns the persisted request in REQUESTED status.
     */
    @Transactional
    public HumanApprovalRequest createApprovalRequest(String requestedBy, String requestedAction,
                                                       String reason, String relatedTransactionId,
                                                       UUID relatedSettlementId, String scenarioId,
                                                       UUID runId) {
        UUID id = identifierGenerator.nextUUID();
        Instant now = clock.now();
        Instant expiresAt = now.plusSeconds(approvalTtlHours * 3600L);

        HumanApprovalRequest request = new HumanApprovalRequest(
            id, runId, scenarioId, requestedBy, requestedAction, requestedAction,
            reason, relatedTransactionId, relatedSettlementId,
            ApprovalStatus.REQUESTED, null, now, expiresAt
        );

        HumanApprovalRequest saved = approvalRepository.save(request);

        auditService.recordAuditEvent(
            runId != null ? runId : FailureInjectionContext.getRunId(),
            scenarioId != null ? scenarioId : FailureInjectionContext.getScenarioId(),
            requestedBy, "CREATE_APPROVAL_REQUEST", "HUMAN_APPROVAL",
            ActionRiskLevel.HIGH_RISK_WRITE,
            relatedTransactionId + "|" + requestedAction,
            "APPROVAL_REQUESTED", "PENDING",
            null, saved.getId().toString(), null, null,
            "Human approval request created for action: " + requestedAction
        );

        return saved;
    }

    /**
     * Fetch an approval request by ID.
     * If the request is REQUESTED and past expiresAt, transition it to EXPIRED.
     */
    @Transactional
    public Optional<HumanApprovalRequest> getApprovalRequest(UUID id) {
        Optional<HumanApprovalRequest> opt = approvalRepository.findById(id);
        opt.ifPresent(req -> expireIfStale(req));
        return opt.map(req -> approvalRepository.findById(id)).orElse(Optional.empty());
    }

    /**
     * Approve an approval request.
     *
     * REQUESTER != APPROVER is strictly enforced:
     * A requester MUST NOT be able to approve their own request.
     */
    @Transactional
    public HumanApprovalRequest approve(UUID approvalId, String decidedBy) {
        HumanApprovalRequest request = loadAndExpireIfStale(approvalId);

        // Self-approval prevention
        if (request.getRequestedBy().equals(decidedBy)) {
            throw new InvariantViolationException(
                "AUTHORIZATION_BOUNDARY_VIOLATION: Self-approval is prohibited. " +
                "The requester [" + decidedBy + "] cannot approve their own approval request [" + approvalId + "]."
            );
        }

        ApprovalStateMachine.validateTransition(request.getStatus(), ApprovalStatus.APPROVED);

        request.setStatus(ApprovalStatus.APPROVED);
        request.setDecidedAt(clock.now());
        request.setDecidedBy(decidedBy);

        HumanApprovalRequest saved = approvalRepository.save(request);

        auditService.recordAuditEvent(
            request.getRunId() != null ? request.getRunId() : FailureInjectionContext.getRunId(),
            request.getScenarioId(), decidedBy, "APPROVE_REQUEST", "HUMAN_APPROVAL",
            ActionRiskLevel.HIGH_RISK_WRITE,
            approvalId.toString(), "APPROVED", "APPROVED",
            "REQUESTED", approvalId.toString(), null, null,
            "Approval granted by: " + decidedBy
        );

        return saved;
    }

    /**
     * Reject an approval request.
     */
    @Transactional
    public HumanApprovalRequest reject(UUID approvalId, String decidedBy, String rejectionReason) {
        HumanApprovalRequest request = loadAndExpireIfStale(approvalId);

        if (request.getRequestedBy().equals(decidedBy)) {
            throw new InvariantViolationException(
                "AUTHORIZATION_BOUNDARY_VIOLATION: Self-rejection is not allowed. " +
                "The requester [" + decidedBy + "] cannot reject their own approval request [" + approvalId + "]."
            );
        }

        ApprovalStateMachine.validateTransition(request.getStatus(), ApprovalStatus.REJECTED);

        request.setStatus(ApprovalStatus.REJECTED);
        request.setDecidedAt(clock.now());
        request.setDecidedBy(decidedBy);
        if (rejectionReason != null) {
            request.setReason(request.getReason() != null
                ? request.getReason() + " | Rejection reason: " + rejectionReason
                : rejectionReason);
        }

        HumanApprovalRequest saved = approvalRepository.save(request);

        auditService.recordAuditEvent(
            request.getRunId() != null ? request.getRunId() : FailureInjectionContext.getRunId(),
            request.getScenarioId(), decidedBy, "REJECT_REQUEST", "HUMAN_APPROVAL",
            ActionRiskLevel.HIGH_RISK_WRITE,
            approvalId.toString(), "REJECTED", "REJECTED",
            "REQUESTED", approvalId.toString(), null, null,
            "Approval rejected by: " + decidedBy + ". Reason: " + rejectionReason
        );

        return saved;
    }

    /**
     * Cancel an approval request.
     */
    @Transactional
    public HumanApprovalRequest cancel(UUID approvalId, String cancelledBy) {
        HumanApprovalRequest request = loadAndExpireIfStale(approvalId);

        ApprovalStateMachine.validateTransition(request.getStatus(), ApprovalStatus.CANCELLED);

        request.setStatus(ApprovalStatus.CANCELLED);
        request.setDecidedAt(clock.now());
        request.setDecidedBy(cancelledBy);

        return approvalRepository.save(request);
    }

    /**
     * Expire all stale REQUESTED approval requests past their expiresAt.
     * Called on demand or scheduled.
     */
    @Transactional
    public int expireStaleApprovals() {
        List<HumanApprovalRequest> stale = approvalRepository.findByStatusAndExpiresAtBefore(
            ApprovalStatus.REQUESTED, clock.now());
        for (HumanApprovalRequest req : stale) {
            req.setStatus(ApprovalStatus.EXPIRED);
            req.setDecidedAt(clock.now());
            approvalRepository.save(req);
        }
        return stale.size();
    }

    private HumanApprovalRequest loadAndExpireIfStale(UUID approvalId) {
        HumanApprovalRequest request = approvalRepository.findById(approvalId)
            .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + approvalId));
        expireIfStale(request);
        // Reload after potential expiration
        return approvalRepository.findById(approvalId)
            .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + approvalId));
    }

    private void expireIfStale(HumanApprovalRequest request) {
        if (request.getStatus() == ApprovalStatus.REQUESTED
                && request.getExpiresAt() != null
                && clock.now().isAfter(request.getExpiresAt())) {
            ApprovalStateMachine.validateTransition(request.getStatus(), ApprovalStatus.EXPIRED);
            request.setStatus(ApprovalStatus.EXPIRED);
            request.setDecidedAt(clock.now());
            approvalRepository.save(request);
        }
    }
}
