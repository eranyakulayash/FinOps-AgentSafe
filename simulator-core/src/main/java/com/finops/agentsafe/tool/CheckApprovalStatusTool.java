package com.finops.agentsafe.tool;

import com.finops.agentsafe.domain.HumanApprovalRequest;
import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.service.HumanApprovalService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class CheckApprovalStatusTool implements AgentTool {

    private final HumanApprovalService approvalService;

    public CheckApprovalStatusTool(HumanApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Override
    public String getToolName() { return "CHECK_APPROVAL_STATUS"; }

    @Override
    public String getDescription() { return "Check the status of a pending HumanApprovalRequest by ID"; }

    @Override
    public ActionRiskLevel getRiskLevel() { return ActionRiskLevel.READ_ONLY; }

    @Override
    public Map<String, String> getInputSchema() { return Map.of("approvalRequestId", "UUID/String"); }

    @Override
    public Map<String, String> getOutputSchema() { return Map.of("approvalRequest", "HumanApprovalRequestObject"); }

    @Override
    public Set<String> getRequiredPermissions() { return Set.of("CHECK_APPROVAL_STATUS"); }

    @Override
    public boolean isRequiresApproval() { return false; }

    @Override
    public boolean isIdempotent() { return true; }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        String reqIdStr = request.getParameter("approvalRequestId", String.class);
        if (reqIdStr == null || reqIdStr.isBlank()) {
            return AgentToolResult.failure(request, AgentToolResult.Status.FAILED, "Missing parameter 'approvalRequestId'", null);
        }

        UUID approvalId = UUID.fromString(reqIdStr);
        Optional<HumanApprovalRequest> reqOpt = approvalService.getApprovalRequest(approvalId);

        if (reqOpt.isEmpty()) {
            return AgentToolResult.failure(request, AgentToolResult.Status.NON_RETRYABLE_FAILURE, "Approval request not found: " + approvalId, null);
        }

        return AgentToolResult.success(request, reqOpt.get(), false, null);
    }
}
