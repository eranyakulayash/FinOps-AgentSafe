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
    private int unsafeActions; // realized unsafe actions
    private int authorizationViolations; // realized auth violations

    // Distinct Agent Safety & Safety Control Metrics
    private int attemptedUnsafeActions;
    private int blockedUnsafeActions;
    private int authorizationViolationAttempts;
    private int realizedUnsafeActions;
    private boolean safetyControlEffective;
    private double safetyControlEffectivenessScore;

    private boolean humanEscalationExpected;
    private boolean humanEscalationOccurred;
    private int toolCalls;
    private int retries;
    private boolean stepLimitExceeded;
    private boolean auditChainValid;

    // Phase 4 Model Run Metadata & Usage
    private String provider = "rule-based";
    private String modelName = "rule-based-baseline";
    private String modelVersion = "1.0.0";
    private String modelAdapterVersion = "1.0.0";
    private String promptVersion = "financial-agent-system-v1";
    private String toolContractVersion = "1.0.0";

    private int modelCalls = 0;
    private int modelRetries = 0;
    private int modelFailures = 0;
    private int invalidModelDecisions = 0;
    private int toolHallucinationCount = 0;
    private int schemaValidationFailures = 0;
    private long modelLatencyMs = 0L;

    private com.finops.agentsafe.model.ModelUsage usage;

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

    public int getAttemptedUnsafeActions() { return attemptedUnsafeActions; }
    public void setAttemptedUnsafeActions(int val) { this.attemptedUnsafeActions = val; }

    public int getBlockedUnsafeActions() { return blockedUnsafeActions; }
    public void setBlockedUnsafeActions(int val) { this.blockedUnsafeActions = val; }

    public int getAuthorizationViolationAttempts() { return authorizationViolationAttempts; }
    public void setAuthorizationViolationAttempts(int val) { this.authorizationViolationAttempts = val; }

    public int getRealizedUnsafeActions() { return realizedUnsafeActions; }
    public void setRealizedUnsafeActions(int val) { this.realizedUnsafeActions = val; }

    public boolean isSafetyControlEffective() { return safetyControlEffective; }
    public void setSafetyControlEffective(boolean val) { this.safetyControlEffective = val; }

    public double getSafetyControlEffectivenessScore() { return safetyControlEffectivenessScore; }
    public void setSafetyControlEffectivenessScore(double val) { this.safetyControlEffectivenessScore = val; }

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

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public String getModelAdapterVersion() { return modelAdapterVersion; }
    public void setModelAdapterVersion(String modelAdapterVersion) { this.modelAdapterVersion = modelAdapterVersion; }

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }

    public String getToolContractVersion() { return toolContractVersion; }
    public void setToolContractVersion(String toolContractVersion) { this.toolContractVersion = toolContractVersion; }

    public int getModelCalls() { return modelCalls; }
    public void setModelCalls(int modelCalls) { this.modelCalls = modelCalls; }

    public int getModelRetries() { return modelRetries; }
    public void setModelRetries(int modelRetries) { this.modelRetries = modelRetries; }

    public int getModelFailures() { return modelFailures; }
    public void setModelFailures(int modelFailures) { this.modelFailures = modelFailures; }

    public int getInvalidModelDecisions() { return invalidModelDecisions; }
    public void setInvalidModelDecisions(int invalidModelDecisions) { this.invalidModelDecisions = invalidModelDecisions; }

    public int getToolHallucinationCount() { return toolHallucinationCount; }
    public void setToolHallucinationCount(int toolHallucinationCount) { this.toolHallucinationCount = toolHallucinationCount; }

    public int getSchemaValidationFailures() { return schemaValidationFailures; }
    public void setSchemaValidationFailures(int schemaValidationFailures) { this.schemaValidationFailures = schemaValidationFailures; }

    public long getModelLatencyMs() { return modelLatencyMs; }
    public void setModelLatencyMs(long modelLatencyMs) { this.modelLatencyMs = modelLatencyMs; }

    public com.finops.agentsafe.model.ModelUsage getUsage() { return usage; }
    public void setUsage(com.finops.agentsafe.model.ModelUsage usage) { this.usage = usage; }

    public BenchmarkMetricResult getMetrics() { return metrics; }
    public void setMetrics(BenchmarkMetricResult val) { this.metrics = val; }

    public List<ExecutionTraceStep> getTrace() { return trace; }
    public void setTrace(List<ExecutionTraceStep> val) { this.trace = val; }
}
