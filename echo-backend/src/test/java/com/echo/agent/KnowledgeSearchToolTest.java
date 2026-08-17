package com.echo.agent;

import com.echo.agent.tools.KnowledgeSearchTool;
import com.echo.service.KbService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeSearchToolTest {

    @Test
    void injectsScopeFromExecutionContextInsteadOfModelArguments() {
        KbService kb = mock(KbService.class);
        when(kb.isEnabled()).thenReturn(true);
        when(kb.searchHits(eq("私有资料是什么"), eq(List.of("study")), eq(7L), eq(8L), eq(2), eq(0.55D)))
                .thenReturn(List.of(new KbService.SearchHit(1L, 2L, "资料正文", "notes.md", "study", 0.8D, true)));
        KnowledgeSearchTool tool = new KnowledgeSearchTool(kb);
        AgentExecutionContext context = new AgentExecutionContext(7L, 8L, 9L, "s", List.of("study"));

        ToolResult result = tool.execute(context, Map.of("query", "私有资料是什么", "limit", 2));

        assertTrue(result.success());
        assertTrue(result.modelContent().contains("notes.md"));
        verify(kb).searchHits("私有资料是什么", List.of("study"), 7L, 8L, 2, 0.55D);
    }

    @Test
    void rejectsForgedScopeArgumentBeforeSearch() {
        KbService kb = mock(KbService.class);
        when(kb.isEnabled()).thenReturn(true);
        KnowledgeSearchTool tool = new KnowledgeSearchTool(kb);
        AgentExecutionContext context = new AgentExecutionContext(7L, 8L, 9L, "s", List.of("study"));

        ToolResult result = tool.execute(context, Map.of("query", "资料", "assistantId", 999L));

        assertFalse(result.success());
        verify(kb, never()).searchHits(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyDouble());
    }
}
