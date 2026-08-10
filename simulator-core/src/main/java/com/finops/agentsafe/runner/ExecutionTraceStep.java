package com.finops.agentsafe.runner;

import com.finops.agentsafe.policy.PolicyDecision;
import com.finops.agentsafe.tool.AgentToolResult;

import java.time.Instant;
import java.util.Map;

/**
 * Single step in a scenario execution trace.
 * Does NOT require or store private model chain-of-thought.
 * Supports briefReasoningSummary when supplied voluntarily.
 */
public class ExecutionTraceStep {

    private final int stepNumber;
    private final Instant timestamp;
    private final String agentId;
    private final String requestedTool;
    private final Map<String, Object> arguments;
    private final String toolInputHash;
    private final PolicyDecision policyDecision;
    private final AgentToolResult toolResult;
    private final boolean financialStateChanged;
    private final boolean failureInjected;
    private final boolean approvalRequested;
    private final boolean humanEscalation;
    private final String auditEventId;
    private final String briefReasoningSummary;

    public ExecutionTraceStep(int stepNumber, Instant timestamp, String agentId, String requestedTool,
                              String toolInputHash, PolicyDecision policyDecision, AgentToolResult toolResult,
                              boolean financialStateChanged, boolean failureInjected, boolean approvalRequested,
                              boolean humanEscalation, String auditEventId, String briefReasoningSummary) {
        this(stepNumber, timestamp, agentId, requestedTool, java.util.Map.of(), toolInputHash, policyDecision, toolResult,
             financialStateChanged, failureInjected, approvalRequested, humanEscalation, auditEventId, briefReasoningSummary);
    }

    public ExecutionTraceStep(int stepNumber, Instant timestamp, String agentId, String requestedTool, java.util.Map<String, Object> arguments,
                              String toolInputHash, PolicyDecision policyDecision, AgentToolResult toolResult,
                              boolean financialStateChanged, boolean failureInjected, boolean approvalRequested,
                              boolean humanEscalation, String auditEventId, String briefReasoningSummary) {
        this.stepNumber = stepNumber;
        this.timestamp = timestamp;
        this.agentId = agentId;
        this.requestedTool = requestedTool;
        this.arguments = arguments != null ? arguments : java.util.Map.of();
        this.toolInputHash = toolInputHash;
        this.policyDecision = policyDecision;
        this.toolResult = toolResult;
        this.financialStateChanged = financialStateChanged;
        this.failureInjected = failureInjected;
        this.approvalRequested = approvalRequested;
        this.humanEscalation = humanEscalation;
        this.auditEventId = auditEventId;
        this.briefReasoningSummary = briefReasoningSummary;
    }

    public int getStepNumber() { return stepNumber; }
    public Instant getTimestamp() { return timestamp; }
    public String getAgentId() { return agentId; }
    public String getRequestedTool() { return requestedTool; }
    public java.util.Map<String, Object> getArguments() { return arguments; }
    public String getToolInputHash() { return toolInputHash; }
    public PolicyDecision getPolicyDecision() { return policyDecision; }
    public AgentToolResult getToolResult() { return toolResult; }
    public boolean isFinancialStateChanged() { return financialStateChanged; }
    public boolean isFailureInjected() { return failureInjected; }
    public boolean isApprovalRequested() { return approvalRequested; }
    public boolean isHumanEscalation() { return humanEscalation; }
    public String getAuditEventId() { return auditEventId; }
    public String getBriefReasoningSummary() { return briefReasoningSummary; }
}
