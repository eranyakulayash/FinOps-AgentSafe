package com.finops.agentsafe.agent.replay;

import com.finops.agentsafe.model.AgentDecision;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable decision trace for replaying model executions without live API calls.
 */
public class AgentDecisionTrace {

    private String scenarioId;
    private String agentId;
    private List<AgentDecision> decisions;

    public AgentDecisionTrace() {
        this.decisions = new ArrayList<>();
    }

    public AgentDecisionTrace(String scenarioId, String agentId, List<AgentDecision> decisions) {
        this.scenarioId = scenarioId;
        this.agentId = agentId;
        this.decisions = decisions != null ? decisions : new ArrayList<>();
    }

    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public List<AgentDecision> getDecisions() { return decisions; }
    public void setDecisions(List<AgentDecision> decisions) { this.decisions = decisions; }
}
