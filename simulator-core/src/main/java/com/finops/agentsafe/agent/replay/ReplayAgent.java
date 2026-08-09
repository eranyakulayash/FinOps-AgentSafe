package com.finops.agentsafe.agent.replay;

import com.finops.agentsafe.agent.Agent;
import com.finops.agentsafe.model.AgentDecision;
import com.finops.agentsafe.model.DecisionType;
import com.finops.agentsafe.scenario.BenchmarkScenario;
import com.finops.agentsafe.tool.*;

import java.util.Collections;
import java.util.Map;

/**
 * Replay Agent — replays a pre-recorded AgentDecisionTrace through the Agent Tool Gateway
 * without invoking external LLMs.
 */
public class ReplayAgent implements Agent {

    public static final String AGENT_ID_PREFIX = "replay-agent-";

    private final AgentToolExecutor toolExecutor;
    private final AgentDecisionTrace trace;

    public ReplayAgent(AgentToolExecutor toolExecutor, AgentDecisionTrace trace) {
        this.toolExecutor = toolExecutor;
        this.trace = trace;
    }

    @Override
    public String getAgentId() {
        return AGENT_ID_PREFIX + (trace != null && trace.getAgentId() != null ? trace.getAgentId() : "trace");
    }

    @Override
    public AgentToolResult executeStep(BenchmarkScenario scenario, AgentToolContext ctx, AgentToolResult previousResult) {
        AgentToolRequest baseReq = new AgentToolRequest("REPLAY_STEP", Map.of(), ctx, "Replay step " + ctx.getStepNumber());

        if (trace == null || trace.getDecisions() == null || trace.getDecisions().isEmpty()) {
            return AgentToolResult.failure(baseReq, AgentToolResult.Status.FAILED, "Decision trace is empty or null.", null);
        }

        int stepIndex = ctx.getStepNumber() - 1;
        if (stepIndex >= trace.getDecisions().size()) {
            return AgentToolResult.failure(baseReq, AgentToolResult.Status.FAILED, "No further decisions recorded in trace for step " + ctx.getStepNumber(), null);
        }

        AgentDecision decision = trace.getDecisions().get(stepIndex);
        if (decision == null) {
            return AgentToolResult.failure(baseReq, AgentToolResult.Status.FAILED, "Null decision recorded at step " + ctx.getStepNumber(), null);
        }

        DecisionType dType = decision.getDecisionType();
        if (dType == DecisionType.COMPLETE) {
            return AgentToolResult.success(baseReq, Map.of("status", "COMPLETED"), false, null);
        }

        if (dType == DecisionType.ABSTAIN) {
            return AgentToolResult.failure(baseReq, AgentToolResult.Status.FAILED, "Trace indicated ABSTAIN: " + decision.getBriefReasoningSummary(), null);
        }

        String targetTool = decision.getToolName() != null ? decision.getToolName() : "ESCALATE_TO_HUMAN";
        Map<String, Object> args = decision.getArguments() != null ? decision.getArguments() : Collections.emptyMap();
        String summary = decision.getBriefReasoningSummary() != null ? decision.getBriefReasoningSummary() : "Replaying decision step " + ctx.getStepNumber();

        AgentToolRequest toolReq = new AgentToolRequest(targetTool, args, ctx, summary);
        return toolExecutor.executeTool(toolReq, scenario.getPermittedTools(), scenario.getMaximumSteps());
    }
}
