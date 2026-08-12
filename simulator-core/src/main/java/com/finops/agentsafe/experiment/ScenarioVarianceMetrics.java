package com.finops.agentsafe.experiment;

public class ScenarioVarianceMetrics {

    private String scenarioId;
    private int repetitionCount;
    private int validMeasurementCount;
    private int providerFailureCount;
    private Double meanFars;
    private Double minFars;
    private Double maxFars;
    private Double stdDevFars;

    private double taskCompletionRate;
    private double safeCompletionRate;
    private double unsafeAttemptRate;
    private double authorizationViolationRate;
    private double realizedHarmRate;

    private double escalationRate;
    private double recoverySuccessRate;

    private double exactDecisionSequenceRate;
    private double exactToolSequenceRate;

    private double avgModelCalls;
    private double avgLatencyMs;
    private double avgTotalTokens;

    public ScenarioVarianceMetrics() {}

    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }

    public int getRepetitionCount() { return repetitionCount; }
    public void setRepetitionCount(int repetitionCount) { this.repetitionCount = repetitionCount; }

    public int getValidMeasurementCount() { return validMeasurementCount; }
    public void setValidMeasurementCount(int validMeasurementCount) { this.validMeasurementCount = validMeasurementCount; }

    public int getProviderFailureCount() { return providerFailureCount; }
    public void setProviderFailureCount(int providerFailureCount) { this.providerFailureCount = providerFailureCount; }

    public Double getMeanFars() { return meanFars; }
    public void setMeanFars(Double meanFars) { this.meanFars = meanFars; }

    public Double getMinFars() { return minFars; }
    public void setMinFars(Double minFars) { this.minFars = minFars; }

    public Double getMaxFars() { return maxFars; }
    public void setMaxFars(Double maxFars) { this.maxFars = maxFars; }

    public Double getStdDevFars() { return stdDevFars; }
    public void setStdDevFars(Double stdDevFars) { this.stdDevFars = stdDevFars; }

    public double getTaskCompletionRate() { return taskCompletionRate; }
    public void setTaskCompletionRate(double taskCompletionRate) { this.taskCompletionRate = taskCompletionRate; }

    public double getSafeCompletionRate() { return safeCompletionRate; }
    public void setSafeCompletionRate(double safeCompletionRate) { this.safeCompletionRate = safeCompletionRate; }

    public double getUnsafeAttemptRate() { return unsafeAttemptRate; }
    public void setUnsafeAttemptRate(double unsafeAttemptRate) { this.unsafeAttemptRate = unsafeAttemptRate; }

    public double getAuthorizationViolationRate() { return authorizationViolationRate; }
    public void setAuthorizationViolationRate(double authorizationViolationRate) { this.authorizationViolationRate = authorizationViolationRate; }

    public double getRealizedHarmRate() { return realizedHarmRate; }
    public void setRealizedHarmRate(double realizedHarmRate) { this.realizedHarmRate = realizedHarmRate; }

    public double getEscalationRate() { return escalationRate; }
    public void setEscalationRate(double escalationRate) { this.escalationRate = escalationRate; }

    public double getRecoverySuccessRate() { return recoverySuccessRate; }
    public void setRecoverySuccessRate(double recoverySuccessRate) { this.recoverySuccessRate = recoverySuccessRate; }

    public double getExactDecisionSequenceRate() { return exactDecisionSequenceRate; }
    public void setExactDecisionSequenceRate(double exactDecisionSequenceRate) { this.exactDecisionSequenceRate = exactDecisionSequenceRate; }

    public double getExactToolSequenceRate() { return exactToolSequenceRate; }
    public void setExactToolSequenceRate(double exactToolSequenceRate) { this.exactToolSequenceRate = exactToolSequenceRate; }

    public double getAvgModelCalls() { return avgModelCalls; }
    public void setAvgModelCalls(double avgModelCalls) { this.avgModelCalls = avgModelCalls; }

    public double getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(double avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }

    public double getAvgTotalTokens() { return avgTotalTokens; }
    public void setAvgTotalTokens(double avgTotalTokens) { this.avgTotalTokens = avgTotalTokens; }
}
