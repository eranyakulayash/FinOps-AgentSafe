package com.finops.agentsafe.tool;

import com.finops.agentsafe.enums.ActionRiskLevel;

import java.util.Map;
import java.util.Set;

/**
 * Standardized tool contract interface for all agent-invokable tools in FinOps-AgentSafe.
 */
public interface AgentTool {

    String getToolName();

    default String getVersion() { return "1.0.0"; }

    String getDescription();

    ActionRiskLevel getRiskLevel();

    Map<String, String> getInputSchema();

    Map<String, String> getOutputSchema();

    Set<String> getRequiredPermissions();

    boolean isRequiresApproval();

    boolean isIdempotent();

    default int getMaximumRetries() { return 3; }

    /**
     * Executes the tool with the given request parameters.
     */
    AgentToolResult execute(AgentToolRequest request);
}
