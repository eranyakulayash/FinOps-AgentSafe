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
class NegativeSafetyTest {

    @Mock
    private HumanApprovalRequestRepository approvalRepository;

    @Mock
    private AuditService auditService;

    private AgentToolPolicyEngine policyEngine;
    private AgentToolExecutor toolExecutor;
    private AgentToolRegistry registry;

    @BeforeEach
    void setUp() {
        policyEngine = new AgentToolPolicyEngine(approvalRepository, auditService);
        registry = new AgentToolRegistry(java.util.List.of());
        toolExecutor = new AgentToolExecutor(registry, policyEngine);
    }

    @Test
    @DisplayName("Negative Safety: Unregistered tool execution is rejected")
    void testUnregisteredToolRejected() {
        AgentToolContext ctx = new AgentToolContext(UUID.randomUUID(), "SCENARIO-NEG", "1.0", "AGENT", "agent-1", 1, 42L, java.time.Instant.now());
        AgentToolRequest req = new AgentToolRequest("EXECUTE_MALICIOUS_SQL", Map.of(), ctx);

        AgentToolResult res = toolExecutor.executeTool(req, Set.of("READ_TRANSACTION"), 10);
        assertEquals(AgentToolResult.Status.NON_RETRYABLE_FAILURE, res.getStatus());
        assertTrue(res.getError().contains("UNREGISTERED_TOOL"));
    }

    @Test
    @DisplayName("Negative Safety: Agent cannot execute tool unpermitted by scenario")
    void testUnpermittedScenarioToolBlocked() {
        AgentTool readTool = new ReadTransactionTool(null);
        registry.registerTool(readTool);

        AgentToolContext ctx = new AgentToolContext(UUID.randomUUID(), "SCENARIO-NEG", "1.0", "AGENT", "agent-1", 1, 42L, java.time.Instant.now());
        AgentToolRequest req = new AgentToolRequest("READ_TRANSACTION", Map.of("transactionId", "TX-1"), ctx);

        AgentToolResult res = toolExecutor.executeTool(req, Set.of("SEARCH_TRANSACTIONS"), 10);
        assertEquals(AgentToolResult.Status.DENIED, res.getStatus());
        assertTrue(res.getError().contains("POLICY_DENIAL"));
    }

    @Test
    @DisplayName("Negative Safety: Exceeding maximum steps returns STEP_LIMIT_EXCEEDED")
    void testExceedingMaxStepsBlocked() {
        AgentTool readTool = new ReadTransactionTool(null);
        registry.registerTool(readTool);

        AgentToolContext ctx = new AgentToolContext(UUID.randomUUID(), "SCENARIO-NEG", "1.0", "AGENT", "agent-1", 6, 42L, java.time.Instant.now());
        AgentToolRequest req = new AgentToolRequest("READ_TRANSACTION", Map.of("transactionId", "TX-1"), ctx);

        AgentToolResult res = toolExecutor.executeTool(req, Set.of("READ_TRANSACTION"), 5);
        assertEquals(AgentToolResult.Status.STEP_LIMIT_EXCEEDED, res.getStatus());
    }

    @Test
    @DisplayName("Negative Safety: Direct self-approval tool is prohibited")
    void testSelfApprovalToolProhibited() {
        AgentTool selfApproveTool = new AgentTool() {
            @Override public String getToolName() { return "APPROVE_HUMAN_REQUEST"; }
            @Override public String getDescription() { return "Prohibited direct approval tool"; }
            @Override public ActionRiskLevel getRiskLevel() { return ActionRiskLevel.HIGH_RISK_WRITE; }
            @Override public Map<String, String> getInputSchema() { return Map.of(); }
            @Override public Map<String, String> getOutputSchema() { return Map.of(); }
            @Override public Set<String> getRequiredPermissions() { return Set.of(); }
            @Override public boolean isRequiresApproval() { return false; }
            @Override public boolean isIdempotent() { return false; }
            @Override public AgentToolResult execute(AgentToolRequest request) { return null; }
        };
        registry.registerTool(selfApproveTool);

        AgentToolContext ctx = new AgentToolContext(UUID.randomUUID(), "SCENARIO-NEG", "1.0", "AGENT", "agent-1", 1, 42L, java.time.Instant.now());
        AgentToolRequest req = new AgentToolRequest("APPROVE_HUMAN_REQUEST", Map.of(), ctx);

        AgentToolResult res = toolExecutor.executeTool(req, Set.of("APPROVE_HUMAN_REQUEST"), 10);
        assertEquals(AgentToolResult.Status.DENIED, res.getStatus());
        assertTrue(res.getError().contains("POLICY_DENIAL"));
    }
}
