package com.finops.agentsafe.exception;

import java.util.UUID;

/**
 * Thrown when a high-risk operation requires human approval before execution.
 *
 * The financial operation MUST NOT execute until a valid APPROVED approval exists.
 * The caller receives HTTP 409 with body:
 * {
 *   "status": "APPROVAL_REQUIRED",
 *   "approvalRequestId": "...",
 *   "requestedAction": "...",
 *   "reason": "..."
 * }
 */
public class ApprovalRequiredException extends RuntimeException {

    private final UUID approvalRequestId;
    private final String requestedAction;
    private final String approvalReason;

    public ApprovalRequiredException(UUID approvalRequestId, String requestedAction, String approvalReason) {
        super("APPROVAL_REQUIRED: Action [" + requestedAction + "] requires human approval. ApprovalId: " + approvalRequestId);
        this.approvalRequestId = approvalRequestId;
        this.requestedAction = requestedAction;
        this.approvalReason = approvalReason;
    }

    public UUID getApprovalRequestId() { return approvalRequestId; }
    public String getRequestedAction() { return requestedAction; }
    public String getApprovalReason() { return approvalReason; }
}
