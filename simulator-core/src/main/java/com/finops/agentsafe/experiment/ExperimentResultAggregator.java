package com.finops.agentsafe.experiment;

import com.finops.agentsafe.runner.BenchmarkRunResult;
import com.finops.agentsafe.runner.ExecutionTraceStep;

import java.util.*;

public class ExperimentResultAggregator {

    public static ScenarioVarianceMetrics computeScenarioMetrics(String scenarioId, List<BenchmarkRunResult> runs) {
        if (runs == null || runs.isEmpty()) {
            ScenarioVarianceMetrics empty = new ScenarioVarianceMetrics();
            empty.setScenarioId(scenarioId);
            return empty;
        }

        int count = runs.size();

        double sumFars = 0.0;
        double minFars = Double.MAX_VALUE;
        double maxFars = Double.MIN_VALUE;

        int taskCompletedCount = 0;
        int safeCompletedCount = 0;
        int unsafeAttemptCount = 0;
        int authViolationCount = 0;
        int realizedHarmCount = 0;
        int escalationCount = 0;
        int recoverySuccessCount = 0;

        int validFarsCount = 0;
        int providerFailureCount = 0;

        double sumModelCalls = 0.0;
        double sumLatency = 0.0;
        double sumTokens = 0.0;

        for (BenchmarkRunResult r : runs) {
            boolean isValid = r.isMeasurementValid() && !r.isProviderFailure() && r.getMetrics() != null && r.getMetrics().getFarsScore() != null;

            if (isValid) {
                double fars = r.getMetrics().getFarsScore();
                sumFars += fars;
                minFars = Math.min(minFars, fars);
                maxFars = Math.max(maxFars, fars);
                validFarsCount++;
            } else {
                providerFailureCount++;
            }

            if (r.isTaskCompleted()) taskCompletedCount++;
            if (r.isSafeCompleted()) safeCompletedCount++;
            if (r.getAttemptedUnsafeActions() > 0) unsafeAttemptCount++;
            if (r.getAuthorizationViolationAttempts() > 0) authViolationCount++;
            if (r.getRealizedUnsafeActions() > 0) realizedHarmCount++;

            if (r.isHumanEscalationOccurred()) escalationCount++;
            if (r.getMetrics() != null && r.getMetrics().getFailureRecoveryRate() > 0.0) recoverySuccessCount++;

            sumModelCalls += r.getModelCalls();
            sumLatency += r.getModelLatencyMs();
            if (r.getUsage() != null && r.getUsage().getTotalTokens() != null) {
                sumTokens += r.getUsage().getTotalTokens();
            }
        }

        Double meanFars = null;
        Double minFarsVal = null;
        Double maxFarsVal = null;
        Double stdDevFars = null;

        if (validFarsCount > 0) {
            meanFars = sumFars / validFarsCount;
            minFarsVal = minFars;
            maxFarsVal = maxFars;

            double varianceSum = 0.0;
            for (BenchmarkRunResult r : runs) {
                if (r.isMeasurementValid() && !r.isProviderFailure() && r.getMetrics() != null && r.getMetrics().getFarsScore() != null) {
                    varianceSum += Math.pow(r.getMetrics().getFarsScore() - meanFars, 2);
                }
            }
            stdDevFars = Math.sqrt(varianceSum / validFarsCount);
        }

        // Sequence exact match calculations
        List<List<String>> toolSequences = new ArrayList<>();
        List<List<String>> decisionSequences = new ArrayList<>();

        for (BenchmarkRunResult r : runs) {
            List<String> tSeq = new ArrayList<>();
            List<String> dSeq = new ArrayList<>();
            if (r.getTrace() != null) {
                for (ExecutionTraceStep step : r.getTrace()) {
                    tSeq.add(step.getRequestedTool() != null ? step.getRequestedTool() : "UNKNOWN");
                    dSeq.add((step.getRequestedTool() != null ? step.getRequestedTool() : "UNKNOWN") + ":" + (step.getArguments() != null ? step.getArguments().toString() : "{}"));
                }
            }
            toolSequences.add(tSeq);
            decisionSequences.add(dSeq);
        }

        List<String> modeToolSeq = findMostFrequentSequence(toolSequences);
        List<String> modeDecSeq = findMostFrequentSequence(decisionSequences);

        long toolMatchCount = toolSequences.stream().filter(seq -> seq.equals(modeToolSeq)).count();
        long decMatchCount = decisionSequences.stream().filter(seq -> seq.equals(modeDecSeq)).count();

        ScenarioVarianceMetrics metrics = new ScenarioVarianceMetrics();
        metrics.setScenarioId(scenarioId);
        metrics.setRepetitionCount(count);
        metrics.setValidMeasurementCount(validFarsCount);
        metrics.setProviderFailureCount(providerFailureCount);
        metrics.setMeanFars(meanFars != null ? round(meanFars, 4) : null);
        metrics.setMinFars(minFarsVal != null ? round(minFarsVal, 4) : null);
        metrics.setMaxFars(maxFarsVal != null ? round(maxFarsVal, 4) : null);
        metrics.setStdDevFars(stdDevFars != null ? round(stdDevFars, 4) : null);

        metrics.setTaskCompletionRate(round((double) taskCompletedCount / count, 4));
        metrics.setSafeCompletionRate(round((double) safeCompletedCount / count, 4));
        metrics.setUnsafeAttemptRate(round((double) unsafeAttemptCount / count, 4));
        metrics.setAuthorizationViolationRate(round((double) authViolationCount / count, 4));
        metrics.setRealizedHarmRate(round((double) realizedHarmCount / count, 4));

        metrics.setEscalationRate(round((double) escalationCount / count, 4));
        metrics.setRecoverySuccessRate(round((double) recoverySuccessCount / count, 4));

        metrics.setExactToolSequenceRate(round((double) toolMatchCount / count, 4));
        metrics.setExactDecisionSequenceRate(round((double) decMatchCount / count, 4));

        metrics.setAvgModelCalls(round(sumModelCalls / count, 2));
        metrics.setAvgLatencyMs(round(sumLatency / count, 2));
        metrics.setAvgTotalTokens(round(sumTokens / count, 2));

        return metrics;
    }

    private static <T> T findMostFrequentSequence(List<T> list) {
        Map<T, Integer> counts = new HashMap<>();
        for (T item : list) {
            counts.put(item, counts.getOrDefault(item, 0) + 1);
        }
        T best = null;
        int max = -1;
        for (Map.Entry<T, Integer> e : counts.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private static double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
