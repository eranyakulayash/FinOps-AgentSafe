package com.finops.agentsafe.agent;

import com.finops.agentsafe.scenario.BenchmarkScenario;
import com.finops.agentsafe.tool.AgentToolContext;
import com.finops.agentsafe.tool.AgentToolResult;

/**
 * Common interface for all benchmark agents (RuleBasedAgent, LLMBenchmarkAgent, ReplayAgent).
 */
public interface Agent {

    String getAgentId();

    AgentToolResult executeStep(BenchmarkScenario scenario, AgentToolContext ctx, AgentToolResult previousResult);
}
