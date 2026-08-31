package com.finops.agentsafe.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finops.agentsafe.agent.LLMBenchmarkAgent;
import com.finops.agentsafe.agent.replay.AgentDecisionTrace;
import com.finops.agentsafe.agent.replay.ReplayAgent;
import com.finops.agentsafe.model.*;
import com.finops.agentsafe.runner.BenchmarkRunResult;
import com.finops.agentsafe.runner.BenchmarkRunner;
import com.finops.agentsafe.scenario.BenchmarkScenario;
import com.finops.agentsafe.scenario.BenchmarkScenarioLoader;
import com.finops.agentsafe.tool.AgentToolExecutor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Instant;
import java.util.*;

/**
 * Committed, fingerprinted canonical Groq repeatability experiment runner for Phase 5C.
 *
 * <p>This class owns ALL substantive canonical protocol logic:
 * <ul>
 *   <li>Exact scenario selection (FIN-NORM-001, FIN-DATA-002, FIN-AUTH-001, FIN-ADV-001, FIN-SYS-001)</li>
 *   <li>5 repetitions per scenario (25 total)</li>
 *   <li>maxSteps = 5</li>
 *   <li>Global request-attempt ceiling = 125</li>
 *   <li>Implementation fingerprint start/mid/end checks</li>
 *   <li>Provider-failure abort rule</li>
 *   <li>Token/pacing accounting</li>
 *   <li>Result + trace serialization</li>
 *   <li>Manifest creation</li>
 *   <li>Dataset consistency validation (25 results, 25 traces, unique runIds)</li>
 *   <li>Aggregate summary generation</li>
 *   <li>Replay validation (0 live calls)</li>
 *   <li>INVALID_CANONICAL_ATTEMPT.json marker on failure</li>
 * </ul>
 *
 * <p>Because this file lives under {@code src/main/java/}, it is included in the SHA-256
 * implementation fingerprint computed by {@link ImplementationFingerprintService}.
 * Any change to this file will change the fingerprint and invalidate a frozen canonical run.
 *
 * <p>Provider: groq
 * <p>Model: openai/gpt-oss-120b
 * <p>Rate limits: RPM=30, RPD=1000, TPM=8000, effective pacing TPM=7000, TPD=200000
 */
@Component
public class GroqCanonicalExperimentRunner {

    // --- Frozen protocol constants ---

    /** The model provider for this canonical experiment. */
    public static final String PROVIDER = "groq";

    /** The model identifier for this canonical experiment. */
    public static final String MODEL = "openai/gpt-oss-120b";

    /** Experiment identifier string. */
    public static final String EXPERIMENT_ID =
            "exp-groq-openai-gpt-oss-120b-repeatability-canonical-v1";

    /** Exact ordered scenario IDs to execute, in canonical order. */
    public static final List<String> EXPERIMENT_SCENARIO_IDS = List.of(
        "FIN-NORM-001", "FIN-DATA-002", "FIN-AUTH-001", "FIN-ADV-001", "FIN-SYS-001"
    );

    /** Number of repetitions per scenario. */
    public static final int REPETITIONS_PER_SCENARIO = 5;

    /** Maximum agent steps per scenario execution. */
    public static final int MAX_STEPS_PER_RUN = 5;

    /** Absolute global provider-request-attempt ceiling across all 25 runs. */
    public static final int HARD_CAP_REQUEST_ATTEMPTS = 125;

    /** Configured RPM for pacing metadata. */
    public static final int CONFIGURED_RPM = 30;

    /** Configured RPD for pacing metadata. */
    public static final int CONFIGURED_RPD = 1000;

    /** Configured TPM for pacing metadata. */
    public static final int CONFIGURED_TPM = 8000;

    /** Effective pacing TPM (10% safety margin). */
    public static final int EFFECTIVE_PACING_TPM = 7000;

    /** Configured TPD for pacing metadata. */
    public static final int CONFIGURED_TPD = 200_000;

    /** Relative path under the project root where canonical results are written. */
    public static final String CANONICAL_RESULT_SUBPATH =
            "results/experiments/groq/openai-gpt-oss-120b/repeatability-v1-canonical";

    // --- Spring-injected dependencies ---

    private final ModelAdapterRegistry adapterRegistry;
    private final BenchmarkScenarioLoader scenarioLoader;
    private final BenchmarkRunner benchmarkRunner;
    private final LLMBenchmarkAgent llmAgent;
    private final AgentToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;
    private final ImplementationFingerprintService fingerprintService;

    /**
     * Optional override for the project root directory.
     * When set (package-private for testability), canonical results are written under this root
     * instead of the auto-resolved project root. Never set in production.
     */
    File testProjectRootOverride = null;

    public GroqCanonicalExperimentRunner(ModelAdapterRegistry adapterRegistry,
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
        this.fingerprintService = fingerprintService != null
                ? fingerprintService
                : new ImplementationFingerprintService();
    }

    // -------------------------------------------------------------------------
    // Dry-run mode: emits the full plan with 0 live API calls.
    // -------------------------------------------------------------------------

    /**
     * Prints the complete experiment plan and verifies all preconditions without
     * making any live provider calls.
     *
     * @param expectedFingerprint when non-null, asserts the current fingerprint matches
     */
    public void runDryRun(String expectedFingerprint) {
        boolean isConfigured = adapterRegistry.getAdapter(PROVIDER)
                .map(ModelAdapter::isConfigured).orElse(false);

        File projectRoot = (testProjectRootOverride != null) ? testProjectRootOverride : resolveProjectRoot();
        File expBaseDir = new File(projectRoot, CANONICAL_RESULT_SUBPATH);
        boolean canonAvailable = !expBaseDir.exists();

        String liveFingerprint = fingerprintService.calculate();
        String gitHead = BenchmarkRunResult.resolveGitHead();

        System.out.println("=== PHASE 5C GROQ CANONICAL DRY RUN ===");
        System.out.println("Provider: " + PROVIDER);
        System.out.println("Model: " + MODEL);
        System.out.println();
        System.out.println("GROQ_API_KEY: " + (isConfigured ? "PRESENT" : "MISSING"));
        System.out.println();
        System.out.println("Configured RPM: " + CONFIGURED_RPM);
        System.out.println("Configured RPD: " + CONFIGURED_RPD);
        System.out.println("Configured TPM: " + CONFIGURED_TPM);
        System.out.println("Effective pacing TPM: " + EFFECTIVE_PACING_TPM);
        System.out.println("Configured TPD: " + CONFIGURED_TPD);
        System.out.println();
        System.out.println("Planned scenarios: " + EXPERIMENT_SCENARIO_IDS.size());
        System.out.println("Repetitions: " + REPETITIONS_PER_SCENARIO);
        System.out.println("Planned executions: " + (EXPERIMENT_SCENARIO_IDS.size() * REPETITIONS_PER_SCENARIO));
        System.out.println("Max steps: " + MAX_STEPS_PER_RUN);
        System.out.println("Request ceiling: " + HARD_CAP_REQUEST_ATTEMPTS);
        System.out.println();
        System.out.println("Git HEAD: " + gitHead);
        System.out.println("Implementation fingerprint: " + liveFingerprint);
        System.out.println("Canonical directory available: " + (canonAvailable ? "YES" : "NO"));
        System.out.println();

        if (expectedFingerprint != null && !expectedFingerprint.equals(liveFingerprint)) {
            System.out.println("[DRY RUN WARNING] Fingerprint mismatch!");
            System.out.println("  Expected: " + expectedFingerprint);
            System.out.println("  Actual:   " + liveFingerprint);
        }

        System.out.println("LIVE GROQ CALLS: 0");
    }

    // -------------------------------------------------------------------------
    // Canonical experiment execution.
    // -------------------------------------------------------------------------

    /**
     * Executes the full 25-run canonical repeatability experiment.
     *
     * <p>Requires:
     * <ul>
     *   <li>GROQ_API_KEY configured in environment</li>
     *   <li>Canonical result directory must NOT exist (clean-directory guard)</li>
     *   <li>Expected fingerprint must match current working tree (when non-null)</li>
     * </ul>
     *
     * @param expectedFingerprint the SHA-256 fingerprint frozen at experiment authorization time
     * @throws IllegalStateException on any protocol violation, provider failure, or integrity check failure
     */
    public void runCanonicalExperiment(String expectedFingerprint) throws Exception {
        System.out.println("==========================================================");
        System.out.println("   STARTING CANONICAL GROQ REPEATABILITY EXPERIMENT   ");
        System.out.println("==========================================================");

        // 1. Provider check
        var adapterOpt = adapterRegistry.getAdapter(PROVIDER);
        if (adapterOpt.isEmpty() || !adapterOpt.get().isConfigured()) {
            throw new IllegalStateException(
                    "PROVIDER_NOT_CONFIGURED: Missing GROQ_API_KEY environment variable. Experiment aborted.");
        }

        // 2. Fingerprint pre-check
        String currentGitHead = BenchmarkRunResult.resolveGitHead();
        String startFingerprint = fingerprintService.calculate();
        System.out.println("Git HEAD: " + currentGitHead);
        System.out.println("Start Implementation Fingerprint: " + startFingerprint);

        if (expectedFingerprint != null && !expectedFingerprint.equals(startFingerprint)) {
            throw new IllegalStateException(
                    "INVALID_CANONICAL_START \u2014 IMPLEMENTATION FINGERPRINT MISMATCH. Expected: "
                    + expectedFingerprint + " but got: " + startFingerprint);
        }

        // 3. Canonical directory clean-state guard
        File projectRoot = (testProjectRootOverride != null) ? testProjectRootOverride : resolveProjectRoot();
        File expBaseDir = new File(projectRoot, CANONICAL_RESULT_SUBPATH);
        if (expBaseDir.exists()) {
            File[] existingFiles = expBaseDir.listFiles();
            if (existingFiles != null && existingFiles.length > 0) {
                throw new IllegalStateException(
                        "CANONICAL_DIRECTORY_ALREADY_EXISTS with " + existingFiles.length
                        + " files: " + expBaseDir.getAbsolutePath());
            }
        }
        expBaseDir.mkdirs();

        // 4. Configure model
        ModelConfiguration config = ModelConfiguration.groq(MODEL);
        llmAgent.setModelConfiguration(config);

        String startTimestamp = Instant.now().toString();

        // 5. Write experiment_manifest.json
        Map<String, Object> manifest = buildManifest(currentGitHead, startFingerprint, startTimestamp);
        File manifestFile = new File(expBaseDir, "experiment_manifest.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestFile, manifest);
        System.out.println("Manifest created: " + manifestFile.getAbsolutePath());

        // 6. Load scenarios
        List<BenchmarkScenario> scenarios = new ArrayList<>();
        for (String id : EXPERIMENT_SCENARIO_IDS) {
            scenarioLoader.getScenario(id).ifPresent(scenarios::add);
        }

        // 7. Main experiment loop
        Map<String, List<BenchmarkRunResult>> scenarioRunsMap = new LinkedHashMap<>();
        int totalRequestAttemptsUsed = 0;
        int totalSuccessfulInferenceCalls = 0;
        int totalRetries = 0;
        long cumulativeTokensUsed = 0;
        long totalPacingWaitMs = 0;

        try {
            for (BenchmarkScenario sc : scenarios) {
                sc.setMaximumSteps(MAX_STEPS_PER_RUN);
                File scDir = new File(expBaseDir, sc.getScenarioId());
                scDir.mkdirs();
                List<BenchmarkRunResult> runList = new ArrayList<>();

                for (int rep = 1; rep <= REPETITIONS_PER_SCENARIO; rep++) {
                    // Mid-run fingerprint check
                    String midFingerprint = fingerprintService.calculate();
                    if (!startFingerprint.equals(midFingerprint)) {
                        throw new IllegalStateException(
                                "CANONICAL_EXPERIMENT_INVALID_CODE_CHANGED: Code modified mid-run ("
                                + startFingerprint + " != " + midFingerprint + ")");
                    }

                    System.out.print("Running Scenario " + sc.getScenarioId() + " Repetition " + rep + "/" + REPETITIONS_PER_SCENARIO + "... ");

                    // Global ceiling guard
                    if (totalRequestAttemptsUsed >= HARD_CAP_REQUEST_ATTEMPTS) {
                        throw new IllegalStateException(
                                "INVALID_CANONICAL_EXPERIMENT: Hard request attempt ceiling reached ("
                                + HARD_CAP_REQUEST_ATTEMPTS + "). Experiment terminated.");
                    }

                    llmAgent.resetMetrics();
                    BenchmarkRunResult res = benchmarkRunner.runScenario(sc, llmAgent);
                    res.setExperimentId(EXPERIMENT_ID);
                    res.setRepetitionNumber(rep);
                    res.setTimestamp(Instant.now().toString());
                    res.setBaseGitCommit(currentGitHead);
                    res.setWorkingTreeFingerprint(startFingerprint);

                    int reqAttempts = res.getProviderRequestAttempts() > 0
                            ? res.getProviderRequestAttempts()
                            : res.getModelCalls();
                    totalRequestAttemptsUsed += reqAttempts;

                    // Provider failure abort
                    if (res.isProviderFailure() || !res.isMeasurementValid()) {
                        String classification = res.getOutcomeClassification() != null
                                ? res.getOutcomeClassification()
                                : "INFRASTRUCTURE_FAILURE";
                        System.out.println("FAILED -> Provider Failure (" + classification + ")");
                        throw new IllegalStateException(
                                "INVALID_CANONICAL_EXPERIMENT: Provider failure detected ("
                                + classification + "). Experiment terminated.");
                    }

                    totalSuccessfulInferenceCalls += res.getModelCalls();
                    totalRetries += Math.max(0, reqAttempts - res.getModelCalls());

                    if (res.getUsage() != null) {
                        Long tt = res.getUsage().getTotalTokens();
                        if (tt != null) cumulativeTokensUsed += tt;
                        Long pw = res.getUsage().getPacingWaitMs();
                        if (pw != null) totalPacingWaitMs += pw;
                    }

                    runList.add(res);

                    Double farsScore = (res.getMetrics() != null) ? res.getMetrics().getFarsScore() : null;
                    System.out.println("DONE -> TaskCompleted: " + res.isTaskCompleted()
                            + " | SafeCompleted: " + res.isSafeCompleted()
                            + " | FARS: " + (farsScore != null ? farsScore : "N/A")
                            + " | Outcome: " + res.getOutcomeClassification()
                            + " | Attempts: " + reqAttempts);

                    // Serialize per-run result
                    File resFile = new File(scDir, sc.getScenarioId() + "_rep" + rep + "_result.json");
                    objectMapper.writerWithDefaultPrettyPrinter().writeValue(resFile, res);

                    // Serialize per-run decision trace
                    List<AgentDecision> decisions = buildDecisions(res);
                    AgentDecisionTrace trace = new AgentDecisionTrace(sc.getScenarioId(), llmAgent.getAgentId(), decisions);
                    File traceFile = new File(scDir, sc.getScenarioId() + "_rep" + rep + "_trace.json");
                    objectMapper.writerWithDefaultPrettyPrinter().writeValue(traceFile, trace);
                }
                scenarioRunsMap.put(sc.getScenarioId(), runList);
            }
        } catch (Exception e) {
            writeInvalidMarker(expBaseDir, e, scenarioRunsMap, totalRequestAttemptsUsed);
            throw e;
        }

        // 8. Dataset consistency validation
        validateDataset(scenarioRunsMap);

        // 9. End fingerprint immutability check
        String endFingerprint = fingerprintService.calculate();
        System.out.println("End Implementation Fingerprint: " + endFingerprint);
        if (!startFingerprint.equals(endFingerprint)) {
            throw new IllegalStateException(
                    "CANONICAL_EXPERIMENT_INVALID_CODE_CHANGED: Code modified during experiment ("
                    + startFingerprint + " != " + endFingerprint + ")");
        }

        // 10. Compute aggregate metrics
        List<ScenarioVarianceMetrics> summaryMetrics = new ArrayList<>();
        for (Map.Entry<String, List<BenchmarkRunResult>> entry : scenarioRunsMap.entrySet()) {
            summaryMetrics.add(ExperimentResultAggregator.computeScenarioMetrics(entry.getKey(), entry.getValue()));
        }

        // 11. Replay validation on 2 representative traces (0 live calls)
        System.out.println();
        System.out.println("=== EXECUTING REPLAY VALIDATION ON 2 SELECTED TRACES (0 LIVE CALLS) ===");
        Map<String, String> replayResultsMap = new LinkedHashMap<>();
        List<String> replayTargets = List.of("FIN-NORM-001", "FIN-AUTH-001");
        for (String targetId : replayTargets) {
            BenchmarkScenario sc = scenarioLoader.getScenario(targetId).orElseThrow();
            sc.setMaximumSteps(MAX_STEPS_PER_RUN);

            File traceFile = new File(new File(expBaseDir, targetId), targetId + "_rep1_trace.json");
            AgentDecisionTrace trace = objectMapper.readValue(traceFile, AgentDecisionTrace.class);

            ReplayAgent replayAgent = new ReplayAgent(toolExecutor, trace);
            BenchmarkRunResult replayRes = benchmarkRunner.runScenario(sc, replayAgent);

            BenchmarkRunResult originalRes = scenarioRunsMap.get(targetId).get(0);
            double origFars = originalRes.getMetrics().getFarsScore();
            double replayFars = replayRes.getMetrics().getFarsScore();
            boolean farsMatch = Math.abs(origFars - replayFars) < 0.001;

            System.out.println("Replay " + targetId + " Rep 1: Original FARS=" + origFars
                    + " | Replay FARS=" + replayFars + " | Match=" + farsMatch);
            replayResultsMap.put(targetId, farsMatch
                    ? "PASS (Original FARS: " + origFars + ", Replay FARS: " + replayFars + ")"
                    : "FAIL");
        }

        // 12. Write aggregate_summary.json
        Map<String, Object> summaryReport = new LinkedHashMap<>();
        summaryReport.put("experimentTitle", "PHASE 5C GROQ REPEATABILITY & VARIANCE STUDY");
        summaryReport.put("classification", "REPEATABILITY_ENGINEERING_EVALUATION");
        summaryReport.put("provider", PROVIDER);
        summaryReport.put("modelName", MODEL);
        summaryReport.put("canonicalRunnerClass", getClass().getName());
        summaryReport.put("scenarioCount", scenarios.size());
        summaryReport.put("repetitionsPerScenario", REPETITIONS_PER_SCENARIO);
        summaryReport.put("totalRunsExecuted", EXPERIMENT_SCENARIO_IDS.size() * REPETITIONS_PER_SCENARIO);
        summaryReport.put("totalRequestAttemptsUsed", totalRequestAttemptsUsed);
        summaryReport.put("totalSuccessfulInferenceCalls", totalSuccessfulInferenceCalls);
        summaryReport.put("totalRetries", totalRetries);
        summaryReport.put("cumulativeTokensUsed", cumulativeTokensUsed);
        summaryReport.put("totalPacingWaitMs", totalPacingWaitMs);
        summaryReport.put("hardCapRequestAttempts", HARD_CAP_REQUEST_ATTEMPTS);
        summaryReport.put("startFingerprint", startFingerprint);
        summaryReport.put("endFingerprint", endFingerprint);
        summaryReport.put("scenarioVarianceMetrics", summaryMetrics);
        summaryReport.put("replayValidation", replayResultsMap);
        summaryReport.put("endTimestamp", Instant.now().toString());

        File summaryFile = new File(expBaseDir, "aggregate_summary.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(summaryFile, summaryReport);
        System.out.println("Summary exported to: " + summaryFile.getAbsolutePath());

        System.out.println("==========================================================");
        System.out.println("   GROQ CANONICAL EXPERIMENT COMPLETED SUCCESSFULLY   ");
        System.out.println("   Total Request Attempts Used: " + totalRequestAttemptsUsed + " / " + HARD_CAP_REQUEST_ATTEMPTS);
        System.out.println("   Summary: " + summaryFile.getAbsolutePath());
        System.out.println("==========================================================");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> buildManifest(String gitHead, String fingerprint, String startTimestamp) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("experimentId", EXPERIMENT_ID);
        manifest.put("classification", "REPEATABILITY_ENGINEERING_EVALUATION");
        manifest.put("provider", PROVIDER);
        manifest.put("model", MODEL);
        manifest.put("canonicalRunnerClass", getClass().getName());
        manifest.put("providerModelVersionMetadata", "NOT PROVIDED");
        manifest.put("baseGitCommit", gitHead);
        manifest.put("workingTreeFingerprint", fingerprint);
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
        manifest.put("plannedScenarios", EXPERIMENT_SCENARIO_IDS.size());
        manifest.put("repetitionsPerScenario", REPETITIONS_PER_SCENARIO);
        manifest.put("plannedScenarioExecutions", EXPERIMENT_SCENARIO_IDS.size() * REPETITIONS_PER_SCENARIO);
        manifest.put("maxStepsPerRun", MAX_STEPS_PER_RUN);
        manifest.put("absoluteRequestAttemptCeiling", HARD_CAP_REQUEST_ATTEMPTS);
        manifest.put("configuredRPM", CONFIGURED_RPM);
        manifest.put("configuredRPD", CONFIGURED_RPD);
        manifest.put("configuredTPM", CONFIGURED_TPM);
        manifest.put("effectivePacingTPM", EFFECTIVE_PACING_TPM);
        manifest.put("configuredTPD", CONFIGURED_TPD);
        manifest.put("generationParameters", Map.of(
            "temperature", "PROVIDER_DEFAULT",
            "topP", "PROVIDER_DEFAULT",
            "topK", "PROVIDER_DEFAULT",
            "providerSeed", "PROVIDER_DEFAULT"
        ));
        manifest.put("startTimestamp", startTimestamp);
        return manifest;
    }

    private List<AgentDecision> buildDecisions(BenchmarkRunResult res) {
        List<AgentDecision> decisions = new ArrayList<>();
        if (res.getTrace() == null) return decisions;
        for (var step : res.getTrace()) {
            String tName = step.getRequestedTool();
            Map<String, Object> stepArgs = step.getArguments() != null ? step.getArguments() : Map.of();
            DecisionType dType;
            if ("COMPLETE".equalsIgnoreCase(tName)) {
                dType = DecisionType.COMPLETE;
            } else if ("ABSTAIN".equalsIgnoreCase(tName)) {
                dType = DecisionType.ABSTAIN;
            } else if ("ESCALATE_TO_HUMAN".equalsIgnoreCase(tName)) {
                dType = DecisionType.ESCALATE;
            } else {
                dType = DecisionType.TOOL_CALL;
            }
            decisions.add(new AgentDecision(dType, tName, stepArgs, step.getBriefReasoningSummary(), 1.0));
        }
        return decisions;
    }

    private void validateDataset(Map<String, List<BenchmarkRunResult>> scenarioRunsMap) {
        int totalResultFiles = 0;
        Set<UUID> runIds = new HashSet<>();
        for (String scId : EXPERIMENT_SCENARIO_IDS) {
            List<BenchmarkRunResult> runs = scenarioRunsMap.get(scId);
            if (runs == null || runs.size() != REPETITIONS_PER_SCENARIO) {
                throw new IllegalStateException(
                        "[VALIDATION FAILURE] Scenario " + scId + " has "
                        + (runs == null ? 0 : runs.size())
                        + " runs instead of " + REPETITIONS_PER_SCENARIO);
            }
            for (BenchmarkRunResult r : runs) {
                totalResultFiles++;
                if (r.getRunId() == null || !runIds.add(r.getRunId())) {
                    throw new IllegalStateException(
                            "[VALIDATION FAILURE] Duplicate or null runId detected: " + r.getRunId());
                }
                if (!PROVIDER.equals(r.getProvider()) || !MODEL.equals(r.getModelName())) {
                    throw new IllegalStateException(
                            "[VALIDATION FAILURE] Mismatched provider/model in run "
                            + r.getRunId() + ": " + r.getProvider() + "/" + r.getModelName());
                }
            }
        }
        int expected = EXPERIMENT_SCENARIO_IDS.size() * REPETITIONS_PER_SCENARIO;
        if (totalResultFiles != expected) {
            throw new IllegalStateException(
                    "[VALIDATION FAILURE] Total result count mismatch: expected="
                    + expected + " actual=" + totalResultFiles);
        }
    }

    private void writeInvalidMarker(File expBaseDir,
                                    Exception e,
                                    Map<String, List<BenchmarkRunResult>> scenarioRunsMap,
                                    int totalRequestAttemptsUsed) {
        try {
            File invalidMarker = new File(expBaseDir, "INVALID_CANONICAL_ATTEMPT.json");
            String errStr = String.valueOf(e.getMessage());
            String reason = (errStr.contains("429") || errStr.contains("rate limit") || errStr.contains("budget"))
                    ? "INVALID_PROVIDER_RATE_LIMIT"
                    : "INVALID_CANONICAL_EXPERIMENT";
            Map<String, Object> invalidData = new LinkedHashMap<>();
            invalidData.put("status", "INVALID_CANONICAL_EXPERIMENT");
            invalidData.put("reason", reason);
            invalidData.put("error", errStr);
            invalidData.put("completedExecutions",
                    scenarioRunsMap.values().stream().mapToInt(List::size).sum());
            invalidData.put("totalRequestAttemptsUsed", totalRequestAttemptsUsed);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(invalidMarker, invalidData);
            System.err.println("[CANONICAL EXPERIMENT FAILED] Marked directory as INVALID_CANONICAL_ATTEMPT ("
                    + reason + "): " + errStr);
        } catch (Exception ex) {
            System.err.println("[CANONICAL EXPERIMENT] Could not write INVALID marker: " + ex.getMessage());
        }
    }

    static File resolveProjectRoot() {
        try {
            File currentDir = new File(".").getCanonicalFile();
            if (currentDir.getName().equalsIgnoreCase("simulator-core")) {
                return currentDir.getParentFile();
            }
            File simCore = new File(currentDir, "simulator-core");
            if (simCore.exists() && simCore.isDirectory()) {
                return currentDir;
            }
            return currentDir;
        } catch (Exception e) {
            return new File(".");
        }
    }
}
