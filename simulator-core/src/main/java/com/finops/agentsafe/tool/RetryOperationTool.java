package com.finops.agentsafe.tool;

import com.finops.agentsafe.enums.ActionRiskLevel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class RetryOperationTool implements AgentTool {

    @Override
    public String getToolName() { return "RETRY_OPERATION"; }

    @Override
    public String getDescription() { return "Request retry of a failed or timed-out tool operation within threshold limits"; }

    @Override
    public ActionRiskLevel getRiskLevel() { return ActionRiskLevel.LOW_RISK_WRITE; }

    @Override
    public Map<String, String> getInputSchema() {
        return Map.of("failedToolName", "String", "attemptNumber", "Integer", "reason", "String");
    }

    @Override
    public Map<String, String> getOutputSchema() { return Map.of("retryPermitted", "Boolean", "attemptNumber", "Integer"); }

    @Override
    public Set<String> getRequiredPermissions() { return Set.of("RETRY_OPERATION"); }

    @Override
    public boolean isRequiresApproval() { return false; }

    @Override
    public boolean isIdempotent() { return true; }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        String failedTool = request.getParameter("failedToolName", String.class);
        Object attemptObj = request.getParameters().get("attemptNumber");
        int attempt = attemptObj instanceof Number ? ((Number) attemptObj).intValue() : 1;

        if (attempt > getMaximumRetries()) {
            return AgentToolResult.failure(request, AgentToolResult.Status.NON_RETRYABLE_FAILURE,
                "RETRY_LIMIT_EXCEEDED: Retry threshold (" + getMaximumRetries() + ") exceeded for tool [" + failedTool + "]", null);
        }

        Map<String, Object> res = Map.of(
            "retryPermitted", true,
            "failedToolName", failedTool != null ? failedTool : "UNKNOWN",
            "attemptNumber", attempt,
            "message", "Retry attempt " + attempt + " permitted"
        );

        return AgentToolResult.success(request, res, false, null);
    }
}
