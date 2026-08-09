package com.finops.agentsafe.tool;

import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.service.AuditService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class ProposeSettlementActionTool implements AgentTool {

    private final AuditService auditService;

    public ProposeSettlementActionTool(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public String getToolName() { return "PROPOSE_SETTLEMENT_ACTION"; }

    @Override
    public String getDescription() { return "Propose a settlement approval or rejection action for human review"; }

    @Override
    public ActionRiskLevel getRiskLevel() { return ActionRiskLevel.LOW_RISK_WRITE; }

    @Override
    public Map<String, String> getInputSchema() {
        return Map.of("batchId", "UUID/String", "proposedAction", "String", "justification", "String");
    }

    @Override
    public Map<String, String> getOutputSchema() { return Map.of("proposalId", "String", "status", "String"); }

    @Override
    public Set<String> getRequiredPermissions() { return Set.of("PROPOSE_SETTLEMENT_ACTION"); }

    @Override
    public boolean isRequiresApproval() { return false; }

    @Override
    public boolean isIdempotent() { return true; }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        String batchId = request.getParameter("batchId", String.class);
        String action = request.getParameter("proposedAction", String.class);
        String justification = request.getParameter("justification", String.class);

        if (batchId == null || action == null) {
            return AgentToolResult.failure(request, AgentToolResult.Status.FAILED, "Missing parameters 'batchId' or 'proposedAction'", null);
        }

        auditService.recordAuditEvent(
            request.getContext() != null ? request.getContext().getRunId() : null,
            request.getContext() != null ? request.getContext().getScenarioId() : null,
            request.getContext() != null ? request.getContext().getActorId() : "AGENT",
            "PROPOSE_SETTLEMENT_ACTION",
            "PROPOSE_SETTLEMENT",
            ActionRiskLevel.LOW_RISK_WRITE,
            batchId + "|" + action,
            "ALLOWED",
            "PROPOSED",
            null,
            null,
            null,
            null,
            "Proposed settlement action: " + action + " - " + justification
        );

        Map<String, Object> result = Map.of(
            "batchId", batchId,
            "proposedAction", action,
            "justification", justification != null ? justification : "",
            "proposalStatus", "SUBMITTED_FOR_REVIEW"
        );

        return AgentToolResult.success(request, result, true, null);
    }
}
