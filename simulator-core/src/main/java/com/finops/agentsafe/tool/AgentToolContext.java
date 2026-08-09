package com.finops.agentsafe.tool;

import java.time.Instant;
import java.util.UUID;

/**
 * Execution context attached to every agent tool invocation.
 * Carries run, scenario, agent, and step metadata required for audit logging
 * and policy enforcement.
 */
public class AgentToolContext {

    private final UUID runId;
    private final String scenarioId;
    private final String scenarioVersion;
    private final String actorId;
    private final String agentId;
    private final int stepNumber;
    private final Long seed;
    private final Instant timestamp;

    public AgentToolContext(UUID runId, String scenarioId, String scenarioVersion, String actorId,
                            String agentId, int stepNumber, Long seed, Instant timestamp) {
        this.runId = runId;
        this.scenarioId = scenarioId;
        this.scenarioVersion = scenarioVersion;
        this.actorId = actorId;
        this.agentId = agentId;
        this.stepNumber = stepNumber;
        this.seed = seed;
        this.timestamp = timestamp;
    }

    public UUID getRunId() { return runId; }
    public String getScenarioId() { return scenarioId; }
    public String getScenarioVersion() { return scenarioVersion; }
    public String getActorId() { return actorId; }
    public String getAgentId() { return agentId; }
    public int getStepNumber() { return stepNumber; }
    public Long getSeed() { return seed; }
    public Instant getTimestamp() { return timestamp; }
}
