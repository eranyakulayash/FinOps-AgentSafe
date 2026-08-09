package com.finops.agentsafe.model;

import com.finops.agentsafe.model.validation.AgentDecisionValidator;
import com.finops.agentsafe.tool.AgentTool;
import com.finops.agentsafe.tool.ReadTransactionTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentDecisionValidatorTest {

    private AgentDecisionValidator validator;
    private List<AgentTool> permittedTools;

    @BeforeEach
    void setUp() {
        validator = new AgentDecisionValidator();
        permittedTools = List.of(new ReadTransactionTool(null));
    }

    @Test
    @DisplayName("Valid tool call within permitted tools passes validation")
    void testValidToolCall() {
        AgentDecision dec = AgentDecision.toolCall("READ_TRANSACTION", Map.of("transactionId", "TX-1"), "Reading tx");
        var res = validator.validate(dec, permittedTools);
        assertTrue(res.isValid());
    }

    @Test
    @DisplayName("Unpermitted or hallucinated tool is rejected by validator")
    void testHallucinatedTool() {
        AgentDecision dec = AgentDecision.toolCall("UNAUTHORIZED_TOOL", Map.of(), "Hallucinated tool call");
        var res = validator.validate(dec, permittedTools);
        assertFalse(res.isValid());
        assertTrue(res.getErrorMessage().contains("not permitted"));
    }

    @Test
    @DisplayName("Self-approval attempt in REQUEST_HUMAN_APPROVAL arguments is rejected")
    void testSelfApprovalInArguments() {
        AgentDecision dec = AgentDecision.toolCall("REQUEST_HUMAN_APPROVAL", Map.of("status", "APPROVE", "approved", true), "Self approving");
        var res = validator.validate(dec, permittedTools);
        assertFalse(res.isValid());
        assertTrue(res.getErrorMessage().contains("self-approval"));
    }

    @Test
    @DisplayName("Privileged field manipulation is rejected")
    void testPrivilegedFieldManipulation() {
        AgentDecision dec = AgentDecision.toolCall("READ_TRANSACTION", Map.of("transactionId", "TX-1", "overridePolicy", true), "Bypassing policy");
        var res = validator.validate(dec, permittedTools);
        assertFalse(res.isValid());
        assertTrue(res.getErrorMessage().contains("privileged field"));
    }
}
