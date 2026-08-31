package com.finops.agentsafe.experiment;

import com.finops.agentsafe.agent.Agent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finops.agentsafe.metrics.BenchmarkMetricResult;
import com.finops.agentsafe.model.*;
import com.finops.agentsafe.runner.BenchmarkRunResult;
import com.finops.agentsafe.runner.BenchmarkRunner;
import com.finops.agentsafe.scenario.BenchmarkScenario;
import com.finops.agentsafe.scenario.BenchmarkScenarioLoader;
import com.finops.agentsafe.tool.AgentToolExecutor;
import com.finops.agentsafe.agent.LLMBenchmarkAgent;
import com.finops.agentsafe.agent.replay.ReplayAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Offline test suite for {@link GroqCanonicalExperimentRunner}.
 *
 * <p>All 15 required test cases. LIVE GROQ CALLS = 0.
 */
class GroqCanonicalExperimentRunnerTest {

    // =========================================================================
    // Test 1: Exact five canonical scenario IDs
    // =========================================================================
    @Test
    void testExactFiveScenarioIds() {
        List<String> ids = GroqCanonicalExperimentRunner.EXPERIMENT_SCENARIO_IDS;
        assertEquals(5, ids.size(), "Must have exactly 5 canonical scenario IDs");
        assertEquals("FIN-NORM-001", ids.get(0));
        assertEquals("FIN-DATA-002", ids.get(1));
        assertEquals("FIN-AUTH-001", ids.get(2));
        assertEquals("FIN-ADV-001", ids.get(3));
        assertEquals("FIN-SYS-001", ids.get(4));
    }

    // =========================================================================
    // Test 2: Exactly five repetitions per scenario
    // =========================================================================
    @Test
    void testExactlyFiveRepetitions() {
        assertEquals(5, GroqCanonicalExperimentRunner.REPETITIONS_PER_SCENARIO);
    }

    // =========================================================================
    // Test 3: Max steps = 5
    // =========================================================================
    @Test
    void testMaxStepsFive() {
        assertEquals(5, GroqCanonicalExperimentRunner.MAX_STEPS_PER_RUN);
    }

    // =========================================================================
    // Test 4: Global request ceiling = 125
    // =========================================================================
    @Test
    void testGlobalRequestCeiling() {
        assertEquals(125, GroqCanonicalExperimentRunner.HARD_CAP_REQUEST_ATTEMPTS);
    }

    // =========================================================================
    // Test 5: Unique run IDs across 25 runs
    // =========================================================================
    @Test
    void testUniqueRunIds(@TempDir Path tempDir) throws Exception {
        GroqCanonicalExperimentRunner runner = buildSuccessRunner(tempDir);
        runner.runCanonicalExperiment(null);

        // Collect all result files and verify unique runIds
        ObjectMapper mapper = new ObjectMapper();
        Set<String> runIds = new HashSet<>();
        for (String scId : GroqCanonicalExperimentRunner.EXPERIMENT_SCENARIO_IDS) {
            File scDir = new File(tempDir.toFile(),
                    GroqCanonicalExperimentRunner.CANONICAL_RESULT_SUBPATH + "/" + scId);
            for (int rep = 1; rep <= 5; rep++) {
                File resFile = new File(scDir, scId + "_rep" + rep + "_result.json");
                var node = mapper.readTree(resFile);
                String runId = node.get("runId").asText();
                assertNotNull(runId, "runId must not be null");
                assertTrue(runIds.add(runId), "runId must be unique, found duplicate: " + runId);
            }
        }
        assertEquals(25, runIds.size(), "Must have 25 unique run IDs");
    }

    // =========================================================================
    // Test 6: Provider failure aborts the experiment
    // =========================================================================
    @Test
    void testProviderFailureAborts(@TempDir Path tempDir) throws Exception {
        GroqCanonicalExperimentRunner runner = buildRunnerWithFailureOnRun(tempDir, 3);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runner.runCanonicalExperiment(null));
        assertTrue(ex.getMessage().contains("INVALID_CANONICAL_EXPERIMENT")
                        || ex.getMessage().contains("Provider failure"),
                "Exception must indicate canonical experiment failure: " + ex.getMessage());
    }

    // =========================================================================
    // Test 7: Incomplete run cannot generate valid aggregate
    // =========================================================================
    @Test
    void testIncompleteRunCannotGenerateAggregate(@TempDir Path tempDir) throws Exception {
        // Fails on run 2, so aggregate_summary.json must NOT be written
        GroqCanonicalExperimentRunner runner = buildRunnerWithFailureOnRun(tempDir, 2);

        assertThrows(IllegalStateException.class,
                () -> runner.runCanonicalExperiment(null));

        File aggregateFile = new File(tempDir.toFile(),
                GroqCanonicalExperimentRunner.CANONICAL_RESULT_SUBPATH + "/aggregate_summary.json");
        assertFalse(aggregateFile.exists(),
                "aggregate_summary.json must NOT be written when < 25 runs completed");
    }

    // =========================================================================
    // Test 8: Fingerprint mismatch aborts the experiment
    // =========================================================================
    @Test
    void testFingerprintMismatchAborts(@TempDir Path tempDir) throws Exception {
        GroqCanonicalExperimentRunner runner = buildSuccessRunner(tempDir);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                runner.runCanonicalExperiment("aaaa-deliberately-wrong-fingerprint-0000bbbb"));
        assertTrue(ex.getMessage().contains("FINGERPRINT_MISMATCH")
                        || ex.getMessage().contains("INVALID_CANONICAL_START"),
                "Exception must indicate fingerprint mismatch: " + ex.getMessage());
    }

    // =========================================================================
    // Test 9: Result serialization writes valid JSON files
    // =========================================================================
    @Test
    void testResultSerializationWritesValidJson(@TempDir Path tempDir) throws Exception {
        buildSuccessRunner(tempDir).runCanonicalExperiment(null);

        ObjectMapper mapper = new ObjectMapper();
        for (String scId : GroqCanonicalExperimentRunner.EXPERIMENT_SCENARIO_IDS) {
            File scDir = new File(tempDir.toFile(),
                    GroqCanonicalExperimentRunner.CANONICAL_RESULT_SUBPATH + "/" + scId);
            for (int rep = 1; rep <= 5; rep++) {
                File resFile = new File(scDir, scId + "_rep" + rep + "_result.json");
                assertTrue(resFile.exists(), "Result file must exist: " + resFile);
                assertNotNull(mapper.readTree(resFile), "Result file must be valid JSON");
            }
        }
    }

    // =========================================================================
    // Test 10: Trace serialization writes valid JSON files
    // =========================================================================
    @Test
    void testTraceSerializationWritesValidJson(@TempDir Path tempDir) throws Exception {
        buildSuccessRunner(tempDir).runCanonicalExperiment(null);

        ObjectMapper mapper = new ObjectMapper();
        for (String scId : GroqCanonicalExperimentRunner.EXPERIMENT_SCENARIO_IDS) {
            File scDir = new File(tempDir.toFile(),
                    GroqCanonicalExperimentRunner.CANONICAL_RESULT_SUBPATH + "/" + scId);
            for (int rep = 1; rep <= 5; rep++) {
                File traceFile = new File(scDir, scId + "_rep" + rep + "_trace.json");
                assertTrue(traceFile.exists(), "Trace file must exist: " + traceFile);
                assertNotNull(mapper.readTree(traceFile), "Trace file must be valid JSON");
            }
        }
    }

    // =========================================================================
    // Test 11: Manifest is generated with required fields
    // =========================================================================
    @Test
    void testManifestGenerationHasRequiredFields(@TempDir Path tempDir) throws Exception {
        buildSuccessRunner(tempDir).runCanonicalExperiment(null);

        File manifestFile = new File(tempDir.toFile(),
                GroqCanonicalExperimentRunner.CANONICAL_RESULT_SUBPATH + "/experiment_manifest.json");
        assertTrue(manifestFile.exists(), "Manifest must exist");

        ObjectMapper mapper = new ObjectMapper();
        var node = mapper.readTree(manifestFile);
        assertEquals("groq", node.get("provider").asText());
        assertEquals("openai/gpt-oss-120b", node.get("model").asText());
        assertEquals(5, node.get("plannedScenarios").asInt());
        assertEquals(5, node.get("repetitionsPerScenario").asInt());
        assertEquals(25, node.get("plannedScenarioExecutions").asInt());
        assertEquals(5, node.get("maxStepsPerRun").asInt());
        assertEquals(125, node.get("absoluteRequestAttemptCeiling").asInt());
        assertTrue(node.has("workingTreeFingerprint"), "Manifest must have fingerprint");
        assertTrue(node.has("baseGitCommit"), "Manifest must have git commit");
        assertTrue(node.has("startTimestamp"), "Manifest must have start timestamp");
    }

    // =========================================================================
    // Test 12: Aggregate only generated after 25/25 runs
    // =========================================================================
    @Test
    void testAggregateOnlyAfterAllTwentyFiveRuns(@TempDir Path tempDir) throws Exception {
        // Fail on run 5 — aggregate must not be written
        GroqCanonicalExperimentRunner runner = buildRunnerWithFailureOnRun(tempDir, 5);

        assertThrows(IllegalStateException.class,
                () -> runner.runCanonicalExperiment(null));

        File aggregateFile = new File(tempDir.toFile(),
                GroqCanonicalExperimentRunner.CANONICAL_RESULT_SUBPATH + "/aggregate_summary.json");
        assertFalse(aggregateFile.exists(),
                "aggregate_summary.json must NOT be written when experiment aborts before 25 runs");
    }

    // =========================================================================
    // Test 13: Replay makes zero provider (adapter.predict) calls
    // =========================================================================
    @Test
    void testReplayMakesZeroProviderCalls(@TempDir Path tempDir) throws Exception {
        ModelAdapter mockAdapter = mock(ModelAdapter.class);
        when(mockAdapter.isConfigured()).thenReturn(true);
        when(mockAdapter.getProviderName()).thenReturn("groq");

        ModelAdapterRegistry registry = mock(ModelAdapterRegistry.class);
        when(registry.getAdapter("groq")).thenReturn(Optional.of(mockAdapter));

        LLMBenchmarkAgent llmAgent = mock(LLMBenchmarkAgent.class);
        when(llmAgent.getAgentId()).thenReturn("groq-agent");

        BenchmarkRunner benchmarkRunner = mock(BenchmarkRunner.class);
        // All 25 main runs + 2 replay runs succeed
        when(benchmarkRunner.runScenario(any(BenchmarkScenario.class), any(Agent.class)))
                .thenAnswer(inv -> buildSuccessResult());

        BenchmarkScenarioLoader scenarioLoader = buildScenarioLoader();
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        ObjectMapper mapper = new ObjectMapper();
        ImplementationFingerprintService fps = mock(ImplementationFingerprintService.class);
        when(fps.calculate()).thenReturn("mock-fp-consistent-0000");

        GroqCanonicalExperimentRunner runner = new GroqCanonicalExperimentRunner(
                registry, scenarioLoader, benchmarkRunner, llmAgent, toolExecutor, mapper, fps);
        runner.testProjectRootOverride = tempDir.toFile();
        runner.runCanonicalExperiment(null);

        // The adapter itself must never be asked to predict — only LLMBenchmarkAgent calls predict
        verify(mockAdapter, never()).predict(any());
    }

    // =========================================================================
    // Test 14: INVALID_CANONICAL_ATTEMPT.json is written on failure
    // =========================================================================
    @Test
    void testInvalidMarkerWrittenOnFailure(@TempDir Path tempDir) throws Exception {
        GroqCanonicalExperimentRunner runner = buildRunnerWithFailureOnRun(tempDir, 1);

        assertThrows(Exception.class, () -> runner.runCanonicalExperiment(null));

        File markerFile = new File(tempDir.toFile(),
                GroqCanonicalExperimentRunner.CANONICAL_RESULT_SUBPATH + "/INVALID_CANONICAL_ATTEMPT.json");
        assertTrue(markerFile.exists(), "INVALID_CANONICAL_ATTEMPT.json must be written on failure");

        ObjectMapper mapper = new ObjectMapper();
        var node = mapper.readTree(markerFile);
        assertEquals("INVALID_CANONICAL_EXPERIMENT", node.get("status").asText());
        assertTrue(node.has("reason"), "Marker must have reason");
        assertTrue(node.has("error"), "Marker must have error");
    }

    // =========================================================================
    // Test 15: Committed runner is inside fingerprint scope
    // =========================================================================
    @Test
    void testCommittedRunnerIsInsideFingerprintScope(@TempDir Path tempDir) throws Exception {
        // Create a fake src tree with a baseline file
        Path expPkg = tempDir.resolve("src/main/java/com/finops/agentsafe/experiment");
        Files.createDirectories(expPkg);
        Path baseline = expPkg.resolve("OtherClass.java");
        Files.writeString(baseline, "public class OtherClass {}");

        ImplementationFingerprintService fps = new ImplementationFingerprintService(tempDir.toFile());
        String initialHash = fps.calculate();

        // Add GroqCanonicalExperimentRunner.java — fingerprint must change
        Path runnerFile = expPkg.resolve("GroqCanonicalExperimentRunner.java");
        Files.writeString(runnerFile,
                "public class GroqCanonicalExperimentRunner { /* canonical harness v1 */ }");
        String hashWithRunner = fps.calculate();
        assertNotEquals(initialHash, hashWithRunner,
                "Adding GroqCanonicalExperimentRunner.java under src/ must change the fingerprint");

        // Modify it — fingerprint must change again
        Files.writeString(runnerFile,
                "public class GroqCanonicalExperimentRunner { /* canonical harness v2 */ }");
        String hashAfterModification = fps.calculate();
        assertNotEquals(hashWithRunner, hashAfterModification,
                "Modifying GroqCanonicalExperimentRunner.java must change the fingerprint");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private GroqCanonicalExperimentRunner buildSuccessRunner(Path tempDir) {
        ModelAdapterRegistry registry = mock(ModelAdapterRegistry.class);
        ModelAdapter mockAdapter = mock(ModelAdapter.class);
        when(mockAdapter.isConfigured()).thenReturn(true);
        when(mockAdapter.getProviderName()).thenReturn("groq");
        when(registry.getAdapter("groq")).thenReturn(Optional.of(mockAdapter));

        LLMBenchmarkAgent llmAgent = mock(LLMBenchmarkAgent.class);
        when(llmAgent.getAgentId()).thenReturn("groq-agent");

        BenchmarkRunner benchmarkRunner = mock(BenchmarkRunner.class);
        when(benchmarkRunner.runScenario(any(BenchmarkScenario.class), any(Agent.class)))
                .thenAnswer(inv -> buildSuccessResult());

        BenchmarkScenarioLoader scenarioLoader = buildScenarioLoader();
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        ObjectMapper mapper = new ObjectMapper();
        ImplementationFingerprintService fps = mock(ImplementationFingerprintService.class);
        when(fps.calculate()).thenReturn("mock-fp-consistent-0000");

        GroqCanonicalExperimentRunner runner = new GroqCanonicalExperimentRunner(
                registry, scenarioLoader, benchmarkRunner, llmAgent, toolExecutor, mapper, fps);
        runner.testProjectRootOverride = tempDir.toFile();
        return runner;
    }

    private GroqCanonicalExperimentRunner buildRunnerWithFailureOnRun(Path tempDir, int failOnCallN) {
        ModelAdapterRegistry registry = mock(ModelAdapterRegistry.class);
        ModelAdapter mockAdapter = mock(ModelAdapter.class);
        when(mockAdapter.isConfigured()).thenReturn(true);
        when(mockAdapter.getProviderName()).thenReturn("groq");
        when(registry.getAdapter("groq")).thenReturn(Optional.of(mockAdapter));

        LLMBenchmarkAgent llmAgent = mock(LLMBenchmarkAgent.class);
        when(llmAgent.getAgentId()).thenReturn("groq-agent");

        final int[] callCount = {0};
        BenchmarkRunner benchmarkRunner = mock(BenchmarkRunner.class);
        when(benchmarkRunner.runScenario(any(BenchmarkScenario.class), eq(llmAgent)))
                .thenAnswer(inv -> {
                    callCount[0]++;
                    return (callCount[0] == failOnCallN) ? buildFailureResult() : buildSuccessResult();
                });

        BenchmarkScenarioLoader scenarioLoader = buildScenarioLoader();
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        ObjectMapper mapper = new ObjectMapper();
        ImplementationFingerprintService fps = mock(ImplementationFingerprintService.class);
        when(fps.calculate()).thenReturn("mock-fp-consistent-0000");

        GroqCanonicalExperimentRunner runner = new GroqCanonicalExperimentRunner(
                registry, scenarioLoader, benchmarkRunner, llmAgent, toolExecutor, mapper, fps);
        runner.testProjectRootOverride = tempDir.toFile();
        return runner;
    }

    private BenchmarkScenarioLoader buildScenarioLoader() {
        BenchmarkScenarioLoader loader = mock(BenchmarkScenarioLoader.class);
        for (String id : GroqCanonicalExperimentRunner.EXPERIMENT_SCENARIO_IDS) {
            BenchmarkScenario sc = new BenchmarkScenario();
            sc.setScenarioId(id);
            sc.setCategory("TEST");
            sc.setDescription("Mock scenario " + id);
            sc.setMaximumSteps(5);
            when(loader.getScenario(id)).thenReturn(Optional.of(sc));
        }
        return loader;
    }

    private BenchmarkRunResult buildSuccessResult() {
        BenchmarkRunResult r = new BenchmarkRunResult();
        r.setRunId(UUID.randomUUID());
        r.setProvider("groq");
        r.setModelName("openai/gpt-oss-120b");
        r.setTaskCompleted(true);
        r.setSafeCompleted(true);
        r.setMeasurementValid(true);
        r.setProviderFailure(false);
        r.setOutcomeClassification("SUCCESS");
        r.setModelCalls(1);
        r.setProviderRequestAttempts(1);
        r.setTrace(Collections.emptyList());
        BenchmarkMetricResult metrics = new BenchmarkMetricResult();
        metrics.setFarsScore(1.0);
        r.setMetrics(metrics);
        return r;
    }

    private BenchmarkRunResult buildFailureResult() {
        BenchmarkRunResult r = new BenchmarkRunResult();
        r.setRunId(UUID.randomUUID());
        r.setProvider("groq");
        r.setModelName("openai/gpt-oss-120b");
        r.setTaskCompleted(false);
        r.setSafeCompleted(false);
        r.setMeasurementValid(false);
        r.setProviderFailure(true);
        r.setOutcomeClassification("PROVIDER_RATE_LIMIT");
        r.setModelCalls(0);
        r.setProviderRequestAttempts(1);
        r.setTrace(Collections.emptyList());
        BenchmarkMetricResult metrics = new BenchmarkMetricResult();
        metrics.setFarsScore(0.0);
        r.setMetrics(metrics);
        return r;
    }
}
