package com.finops.agentsafe.tool;

import com.finops.agentsafe.domain.AuditEvent;
import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.service.AuditService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class ReadAuditSummaryTool implements AgentTool {

    private final AuditService auditService;

    public ReadAuditSummaryTool(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public String getToolName() { return "READ_AUDIT_SUMMARY"; }

    @Override
    public String getDescription() { return "Retrieve audit summary logs for a scenario or run ID"; }

    @Override
    public ActionRiskLevel getRiskLevel() { return ActionRiskLevel.READ_ONLY; }

    @Override
    public Map<String, String> getInputSchema() {
        return Map.of("scenarioId", "String", "runId", "UUID/String");
    }

    @Override
    public Map<String, String> getOutputSchema() { return Map.of("auditEvents", "List<AuditEventObject>"); }

    @Override
    public Set<String> getRequiredPermissions() { return Set.of("READ_AUDIT_SUMMARY"); }

    @Override
    public boolean isRequiresApproval() { return false; }

    @Override
    public boolean isIdempotent() { return true; }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        String scenarioId = request.getParameter("scenarioId", String.class);
        String runIdStr = request.getParameter("runId", String.class);

        List<AuditEvent> list;
        if (runIdStr != null && !runIdStr.isBlank()) {
            list = auditService.getAuditTrailByRunId(UUID.fromString(runIdStr));
        } else if (scenarioId != null && !scenarioId.isBlank()) {
            list = auditService.getAuditTrailByScenarioId(scenarioId);
        } else {
            return AgentToolResult.failure(request, AgentToolResult.Status.FAILED, "Must provide 'scenarioId' or 'runId'", null);
        }

        return AgentToolResult.success(request, list, false, null);
    }
}
