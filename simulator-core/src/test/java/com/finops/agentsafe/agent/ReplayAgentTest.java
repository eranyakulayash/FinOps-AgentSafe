package com.finops.agentsafe.agent;

import com.finops.agentsafe.agent.replay.AgentDecisionTrace;
import com.finops.agentsafe.agent.replay.ReplayAgent;
import com.finops.agentsafe.model.AgentDecision;
import com.finops.agentsafe.policy.AgentToolPolicyEngine;
import com.finops.agentsafe.repository.HumanApprovalRequestRepository;
import com.finops.agentsafe.scenario.BenchmarkScenario;
import com.finops.agentsafe.service.AuditService;
import com.finops.agentsafe.tool.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReplayAgentTest {

    @Test
    @DisplayName("ReplayAgent replays pre-recorded decision trace without live model calls")
    void testDecisionReplay() {
        AgentDecision d1 = AgentDecision.toolCall("READ_TRANSACTION", Map.of("transactionId", "TX-REPLAY-1"), "Replay step 1");
        AgentDecision d2 = AgentDecision.toolCall("RECONCILE_TRANSACTION", Map.of("transactionId", "TX-REPLAY-1"), "Replay step 2");

        AgentDecisionTrace trace = new AgentDecisionTrace("FIN-REPLAY-001", "mock-agent", List.of(d1, d2));

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

        AgentToolRegistry registry = new AgentToolRegistry(List.of(dummyRead, dummyRecon));
        HumanApprovalRequestRepository mockRepo = org.mockito.Mockito.mock(HumanApprovalRequestRepository.class);
        AuditService mockAudit = org.mockito.Mockito.mock(AuditService.class);
        AgentToolExecutor executor = new AgentToolExecutor(registry, new AgentToolPolicyEngine(mockRepo, mockAudit));

        ReplayAgent replayAgent = new ReplayAgent(executor, trace);

        assertEquals("replay-agent-mock-agent", replayAgent.getAgentId());

        BenchmarkScenario sc = new BenchmarkScenario();
        sc.setScenarioId("FIN-REPLAY-001");
        sc.setPermittedTools(Set.of("READ_TRANSACTION", "RECONCILE_TRANSACTION"));

        AgentToolContext ctx1 = new AgentToolContext(null, "FIN-REPLAY-001", "1.0", "AGENT", "test", 1, 42L, null);
        AgentToolResult res1 = replayAgent.executeStep(sc, ctx1, null);
        assertEquals("READ_TRANSACTION", res1.getToolName());

        AgentToolContext ctx2 = new AgentToolContext(null, "FIN-REPLAY-001", "1.0", "AGENT", "test", 2, 42L, null);
        AgentToolResult res2 = replayAgent.executeStep(sc, ctx2, res1);
        assertEquals("RECONCILE_TRANSACTION", res2.getToolName());
    }
}
