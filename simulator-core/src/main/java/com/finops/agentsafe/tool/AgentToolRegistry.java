package com.finops.agentsafe.tool;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry containing all available AgentTools.
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    public AgentToolRegistry(List<AgentTool> registeredTools) {
        if (registeredTools != null) {
            for (AgentTool tool : registeredTools) {
                registerTool(tool);
            }
        }
    }

    public void registerTool(AgentTool tool) {
        tools.put(tool.getToolName().toUpperCase(), tool);
    }

    public Optional<AgentTool> getTool(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(tools.get(name.toUpperCase()));
    }

    public Set<String> getAvailableToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    public Collection<AgentTool> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }
}
