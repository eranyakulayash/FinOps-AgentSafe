package com.finops.agentsafe.tool;

import java.util.UUID;

/**
 * Standardized result structure returned by all tool executions.
 * Matches the Phase 3 schema requirement.
 */
public class AgentToolResult {

    public enum Status {
        SUCCESS,
        FAILED,
        DENIED,
        APPROVAL_REQUIRED,
        ESCALATION_REQUIRED,
        RETRYABLE_FAILURE,
        NON_RETRYABLE_FAILURE,
        STEP_LIMIT_EXCEEDED
    }

    private final String toolName;
    private final Status status;
    private final UUID runId;
    private final String scenarioId;
    private final int stepNumber;
    private final java.util.Map<String, Object> arguments;
    private final Object result;
    private final String error;
    private final boolean requiresHumanAction;
    private final boolean financialStateChanged;
    private final String auditEventId;

    public AgentToolResult(String toolName, Status status, UUID runId, String scenarioId, int stepNumber,
                           Object result, String error, boolean requiresHumanAction,
                           boolean financialStateChanged, String auditEventId) {
        this(toolName, status, runId, scenarioId, stepNumber, java.util.Map.of(), result, error, requiresHumanAction, financialStateChanged, auditEventId);
    }

    public AgentToolResult(String toolName, Status status, UUID runId, String scenarioId, int stepNumber,
                           java.util.Map<String, Object> arguments, Object result, String error, boolean requiresHumanAction,
                           boolean financialStateChanged, String auditEventId) {
        this.toolName = toolName;
        this.status = status;
        this.runId = runId;
        this.scenarioId = scenarioId;
        this.stepNumber = stepNumber;
        this.arguments = arguments != null ? arguments : java.util.Map.of();
        this.result = result;
        this.error = error;
        this.requiresHumanAction = requiresHumanAction;
        this.financialStateChanged = financialStateChanged;
        this.auditEventId = auditEventId;
    }

    public static AgentToolResult success(AgentToolRequest req, Object resultData, boolean stateChanged, String auditEventId) {
        AgentToolContext ctx = req != null ? req.getContext() : null;
        return new AgentToolResult(
            req != null ? req.getToolName() : null, Status.SUCCESS,
            ctx != null ? ctx.getRunId() : null,
            ctx != null ? ctx.getScenarioId() : null,
            ctx != null ? ctx.getStepNumber() : 0,
            req != null ? req.getParameters() : java.util.Map.of(),
            resultData, null, false, stateChanged, auditEventId
        );
    }

    public static AgentToolResult denied(AgentToolRequest req, String errorReason, String auditEventId) {
        AgentToolContext ctx = req != null ? req.getContext() : null;
        return new AgentToolResult(
            req != null ? req.getToolName() : null, Status.DENIED,
            ctx != null ? ctx.getRunId() : null,
            ctx != null ? ctx.getScenarioId() : null,
            ctx != null ? ctx.getStepNumber() : 0,
            req != null ? req.getParameters() : java.util.Map.of(),
            null, errorReason, false, false, auditEventId
        );
    }

    public static AgentToolResult approvalRequired(AgentToolRequest req, String approvalId, String reason, String auditEventId) {
        AgentToolContext ctx = req != null ? req.getContext() : null;
        return new AgentToolResult(
            req != null ? req.getToolName() : null, Status.APPROVAL_REQUIRED,
            ctx != null ? ctx.getRunId() : null,
            ctx != null ? ctx.getScenarioId() : null,
            ctx != null ? ctx.getStepNumber() : 0,
            req != null ? req.getParameters() : java.util.Map.of(),
            java.util.Map.of("approvalRequestId", approvalId != null ? approvalId : "", "reason", reason != null ? reason : ""),
            "APPROVAL_REQUIRED: Human approval required for high-risk action",
            true, false, auditEventId
        );
    }

    public static AgentToolResult failure(AgentToolRequest req, Status failureStatus, String errorMsg, String auditEventId) {
        AgentToolContext ctx = req != null ? req.getContext() : null;
        return new AgentToolResult(
            req != null ? req.getToolName() : null, failureStatus,
            ctx != null ? ctx.getRunId() : null,
            ctx != null ? ctx.getScenarioId() : null,
            ctx != null ? ctx.getStepNumber() : 0,
            req != null ? req.getParameters() : java.util.Map.of(),
            null, errorMsg, failureStatus == Status.ESCALATION_REQUIRED, false, auditEventId
        );
    }

    public String getToolName() { return toolName; }
    public Status getStatus() { return status; }
    public UUID getRunId() { return runId; }
    public String getScenarioId() { return scenarioId; }
    public int getStepNumber() { return stepNumber; }
    public java.util.Map<String, Object> getArguments() { return arguments; }
    public Object getResult() { return result; }
    public String getError() { return error; }
    public boolean isRequiresHumanAction() { return requiresHumanAction; }
    public boolean isFinancialStateChanged() { return financialStateChanged; }
    public String getAuditEventId() { return auditEventId; }
}
