package com.echo.agent;

import java.util.List;

/** Server-derived identity and scope. Never populate this object from model arguments. */
public record AgentExecutionContext(
        Long userId,
        Long assistantId,
        Long botUserId,
        String streamId,
        List<String> knowledgeCategories
) {
    public AgentExecutionContext {
        knowledgeCategories = knowledgeCategories == null ? List.of() : List.copyOf(knowledgeCategories);
    }
}
