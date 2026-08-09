package com.finops.agentsafe.tool;

import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.service.AuditService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class EscalateToHumanTool implements AgentTool {

    private final AuditService auditService;

    public EscalateToHumanTool(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public String getToolName() { return "ESCALATE_TO_HUMAN"; }

    @Override
    public String getDescription() { return "Escalate an ambiguous or unresolvable scenario directly to a human operator"; }

    @Override
    public ActionRiskLevel getRiskLevel() { return ActionRiskLevel.HIGH_RISK_WRITE; }

    @Override
    public Map<String, String> getInputSchema() {
        return Map.of("reason", "String", "suggestedAction", "String", "contextData", "String");
    }

    @Override
    public Map<String, String> getOutputSchema() { return Map.of("escalationId", "String", "status", "String"); }

    @Override
    public Set<String> getRequiredPermissions() { return Set.of("ESCALATE_TO_HUMAN"); }

    @Override
    public boolean isRequiresApproval() { return false; }

    @Override
    public boolean isIdempotent() { return true; }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        String reason = request.getParameter("reason", String.class);
        String suggestedAction = request.getParameter("suggestedAction", String.class);

        if (reason == null || reason.isBlank()) {
            return AgentToolResult.failure(request, AgentToolResult.Status.FAILED, "Missing parameter 'reason'", null);
        }

        auditService.recordAuditEvent(
            request.getContext() != null ? request.getContext().getRunId() : null,
            request.getContext() != null ? request.getContext().getScenarioId() : null,
            request.getContext() != null ? request.getContext().getActorId() : "AGENT",
            "ESCALATE_TO_HUMAN",
            "ESCALATE",
            ActionRiskLevel.HIGH_RISK_WRITE,
            reason,
            "ALLOWED",
            "ESCALATED",
            null,
            null,
            null,
            null,
            "Escalated to human operator: " + reason + " (Suggested: " + suggestedAction + ")"
        );

        Map<String, Object> result = Map.of(
            "escalationStatus", "ESCALATED_TO_HUMAN",
            "reason", reason,
            "suggestedAction", suggestedAction != null ? suggestedAction : ""
        );

        return AgentToolResult.failure(request, AgentToolResult.Status.ESCALATION_REQUIRED, "Human escalation requested: " + reason, null);
    }
}
