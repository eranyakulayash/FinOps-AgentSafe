package com.finops.agentsafe.tool;

import com.finops.agentsafe.domain.HumanApprovalRequest;
import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.service.HumanApprovalService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class RequestHumanApprovalTool implements AgentTool {

    private final HumanApprovalService approvalService;

    public RequestHumanApprovalTool(HumanApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Override
    public String getToolName() { return "REQUEST_HUMAN_APPROVAL"; }

    @Override
    public String getDescription() { return "Request human supervisor approval for a high-risk financial action"; }

    @Override
    public ActionRiskLevel getRiskLevel() { return ActionRiskLevel.HIGH_RISK_WRITE; }

    @Override
    public Map<String, String> getInputSchema() {
        return Map.of(
            "requestedAction", "String",
            "reason", "String",
            "relatedTransactionId", "String",
            "relatedSettlementId", "UUID/String"
        );
    }

    @Override
    public Map<String, String> getOutputSchema() { return Map.of("approvalRequest", "HumanApprovalRequestObject"); }

    @Override
    public Set<String> getRequiredPermissions() { return Set.of("REQUEST_HUMAN_APPROVAL"); }

    @Override
    public boolean isRequiresApproval() { return false; }

    @Override
    public boolean isIdempotent() { return true; }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        AgentToolContext ctx = request.getContext();
        String action = request.getParameter("requestedAction", String.class);
        String reason = request.getParameter("reason", String.class);
        String txId = request.getParameter("relatedTransactionId", String.class);
        String stlStr = request.getParameter("relatedSettlementId", String.class);

        if (action == null || reason == null) {
            return AgentToolResult.failure(request, AgentToolResult.Status.FAILED, "Missing parameters 'requestedAction' or 'reason'", null);
        }

        UUID stlId = stlStr != null && !stlStr.isBlank() ? UUID.fromString(stlStr) : null;
        String requestedBy = ctx != null && ctx.getActorId() != null ? ctx.getActorId() : "AGENT_UNDER_TEST";

        HumanApprovalRequest req = approvalService.createApprovalRequest(
            requestedBy,
            action,
            reason,
            txId,
            stlId,
            ctx != null ? ctx.getScenarioId() : null,
            ctx != null ? ctx.getRunId() : null
        );

        return AgentToolResult.success(request, req, true, req.getId().toString());
    }
}
