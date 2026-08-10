package com.finops.agentsafe.agent;

import com.finops.agentsafe.model.*;
import com.finops.agentsafe.model.prompt.PromptSecurityManager;
import com.finops.agentsafe.model.validation.AgentDecisionValidator;
import com.finops.agentsafe.scenario.BenchmarkScenario;
import com.finops.agentsafe.tool.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Provider-neutral LLM Benchmark Agent.
 * Interacts with financial systems ONLY through the Agent Tool Gateway.
 * Does NOT call repositories, SQL, database, or direct approval APIs.
 */
@Component
public class LLMBenchmarkAgent implements Agent {

    private final ModelAdapterRegistry adapterRegistry;
    private final AgentToolRegistry toolRegistry;
    private final AgentToolExecutor toolExecutor;
    private final PromptSecurityManager promptSecurityManager;
    private final AgentDecisionValidator decisionValidator;

    private ModelConfiguration modelConfiguration;
    private int modelCalls = 0;
    private int modelRetries = 0;
    private int modelFailures = 0;
    private long modelLatencyMs = 0L;
    private long inputTokens = 0L;
    private long outputTokens = 0L;
    private long totalTokens = 0L;

    public LLMBenchmarkAgent(ModelAdapterRegistry adapterRegistry,
                             AgentToolRegistry toolRegistry,
                             AgentToolExecutor toolExecutor,
                             PromptSecurityManager promptSecurityManager,
                             AgentDecisionValidator decisionValidator) {
        this.adapterRegistry = adapterRegistry;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.promptSecurityManager = promptSecurityManager;
        this.decisionValidator = decisionValidator;
        this.modelConfiguration = ModelConfiguration.defaultMock();
    }

    public void resetMetrics() {
        this.modelCalls = 0;
        this.modelRetries = 0;
        this.modelFailures = 0;
        this.modelLatencyMs = 0L;
        this.inputTokens = 0L;
        this.outputTokens = 0L;
        this.totalTokens = 0L;
    }

    public int getModelCalls() { return modelCalls; }
    public int getModelRetries() { return modelRetries; }
    public int getModelFailures() { return modelFailures; }
    public long getModelLatencyMs() { return modelLatencyMs; }
    public ModelUsage getCumulativeUsage() {
        return new ModelUsage(inputTokens, outputTokens, totalTokens, 0L, modelCalls, modelRetries, modelLatencyMs, null);
    }

    @Override
    public String getAgentId() {
        return "llm-agent-" + (modelConfiguration != null ? modelConfiguration.getProvider() : "mock");
    }

    public void setModelConfiguration(ModelConfiguration config) {
        this.modelConfiguration = config;
    }

    public ModelConfiguration getModelConfiguration() {
        return modelConfiguration;
    }

    @Override
    public AgentToolResult executeStep(BenchmarkScenario scenario, AgentToolContext ctx, AgentToolResult previousResult) {
        String providerName = modelConfiguration != null ? modelConfiguration.getProvider() : "mock";
        Optional<ModelAdapter> adapterOpt = adapterRegistry.getAdapter(providerName);

        AgentToolRequest baseContextReq = new AgentToolRequest(
            "MODEL_PREDICTION",
            Map.of(),
            ctx,
            "LLM Agent prediction step"
        );

        if (adapterOpt.isEmpty()) {
            return AgentToolResult.denied(
                baseContextReq,
                "PROVIDER_NOT_CONFIGURED: Model provider '" + providerName + "' is not registered in ModelAdapterRegistry.",
                null
            );
        }

        ModelAdapter adapter = adapterOpt.get();
        if (!adapter.isConfigured()) {
            return AgentToolResult.denied(
                baseContextReq,
                "PROVIDER_NOT_CONFIGURED: Model provider '" + providerName + "' is not configured. Missing required API credentials in environment.",
                null
            );
        }

        // 1. Gather ONLY scenario-permitted tools
        Set<String> permittedNames = scenario.getPermittedTools();
        List<AgentTool> permittedTools = new ArrayList<>();
        if (permittedNames != null) {
            for (String tName : permittedNames) {
                toolRegistry.getTool(tName).ifPresent(permittedTools::add);
            }
        }

        // 2. Construct ModelRequest with strict prompt-injection boundary
        String untrustedMetadata = scenario.getDescription() != null ? scenario.getDescription() : "Scenario category: " + scenario.getCategory();
        String systemInstruction = promptSecurityManager.getSystemPrompt();

        List<AgentToolResult> history = previousResult != null ? List.of(previousResult) : Collections.emptyList();

        ModelRequest request = new ModelRequest(
            systemInstruction,
            untrustedMetadata,
            permittedTools,
            history,
            modelConfiguration,
            ctx.getStepNumber()
        );

        // 3. Call ModelAdapter with retry logic for retryable failures
        int maxRetries = modelConfiguration.getMaximumModelRetries() != null ? modelConfiguration.getMaximumModelRetries() : 3;
        ModelResponse response = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            modelCalls++;
            if (attempt > 0) modelRetries++;
            response = adapter.predict(request);
            if (response != null && response.getUsage() != null) {
                ModelUsage u = response.getUsage();
                if (u.getInputTokens() != null) inputTokens += u.getInputTokens();
                if (u.getOutputTokens() != null) outputTokens += u.getOutputTokens();
                if (u.getTotalTokens() != null) totalTokens += u.getTotalTokens();
                modelLatencyMs += u.getLatencyMs();
            }
            if (response != null && response.isSuccess()) break;
            if (response != null && response.getError() != null && !response.getError().isRetryable()) {
                modelFailures++;
                break;
            }
            if (attempt == maxRetries) modelFailures++;
        }

        if (response == null || !response.isSuccess()) {
            ModelError err = response != null ? response.getError() : null;
            String errDetail = err != null ? err.getMessage() : "Unknown model prediction error";
            return AgentToolResult.failure(
                baseContextReq,
                AgentToolResult.Status.FAILED,
                "MODEL_ERROR: " + errDetail,
                null
            );
        }

        AgentDecision decision = response.getDecision();

        // 4. Validate AgentDecision
        AgentDecisionValidator.ValidationResult valRes = decisionValidator.validate(decision, permittedTools);
        if (!valRes.isValid()) {
            AgentToolRequest invalidDecReq = new AgentToolRequest(
                decision.getToolName() != null ? decision.getToolName() : "INVALID_MODEL_DECISION",
                decision.getArguments() != null ? decision.getArguments() : Map.of(),
                ctx,
                "Invalid decision validation"
            );
            return AgentToolResult.denied(
                invalidDecReq,
                "Model decision validation failed: " + valRes.getErrorMessage(),
                null
            );
        }

        // 5. Handle Non-Tool Actions (COMPLETE, ABSTAIN, ESCALATE)
        DecisionType dType = decision.getDecisionType();
        if (dType == DecisionType.COMPLETE) {
            AgentToolRequest completeReq = new AgentToolRequest("COMPLETE", Map.of(), ctx, decision.getBriefReasoningSummary());
            return AgentToolResult.success(
                completeReq,
                Map.of("status", "COMPLETED", "summary", decision.getBriefReasoningSummary() != null ? decision.getBriefReasoningSummary() : "Task completed"),
                false,
                null
            );
        }

        if (dType == DecisionType.ABSTAIN) {
            AgentToolRequest abstainReq = new AgentToolRequest("ABSTAIN", Map.of(), ctx, decision.getBriefReasoningSummary());
            return AgentToolResult.failure(
                abstainReq,
                AgentToolResult.Status.FAILED,
                "Agent abstained from taking action: " + decision.getBriefReasoningSummary(),
                null
            );
        }

        if (dType == DecisionType.ESCALATE) {
            Map<String, Object> escArgs = decision.getArguments() != null ? decision.getArguments() : Map.of();
            AgentToolRequest escReq = new AgentToolRequest(
                "ESCALATE_TO_HUMAN",
                escArgs,
                ctx,
                decision.getBriefReasoningSummary()
            );
            return toolExecutor.executeTool(escReq, scenario.getPermittedTools(), scenario.getMaximumSteps());
        }

        // 6. Handle TOOL_CALL / REQUEST_HUMAN_APPROVAL / RETRY
        String targetTool = decision.getToolName();
        Map<String, Object> args = decision.getArguments() != null ? decision.getArguments() : Collections.emptyMap();
        String justification = decision.getBriefReasoningSummary() != null ? decision.getBriefReasoningSummary() : "Executing " + targetTool;

        AgentToolRequest toolReq = new AgentToolRequest(targetTool, args, ctx, justification);
        return toolExecutor.executeTool(toolReq, scenario.getPermittedTools(), scenario.getMaximumSteps());
    }
}
