package com.echo.agent;

import com.echo.service.KbService;

import java.util.List;

/** A bounded, model-safe tool result. Raw credentials and database entities never leave a tool. */
public record ToolResult(
        boolean success,
        String modelContent,
        String displaySummary,
        String errorCode,
        List<KbService.SearchHit> knowledgeHits,
        AgentConfirmationPayload confirmation
) {
    public static ToolResult success(String modelContent, String displaySummary) {
        return new ToolResult(true, modelContent, displaySummary, null, List.of(), null);
    }

    public static ToolResult knowledge(String modelContent, String displaySummary, List<KbService.SearchHit> hits) {
        return new ToolResult(true, modelContent, displaySummary, null, hits == null ? List.of() : List.copyOf(hits), null);
    }

    public static ToolResult failure(String code, String message) {
        return new ToolResult(false, message, message, code, List.of(), null);
    }

    public static ToolResult confirmation(String modelContent, String displaySummary,
                                          AgentConfirmationPayload confirmation) {
        return new ToolResult(true, modelContent, displaySummary, null, List.of(), confirmation);
    }
}
