package com.finops.agentsafe.domain;

import com.finops.agentsafe.enums.ActionRiskLevel;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "scenario_id", nullable = false)
    private String scenarioId;

    @Column(nullable = false)
    private String actor;

    @Column(name = "requested_action", nullable = false)
    private String requestedAction;

    @Column(name = "tool_used", nullable = false)
    private String toolUsed;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private ActionRiskLevel riskLevel;

    @Column(name = "input_payload_hash")
    private String inputPayloadHash;

    @Column(name = "authz_decision", nullable = false)
    private String authzDecision;

    @Column(name = "execution_result", nullable = false)
    private String executionResult;

    @Column(name = "before_state_ref")
    private String beforeStateRef;

    @Column(name = "after_state_ref")
    private String afterStateRef;

    @Column(name = "injected_failure_type")
    private String injectedFailureType;

    @Column(name = "human_approval_info", columnDefinition = "TEXT")
    private String humanApprovalInfo;

    @Column(name = "reasoning_summary", columnDefinition = "TEXT")
    private String reasoningSummary;

    @Column(name = "prev_hash", nullable = false)
    private String prevHash;

    @Column(name = "current_hash", nullable = false)
    private String currentHash;

    @Column(nullable = false)
    private Instant timestamp;

    public AuditEvent() {}

    public AuditEvent(UUID id, UUID runId, String scenarioId, String actor, String requestedAction, String toolUsed, ActionRiskLevel riskLevel, String inputPayloadHash, String authzDecision, String executionResult, String beforeStateRef, String afterStateRef, String injectedFailureType, String humanApprovalInfo, String reasoningSummary, String prevHash, String currentHash) {
        this.id = id;
        this.runId = runId;
        this.scenarioId = scenarioId;
        this.actor = actor;
        this.requestedAction = requestedAction;
        this.toolUsed = toolUsed;
        this.riskLevel = riskLevel;
        this.inputPayloadHash = inputPayloadHash;
        this.authzDecision = authzDecision;
        this.executionResult = executionResult;
        this.beforeStateRef = beforeStateRef;
        this.afterStateRef = afterStateRef;
        this.injectedFailureType = injectedFailureType;
        this.humanApprovalInfo = humanApprovalInfo;
        this.reasoningSummary = reasoningSummary;
        this.prevHash = prevHash;
        this.currentHash = currentHash;
        this.timestamp = Instant.now(); // Will be overridden by AuditService via SimulatorClock
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getRequestedAction() { return requestedAction; }
    public void setRequestedAction(String requestedAction) { this.requestedAction = requestedAction; }

    public String getToolUsed() { return toolUsed; }
    public void setToolUsed(String toolUsed) { this.toolUsed = toolUsed; }

    public ActionRiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(ActionRiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public String getInputPayloadHash() { return inputPayloadHash; }
    public void setInputPayloadHash(String inputPayloadHash) { this.inputPayloadHash = inputPayloadHash; }

    public String getAuthzDecision() { return authzDecision; }
    public void setAuthzDecision(String authzDecision) { this.authzDecision = authzDecision; }

    public String getExecutionResult() { return executionResult; }
    public void setExecutionResult(String executionResult) { this.executionResult = executionResult; }

    public String getBeforeStateRef() { return beforeStateRef; }
    public void setBeforeStateRef(String beforeStateRef) { this.beforeStateRef = beforeStateRef; }

    public String getAfterStateRef() { return afterStateRef; }
    public void setAfterStateRef(String afterStateRef) { this.afterStateRef = afterStateRef; }

    public String getInjectedFailureType() { return injectedFailureType; }
    public void setInjectedFailureType(String injectedFailureType) { this.injectedFailureType = injectedFailureType; }

    public String getHumanApprovalInfo() { return humanApprovalInfo; }
    public void setHumanApprovalInfo(String humanApprovalInfo) { this.humanApprovalInfo = humanApprovalInfo; }

    public String getReasoningSummary() { return reasoningSummary; }
    public void setReasoningSummary(String reasoningSummary) { this.reasoningSummary = reasoningSummary; }

    public String getPrevHash() { return prevHash; }
    public void setPrevHash(String prevHash) { this.prevHash = prevHash; }

    public String getCurrentHash() { return currentHash; }
    public void setCurrentHash(String currentHash) { this.currentHash = currentHash; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
