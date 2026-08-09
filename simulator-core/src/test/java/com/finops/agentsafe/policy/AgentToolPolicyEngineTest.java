package com.finops.agentsafe.policy;

import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.repository.HumanApprovalRequestRepository;
import com.finops.agentsafe.service.AuditService;
import com.finops.agentsafe.tool.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AgentToolPolicyEngineTest {

    @Mock
    private HumanApprovalRequestRepository approvalRepository;

    @Mock
    private AuditService auditService;

    private AgentToolPolicyEngine policyEngine;

    @BeforeEach
    void setUp() {
        policyEngine = new AgentToolPolicyEngine(approvalRepository, auditService);
    }

    @Test
    @DisplayName("Policy Engine returns STEP_LIMIT_EXCEEDED when step exceeds maximum")
    void testStepLimitExceeded() {
        AgentTool dummyTool = new ReadTransactionTool(null);
        AgentToolContext ctx = new AgentToolContext(UUID.randomUUID(), "SCENARIO-1", "1.0", "AGENT", "agent-1", 11, 42L, java.time.Instant.now());
        AgentToolRequest req = new AgentToolRequest("READ_TRANSACTION", Map.of(), ctx);

        PolicyDecision decision = policyEngine.evaluate(dummyTool, req, Set.of("READ_TRANSACTION"), 10);
        assertEquals(PolicyDecision.STEP_LIMIT_EXCEEDED, decision);
    }

    @Test
    @DisplayName("Policy Engine returns DENY when tool is not permitted in scenario")
    void testUnpermittedToolDenied() {
        AgentTool dummyTool = new ReadTransactionTool(null);
        AgentToolContext ctx = new AgentToolContext(UUID.randomUUID(), "SCENARIO-1", "1.0", "AGENT", "agent-1", 1, 42L, java.time.Instant.now());
        AgentToolRequest req = new AgentToolRequest("READ_TRANSACTION", Map.of(), ctx);

        PolicyDecision decision = policyEngine.evaluate(dummyTool, req, Set.of("SEARCH_TRANSACTIONS"), 10);
        assertEquals(PolicyDecision.DENY, decision);
    }

    @Test
    @DisplayName("Policy Engine DENIES direct approval attempts by agent actors")
    void testProhibitDirectAgentApproval() {
        AgentTool directApprovalTool = new AgentTool() {
            @Override public String getToolName() { return "APPROVE_HUMAN_REQUEST"; }
            @Override public String getDescription() { return "Direct approval"; }
            @Override public ActionRiskLevel getRiskLevel() { return ActionRiskLevel.HIGH_RISK_WRITE; }
            @Override public Map<String, String> getInputSchema() { return Map.of(); }
            @Override public Map<String, String> getOutputSchema() { return Map.of(); }
            @Override public Set<String> getRequiredPermissions() { return Set.of(); }
            @Override public boolean isRequiresApproval() { return false; }
            @Override public boolean isIdempotent() { return false; }
            @Override public AgentToolResult execute(AgentToolRequest request) { return null; }
        };

        AgentToolContext ctx = new AgentToolContext(UUID.randomUUID(), "SCENARIO-1", "1.0", "AGENT", "agent-1", 1, 42L, java.time.Instant.now());
        AgentToolRequest req = new AgentToolRequest("APPROVE_HUMAN_REQUEST", Map.of(), ctx);

        PolicyDecision decision = policyEngine.evaluate(directApprovalTool, req, Set.of("APPROVE_HUMAN_REQUEST"), 10);
        assertEquals(PolicyDecision.DENY, decision, "Agents must never be permitted to execute APPROVE_HUMAN_REQUEST");
    }
}
