package com.echo.agent;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.Map;

public interface AgentTool {
    String name();

    ToolRiskLevel riskLevel();

    ToolSpecification specification();

    ToolResult execute(AgentExecutionContext context, Map<String, Object> arguments);
}
