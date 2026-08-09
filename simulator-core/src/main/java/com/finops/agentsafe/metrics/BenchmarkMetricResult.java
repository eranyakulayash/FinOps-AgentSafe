package com.finops.agentsafe.metrics;

import java.util.Map;

/**
 * Encapsulates all 10 individual benchmark metrics and composite FARS score.
 */
public class BenchmarkMetricResult {

    private double taskCompletionRate;
    private double unsafeActionRate;
    private double financialIntegrityScore;
    private double authorizationCompliance;
    private double failureRecoveryRate;
    private double escalationPrecision;
    private double escalationRecall;
    private double escalationF1;
    private double toolSelectionAccuracy;
    private double auditTrailCompleteness;
    private double efficiencyScore;

    private double farsScore;
    private Map<String, Double> farsWeights;

    public BenchmarkMetricResult() {}

    public double getTaskCompletionRate() { return taskCompletionRate; }
    public void setTaskCompletionRate(double val) { this.taskCompletionRate = val; }

    public double getUnsafeActionRate() { return unsafeActionRate; }
    public void setUnsafeActionRate(double val) { this.unsafeActionRate = val; }

    public double getFinancialIntegrityScore() { return financialIntegrityScore; }
    public void setFinancialIntegrityScore(double val) { this.financialIntegrityScore = val; }

    public double getAuthorizationCompliance() { return authorizationCompliance; }
    public void setAuthorizationCompliance(double val) { this.authorizationCompliance = val; }

    public double getFailureRecoveryRate() { return failureRecoveryRate; }
    public void setFailureRecoveryRate(double val) { this.failureRecoveryRate = val; }

    public double getEscalationPrecision() { return escalationPrecision; }
    public void setEscalationPrecision(double val) { this.escalationPrecision = val; }

    public double getEscalationRecall() { return escalationRecall; }
    public void setEscalationRecall(double val) { this.escalationRecall = val; }

    public double getEscalationF1() { return escalationF1; }
    public void setEscalationF1(double val) { this.escalationF1 = val; }

    public double getToolSelectionAccuracy() { return toolSelectionAccuracy; }
    public void setToolSelectionAccuracy(double val) { this.toolSelectionAccuracy = val; }

    public double getAuditTrailCompleteness() { return auditTrailCompleteness; }
    public void setAuditTrailCompleteness(double val) { this.auditTrailCompleteness = val; }

    public double getEfficiencyScore() { return efficiencyScore; }
    public void setEfficiencyScore(double val) { this.efficiencyScore = val; }

    public double getFarsScore() { return farsScore; }
    public void setFarsScore(double val) { this.farsScore = val; }

    public Map<String, Double> getFarsWeights() { return farsWeights; }
    public void setFarsWeights(Map<String, Double> val) { this.farsWeights = val; }
}
