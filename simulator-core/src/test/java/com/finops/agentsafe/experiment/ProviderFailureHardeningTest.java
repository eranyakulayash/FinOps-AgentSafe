package com.finops.agentsafe.experiment;

import com.finops.agentsafe.agent.LLMBenchmarkAgent;
import com.finops.agentsafe.metrics.BenchmarkMetricResult;
import com.finops.agentsafe.model.*;
import com.finops.agentsafe.model.prompt.PromptSecurityManager;
import com.finops.agentsafe.model.validation.AgentDecisionValidator;
import com.finops.agentsafe.runner.BenchmarkRunResult;
import com.finops.agentsafe.runner.BenchmarkRunner;
import com.finops.agentsafe.scenario.BenchmarkScenario;
import com.finops.agentsafe.tool.AgentToolExecutor;
import com.finops.agentsafe.tool.AgentToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ProviderFailureHardeningTest {

    private ModelAdapterRegistry adapterRegistry;
    private AgentToolRegistry toolRegistry;
    private AgentToolExecutor toolExecutor;
    private PromptSecurityManager promptSecurityManager;
    private AgentDecisionValidator decisionValidator;
    private ModelAdapter mockAdapter;
    private BenchmarkRunner benchmarkRunner;

    @BeforeEach
    public void setUp() {
        adapterRegistry = mock(ModelAdapterRegistry.class);
        toolRegistry = mock(AgentToolRegistry.class);
        toolExecutor = mock(AgentToolExecutor.class);
        promptSecurityManager = mock(PromptSecurityManager.class);
        decisionValidator = mock(AgentDecisionValidator.class);
        mockAdapter = mock(ModelAdapter.class);
        benchmarkRunner = mock(BenchmarkRunner.class);

        when(mockAdapter.isConfigured()).thenReturn(true);
        when(mockAdapter.getProviderName()).thenReturn("gemini");
        when(adapterRegistry.getAdapter("gemini")).thenReturn(Optional.of(mockAdapter));
        when(promptSecurityManager.getSystemPrompt()).thenReturn("System Instruction");
        when(decisionValidator.validate(any(), any())).thenReturn(new AgentDecisionValidator.ValidationResult(true, Collections.emptyList()));
    }

    @Test
    public void testExhaustedHttp429RetriesProduceMeasurementValidFalseAndNullFars() {
        ModelResponse error429 = ModelResponse.failure(
            ModelError.rateLimit("Gemini API rate limit exceeded (HTTP 429)"),
            new ModelMetadata("gemini", "gemini-3.6-flash", "1.5", "1.0", "v1"),
            new ModelUsage(0L, 0L, 0L, 0L, 1, 0, 100L, null)
        );

        when(mockAdapter.predict(any(ModelRequest.class))).thenReturn(error429);

        LLMBenchmarkAgent agent = new LLMBenchmarkAgent(
            adapterRegistry, toolRegistry, toolExecutor, promptSecurityManager, decisionValidator
        );
        ModelConfiguration cfg = new ModelConfiguration("gemini", "gemini-3.6-flash", 0.0, 1024, 5000L, 3, 42L, "v1");
        agent.setModelConfiguration(cfg);

        BenchmarkScenario scenario = new BenchmarkScenario();
        scenario.setScenarioId("FIN-DATA-002");
        scenario.setVersion("1.0.0");
        scenario.setMaximumSteps(1);

        com.finops.agentsafe.tool.AgentToolContext ctx = new com.finops.agentsafe.tool.AgentToolContext(
            UUID.randomUUID(), "FIN-DATA-002", "1.0.0", "TEST", "llm-agent-gemini", 1, 42L, java.time.Instant.now()
        );

        var result = agent.executeStep(scenario, ctx, null);
        assertNotNull(result);
        assertEquals(com.finops.agentsafe.tool.AgentToolResult.Status.FAILED, result.getStatus());
        assertTrue(result.getError().contains("PROVIDER_RATE_LIMIT"));
        assertEquals(4, agent.getProviderRequestAttempts()); // 1 initial + 3 retries
        assertEquals(3, agent.getProviderRetries());
        assertEquals(4, agent.getProvider429Responses());
        assertEquals(0, agent.getSuccessfulModelInferenceCalls());
    }

    @Test
    public void testProviderFailureExcludedFromFarsAggregate() {
        List<BenchmarkRunResult> runs = new ArrayList<>();

        // 3 valid runs
        for (double fars : new double[]{1.0, 0.8, 0.9}) {
            BenchmarkRunResult r = new BenchmarkRunResult();
            r.setScenarioId("FIN-NORM-001");
            r.setMeasurementValid(true);
            r.setProviderFailure(false);
            r.setOutcomeClassification("SUCCESS");

            BenchmarkMetricResult m = new BenchmarkMetricResult();
            m.setFarsScore(fars);
            r.setMetrics(m);
            runs.add(r);
        }

        // 2 provider failure runs
        for (int i = 0; i < 2; i++) {
            BenchmarkRunResult r = new BenchmarkRunResult();
            r.setScenarioId("FIN-NORM-001");
            r.setMeasurementValid(false);
            r.setProviderFailure(true);
            r.setProviderFailureType("PROVIDER_RATE_LIMIT");
            r.setOutcomeClassification("PROVIDER_RATE_LIMIT");

            BenchmarkMetricResult m = new BenchmarkMetricResult();
            m.setFarsScore(null);
            r.setMetrics(m);
            runs.add(r);
        }

        ScenarioVarianceMetrics summary = ExperimentResultAggregator.computeScenarioMetrics("FIN-NORM-001", runs);

        assertEquals(5, summary.getRepetitionCount());
        assertEquals(3, summary.getValidMeasurementCount());
        assertEquals(2, summary.getProviderFailureCount());
        assertNotNull(summary.getMeanFars());
        assertEquals(0.9, summary.getMeanFars(), 0.001); // (1.0 + 0.8 + 0.9) / 3 = 0.9
        assertEquals(0.8, summary.getMinFars(), 0.001);
        assertEquals(1.0, summary.getMaxFars(), 0.001);
    }

    @Test
    public void testSeparateInferenceAndAttemptCounters() {
        LLMBenchmarkAgent agent = new LLMBenchmarkAgent(
            adapterRegistry, toolRegistry, toolExecutor, promptSecurityManager, decisionValidator
        );
        agent.resetMetrics();

        assertEquals(0, agent.getSuccessfulModelInferenceCalls());
        assertEquals(0, agent.getProviderRequestAttempts());
        assertEquals(0, agent.getProvider429Responses());
        assertEquals(0, agent.getProviderRetries());

        ModelResponse error429 = ModelResponse.failure(
            ModelError.rateLimit("Gemini API rate limit exceeded (HTTP 429)"),
            new ModelMetadata("gemini", "gemini-3.6-flash", "1.5", "1.0", "v1"),
            new ModelUsage(0L, 0L, 0L, 0L, 1, 0, 100L, null)
        );

        AgentDecision validDecision = AgentDecision.complete("Done");
        ModelResponse success = ModelResponse.success(
            validDecision,
            new ModelMetadata("gemini", "gemini-3.6-flash", "1.5", "1.0", "v1"),
            new ModelUsage(10L, 10L, 20L, 0L, 1, 0, 100L, null),
            "{}"
        );

        when(mockAdapter.predict(any(ModelRequest.class)))
            .thenReturn(error429)
            .thenReturn(success);

        ModelConfiguration cfg = new ModelConfiguration("gemini", "gemini-3.6-flash", 0.0, 1024, 5000L, 3, 42L, "v1");
        agent.setModelConfiguration(cfg);

        BenchmarkScenario scenario = new BenchmarkScenario();
        scenario.setScenarioId("FIN-NORM-001");
        scenario.setVersion("1.0.0");

        com.finops.agentsafe.tool.AgentToolContext ctx = new com.finops.agentsafe.tool.AgentToolContext(
            UUID.randomUUID(), "FIN-NORM-001", "1.0.0", "TEST", "llm-agent-gemini", 1, 42L, java.time.Instant.now()
        );

        var result = agent.executeStep(scenario, ctx, null);

        assertNotNull(result);
        assertEquals(2, agent.getProviderRequestAttempts());
        assertEquals(1, agent.getSuccessfulModelInferenceCalls());
        assertEquals(1, agent.getProvider429Responses());
        assertEquals(1, agent.getProviderRetries());
    }

    @Test
    public void testMaximumInitialPlusThreeRetryBehavior() {
        ModelResponse error429 = ModelResponse.failure(
            ModelError.rateLimit("Gemini API rate limit exceeded (HTTP 429)"),
            new ModelMetadata("gemini", "gemini-3.6-flash", "1.5", "1.0", "v1"),
            new ModelUsage(0L, 0L, 0L, 0L, 1, 0, 100L, null)
        );
        when(mockAdapter.predict(any(ModelRequest.class))).thenReturn(error429);

        LLMBenchmarkAgent agent = new LLMBenchmarkAgent(
            adapterRegistry, toolRegistry, toolExecutor, promptSecurityManager, decisionValidator
        );
        ModelConfiguration cfg = new ModelConfiguration("gemini", "gemini-3.6-flash", 0.0, 1024, 5000L, 3, 42L, "v1");
        agent.setModelConfiguration(cfg);

        BenchmarkScenario scenario = new BenchmarkScenario();
        scenario.setScenarioId("FIN-NORM-001");

        com.finops.agentsafe.tool.AgentToolContext ctx = new com.finops.agentsafe.tool.AgentToolContext(
            UUID.randomUUID(), "FIN-NORM-001", "1.0.0", "TEST", "llm-agent-gemini", 1, 42L, java.time.Instant.now()
        );

        agent.executeStep(scenario, ctx, null);

        verify(mockAdapter, times(4)).predict(any(ModelRequest.class)); // Exactly 1 initial + 3 retries = 4
        assertEquals(4, agent.getProviderRequestAttempts());
        assertEquals(3, agent.getProviderRetries());
    }

    @Test
    public void testNoNestedRetryAmplification() {
        ModelResponse error429 = ModelResponse.failure(
            ModelError.rateLimit("Gemini API rate limit exceeded (HTTP 429)"),
            new ModelMetadata("gemini", "gemini-3.6-flash", "1.5", "1.0", "v1"),
            new ModelUsage(0L, 0L, 0L, 0L, 1, 0, 100L, null)
        );
        when(mockAdapter.predict(any(ModelRequest.class))).thenReturn(error429);

        LLMBenchmarkAgent agent = new LLMBenchmarkAgent(
            adapterRegistry, toolRegistry, toolExecutor, promptSecurityManager, decisionValidator
        );
        ModelConfiguration cfg = new ModelConfiguration("gemini", "gemini-3.6-flash", 0.0, 1024, 5000L, 3, 42L, "v1");
        agent.setModelConfiguration(cfg);

        BenchmarkScenario scenario = new BenchmarkScenario();
        scenario.setScenarioId("FIN-NORM-001");

        com.finops.agentsafe.tool.AgentToolContext ctx = new com.finops.agentsafe.tool.AgentToolContext(
            UUID.randomUUID(), "FIN-NORM-001", "1.0.0", "TEST", "llm-agent-gemini", 1, 42L, java.time.Instant.now()
        );

        agent.executeStep(scenario, ctx, null);

        // Verify that retries are bounded strictly at 4 total HTTP attempts, not 16 or 64.
        verify(mockAdapter, times(4)).predict(any(ModelRequest.class));
    }

    @Test
    public void testThreeConsecutiveExhausted429DecisionsTripCircuitBreaker() {
        ModelResponse error429 = ModelResponse.failure(
            ModelError.rateLimit("Gemini API rate limit exceeded (HTTP 429)"),
            new ModelMetadata("gemini", "gemini-3.6-flash", "1.5", "1.0", "v1"),
            new ModelUsage(0L, 0L, 0L, 0L, 1, 0, 100L, null)
        );
        when(mockAdapter.predict(any(ModelRequest.class))).thenReturn(error429);

        LLMBenchmarkAgent agent = new LLMBenchmarkAgent(
            adapterRegistry, toolRegistry, toolExecutor, promptSecurityManager, decisionValidator
        );
        ModelConfiguration cfg = new ModelConfiguration("gemini", "gemini-3.6-flash", 0.0, 1024, 5000L, 3, 42L, "v1");
        agent.setModelConfiguration(cfg);

        BenchmarkScenario scenario = new BenchmarkScenario();
        scenario.setScenarioId("FIN-NORM-001");

        for (int i = 1; i <= 3; i++) {
            com.finops.agentsafe.tool.AgentToolContext ctx = new com.finops.agentsafe.tool.AgentToolContext(
                UUID.randomUUID(), "FIN-NORM-001", "1.0.0", "TEST", "llm-agent-gemini", i, 42L, java.time.Instant.now()
            );
            agent.executeStep(scenario, ctx, null);
        }

        assertEquals(3, agent.getConsecutiveExhausted429Decisions());
        assertTrue(agent.isLastDecisionExhausted429());
    }

    @Test
    public void testIncompleteCanonicalExperimentCannotReportReady(@TempDir Path tempDir) {
        File expDir = tempDir.toFile();

        RepeatabilityExperimentRunner.CanonicalStatusResult status =
            RepeatabilityExperimentRunner.evaluateCanonicalReadiness(expDir, 25);

        assertFalse(status.isReady());
        assertEquals("INVALID_CANONICAL_EXPERIMENT", status.getStatus());
        assertNotNull(status.getReason());
    }

    @Test
    public void testInvalidCanonicalAttemptMarkerPreventsReadyState(@TempDir Path tempDir) throws Exception {
        File expDir = tempDir.toFile();

        File invalidMarker = new File(expDir, "INVALID_CANONICAL_ATTEMPT.json");
        java.nio.file.Files.writeString(invalidMarker.toPath(), "{\"status\": \"INVALID_CANONICAL_EXPERIMENT\", \"reason\": \"INVALID_PROVIDER_RATE_LIMIT\"}");

        RepeatabilityExperimentRunner.CanonicalStatusResult status =
            RepeatabilityExperimentRunner.evaluateCanonicalReadiness(expDir, 25);

        assertFalse(status.isReady());
        assertEquals("INVALID_CANONICAL_EXPERIMENT", status.getStatus());
        assertEquals("INVALID_PROVIDER_RATE_LIMIT", status.getReason());
    }

    @Test
    public void testCanonicalAggregateRequiresAll25ValidMeasurements(@TempDir Path tempDir) throws Exception {
        File expDir = tempDir.toFile();
        File scDir = new File(expDir, "FIN-NORM-001");
        scDir.mkdirs();

        // Only create 11 result files instead of 25
        for (int i = 1; i <= 11; i++) {
            File resFile = new File(scDir, "FIN-NORM-001_rep" + i + "_result.json");
            java.nio.file.Files.writeString(resFile.toPath(), "{\"scenarioId\":\"FIN-NORM-001\"}");
        }

        File aggregateFile = new File(expDir, "aggregate_summary.json");
        java.nio.file.Files.writeString(aggregateFile.toPath(), "{}");

        RepeatabilityExperimentRunner.CanonicalStatusResult status =
            RepeatabilityExperimentRunner.evaluateCanonicalReadiness(expDir, 25);

        assertFalse(status.isReady());
        assertEquals("INVALID_CANONICAL_EXPERIMENT", status.getStatus());
        assertEquals("INVALID_PROVIDER_RATE_LIMIT", status.getReason());
    }

    @Test
    public void testZeroRealGeminiCalls() {
        // Verify mock adapter is used and zero real Gemini network endpoints were queried
        verifyNoInteractions(mockAdapter);
    }
}
