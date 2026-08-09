package com.finops.agentsafe.runner;

import com.finops.agentsafe.metrics.BenchmarkMetricResult;

import java.util.List;
import java.util.UUID;

/**
 * Standard reproducible benchmark result schema matching Phase 3 requirements.
 */
public class BenchmarkRunResult {

    private String benchmarkVersion = "0.1.0";
    private String scenarioId;
    private String scenarioVersion = "1.0.0";
    private UUID runId;
    private String agent;
    private long seed;

    private boolean taskCompleted;
    private boolean financialIntegrityPreserved;
    private int unsafeActions;
    private int authorizationViolations;
    private boolean humanEscalationExpected;
    private boolean humanEscalationOccurred;
    private int toolCalls;
    private int retries;
    private boolean stepLimitExceeded;
    private boolean auditChainValid;

    private BenchmarkMetricResult metrics;
    private List<ExecutionTraceStep> trace;

    public BenchmarkRunResult() {}

    public String getBenchmarkVersion() { return benchmarkVersion; }
    public void setBenchmarkVersion(String val) { this.benchmarkVersion = val; }

    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String val) { this.scenarioId = val; }

    public String getScenarioVersion() { return scenarioVersion; }
    public void setScenarioVersion(String val) { this.scenarioVersion = val; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID val) { this.runId = val; }

    public String getAgent() { return agent; }
    public void setAgent(String val) { this.agent = val; }

    public long getSeed() { return seed; }
    public void setSeed(long val) { this.seed = val; }

    public boolean isTaskCompleted() { return taskCompleted; }
    public void setTaskCompleted(boolean val) { this.taskCompleted = val; }

    public boolean isFinancialIntegrityPreserved() { return financialIntegrityPreserved; }
    public void setFinancialIntegrityPreserved(boolean val) { this.financialIntegrityPreserved = val; }

    public int getUnsafeActions() { return unsafeActions; }
    public void setUnsafeActions(int val) { this.unsafeActions = val; }

    public int getAuthorizationViolations() { return authorizationViolations; }
    public void setAuthorizationViolations(int val) { this.authorizationViolations = val; }

    public boolean isHumanEscalationExpected() { return humanEscalationExpected; }
    public void setHumanEscalationExpected(boolean val) { this.humanEscalationExpected = val; }

    public boolean isHumanEscalationOccurred() { return humanEscalationOccurred; }
    public void setHumanEscalationOccurred(boolean val) { this.humanEscalationOccurred = val; }

    public int getToolCalls() { return toolCalls; }
    public void setToolCalls(int val) { this.toolCalls = val; }

    public int getRetries() { return retries; }
    public void setRetries(int val) { this.retries = val; }

    public boolean isStepLimitExceeded() { return stepLimitExceeded; }
    public void setStepLimitExceeded(boolean val) { this.stepLimitExceeded = val; }

    public boolean isAuditChainValid() { return auditChainValid; }
    public void setAuditChainValid(boolean val) { this.auditChainValid = val; }

    public BenchmarkMetricResult getMetrics() { return metrics; }
    public void setMetrics(BenchmarkMetricResult val) { this.metrics = val; }

    public List<ExecutionTraceStep> getTrace() { return trace; }
    public void setTrace(List<ExecutionTraceStep> val) { this.trace = val; }
}
