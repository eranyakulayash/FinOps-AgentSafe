package com.finops.agentsafe.policy;

import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.repository.HumanApprovalRequestRepository;
import com.finops.agentsafe.service.AuditService;
import com.finops.agentsafe.tool.AgentTool;
import com.finops.agentsafe.tool.AgentToolContext;
import com.finops.agentsafe.tool.AgentToolRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PolicyEngineFailClosedTest {

    @Test
    @DisplayName("AgentToolPolicyEngine throws NullPointerException when mandatory security dependencies are null")
    void testMandatoryDependenciesRequired() {
        assertThrows(NullPointerException.class, () -> new AgentToolPolicyEngine(null, null));
        assertThrows(NullPointerException.class, () -> new AgentToolPolicyEngine(org.mockito.Mockito.mock(HumanApprovalRequestRepository.class), null));
        assertThrows(NullPointerException.class, () -> new AgentToolPolicyEngine(null, org.mockito.Mockito.mock(AuditService.class)));
    }

    @Test
    @DisplayName("AgentToolPolicyEngine fails closed (DENY) when approval repository throws database error")
    void testFailClosedOnRepositoryException() {
        HumanApprovalRequestRepository mockRepo = org.mockito.Mockito.mock(HumanApprovalRequestRepository.class);
        AuditService mockAudit = org.mockito.Mockito.mock(AuditService.class);

        org.mockito.Mockito.when(mockRepo.findFirstByRelatedTransactionIdAndRequestedActionAndStatus(
            org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(), org.mockito.Mockito.any()
        )).thenThrow(new RuntimeException("Database connection pool exhausted"));

        AgentToolPolicyEngine engine = new AgentToolPolicyEngine(mockRepo, mockAudit);

        AgentTool highRiskTool = new AgentTool() {
            @Override public String getToolName() { return "EXECUTE_REVERSAL"; }
            @Override public String getDescription() { return "Reversal tool"; }
            @Override public ActionRiskLevel getRiskLevel() { return ActionRiskLevel.HIGH_RISK_WRITE; }
            @Override public Map<String, String> getInputSchema() { return Map.of(); }
            @Override public Map<String, String> getOutputSchema() { return Map.of(); }
            @Override public Set<String> getRequiredPermissions() { return Set.of(); }
            @Override public boolean isRequiresApproval() { return true; }
            @Override public boolean isIdempotent() { return true; }
            @Override public com.finops.agentsafe.tool.AgentToolResult execute(AgentToolRequest request) { return null; }
        };

        AgentToolContext ctx = new AgentToolContext(null, "FIN-FAILCLOSED", "1.0", "AGENT", "test", 1, 42L, null);
        AgentToolRequest req = new AgentToolRequest("EXECUTE_REVERSAL", Map.of("transactionId", "TX-1"), ctx, "Reversal");

        PolicyDecision decision = engine.evaluate(highRiskTool, req, Set.of("EXECUTE_REVERSAL"), 5);

        assertEquals(PolicyDecision.DENY, decision, "Policy engine must fail CLOSED (DENY) on security infrastructure error");
    }
}
