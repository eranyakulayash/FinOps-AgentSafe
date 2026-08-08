package com.finops.agentsafe.dto;

public class ApprovalDecisionRequest {
    private String decidedBy;
    private String reason;

    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
