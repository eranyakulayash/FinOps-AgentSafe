package com.finops.agentsafe.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finops.agentsafe.agent.LLMBenchmarkAgent;
import com.finops.agentsafe.agent.replay.AgentDecisionTrace;
import com.finops.agentsafe.agent.replay.ReplayAgent;
import com.finops.agentsafe.model.AgentDecision;
import com.finops.agentsafe.model.DecisionType;
import com.finops.agentsafe.model.ModelAdapterRegistry;
import com.finops.agentsafe.model.ModelConfiguration;
import com.finops.agentsafe.runner.BenchmarkRunResult;
import com.finops.agentsafe.runner.BenchmarkRunner;
import com.finops.agentsafe.scenario.BenchmarkScenario;
import com.finops.agentsafe.scenario.BenchmarkScenarioLoader;
import com.finops.agentsafe.tool.AgentToolExecutor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;

@Component
public class RepeatabilityExperimentRunner {

    public static final List<String> EXPERIMENT_SCENARIO_IDS = List.of(
        "FIN-NORM-001", "FIN-DATA-002", "FIN-AUTH-001", "FIN-ADV-001", "FIN-SYS-001"
    );

    public static final int HARD_CAP_MODEL_CALLS = 125;

    private final ModelAdapterRegistry adapterRegistry;
    private final BenchmarkScenarioLoader scenarioLoader;
    private final BenchmarkRunner benchmarkRunner;
    private final LLMBenchmarkAgent llmAgent;
    private final AgentToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;
    private final ImplementationFingerprintService fingerprintService;

    public RepeatabilityExperimentRunner(ModelAdapterRegistry adapterRegistry,
                                         BenchmarkScenarioLoader scenarioLoader,
                                         BenchmarkRunner benchmarkRunner,
                                         LLMBenchmarkAgent llmAgent,
                                         AgentToolExecutor toolExecutor,
                                         ObjectMapper objectMapper,
                                         ImplementationFingerprintService fingerprintService) {
        this.adapterRegistry = adapterRegistry;
        this.scenarioLoader = scenarioLoader;
        this.benchmarkRunner = benchmarkRunner;
        this.llmAgent = llmAgent;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
        this.fingerprintService = fingerprintService != null ? fingerprintService : new ImplementationFingerprintService();
    }

    public void runExperiment(String modelName, boolean dryRun) throws Exception {
        runExperiment(modelName, dryRun, 5);
    }

    public void runExperiment(String modelName, boolean dryRun, int repetitionsPerScenario) throws Exception {
        String targetModel = modelName != null ? modelName : "gemini-3.6-flash";
        if (!"gemini-3.6-flash".equals(targetModel)) {
            System.out.println("CANONICAL_EXPERIMENT_ABORTED_MODEL_MISMATCH");
            throw new IllegalArgumentException("CANONICAL_EXPERIMENT_ABORTED_MODEL_MISMATCH: Model must be gemini-3.6-flash but got " + targetModel);
        }

        var adapterOpt = adapterRegistry.getAdapter("gemini");
        boolean isConfigured = adapterOpt.isPresent() && adapterOpt.get().isConfigured();

        List<BenchmarkScenario> scenarios = new ArrayList<>();
        for (String id : EXPERIMENT_SCENARIO_IDS) {
            scenarioLoader.getScenario(id).ifPresent(scenarios::add);
        }

        int plannedRuns = scenarios.size() * repetitionsPerScenario;

        File currentDir = new File(".").getCanonicalFile();
        File projectRoot = currentDir.getName().equalsIgnoreCase("simulator-core") ? currentDir.getParentFile() : currentDir;
        File expBaseDir = new File(projectRoot, "results/experiments/gemini/" + targetModel + "/repeatability-v1-canonical");

        String liveFingerprint = fingerprintService.calculate();

        if (dryRun) {
            boolean canonAvailable = !expBaseDir.exists();
            System.out.println("=== PHASE 5B GEMINI REPEATABILITY EXPERIMENT DRY RUN ===");
            System.out.println("Provider: gemini");
            System.out.println("Model: gemini-3.6-flash");
            System.out.println("Working tree fingerprint: " + liveFingerprint);
            System.out.println("Planned runs: " + plannedRuns);
            System.out.println("Maximum calls: " + HARD_CAP_MODEL_CALLS);
            System.out.println("Canonical directory available: " + (canonAvailable ? "YES" : "NO"));
            System.out.println("LIVE MODEL CALLS: 0");
            System.out.println("PREFLIGHT PASS");
            return;
        }

        if (!isConfigured) {
            System.out.println("[EXPERIMENT ERROR] PROVIDER_NOT_CONFIGURED: Missing GEMINI_API_KEY environment variable. Experiment aborted.");
            return;
        }

        ModelConfiguration config = new ModelConfiguration(
            "gemini", targetModel, 0.0, 2048, 10000L, 3, 42L, "financial-agent-system-v1"
        );
        llmAgent.setModelConfiguration(config);

        // Canonical experiment directory check
        if (expBaseDir.exists()) {
            System.out.println("[EXPERIMENT ERROR] CANONICAL_DIRECTORY_ALREADY_EXISTS: " + expBaseDir.getAbsolutePath() + ". Aborting experiment to prevent overwriting.");
            throw new IllegalStateException("CANONICAL_DIRECTORY_ALREADY_EXISTS: " + expBaseDir.getAbsolutePath());
        }
        expBaseDir.mkdirs();

        String experimentId = "exp-gemini-3.6-flash-repeatability-canonical-v1";
        String startTimestamp = java.time.Instant.now().toString();
        String startFingerprint = liveFingerprint;

        String currentGitHead = BenchmarkRunResult.resolveGitHead();

        // Write experiment_manifest.json
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("experimentId", experimentId);
        manifest.put("classification", "REPEATABILITY_ENGINEERING_EVALUATION");
        manifest.put("provider", "gemini");
        manifest.put("model", targetModel);
        manifest.put("baseGitCommit", currentGitHead);
        manifest.put("workingTreeFingerprint", startFingerprint);
        manifest.put("benchmarkVersion", "1.0.0");
        manifest.put("scenarioVersions", Map.of(
            "FIN-NORM-001", "1.0.0",
            "FIN-DATA-002", "1.0.0",
            "FIN-AUTH-001", "1.0.0",
            "FIN-ADV-001", "1.0.0",
            "FIN-SYS-001", "1.0.0"
        ));
        manifest.put("toolContractVersion", "1.0.0");
        manifest.put("metricVersion", "1.0.0");
        manifest.put("promptVersion", "financial-agent-system-v1");
        manifest.put("adapterVersion", "1.5");
        manifest.put("repetitionsPerScenario", repetitionsPerScenario);
        manifest.put("scenarioIds", EXPERIMENT_SCENARIO_IDS);
        manifest.put("plannedScenarioRuns", 25);
        manifest.put("maxStepsPerRun", 5);
        manifest.put("maxRetries", 3);
        manifest.put("maxCanonicalModelCalls", 125);
        manifest.put("temperature", "PROVIDER_DEFAULT");
        manifest.put("topP", "PROVIDER_DEFAULT");
        manifest.put("topK", "PROVIDER_DEFAULT");
        manifest.put("providerSeed", "PROVIDER_DEFAULT");
        manifest.put("generationParameters", Map.of(
            "temperature", "PROVIDER_DEFAULT",
            "topP", "PROVIDER_DEFAULT",
            "topK", "PROVIDER_DEFAULT",
            "providerSeed", "PROVIDER_DEFAULT"
        ));
        manifest.put("startTimestamp", startTimestamp);

        File manifestFile = new File(expBaseDir, "experiment_manifest.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestFile, manifest);
        System.out.println("Manifest created: " + manifestFile.getAbsolutePath());

        Map<String, List<BenchmarkRunResult>> scenarioRunsMap = new LinkedHashMap<>();
        int totalModelCallsUsed = 0;

        System.out.println("=== EXECUTING PHASE 5B CANONICAL GEMINI REPEATABILITY EXPERIMENT ===");
        System.out.println("Experiment ID: " + experimentId);
        System.out.println("Model: " + targetModel);
        System.out.println("Total Planned Runs: " + plannedRuns);

        try {
            for (BenchmarkScenario sc : scenarios) {
                sc.setMaximumSteps(5); // Guardrail: max 5 steps per scenario
                File scDir = new File(expBaseDir, sc.getScenarioId());
                scDir.mkdirs();

                List<BenchmarkRunResult> runList = new ArrayList<>();

                for (int rep = 1; rep <= repetitionsPerScenario; rep++) {
                    // Mid-run fingerprint check
                    String midFingerprint = fingerprintService.calculate();
                    if (!startFingerprint.equals(midFingerprint)) {
                        System.out.println("CANONICAL_EXPERIMENT_INVALID_CODE_CHANGED");
                        throw new IllegalStateException("CANONICAL_EXPERIMENT_INVALID_CODE_CHANGED: Code modified mid-run (" + startFingerprint + " != " + midFingerprint + ")");
                    }

                    System.out.print("Running Scenario " + sc.getScenarioId() + " Repetition " + rep + "/" + repetitionsPerScenario + "... ");

                    // Enforce budget guardrail
                    if (totalModelCallsUsed >= HARD_CAP_MODEL_CALLS) {
                        throw new IllegalStateException("INVALID_CANONICAL_EXPERIMENT: INVALID_PROVIDER_RATE_LIMIT: Hard call budget ceiling reached (" + HARD_CAP_MODEL_CALLS + " calls). Experiment terminated.");
                    }

                    llmAgent.resetMetrics();
                    BenchmarkRunResult res = benchmarkRunner.runScenario(sc, llmAgent);
                    res.setExperimentId(experimentId);
                    res.setRepetitionNumber(rep);
                    res.setTimestamp(java.time.Instant.now().toString());
                    res.setBaseGitCommit(currentGitHead);
                    res.setWorkingTreeFingerprint(startFingerprint);

                    totalModelCallsUsed += res.getProviderRequestAttempts() > 0 ? res.getProviderRequestAttempts() : res.getModelCalls();
                    runList.add(res);

                    System.out.println("DONE -> TaskCompleted: " + res.isTaskCompleted() + " | SafeCompleted: " + res.isSafeCompleted() + " | FARS: " + (res.getMetrics() != null && res.getMetrics().getFarsScore() != null ? res.getMetrics().getFarsScore() : "N/A") + " | Outcome: " + res.getOutcomeClassification() + " | Calls: " + res.getModelCalls());

                    // Save per-run result JSON
                    File resFile = new File(scDir, sc.getScenarioId() + "_rep" + rep + "_result.json");
                    try {
                        objectMapper.writerWithDefaultPrettyPrinter().writeValue(resFile, res);
                        System.out.println("   Saved result: " + resFile.getAbsolutePath() + " (size=" + resFile.length() + " bytes)");
                    } catch (Exception e) {
                        System.err.println("   [ERROR] Failed to save result file " + resFile.getAbsolutePath() + ": " + e.getMessage());
                        e.printStackTrace();
                    }

                    // Save per-run decision trace JSON
                    List<AgentDecision> decisions = new ArrayList<>();
                    if (res.getTrace() != null) {
                        for (var step : res.getTrace()) {
                            String tName = step.getRequestedTool();
                            Map<String, Object> args = step.getArguments() != null ? step.getArguments() : Map.of();
                            DecisionType dType = DecisionType.TOOL_CALL;
                            if ("COMPLETE".equalsIgnoreCase(tName)) {
                                dType = DecisionType.COMPLETE;
                            } else if ("ABSTAIN".equalsIgnoreCase(tName)) {
                                dType = DecisionType.ABSTAIN;
                            } else if ("ESCALATE_TO_HUMAN".equalsIgnoreCase(tName)) {
                                dType = DecisionType.ESCALATE;
                            }
                            decisions.add(new AgentDecision(dType, tName, args, step.getBriefReasoningSummary(), 1.0));
                        }
                    }
                    AgentDecisionTrace trace = new AgentDecisionTrace(sc.getScenarioId(), llmAgent.getAgentId(), decisions);
                    File traceFile = new File(scDir, sc.getScenarioId() + "_rep" + rep + "_trace.json");
                    try {
                        objectMapper.writerWithDefaultPrettyPrinter().writeValue(traceFile, trace);
                        System.out.println("   Saved trace: " + traceFile.getAbsolutePath() + " (size=" + traceFile.length() + " bytes)");
                    } catch (Exception e) {
                        System.err.println("   [ERROR] Failed to save trace file " + traceFile.getAbsolutePath() + ": " + e.getMessage());
                        e.printStackTrace();
                    }

                    // Circuit Breaker Guard: Check if 3 consecutive model decisions exhausted retries due to HTTP 429
                    if (llmAgent.getConsecutiveExhausted429Decisions() >= 3) {
                        throw new IllegalStateException("INVALID_CANONICAL_EXPERIMENT: INVALID_PROVIDER_RATE_LIMIT: Circuit breaker tripped due to 3 consecutive exhausted HTTP 429 decisions.");
                    }
                }
                scenarioRunsMap.put(sc.getScenarioId(), runList);
            }
        } catch (Exception e) {
            File invalidMarker = new File(expBaseDir, "INVALID_CANONICAL_ATTEMPT.json");
            String errStr = String.valueOf(e.getMessage());
            String reason = (errStr.contains("INVALID_PROVIDER_RATE_LIMIT") || errStr.contains("429") || errStr.contains("rate limit") || errStr.contains("budget"))
                ? "INVALID_PROVIDER_RATE_LIMIT"
                : "INVALID_CANONICAL_EXPERIMENT";

            Map<String, Object> invalidData = new LinkedHashMap<>();
            invalidData.put("status", "INVALID_CANONICAL_EXPERIMENT");
            invalidData.put("reason", reason);
            invalidData.put("error", errStr);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(invalidMarker, invalidData);
            System.err.println("[CANONICAL EXPERIMENT FAILED] Marked directory as INVALID_CANONICAL_EXPERIMENT (" + reason + "): " + errStr);
            throw e;
        }

        // Validate dataset consistency over all 25 runs before generating aggregate
        int totalResultFiles = 0;
        int totalTraceFiles = 0;
        Set<UUID> runIds = new HashSet<>();
        for (String scId : EXPERIMENT_SCENARIO_IDS) {
            List<BenchmarkRunResult> runs = scenarioRunsMap.get(scId);
            if (runs == null || runs.size() != repetitionsPerScenario) {
                throw new IllegalStateException("[VALIDATION FAILURE] Scenario " + scId + " has " + (runs == null ? 0 : runs.size()) + " runs instead of " + repetitionsPerScenario);
            }
            for (BenchmarkRunResult r : runs) {
                totalResultFiles++;
                totalTraceFiles++;
                if (r.getRunId() == null || !runIds.add(r.getRunId())) {
                    throw new IllegalStateException("[VALIDATION FAILURE] Duplicate or null runId detected: " + r.getRunId());
                }
                if (!"gemini".equals(r.getProvider()) || !"gemini-3.6-flash".equals(r.getModelName())) {
                    throw new IllegalStateException("[VALIDATION FAILURE] Mismatched provider/model in run " + r.getRunId() + ": " + r.getProvider() + "/" + r.getModelName());
                }
                if (!experimentId.equals(r.getExperimentId())) {
                    throw new IllegalStateException("[VALIDATION FAILURE] Mismatched experimentId in run " + r.getRunId() + ": " + r.getExperimentId());
                }
            }
        }
        if (totalResultFiles != plannedRuns || totalTraceFiles != plannedRuns) {
            throw new IllegalStateException("[VALIDATION FAILURE] Total result/trace files count mismatch: results=" + totalResultFiles + ", traces=" + totalTraceFiles);
        }

        // Post-run fingerprint immutability check
        String endFingerprint = fingerprintService.calculate();
        if (!startFingerprint.equals(endFingerprint)) {
            System.out.println("CANONICAL_EXPERIMENT_INVALID_CODE_CHANGED");
            throw new IllegalStateException("CANONICAL_EXPERIMENT_INVALID_CODE_CHANGED: Code modified during experiment (" + startFingerprint + " != " + endFingerprint + ")");
        }

        // Aggregate Metrics Calculation
        List<ScenarioVarianceMetrics> summaryMetrics = new ArrayList<>();
        for (Map.Entry<String, List<BenchmarkRunResult>> entry : scenarioRunsMap.entrySet()) {
            ScenarioVarianceMetrics varMetrics = ExperimentResultAggregator.computeScenarioMetrics(entry.getKey(), entry.getValue());
            summaryMetrics.add(varMetrics);
        }

        // Replay Validation on 2 select runs (FIN-NORM-001 rep 1, FIN-AUTH-001 rep 1)
        System.out.println();
        System.out.println("=== EXECUTING REPLAY VALIDATION ON 2 SELECTED TRACES ===");
        Map<String, String> replayResultsMap = new LinkedHashMap<>();

        List<String> replayTargets = List.of("FIN-NORM-001", "FIN-AUTH-001");
        for (String targetId : replayTargets) {
            BenchmarkScenario sc = scenarioLoader.getScenario(targetId).orElseThrow();
            sc.setMaximumSteps(5);

            File traceFile = new File(new File(expBaseDir, targetId), targetId + "_rep1_trace.json");
            AgentDecisionTrace trace = objectMapper.readValue(traceFile, AgentDecisionTrace.class);

            ReplayAgent replayAgent = new ReplayAgent(toolExecutor, trace);
            BenchmarkRunResult replayRes = benchmarkRunner.runScenario(sc, replayAgent);

            BenchmarkRunResult originalRes = scenarioRunsMap.get(targetId).get(0);
            boolean farsMatch = Math.abs(originalRes.getMetrics().getFarsScore() - replayRes.getMetrics().getFarsScore()) < 0.001;

            System.out.println("Replay " + targetId + " Rep 1: Original FARS=" + originalRes.getMetrics().getFarsScore() + " | Replay FARS=" + replayRes.getMetrics().getFarsScore() + " | Match=" + farsMatch);
            replayResultsMap.put(targetId, farsMatch ? "PASS (Original FARS: " + originalRes.getMetrics().getFarsScore() + ", Replay FARS: " + replayRes.getMetrics().getFarsScore() + ")" : "FAIL");
        }

        // Export aggregate summary JSON
        Map<String, Object> summaryReport = new LinkedHashMap<>();
        summaryReport.put("experimentTitle", "PHASE 5B GEMINI REPEATABILITY & VARIANCE STUDY");
        summaryReport.put("provider", "gemini");
        summaryReport.put("modelName", targetModel);
        summaryReport.put("scenarioCount", scenarios.size());
        summaryReport.put("repetitionsPerScenario", repetitionsPerScenario);
        summaryReport.put("totalRunsExecuted", plannedRuns);
        summaryReport.put("totalModelCallsUsed", totalModelCallsUsed);
        summaryReport.put("hardCapModelCalls", HARD_CAP_MODEL_CALLS);
        summaryReport.put("scenarioVarianceMetrics", summaryMetrics);
        summaryReport.put("replayValidation", replayResultsMap);

        File summaryFile = new File(expBaseDir, "aggregate_summary.json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(summaryFile, summaryReport);
            System.out.println("Summary exported to: " + summaryFile.getAbsolutePath() + " (size=" + summaryFile.length() + " bytes)");
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to save summary file " + summaryFile.getAbsolutePath() + ": " + e.getMessage());
            e.printStackTrace();
        }

        // Final Canonical Status Semantics Validation
        CanonicalStatusResult status = evaluateCanonicalReadiness(expBaseDir, plannedRuns);
        if (!status.isReady) {
            System.out.println("STATUS: " + status.status + " (" + status.reason + "): " + status.details);
            throw new IllegalStateException(status.status + ": " + status.reason + " - " + status.details);
        }

        System.out.println();
        System.out.println("=== PHASE 5B EXPERIMENT COMPLETED SUCCESSFULLY ===");
        System.out.println("Total Live Model Calls Used: " + totalModelCallsUsed + " / " + HARD_CAP_MODEL_CALLS);
        System.out.println("Summary exported to: " + summaryFile.getAbsolutePath());
    }

    public static class CanonicalStatusResult {
        private final boolean isReady;
        private final String status;
        private final String reason;
        private final String details;

        public CanonicalStatusResult(boolean isReady, String status, String reason, String details) {
            this.isReady = isReady;
            this.status = status;
            this.reason = reason;
            this.details = details;
        }

        public boolean isReady() { return isReady; }
        public String getStatus() { return status; }
        public String getReason() { return reason; }
        public String getDetails() { return details; }
    }

    public static CanonicalStatusResult evaluateCanonicalReadiness(File expBaseDir, int plannedRuns) {
        if (expBaseDir == null || !expBaseDir.exists()) {
            return new CanonicalStatusResult(false, "INVALID_CANONICAL_EXPERIMENT", "DIRECTORY_MISSING", "Canonical directory does not exist.");
        }

        File invalidMarker = new File(expBaseDir, "INVALID_CANONICAL_ATTEMPT.json");
        if (invalidMarker.exists()) {
            return new CanonicalStatusResult(false, "INVALID_CANONICAL_EXPERIMENT", "INVALID_PROVIDER_RATE_LIMIT", "INVALID_CANONICAL_ATTEMPT.json marker exists in canonical directory.");
        }

        File aggregateFile = new File(expBaseDir, "aggregate_summary.json");
        if (!aggregateFile.exists()) {
            return new CanonicalStatusResult(false, "INVALID_CANONICAL_EXPERIMENT", "AGGREGATE_MISSING", "aggregate_summary.json does not exist.");
        }

        int completedRuns = 0;
        File[] subDirs = expBaseDir.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                File[] resFiles = subDir.listFiles((dir, name) -> name.endsWith("_result.json"));
                if (resFiles != null) {
                    completedRuns += resFiles.length;
                }
            }
        }

        if (completedRuns != plannedRuns) {
            return new CanonicalStatusResult(false, "INVALID_CANONICAL_EXPERIMENT", "INVALID_PROVIDER_RATE_LIMIT", "Completed runs (" + completedRuns + ") != planned runs (" + plannedRuns + ").");
        }

        return new CanonicalStatusResult(true, "CANONICAL_READY", "SUCCESS", "All " + plannedRuns + " runs completed with valid aggregate.");
    }
}
