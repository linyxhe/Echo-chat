package com.echo.agent;

import com.echo.mapper.AgentRunMapper;
import com.echo.mapper.AgentToolCallMapper;
import com.echo.service.AgentAuditService;
import com.echo.service.AgentToolGrantService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AgentOrchestratorTest {

    @Test
    void executesOnlyRegisteredReadOnlyToolAndReturnsFinalAnswer() {
        AtomicInteger executions = new AtomicInteger();
        AgentTool tool = new TestTool("current_time", executions);
        AgentOrchestrator orchestrator = orchestrator(tool);
        StreamingChatModel model = new ScriptedModel(
                AiMessage.from(ToolExecutionRequest.builder().id("call-1").name("current_time").arguments("{}").build()),
                AiMessage.from("当前时间已由工具查询。"));

        AgentOrchestrator.AgentPreparation result = orchestrator.prepare(model,
                List.of(UserMessage.from("现在几点")), context(), ignored -> { }, () -> false);

        assertTrue(result.handled());
        assertTrue(result.toolsUsed());
        assertEquals("当前时间已由工具查询。", result.answer());
        assertEquals(1, executions.get());
    }

    @Test
    void rejectsUnknownToolInsteadOfExecutingAnything() {
        AtomicInteger executions = new AtomicInteger();
        AgentOrchestrator orchestrator = orchestrator(new TestTool("current_time", executions));
        StreamingChatModel model = new ScriptedModel(
                AiMessage.from(ToolExecutionRequest.builder().id("call-x").name("delete_all_messages")
                        .arguments("{\"userId\":999}").build()),
                AiMessage.from("该请求不能执行。"));

        AgentOrchestrator.AgentPreparation result = orchestrator.prepare(model,
                List.of(UserMessage.from("删除全部消息")), context(), ignored -> { }, () -> false);

        assertTrue(result.handled());
        assertTrue(result.toolsUsed());
        assertEquals("该请求不能执行。", result.answer());
        assertEquals(0, executions.get());
    }

    @Test
    void stopsAfterWriteConfirmationProposalWithoutRunningAnotherModelStep() {
        AtomicInteger executions = new AtomicInteger();
        AgentTool tool = new TestConfirmationTool(executions);
        AgentOrchestrator orchestrator = orchestrator(tool);
        StreamingChatModel model = new ScriptedModel(
                AiMessage.from(ToolExecutionRequest.builder().id("call-confirm").name("memory_propose")
                        .arguments("{\"content\":\"prefer concise replies\"}").build()));

        AgentOrchestrator.AgentPreparation result = orchestrator.prepare(model,
                List.of(UserMessage.from("记住我喜欢简洁回答")), context(), ignored -> { }, () -> false);

        assertTrue(result.handled());
        assertTrue(result.toolsUsed());
        assertEquals(1, executions.get());
        assertEquals("MEMORY", result.confirmation().actionType());
    }

    private AgentOrchestrator orchestrator(AgentTool tool) {
        AgentAuditService audit = new AgentAuditService(mock(AgentRunMapper.class), mock(AgentToolCallMapper.class));
        AgentToolGrantService grants = mock(AgentToolGrantService.class);
        when(grants.enabledTools(any())).thenReturn(Set.of(tool.name()));
        return new AgentOrchestrator(new AgentToolRegistry(List.of(tool)), audit, grants, true, 3, 4, 5_000L);
    }

    private AgentExecutionContext context() {
        return new AgentExecutionContext(7L, 8L, 9L, "stream-1", List.of("study"));
    }

    private static final class TestTool implements AgentTool {
        private final String name;
        private final AtomicInteger executions;

        private TestTool(String name, AtomicInteger executions) {
            this.name = name;
            this.executions = executions;
        }

        @Override public String name() { return name; }
        @Override public ToolRiskLevel riskLevel() { return ToolRiskLevel.READ_ONLY; }
        @Override public ToolSpecification specification() {
            return ToolSpecification.builder().name(name).description("test")
                    .parameters(JsonObjectSchema.builder().additionalProperties(false).build()).build();
        }
        @Override public ToolResult execute(AgentExecutionContext context, Map<String, Object> arguments) {
            assertTrue(arguments.isEmpty());
            executions.incrementAndGet();
            return ToolResult.success("time", "已查询当前时间");
        }
    }

    private static final class TestConfirmationTool implements AgentTool {
        private final AtomicInteger executions;
        private TestConfirmationTool(AtomicInteger executions) { this.executions = executions; }
        @Override public String name() { return "memory_propose"; }
        @Override public ToolRiskLevel riskLevel() { return ToolRiskLevel.WRITE_CONFIRM; }
        @Override public ToolSpecification specification() {
            return ToolSpecification.builder().name(name()).description("test")
                    .parameters(JsonObjectSchema.builder().addStringProperty("content", "text")
                            .required("content").additionalProperties(false).build()).build();
        }
        @Override public ToolResult execute(AgentExecutionContext context, Map<String, Object> arguments) {
            executions.incrementAndGet();
            return ToolResult.confirmation("pending", "已生成待确认记忆",
                    new AgentConfirmationPayload("token", "MEMORY", "保存记忆", "prefer concise replies",
                            LocalDateTime.now().plusMinutes(15), context.botUserId()));
        }
    }

    private static final class ScriptedModel implements StreamingChatModel {
        private final AiMessage[] messages;
        private int index;

        private ScriptedModel(AiMessage... messages) {
            this.messages = messages;
        }

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            assertFalse(request.toolSpecifications().isEmpty());
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(messages[index++]).build());
        }
    }
}
