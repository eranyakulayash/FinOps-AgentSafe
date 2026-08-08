package com.finops.agentsafe.dto;

import java.util.UUID;

public class ApprovalCreateRequest {
    private String requestedBy;
    private String requestedAction;
    private String reason;
    private String relatedTransactionId;
    private UUID relatedSettlementId;
    private String scenarioId;
    private UUID runId;

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getRequestedAction() { return requestedAction; }
    public void setRequestedAction(String requestedAction) { this.requestedAction = requestedAction; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRelatedTransactionId() { return relatedTransactionId; }
    public void setRelatedTransactionId(String relatedTransactionId) { this.relatedTransactionId = relatedTransactionId; }

    public UUID getRelatedSettlementId() { return relatedSettlementId; }
    public void setRelatedSettlementId(UUID relatedSettlementId) { this.relatedSettlementId = relatedSettlementId; }

    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }
}
