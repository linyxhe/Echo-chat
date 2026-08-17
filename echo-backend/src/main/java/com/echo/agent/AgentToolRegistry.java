package com.echo.agent;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit allow-list. Adding a Spring bean alone never grants a model a new capability. */
@Component
public class AgentToolRegistry {
    private final Map<String, AgentTool> tools;

    public AgentToolRegistry(List<AgentTool> discoveredTools) {
        Map<String, AgentTool> entries = new LinkedHashMap<>();
        for (AgentTool tool : discoveredTools) {
            if (tool == null || (tool.riskLevel() != ToolRiskLevel.READ_ONLY && tool.riskLevel() != ToolRiskLevel.SENSITIVE_READ
                    && tool.riskLevel() != ToolRiskLevel.WRITE_CONFIRM && tool.riskLevel() != ToolRiskLevel.EXTERNAL_CONFIRM)
                    || entries.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalStateException("Invalid or duplicate Agent tool registration");
            }
        }
        this.tools = Map.copyOf(entries);
    }

    public Collection<AgentTool> allTools() {
        return tools.values();
    }

    public Collection<AgentTool> enabledTools(Collection<String> names) {
        if (names == null || names.isEmpty()) return List.of();
        return names.stream().map(tools::get).filter(java.util.Objects::nonNull).toList();
    }

    public AgentTool find(String name) {
        return name == null ? null : tools.get(name);
    }
}
