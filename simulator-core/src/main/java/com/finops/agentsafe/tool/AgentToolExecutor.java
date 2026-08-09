package com.finops.agentsafe.tool;

import com.finops.agentsafe.policy.AgentToolPolicyEngine;
import com.finops.agentsafe.policy.PolicyDecision;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Entry point for executing agent tool requests.
 * Connects AgentToolRegistry, AgentToolPolicyEngine, and domain services.
 */
@Component
public class AgentToolExecutor {

    private final AgentToolRegistry registry;
    private final AgentToolPolicyEngine policyEngine;

    public AgentToolExecutor(AgentToolRegistry registry, AgentToolPolicyEngine policyEngine) {
        this.registry = registry;
        this.policyEngine = policyEngine;
    }

    public AgentToolResult executeTool(AgentToolRequest request, Set<String> permittedTools, int maxSteps) {
        Optional<AgentTool> toolOpt = registry.getTool(request.getToolName());

        if (toolOpt.isEmpty()) {
            return AgentToolResult.failure(request, AgentToolResult.Status.NON_RETRYABLE_FAILURE,
                "UNREGISTERED_TOOL: Tool [" + request.getToolName() + "] is not registered in AgentToolRegistry.", null);
        }

        AgentTool tool = toolOpt.get();

        // Evaluate Policy
        PolicyDecision decision = policyEngine.evaluate(tool, request, permittedTools, maxSteps);

        if (decision == PolicyDecision.STEP_LIMIT_EXCEEDED) {
            return AgentToolResult.failure(request, AgentToolResult.Status.STEP_LIMIT_EXCEEDED,
                "STEP_LIMIT_EXCEEDED: Maximum scenario steps exceeded.", null);
        }

        if (decision == PolicyDecision.DENY) {
            return AgentToolResult.denied(request,
                "POLICY_DENIAL: Execution of tool [" + tool.getToolName() + "] is prohibited by policy.", null);
        }

        if (decision == PolicyDecision.APPROVAL_REQUIRED) {
            String txId = request.getParameter("relatedTransactionId", String.class);
            if (txId == null) txId = request.getParameter("originalTransactionId", String.class);
            if (txId == null) txId = request.getParameter("transactionId", String.class);
            return AgentToolResult.approvalRequired(request, null,
                "Action [" + tool.getToolName() + "] on transaction [" + txId + "] requires human approval.", null);
        }

        if (decision == PolicyDecision.ESCALATION_REQUIRED) {
            return AgentToolResult.failure(request, AgentToolResult.Status.ESCALATION_REQUIRED,
                "ESCALATION_REQUIRED: Agent must escalate to human operator.", null);
        }

        // Execute Tool
        try {
            return tool.execute(request);
        } catch (Exception e) {
            return AgentToolResult.failure(request, AgentToolResult.Status.FAILED,
                "TOOL_EXECUTION_ERROR: " + e.getMessage(), null);
        }
    }
}
