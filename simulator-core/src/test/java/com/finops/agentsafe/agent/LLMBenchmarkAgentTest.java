package com.finops.agentsafe.agent;

import com.finops.agentsafe.model.ModelAdapterRegistry;
import com.finops.agentsafe.model.ModelConfiguration;
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

class LLMBenchmarkAgentTest {

    private LLMBenchmarkAgent agent;
    private MockModelAdapter mockAdapter;

    @BeforeEach
    void setUp() {
        mockAdapter = new MockModelAdapter(MockModelAdapter.MockMode.DETERMINISTIC_SUCCESS);
        ModelAdapterRegistry registry = new ModelAdapterRegistry(List.of(mockAdapter));

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
                return AgentToolResult.success(request, Map.of("status", "OK"), false, "AUDIT-1");
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
                return AgentToolResult.success(request, Map.of("status", "OK"), true, "AUDIT-2");
            }
        };

        AgentToolRegistry toolRegistry = new AgentToolRegistry(List.of(dummyRead, dummyRecon));
        HumanApprovalRequestRepository mockRepo = Mockito.mock(HumanApprovalRequestRepository.class);
        AuditService mockAudit = Mockito.mock(AuditService.class);
        AgentToolExecutor toolExecutor = new AgentToolExecutor(toolRegistry, new AgentToolPolicyEngine(mockRepo, mockAudit));

        agent = new LLMBenchmarkAgent(
            registry,
            toolRegistry,
            toolExecutor,
            new PromptSecurityManager(),
            new AgentDecisionValidator()
        );
    }

    @Test
    @DisplayName("LLMBenchmarkAgent executes step using mock adapter")
    void testExecuteStep() {
        BenchmarkScenario sc = new BenchmarkScenario();
        sc.setScenarioId("FIN-LLM-001");
        sc.setPermittedTools(Set.of("READ_TRANSACTION", "RECONCILE_TRANSACTION"));

        AgentToolContext ctx = new AgentToolContext(null, "FIN-LLM-001", "1.0", "AGENT", "test", 1, 42L, null);
        AgentToolResult res = agent.executeStep(sc, ctx, null);

        assertEquals("READ_TRANSACTION", res.getToolName());
        assertEquals(AgentToolResult.Status.SUCCESS, res.getStatus());
    }

    @Test
    @DisplayName("LLMBenchmarkAgent returns PROVIDER_NOT_CONFIGURED if unconfigured provider is selected")
    void testUnconfiguredProvider() {
        agent.setModelConfiguration(new ModelConfiguration("unregistered", "m1", 0.0, 1024, 1000L, 1, 1L, "v1"));
        BenchmarkScenario sc = new BenchmarkScenario();
        sc.setScenarioId("FIN-LLM-001");
        sc.setPermittedTools(Set.of("READ_TRANSACTION"));

        AgentToolContext ctx = new AgentToolContext(null, "FIN-LLM-001", "1.0", "AGENT", "test", 1, 42L, null);
        AgentToolResult res = agent.executeStep(sc, ctx, null);

        assertEquals(AgentToolResult.Status.DENIED, res.getStatus());
        assertTrue(res.getError().contains("PROVIDER_NOT_CONFIGURED"));
    }
}
