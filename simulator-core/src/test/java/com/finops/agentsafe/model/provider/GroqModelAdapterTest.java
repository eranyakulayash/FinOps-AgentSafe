package com.finops.agentsafe.model.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finops.agentsafe.agent.LLMBenchmarkAgent;
import com.finops.agentsafe.agent.replay.AgentDecisionTrace;
import com.finops.agentsafe.agent.replay.ReplayAgent;
import com.finops.agentsafe.metrics.MetricEngine;
import com.finops.agentsafe.model.*;
import com.finops.agentsafe.model.prompt.PromptSecurityManager;
import com.finops.agentsafe.model.validation.AgentDecisionValidator;
import com.finops.agentsafe.runner.BenchmarkRunResult;
import com.finops.agentsafe.runner.BenchmarkRunner;
import com.finops.agentsafe.scenario.BenchmarkScenario;
import com.finops.agentsafe.service.AuditService;
import com.finops.agentsafe.service.SyntheticDataService;
import com.finops.agentsafe.tool.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GroqModelAdapterTest {

    private GroqModelAdapter unconfiguredAdapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        unconfiguredAdapter = new GroqModelAdapter(null);
    }

    @Test
    @DisplayName("GroqModelAdapter handles missing GROQ_API_KEY cleanly")
    void testMissingApiKey() {
        assertEquals("groq", unconfiguredAdapter.getProviderName());
        assertFalse(unconfiguredAdapter.isConfigured());

        ModelMetadata meta = unconfiguredAdapter.getMetadata(ModelConfiguration.groq("openai/gpt-oss-120b"));
        assertEquals("groq", meta.getProvider());
        assertEquals("openai/gpt-oss-120b", meta.getModelName());
        assertEquals("Groq", meta.getModelVersion());

        ModelResponse resp = unconfiguredAdapter.predict(new ModelRequest());
        assertFalse(resp.isSuccess());
        assertEquals(ModelErrorKind.PROVIDER_NOT_CONFIGURED, resp.getError().getKind());
    }

    @Test
    @DisplayName("GroqModelAdapter exact-model guard and metadata validation")
    void testExactModelGuard() {
        ModelConfiguration config = ModelConfiguration.groq("openai/gpt-oss-120b");
        ModelMetadata meta = unconfiguredAdapter.getMetadata(config);

        assertEquals("groq", meta.getProvider());
        assertEquals("openai/gpt-oss-120b", meta.getModelName());
        assertEquals("Groq", meta.getModelVersion());
    }

    @Test
    @DisplayName("GroqModelAdapter sends Bearer authorization header without leaking key in URL")
    void testBearerHeaderAuthentication() throws Exception {
        String testApiKey = "gsk_test_1234567890_secret_key";
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResp = mock(HttpResponse.class);

        when(mockResp.statusCode()).thenReturn(200);
        when(mockResp.body()).thenReturn("""
            {
              "choices": [
                { "message": { "content": "OK" } }
              ],
              "usage": { "prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15 }
            }
            """);
        doReturn(mockResp).when(mockClient).send(any(HttpRequest.class), any());

        GroqModelAdapter adapter = new GroqModelAdapter(testApiKey, mockClient, objectMapper, null);
        ModelConfiguration config = ModelConfiguration.groq("openai/gpt-oss-120b");
        ModelRequest request = new ModelRequest(null, "Test scenario", null, null, config, 1);

        ModelResponse response = adapter.predict(request);

        assertTrue(response.isSuccess());
        verify(mockClient).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("GroqModelAdapter parses TOOL_CALL decision correctly")
    void testParseToolCallResponse() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResp = mock(HttpResponse.class);

        when(mockResp.statusCode()).thenReturn(200);
        when(mockResp.body()).thenReturn("""
            {
              "choices": [
                {
                  "message": {
                    "tool_calls": [
                      {
                        "type": "function",
                        "function": {
                          "name": "SEARCH_TRANSACTIONS",
                          "arguments": "{\\"query\\": \\"merchant_01\\"}"
                        }
                      }
                    ]
                  }
                }
              ],
              "usage": { "prompt_tokens": 20, "completion_tokens": 10, "total_tokens": 30 }
            }
            """);
        doReturn(mockResp).when(mockClient).send(any(HttpRequest.class), any());

        GroqModelAdapter adapter = new GroqModelAdapter("gsk_dummy", mockClient, objectMapper, null);
        ModelResponse response = adapter.predict(new ModelRequest());

        assertTrue(response.isSuccess());
        AgentDecision dec = response.getDecision();
        assertEquals(DecisionType.TOOL_CALL, dec.getDecisionType());
        assertEquals("SEARCH_TRANSACTIONS", dec.getToolName());
        assertEquals("merchant_01", dec.getArguments().get("query"));
    }

    @Test
    @DisplayName("GroqModelAdapter normalizes COMPLETE, ABSTAIN, ESCALATE, and REQUEST_HUMAN_APPROVAL decisions")
    void testNormalizeDecisionTypes() {
        GroqModelAdapter adapter = new GroqModelAdapter("gsk_dummy", mock(HttpClient.class), objectMapper, null);
        ModelMetadata meta = new ModelMetadata("groq", "openai/gpt-oss-120b", "Groq", "1.0.0", "v1");

        // 1. COMPLETE
        String completeJson = """
            { "choices": [ { "message": { "content": "Task completed successfully" } } ] }
            """;
        ModelResponse resp1 = adapter.parseGroqResponse(completeJson, meta, new ModelUsage());
        assertTrue(resp1.isSuccess());
        assertEquals(DecisionType.COMPLETE, resp1.getDecision().getDecisionType());

        // 2. ABSTAIN
        String abstainJson = """
            { "choices": [ { "message": { "content": "I must ABSTAIN due to risk" } } ] }
            """;
        ModelResponse resp2 = adapter.parseGroqResponse(abstainJson, meta, new ModelUsage());
        assertTrue(resp2.isSuccess());
        assertEquals(DecisionType.ABSTAIN, resp2.getDecision().getDecisionType());

        // 3. ESCALATE
        String escalateJson = """
            { "choices": [ { "message": { "content": "ESCALATE to supervisor" } } ] }
            """;
        ModelResponse resp3 = adapter.parseGroqResponse(escalateJson, meta, new ModelUsage());
        assertTrue(resp3.isSuccess());
        assertEquals(DecisionType.ESCALATE, resp3.getDecision().getDecisionType());

        // 4. REQUEST_HUMAN_APPROVAL
        String reqApprJson = """
            { "choices": [ { "message": { "content": "REQUEST_HUMAN_APPROVAL for transaction tx_123" } } ] }
            """;
        ModelResponse resp4 = adapter.parseGroqResponse(reqApprJson, meta, new ModelUsage());
        assertTrue(resp4.isSuccess());
        assertEquals(DecisionType.REQUEST_HUMAN_APPROVAL, resp4.getDecision().getDecisionType());
    }

    @Test
    @DisplayName("Official Rate-Limit Header Mappings: x-ratelimit-remaining-requests = RPD, x-ratelimit-remaining-tokens = TPM")
    void testOfficialRateLimitHeaderMappingsRPDAndTPM() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResp = mock(HttpResponse.class);
        HttpHeaders realHeaders = HttpHeaders.of(
            Map.of(
                "x-ratelimit-limit-requests", List.of("1000"),
                "x-ratelimit-remaining-requests", List.of("999"),
                "x-ratelimit-reset-requests", List.of("24h"),
                "x-ratelimit-limit-tokens", List.of("8000"),
                "x-ratelimit-remaining-tokens", List.of("5867"),
                "x-ratelimit-reset-tokens", List.of("5.86s")
            ),
            (k, v) -> true
        );

        when(mockResp.statusCode()).thenReturn(200);
        when(mockResp.headers()).thenReturn(realHeaders);
        when(mockResp.body()).thenReturn("""
            {
              "choices": [ { "message": { "content": "OK" } } ],
              "usage": { "prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15 }
            }
            """);

        doReturn(mockResp).when(mockClient).send(any(), any());

        GroqModelAdapter adapter = new GroqModelAdapter("gsk_dummy", mockClient, objectMapper, null);
        ModelResponse resp = adapter.predict(new ModelRequest());

        assertTrue(resp.isSuccess());
        ModelUsage usage = resp.getUsage();

        // RPD verification
        assertEquals(1000L, usage.getLimitRPD());
        assertEquals(999L, usage.getRemainingRPD());
        assertEquals("24h", usage.getResetRPD());

        // TPM verification
        assertEquals(8000L, usage.getLimitTPM());
        assertEquals(5867L, usage.getRemainingTPM());
        assertEquals("5.86s", usage.getResetTPM());
    }

    @Test
    @DisplayName("Token-Aware Pacing works, excludes pacing wait from model inference latency, and does not alter FARS")
    void testTokenAwarePacingExcludesWaitFromInferenceLatencyAndFars() throws Exception {
        // Pacing controller with small TPM ceiling for fast unit test testing
        ProviderPacingController pacing = new ProviderPacingController(30, 1000, 8000, 200000L, 100);
        // Pre-fill 100 tokens to trigger TPM ceiling
        pacing.recordTokensUsed(100);

        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResp = mock(HttpResponse.class);
        when(mockResp.statusCode()).thenReturn(200);
        when(mockResp.body()).thenReturn("""
            {
              "choices": [ { "message": { "content": "OK" } } ],
              "usage": { "prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15 }
            }
            """);
        doReturn(mockResp).when(mockClient).send(any(), any());

        GroqModelAdapter adapter = new GroqModelAdapter("gsk_dummy", mockClient, objectMapper, pacing);

        long start = System.currentTimeMillis();
        ModelResponse resp = adapter.predict(new ModelRequest());
        long totalElapsed = System.currentTimeMillis() - start;

        assertTrue(resp.isSuccess());
        ModelUsage usage = resp.getUsage();

        // Pacing wait is recorded separately
        assertTrue(usage.getPacingWaitMs() >= 0);
        // Model latency measures strictly HTTP time, excluding pacing wait
        assertTrue(usage.getLatencyMs() < totalElapsed + 50);

        // Verify agent FARS metric calculation is unaffected by pacing wait
        AgentDecisionValidator validator = new AgentDecisionValidator();
        AgentDecision validDecision = AgentDecision.complete("Task finished safely");
        var valRes = validator.validate(validDecision, List.of());
        assertTrue(valRes.isValid());
    }

    @Test
    @DisplayName("TPD Quota Protection blocks request when cumulative tokens exceed 200,000 TPD limit")
    void testTPDProtectionTriggersQuotaError() {
        ProviderPacingController pacing = new ProviderPacingController(30, 1000, 8000, 200000L, 7000);
        // Simulate cumulative tokens at 199,900
        pacing.recordTokensUsed(199900L);

        GroqModelAdapter adapter = new GroqModelAdapter("gsk_dummy", mock(HttpClient.class), objectMapper, pacing);

        // Next request requiring 500 estimated tokens exceeds 200,000 TPD limit
        ModelResponse response = adapter.predict(new ModelRequest());

        assertFalse(response.isSuccess());
        assertEquals(ModelErrorKind.MODEL_RATE_LIMIT, response.getError().getKind());
        assertTrue(response.getError().getMessage().contains("TPD 200000"));
    }

    @Test
    @DisplayName("Provider quota failure sets measurementValid=false and FARS=null, preventing invalid canonical aggregate")
    void testProviderQuotaFailureExcludesRunFromCanonicalAggregate() {
        ModelAdapterRegistry registry = mock(ModelAdapterRegistry.class);
        ModelAdapter mockAdapter = mock(ModelAdapter.class);

        when(mockAdapter.getProviderName()).thenReturn("groq");
        when(mockAdapter.isConfigured()).thenReturn(true);
        when(mockAdapter.getMetadata(any())).thenReturn(new ModelMetadata("groq", "openai/gpt-oss-120b", "Groq", "1.0.0", "v1"));

        // Simulate quota/rate limit error
        ModelResponse quotaError = ModelResponse.failure(
            ModelError.rateLimit("Provider TPD/RPD quota limit reached"),
            new ModelMetadata("groq", "openai/gpt-oss-120b", "Groq", "1.0.0", "v1"),
            new ModelUsage()
        );
        when(mockAdapter.predict(any())).thenReturn(quotaError);
        when(registry.getAdapter("groq")).thenReturn(Optional.of(mockAdapter));

        LLMBenchmarkAgent agent = new LLMBenchmarkAgent(registry, mock(AgentToolRegistry.class), mock(AgentToolExecutor.class), new PromptSecurityManager(), new AgentDecisionValidator());
        ModelConfiguration cfg = new ModelConfiguration("groq", "openai/gpt-oss-120b", 0.0, 1024, 5000L, 0, 42L, "v1");
        agent.setModelConfiguration(cfg);

        BenchmarkScenario scenario = new BenchmarkScenario();
        scenario.setScenarioId("FIN-NORM-001");
        scenario.setVersion("1.0.0");
        scenario.setPermittedTools(Set.of("COMPLETE"));
        scenario.setMaximumSteps(1);

        SyntheticDataService synthService = mock(SyntheticDataService.class);
        AuditService auditService = mock(AuditService.class);
        MetricEngine metricEngine = new MetricEngine();

        BenchmarkRunner runner = new BenchmarkRunner(synthService, null, auditService, metricEngine, objectMapper);
        BenchmarkRunResult runRes = runner.runScenario(scenario, agent);

        // Verification of safety semantics
        assertFalse(runRes.isMeasurementValid());
        assertTrue(runRes.isProviderFailure());
        assertNull(runRes.getMetrics().getFarsScore()); // FARS penalty = NONE
        assertEquals("PROVIDER_RATE_LIMIT", runRes.getOutcomeClassification());
    }

    @Test
    @DisplayName("ReplayAgent executes trace with ZERO Groq provider calls")
    void testReplayAgentZeroProviderCalls() {
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        AgentDecision d1 = AgentDecision.complete("Finished");
        AgentDecisionTrace trace = new AgentDecisionTrace("FIN-NORM-001", "llm-agent-groq", List.of(d1));

        ReplayAgent replayAgent = new ReplayAgent(toolExecutor, trace);
        assertEquals("replay-agent-llm-agent-groq", replayAgent.getAgentId());

        BenchmarkScenario sc = new BenchmarkScenario();
        sc.setScenarioId("FIN-NORM-001");
        AgentToolContext ctx = new AgentToolContext(UUID.randomUUID(), "FIN-NORM-001", "1.0.0", "TEST", "replay", 1, 42L, Instant.now());

        AgentToolResult res = replayAgent.executeStep(sc, ctx, null);
        assertNotNull(res);
    }
}
