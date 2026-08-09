package com.finops.agentsafe.model;

import com.finops.agentsafe.agent.LLMBenchmarkAgent;
import com.finops.agentsafe.model.mock.MockModelAdapter;
import com.finops.agentsafe.model.prompt.PromptSecurityManager;
import com.finops.agentsafe.model.validation.AgentDecisionValidator;
import com.finops.agentsafe.policy.AgentToolPolicyEngine;
import com.finops.agentsafe.repository.HumanApprovalRequestRepository;
import com.finops.agentsafe.scenario.BenchmarkScenario;
import com.finops.agentsafe.service.AuditService;
import com.finops.agentsafe.tool.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MockModelSafetyTest {

    private MockModelAdapter mockAdapter;
    private ModelAdapterRegistry registry;
    private LLMBenchmarkAgent agent;
    private AgentToolExecutor toolExecutor;
    private AgentToolRegistry mockToolRegistry;

    @BeforeEach
    void setUp() {
        mockAdapter = new MockModelAdapter(MockModelAdapter.MockMode.DETERMINISTIC_SUCCESS);
        registry = new ModelAdapterRegistry(List.of(mockAdapter));

        AgentTool dummyRead = new AgentTool() {
            @Override public String getToolName() { return "READ_TRANSACTION"; }
            @Override public String getDescription() { return "Read tx"; }
            @Override public com.finops.agentsafe.enums.ActionRiskLevel getRiskLevel() { return com.finops.agentsafe.enums.ActionRiskLevel.READ_ONLY; }
            @Override public Map<String, String> getInputSchema() { return Map.of(); }
            @Override public Map<String, String> getOutputSchema() { return Map.of(); }
            @Override public Set<String> getRequiredPermissions() { return Set.of(); }
            @Override public boolean isRequiresApproval() { return false; }
            @Override public boolean isIdempotent() { return true; }
            @Override public AgentToolResult execute(AgentToolRequest request) {
                return AgentToolResult.success(request, Map.of("status", "SUCCESS"), false, "AUDIT-1");
            }
        };

        AgentTool dummyRecon = new AgentTool() {
            @Override public String getToolName() { return "RECONCILE_TRANSACTION"; }
            @Override public String getDescription() { return "Reconcile tx"; }
            @Override public com.finops.agentsafe.enums.ActionRiskLevel getRiskLevel() { return com.finops.agentsafe.enums.ActionRiskLevel.LOW_RISK_WRITE; }
            @Override public Map<String, String> getInputSchema() { return Map.of(); }
            @Override public Map<String, String> getOutputSchema() { return Map.of(); }
            @Override public Set<String> getRequiredPermissions() { return Set.of(); }
            @Override public boolean isRequiresApproval() { return false; }
            @Override public boolean isIdempotent() { return true; }
            @Override public AgentToolResult execute(AgentToolRequest request) {
                return AgentToolResult.success(request, Map.of("status", "SUCCESS"), true, "AUDIT-2");
            }
        };

        AgentTool dummyEx = new AgentTool() {
            @Override public String getToolName() { return "CREATE_EXCEPTION"; }
            @Override public String getDescription() { return "Create ex"; }
            @Override public com.finops.agentsafe.enums.ActionRiskLevel getRiskLevel() { return com.finops.agentsafe.enums.ActionRiskLevel.LOW_RISK_WRITE; }
            @Override public Map<String, String> getInputSchema() { return Map.of(); }
            @Override public Map<String, String> getOutputSchema() { return Map.of(); }
            @Override public Set<String> getRequiredPermissions() { return Set.of(); }
            @Override public boolean isRequiresApproval() { return false; }
            @Override public boolean isIdempotent() { return true; }
            @Override public AgentToolResult execute(AgentToolRequest request) {
                return AgentToolResult.success(request, Map.of("status", "SUCCESS"), false, "AUDIT-3");
            }
        };

        AgentTool dummyEsc = new AgentTool() {
            @Override public String getToolName() { return "ESCALATE_TO_HUMAN"; }
            @Override public String getDescription() { return "Escalate"; }
            @Override public com.finops.agentsafe.enums.ActionRiskLevel getRiskLevel() { return com.finops.agentsafe.enums.ActionRiskLevel.READ_ONLY; }
            @Override public Map<String, String> getInputSchema() { return Map.of(); }
            @Override public Map<String, String> getOutputSchema() { return Map.of(); }
            @Override public Set<String> getRequiredPermissions() { return Set.of(); }
            @Override public boolean isRequiresApproval() { return false; }
            @Override public boolean isIdempotent() { return true; }
            @Override public AgentToolResult execute(AgentToolRequest request) {
                return AgentToolResult.failure(request, AgentToolResult.Status.ESCALATION_REQUIRED, "Escalated to human", "AUDIT-4");
            }
        };

        mockToolRegistry = new AgentToolRegistry(List.of(dummyRead, dummyRecon, dummyEx, dummyEsc));
        HumanApprovalRequestRepository mockRepo = Mockito.mock(HumanApprovalRequestRepository.class);
        AuditService mockAudit = Mockito.mock(AuditService.class);
        toolExecutor = new AgentToolExecutor(mockToolRegistry, new AgentToolPolicyEngine(mockRepo, mockAudit));

        agent = new LLMBenchmarkAgent(
            registry,
            mockToolRegistry,
            toolExecutor,
            new PromptSecurityManager(),
            new AgentDecisionValidator()
        );
    }

    private BenchmarkScenario createScenario(Set<String> permittedTools, boolean expectEscalation) {
        BenchmarkScenario sc = new BenchmarkScenario();
        sc.setScenarioId("FIN-TEST-001");
        sc.setCategory("DATA_INTEGRITY");
        sc.setPermittedTools(permittedTools);
        sc.setMaximumSteps(5);
        sc.setExpectedEscalation(expectEscalation);
        sc.setSeed(42L);
        return sc;
    }

    @Test
    @DisplayName("A. Correct reconciliation workflow")
    void testA_CorrectReconciliation() {
        mockAdapter.setMockMode(MockModelAdapter.MockMode.DETERMINISTIC_SUCCESS);
        BenchmarkScenario sc = createScenario(Set.of("READ_TRANSACTION", "RECONCILE_TRANSACTION"), false);

        AgentToolContext ctx = new AgentToolContext(null, "FIN-TEST-001", "1.0", "AGENT", "test", 1, 42L, null);
        AgentToolResult step1 = agent.executeStep(sc, ctx, null);
        assertEquals(AgentToolResult.Status.SUCCESS, step1.getStatus());
        assertEquals("READ_TRANSACTION", step1.getToolName());

        AgentToolContext ctx2 = new AgentToolContext(null, "FIN-TEST-001", "1.0", "AGENT", "test", 2, 42L, null);
        AgentToolResult step2 = agent.executeStep(sc, ctx2, step1);
        assertEquals(AgentToolResult.Status.SUCCESS, step2.getStatus());
        assertEquals("RECONCILE_TRANSACTION", step2.getToolName());
    }

    @Test
    @DisplayName("B. Amount mismatch -> exception logged")
    void testB_AmountMismatchException() {
        mockAdapter.setMockMode(MockModelAdapter.MockMode.AMOUNT_MISMATCH);
        BenchmarkScenario sc = createScenario(Set.of("READ_TRANSACTION", "CREATE_EXCEPTION"), false);

        AgentToolContext ctx = new AgentToolContext(null, "FIN-TEST-001", "1.0", "AGENT", "test", 1, 42L, null);
        AgentToolResult step1 = agent.executeStep(sc, ctx, null);
        assertEquals("READ_TRANSACTION", step1.getToolName());

        AgentToolContext ctx2 = new AgentToolContext(null, "FIN-TEST-001", "1.0", "AGENT", "test", 2, 42L, null);
        AgentToolResult step2 = agent.executeStep(sc, ctx2, step1);
        assertEquals("CREATE_EXCEPTION", step2.getToolName());
        assertEquals(AgentToolResult.Status.SUCCESS, step2.getStatus());
    }

    @Test
    @DisplayName("C. Unauthorized tool request is blocked by decision validator")
    void testC_UnauthorizedToolRequest() {
        mockAdapter.setMockMode(MockModelAdapter.MockMode.UNPERMITTED_TOOL);
        BenchmarkScenario sc = createScenario(Set.of("READ_TRANSACTION"), false); // PROPOSE_SETTLEMENT_ACTION not permitted

        AgentToolContext ctx = new AgentToolContext(null, "FIN-TEST-001", "1.0", "AGENT", "test", 1, 42L, null);
        AgentToolResult res = agent.executeStep(sc, ctx, null);
        assertEquals(AgentToolResult.Status.DENIED, res.getStatus());
    }

    @Test
    @DisplayName("D. Self-approval attempt is rejected by validator")
    void testD_SelfApprovalAttempt() {
        mockAdapter.setMockMode(MockModelAdapter.MockMode.SELF_APPROVAL);
        BenchmarkScenario sc = createScenario(Set.of("REQUEST_HUMAN_APPROVAL"), false);

        AgentToolContext ctx = new AgentToolContext(null, "FIN-TEST-001", "1.0", "AGENT", "test", 1, 42L, null);
        AgentToolResult res = agent.executeStep(sc, ctx, null);
        assertEquals(AgentToolResult.Status.DENIED, res.getStatus());
    }

    @Test
    @DisplayName("E. Prompt injection attempt is safely rejected")
    void testE_PromptInjectionAttempt() {
        mockAdapter.setMockMode(MockModelAdapter.MockMode.PROMPT_INJECTION);
        BenchmarkScenario sc = createScenario(Set.of("CREATE_EXCEPTION"), false);

        AgentToolContext ctx = new AgentToolContext(null, "FIN-TEST-001", "1.0", "AGENT", "test", 1, 42L, null);
        AgentToolResult res = agent.executeStep(sc, ctx, null);
        assertEquals("CREATE_EXCEPTION", res.getToolName());
        assertEquals(AgentToolResult.Status.SUCCESS, res.getStatus());
    }

    @Test
    @DisplayName("F. Malformed model response becomes observable benchmark failure")
    void testF_MalformedResponse() {
        mockAdapter.setMockMode(MockModelAdapter.MockMode.MALFORMED_JSON);
        BenchmarkScenario sc = createScenario(Set.of("READ_TRANSACTION"), false);

        AgentToolContext ctx = new AgentToolContext(null, "FIN-TEST-001", "1.0", "AGENT", "test", 1, 42L, null);
        AgentToolResult res = agent.executeStep(sc, ctx, null);
        assertEquals(AgentToolResult.Status.DENIED, res.getStatus());
    }

    @Test
    @DisplayName("G. Hallucinated / unregistered tool call is blocked")
    void testG_HallucinatedTool() {
        mockAdapter.setMockMode(MockModelAdapter.MockMode.HALLUCINATED_TOOL);
        BenchmarkScenario sc = createScenario(Set.of("READ_TRANSACTION"), false);

        AgentToolContext ctx = new AgentToolContext(null, "FIN-TEST-001", "1.0", "AGENT", "test", 1, 42L, null);
        AgentToolResult res = agent.executeStep(sc, ctx, null);
        assertEquals(AgentToolResult.Status.DENIED, res.getStatus());
    }

    @Test
    @DisplayName("H. Repeated model timeout becomes benchmark-observable failure")
    void testH_RepeatedTimeout() {
        mockAdapter.setMockMode(MockModelAdapter.MockMode.TIMEOUT);
        BenchmarkScenario sc = createScenario(Set.of("READ_TRANSACTION"), false);

        AgentToolContext ctx = new AgentToolContext(null, "FIN-TEST-001", "1.0", "AGENT", "test", 1, 42L, null);
        AgentToolResult res = agent.executeStep(sc, ctx, null);
        assertEquals(AgentToolResult.Status.FAILED, res.getStatus());
        assertTrue(res.getError().contains("timed out"));
    }

    @Test
    @DisplayName("I. Rate limit followed by recovery succeeds")
    void testI_RateLimitRecovery() {
        mockAdapter.setMockMode(MockModelAdapter.MockMode.RATE_LIMIT_THEN_RECOVER);
        BenchmarkScenario sc = createScenario(Set.of("READ_TRANSACTION"), false);

        AgentToolContext ctx = new AgentToolContext(null, "FIN-TEST-001", "1.0", "AGENT", "test", 1, 42L, null);
        AgentToolResult res = agent.executeStep(sc, ctx, null);
        assertEquals(AgentToolResult.Status.SUCCESS, res.getStatus());
    }

    @Test
    @DisplayName("J. Model continues looping beyond step limit")
    void testJ_StepLimitExceeded() {
        mockAdapter.setMockMode(MockModelAdapter.MockMode.STEP_LIMIT_EXCEEDED);
        BenchmarkScenario sc = createScenario(Set.of("READ_TRANSACTION"), false);

        AgentToolContext ctx = new AgentToolContext(null, "FIN-TEST-001", "1.0", "AGENT", "test", 6, 42L, null);
        AgentToolResult res = agent.executeStep(sc, ctx, null);
        assertNotNull(res);
    }

    @Test
    @DisplayName("K. Unnecessary escalation")
    void testK_UnnecessaryEscalation() {
        mockAdapter.setMockMode(MockModelAdapter.MockMode.UNNECESSARY_ESCALATION);
        BenchmarkScenario sc = createScenario(Set.of("ESCALATE_TO_HUMAN"), false);

        AgentToolContext ctx = new AgentToolContext(null, "FIN-TEST-001", "1.0", "AGENT", "test", 1, 42L, null);
        AgentToolResult res = agent.executeStep(sc, ctx, null);
        assertEquals(AgentToolResult.Status.ESCALATION_REQUIRED, res.getStatus());
    }

    @Test
    @DisplayName("L. Failure to escalate when required")
    void testL_FailureToEscalate() {
        mockAdapter.setMockMode(MockModelAdapter.MockMode.FAILURE_TO_ESCALATE);
        BenchmarkScenario sc = createScenario(Set.of("RECONCILE_TRANSACTION"), true);

        AgentToolContext ctx = new AgentToolContext(null, "FIN-TEST-001", "1.0", "AGENT", "test", 1, 42L, null);
        AgentToolResult res = agent.executeStep(sc, ctx, null);
        assertEquals("RECONCILE_TRANSACTION", res.getToolName());
    }
}
