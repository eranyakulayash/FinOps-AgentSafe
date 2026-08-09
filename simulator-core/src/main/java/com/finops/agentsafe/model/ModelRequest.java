package com.finops.agentsafe.model;

import com.finops.agentsafe.tool.AgentTool;
import com.finops.agentsafe.tool.AgentToolResult;

import java.util.Collections;
import java.util.List;

/**
 * Provider-neutral request payload.
 * Strictly separates trusted system prompt from untrusted scenario metadata.
 */
public class ModelRequest {

    private String systemInstruction; // TRUSTED
    private String untrustedScenarioData; // UNTRUSTED
    private List<AgentTool> availableTools; // Permitted tools for scenario
    private List<AgentToolResult> previousStepResults;
    private ModelConfiguration configuration;
    private int stepNumber;

    public ModelRequest() {
        this.availableTools = Collections.emptyList();
        this.previousStepResults = Collections.emptyList();
        this.configuration = new ModelConfiguration();
    }

    public ModelRequest(String systemInstruction, String untrustedScenarioData, List<AgentTool> availableTools, List<AgentToolResult> previousStepResults, ModelConfiguration configuration, int stepNumber) {
        this.systemInstruction = systemInstruction;
        this.untrustedScenarioData = untrustedScenarioData;
        this.availableTools = availableTools != null ? availableTools : Collections.emptyList();
        this.previousStepResults = previousStepResults != null ? previousStepResults : Collections.emptyList();
        this.configuration = configuration != null ? configuration : new ModelConfiguration();
        this.stepNumber = stepNumber;
    }

    public String getSystemInstruction() { return systemInstruction; }
    public void setSystemInstruction(String systemInstruction) { this.systemInstruction = systemInstruction; }

    public String getUntrustedScenarioData() { return untrustedScenarioData; }
    public void setUntrustedScenarioData(String untrustedScenarioData) { this.untrustedScenarioData = untrustedScenarioData; }

    public List<AgentTool> getAvailableTools() { return availableTools; }
    public void setAvailableTools(List<AgentTool> availableTools) { this.availableTools = availableTools != null ? availableTools : Collections.emptyList(); }

    public List<AgentToolResult> getPreviousStepResults() { return previousStepResults; }
    public void setPreviousStepResults(List<AgentToolResult> previousStepResults) { this.previousStepResults = previousStepResults != null ? previousStepResults : Collections.emptyList(); }

    public ModelConfiguration getConfiguration() { return configuration; }
    public void setConfiguration(ModelConfiguration configuration) { this.configuration = configuration; }

    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }
}
