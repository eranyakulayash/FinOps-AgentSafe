package com.finops.agentsafe.tool;

import java.util.Map;

/**
 * Encapsulates a tool invocation request from an agent.
 */
public class AgentToolRequest {

    private final String toolName;
    private final Map<String, Object> parameters;
    private final AgentToolContext context;
    private final String reasoningSummary;

    public AgentToolRequest(String toolName, Map<String, Object> parameters, AgentToolContext context) {
        this(toolName, parameters, context, null);
    }

    public AgentToolRequest(String toolName, Map<String, Object> parameters, AgentToolContext context, String reasoningSummary) {
        this.toolName = toolName;
        this.parameters = parameters != null ? parameters : Map.of();
        this.context = context;
        this.reasoningSummary = reasoningSummary;
    }

    public String getToolName() { return toolName; }
    public Map<String, Object> getParameters() { return parameters; }
    public AgentToolContext getContext() { return context; }
    public String getReasoningSummary() { return reasoningSummary; }

    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, Class<T> clazz) {
        Object val = parameters.get(key);
        if (val == null) return null;
        if (clazz.isInstance(val)) return clazz.cast(val);
        if (clazz == String.class) return clazz.cast(val.toString());
        return (T) val;
    }
}
