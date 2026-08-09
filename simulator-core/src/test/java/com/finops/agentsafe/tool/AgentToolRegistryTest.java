package com.finops.agentsafe.tool;

import com.finops.agentsafe.enums.ActionRiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AgentToolRegistryTest {

    @Test
    @DisplayName("AgentToolRegistry indexes tools by name (case-insensitive) and exposes available tool names")
    void testRegistryLookup() {
        AgentTool dummyTool = new AgentTool() {
            @Override public String getToolName() { return "READ_TRANSACTION"; }
            @Override public String getDescription() { return "Dummy"; }
            @Override public ActionRiskLevel getRiskLevel() { return ActionRiskLevel.READ_ONLY; }
            @Override public java.util.Map<String, String> getInputSchema() { return java.util.Map.of(); }
            @Override public java.util.Map<String, String> getOutputSchema() { return java.util.Map.of(); }
            @Override public java.util.Set<String> getRequiredPermissions() { return java.util.Set.of(); }
            @Override public boolean isRequiresApproval() { return false; }
            @Override public boolean isIdempotent() { return true; }
            @Override public AgentToolResult execute(AgentToolRequest request) { return null; }
        };

        AgentToolRegistry registry = new AgentToolRegistry(List.of(dummyTool));

        Optional<AgentTool> found = registry.getTool("read_transaction");
        assertTrue(found.isPresent());
        assertEquals("READ_TRANSACTION", found.get().getToolName());

        assertTrue(registry.getAvailableToolNames().contains("READ_TRANSACTION"));
    }
}
