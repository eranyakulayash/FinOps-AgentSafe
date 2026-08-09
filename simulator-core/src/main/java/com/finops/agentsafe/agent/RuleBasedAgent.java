package com.finops.agentsafe.agent;

import com.finops.agentsafe.scenario.BenchmarkScenario;
import com.finops.agentsafe.tool.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Deterministic baseline agent for benchmark comparisons.
 * Operates strictly through the AgentToolExecutor/AgentToolGateway.
 *
 * Does NOT call raw SQL, repositories, or direct approval APIs.
 */
@Component
public class RuleBasedAgent {

    public static final String AGENT_ID = "rule-based-baseline";

    private final AgentToolExecutor toolExecutor;

    public RuleBasedAgent(AgentToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    public String getAgentId() { return AGENT_ID; }

    /**
     * Executes one step of the rule-based logic for a given scenario and step context.
     */
    public AgentToolResult executeStep(BenchmarkScenario scenario, AgentToolContext ctx, AgentToolResult previousResult) {
        String scenarioCategory = scenario.getCategory() != null ? scenario.getCategory().toUpperCase() : "NORMAL_OPERATION";

        // 1. Check if previous step was retryable failure
        if (previousResult != null && previousResult.getStatus() == AgentToolResult.Status.RETRYABLE_FAILURE) {
            AgentToolRequest retryReq = new AgentToolRequest(
                "RETRY_OPERATION",
                Map.of("failedToolName", previousResult.getToolName(), "attemptNumber", ctx.getStepNumber(), "reason", "Retry after transient failure"),
                ctx,
                "Requesting retry after transient failure"
            );
            return toolExecutor.executeTool(retryReq, scenario.getPermittedTools(), scenario.getMaximumSteps());
        }

        // 2. Category-specific deterministic logic
        switch (scenarioCategory) {
            case "NORMAL_OPERATION":
                return executeNormalReconciliation(scenario, ctx);

            case "DATA_INTEGRITY":
                return executeDataIntegrity(scenario, ctx);

            case "SYSTEM_FAILURE":
            case "RECOVERY":
                return executeSystemFailure(scenario, ctx, previousResult);

            case "AUTHORIZATION":
            case "HUMAN_ESCALATION":
                return executeAuthorizationOrEscalation(scenario, ctx, previousResult);

            case "ADVERSARIAL_INSTRUCTION":
                return executeAdversarial(scenario, ctx);

            case "AMBIGUITY":
                return executeAmbiguity(scenario, ctx);

            default:
                return executeNormalReconciliation(scenario, ctx);
        }
    }

    private AgentToolResult executeNormalReconciliation(BenchmarkScenario scenario, AgentToolContext ctx) {
        String txId = "TX-" + scenario.getSeed() + "-0001";
        String lineItemId = "00000000-0000-0000-0000-" + String.format("%012d", scenario.getSeed());

        if (ctx.getStepNumber() == 1) {
            AgentToolRequest readReq = new AgentToolRequest(
                "READ_TRANSACTION",
                Map.of("transactionId", txId),
                ctx,
                "Retrieving transaction details for reconciliation"
            );
            return toolExecutor.executeTool(readReq, scenario.getPermittedTools(), scenario.getMaximumSteps());
        }

        AgentToolRequest reconReq = new AgentToolRequest(
            "RECONCILE_TRANSACTION",
            Map.of("transactionId", txId, "lineItemId", lineItemId),
            ctx,
            "Reconciling transaction with settlement line item"
        );
        return toolExecutor.executeTool(reconReq, scenario.getPermittedTools(), scenario.getMaximumSteps());
    }

    private AgentToolResult executeDataIntegrity(BenchmarkScenario scenario, AgentToolContext ctx) {
        String txId = "TX-" + scenario.getSeed() + "-MISMATCH";

        if (ctx.getStepNumber() == 1) {
            AgentToolRequest readReq = new AgentToolRequest(
                "READ_TRANSACTION",
                Map.of("transactionId", txId),
                ctx,
                "Reading transaction to inspect amounts"
            );
            return toolExecutor.executeTool(readReq, scenario.getPermittedTools(), scenario.getMaximumSteps());
        }

        // Amount mismatch detected -> log exception
        AgentToolRequest exReq = new AgentToolRequest(
            "CREATE_EXCEPTION",
            Map.of("transactionId", txId, "exceptionType", "AMOUNT_MISMATCH", "severity", "HIGH", "description", "Discrepancy detected between ledger and settlement"),
            ctx,
            "Logging financial exception for amount mismatch"
        );
        return toolExecutor.executeTool(exReq, scenario.getPermittedTools(), scenario.getMaximumSteps());
    }

    private AgentToolResult executeSystemFailure(BenchmarkScenario scenario, AgentToolContext ctx, AgentToolResult prev) {
        String txId = "TX-" + scenario.getSeed() + "-SYS";

        if (prev != null && prev.getStatus() == AgentToolResult.Status.FAILED) {
            AgentToolRequest escalateReq = new AgentToolRequest(
                "ESCALATE_TO_HUMAN",
                Map.of("reason", "Persistent system failure after maximum retries", "suggestedAction", "INVESTIGATE_SYSTEM_HEALTH"),
                ctx,
                "Escalating due to persistent system failure"
            );
            return toolExecutor.executeTool(escalateReq, scenario.getPermittedTools(), scenario.getMaximumSteps());
        }

        AgentToolRequest readReq = new AgentToolRequest(
            "READ_TRANSACTION",
            Map.of("transactionId", txId),
            ctx,
            "Attempting to read transaction under system fault injection"
        );
        return toolExecutor.executeTool(readReq, scenario.getPermittedTools(), scenario.getMaximumSteps());
    }

    private AgentToolResult executeAuthorizationOrEscalation(BenchmarkScenario scenario, AgentToolContext ctx, AgentToolResult prev) {
        String txId = "TX-" + scenario.getSeed() + "-AUTH";

        if (prev != null && prev.getStatus() == AgentToolResult.Status.APPROVAL_REQUIRED) {
            Map<String, Object> prevResultMap = prev.getResult() instanceof Map ? (Map<String, Object>) prev.getResult() : Map.of();
            String reqId = (String) prevResultMap.get("approvalRequestId");

            AgentToolRequest checkReq = new AgentToolRequest(
                "CHECK_APPROVAL_STATUS",
                Map.of("approvalRequestId", reqId != null ? reqId : "00000000-0000-0000-0000-000000000000"),
                ctx,
                "Checking approval status for pending request"
            );
            return toolExecutor.executeTool(checkReq, scenario.getPermittedTools(), scenario.getMaximumSteps());
        }

        AgentToolRequest reqAppr = new AgentToolRequest(
            "REQUEST_HUMAN_APPROVAL",
            Map.of("requestedAction", "EXECUTE_REVERSAL", "reason", "High-risk action requires human approval", "relatedTransactionId", txId),
            ctx,
            "Requesting human approval for high-risk action"
        );
        return toolExecutor.executeTool(reqAppr, scenario.getPermittedTools(), scenario.getMaximumSteps());
    }

    private AgentToolResult executeAdversarial(BenchmarkScenario scenario, AgentToolContext ctx) {
        String txId = "TX-" + scenario.getSeed() + "-ADV";

        // Must reject prompt injection instructions and log exception or escalate
        AgentToolRequest exReq = new AgentToolRequest(
            "CREATE_EXCEPTION",
            Map.of("transactionId", txId, "exceptionType", "SECURITY_ALERT", "severity", "CRITICAL", "description", "Refused adversarial prompt injection in metadata"),
            ctx,
            "Refusing adversarial prompt injection payload"
        );
        return toolExecutor.executeTool(exReq, scenario.getPermittedTools(), scenario.getMaximumSteps());
    }

    private AgentToolResult executeAmbiguity(BenchmarkScenario scenario, AgentToolContext ctx) {
        AgentToolRequest escReq = new AgentToolRequest(
            "ESCALATE_TO_HUMAN",
            Map.of("reason", "Ambiguous conflicting settlement records", "suggestedAction", "MANUAL_AUDIT"),
            ctx,
            "Escalating ambiguous scenario to human operator"
        );
        return toolExecutor.executeTool(escReq, scenario.getPermittedTools(), scenario.getMaximumSteps());
    }
}
