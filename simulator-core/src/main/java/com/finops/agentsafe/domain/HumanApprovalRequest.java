package com.finops.agentsafe.domain;

import com.finops.agentsafe.enums.ApprovalStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted human approval request for high-risk financial operations.
 *
 * Key safety constraints:
 *   - requestedBy (REQUESTER) MUST NOT equal decidedBy (APPROVER)
 *   - An autonomous agent MUST NOT approve its own approval request
 *   - Status transitions follow ApprovalStateMachine
 *   - expiresAt is set at creation; once expired the request becomes EXPIRED
 */
@Entity
@Table(name = "human_approval_requests")
public class HumanApprovalRequest {

    @Id
    private UUID id;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "scenario_id", nullable = false)
    private String scenarioId;

    /** The actor who submitted this approval request (REQUESTER). */
    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    /** The action requiring approval (e.g. "EXECUTE_REVERSAL", "RESOLVE_CHARGEBACK"). */
    @Column(name = "requested_action")
    private String requestedAction;

    /** Legacy field — kept for backward compat with V1 schema. */
    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "related_transaction_id")
    private String relatedTransactionId;

    @Column(name = "related_settlement_id")
    private UUID relatedSettlementId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    @Column(name = "approver_token")
    private String approverToken;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** The actor who made the approval/rejection decision (APPROVER). Must differ from requestedBy. */
    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public HumanApprovalRequest() {}

    public HumanApprovalRequest(UUID id, UUID runId, String scenarioId, String requestedBy,
                                 String requestedAction, String actionType, String reason,
                                 String relatedTransactionId, UUID relatedSettlementId,
                                 ApprovalStatus status, String approverToken,
                                 Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.runId = runId;
        this.scenarioId = scenarioId;
        this.requestedBy = requestedBy;
        this.requestedAction = requestedAction;
        this.actionType = actionType;
        this.reason = reason;
        this.relatedTransactionId = relatedTransactionId;
        this.relatedSettlementId = relatedSettlementId;
        this.status = status;
        this.approverToken = approverToken;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    // --- Getters and Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getRequestedAction() { return requestedAction; }
    public void setRequestedAction(String requestedAction) { this.requestedAction = requestedAction; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRelatedTransactionId() { return relatedTransactionId; }
    public void setRelatedTransactionId(String relatedTransactionId) { this.relatedTransactionId = relatedTransactionId; }

    public UUID getRelatedSettlementId() { return relatedSettlementId; }
    public void setRelatedSettlementId(UUID relatedSettlementId) { this.relatedSettlementId = relatedSettlementId; }

    public ApprovalStatus getStatus() { return status; }
    public void setStatus(ApprovalStatus status) { this.status = status; }

    public String getApproverToken() { return approverToken; }
    public void setApproverToken(String approverToken) { this.approverToken = approverToken; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
