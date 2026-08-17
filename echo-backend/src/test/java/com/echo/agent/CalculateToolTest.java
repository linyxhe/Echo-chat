package com.echo.agent;

import com.echo.agent.tools.CalculateTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculateToolTest {
    private final CalculateTool tool = new CalculateTool();
    private final AgentExecutionContext context = new AgentExecutionContext(1L, 2L, 3L, "s", List.of());

    @Test
    void evaluatesOnlyDeterministicArithmetic() {
        ToolResult result = tool.execute(context, Map.of("expression", "(120 * 0.8) + 5%"));
        assertTrue(result.success());
        assertTrue(result.modelContent().contains("96.05"));
    }

    @Test
    void rejectsCodeAndDivisionByZero() {
        assertFalse(tool.execute(context, Map.of("expression", "Runtime.getRuntime()")).success());
        assertFalse(tool.execute(context, Map.of("expression", "1/0")).success());
    }
}
