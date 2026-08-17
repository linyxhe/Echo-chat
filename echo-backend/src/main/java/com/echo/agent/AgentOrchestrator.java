package com.echo.agent;

import com.alibaba.fastjson2.JSON;
import com.echo.service.AgentAuditService;
import com.echo.service.AgentToolGrantService;
import com.echo.service.KbService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A deliberately small, server-controlled agent loop. It only exposes the registry's read-only
 * tools, validates every model argument again, and keeps a hard execution budget.
 */
@Service
public class AgentOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    private static final String AGENT_INSTRUCTION = """
            你正在作为 Echo Chat 的受控助手工作。可用工具由服务端严格授权。
            只有在确实需要当前时间时调用 current_time；需要精确算术时调用 calculate；需要核对当前助手知识库资料时调用 knowledge_search。
            conversation_search 和 file_catalog_search 属于敏感读取，只能在用户明确要求查找自己的聊天记录或附件时调用；不要用它们猜测用户信息。
            不要为了闲聊或一般性建议调用知识库；没有工具依据时不能虚构“已查询”。
            工具参数中绝不能填写用户 ID、助手 ID、文件 ID、路径、权限或访问凭据。
            在收到工具结果后，仅根据结果和对话上下文给出简洁的最终回答，并说明资料不足之处。
            """;

    private final AgentToolRegistry toolRegistry;
    private final AgentAuditService auditService;
    private final AgentToolGrantService grantService;
    private final boolean enabled;
    private final int maxSteps;
    private final int maxToolCalls;
    private final long planningTimeoutMs;

    public AgentOrchestrator(AgentToolRegistry toolRegistry,
                             AgentAuditService auditService,
                             AgentToolGrantService grantService,
                             @Value("${app.ai.agent.enabled:true}") boolean enabled,
                             @Value("${app.ai.agent.max-steps:3}") int maxSteps,
                             @Value("${app.ai.agent.max-tool-calls:4}") int maxToolCalls,
                             @Value("${app.ai.agent.planning-timeout-ms:45000}") long planningTimeoutMs) {
        this.toolRegistry = toolRegistry;
        this.auditService = auditService;
        this.grantService = grantService;
        this.enabled = enabled;
        this.maxSteps = Math.max(1, Math.min(maxSteps, 5));
        this.maxToolCalls = Math.max(1, Math.min(maxToolCalls, 8));
        this.planningTimeoutMs = Math.max(5_000L, Math.min(planningTimeoutMs, 60_000L));
    }

    public boolean isEnabled() {
        return enabled && !toolRegistry.allTools().isEmpty();
    }

    public AgentPreparation prepare(StreamingChatModel model, List<ChatMessage> initialMessages,
                                    AgentExecutionContext context, Consumer<String> progress,
                                    BooleanSupplier cancelled) {
        if (!isEnabled()) return AgentPreparation.notHandled();
        List<ChatMessage> messages = new ArrayList<>(initialMessages);
        messages.add(SystemMessage.from(AGENT_INSTRUCTION));
        messages.add(SystemMessage.from("只有在用户明确要求你记住信息时才可调用 memory_propose；它只会生成待确认记忆，确认前绝不保存。"
                + "只有在用户明确要求起草一段消息/文案时才可调用 draft_message；它只会生成待确认草稿，绝不选择收件人、更不会发送。"
                + "只有在用户明确询问某个中国城市天气时才可调用 weather_propose；服务端会查询固定和风天气服务，再把结果交给你整理回答，不得使用设备定位。"
                + "只有在用户明确要求联网搜索、查询最新公开信息或网页来源时才可调用 web_search_propose；服务端会搜索公开网页，再把有限结果交给你整理回答，绝不能搜索或发送私人信息。"
                + "只有在用户明确要求创建一次站内提醒且时间足够明确时才可调用 reminder_propose；它只能生成待确认项，提醒时间必须为服务器时区的 YYYY-MM-DD HH:mm，不能创建循环提醒或自动发送聊天消息。"
                + "调用 memory_propose、draft_message 或 reminder_propose 后，必须停止后续工具调用，等待用户在界面确认或取消。天气和联网搜索属于自动只读查询，必须在收到工具结果后继续处理并给出自然语言回答。"));
        Collection<AgentTool> allowedTools = toolRegistry.enabledTools(grantService.enabledTools(context.assistantId()));
        if (allowedTools.isEmpty()) return AgentPreparation.notHandled();
        List<ToolSpecification> specifications = allowedTools.stream().map(AgentTool::specification).toList();
        Long runId = auditService.start(context);
        List<KbService.SearchHit> sourceHits = new ArrayList<>();
        int calls = 0;

        try {
            for (int step = 1; step <= maxSteps; step++) {
                if (cancelled.getAsBoolean()) {
                    auditService.finish(runId, "CANCELLED", step - 1, calls, "用户取消");
                    return AgentPreparation.cancelledPreparation();
                }
                ChatResponse response = requestPlan(model, messages, specifications);
                AiMessage aiMessage = response == null ? null : response.aiMessage();
                if (aiMessage == null) {
                    auditService.finish(runId, "FAILED", step, calls, "模型未返回消息");
                    return AgentPreparation.notHandled();
                }
                if (!aiMessage.hasToolExecutionRequests()) {
                    String answer = aiMessage.text();
                    if (!StringUtils.hasText(answer)) answer = "AI 没有返回可用内容。";
                    auditService.finish(runId, "COMPLETED", step, calls, null);
                    return AgentPreparation.completed(answer, sourceHits, calls > 0);
                }

                messages.add(aiMessage);
                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    if (cancelled.getAsBoolean()) {
                        auditService.finish(runId, "CANCELLED", step, calls, "用户取消");
                        return AgentPreparation.cancelledPreparation();
                    }
                    calls++;
                    if (calls > maxToolCalls) {
                        auditService.finish(runId, "FAILED", step, calls - 1, "工具调用次数超出限制");
                        return AgentPreparation.completed("这次请求需要的操作超过安全执行上限，请缩小问题范围后重试。", sourceHits, true);
                    }

                    AgentTool tool = toolRegistry.find(request.name());
                    if (tool != null && !allowedTools.contains(tool)) tool = null;
                    Map<String, Object> arguments = parseArguments(request.arguments());
                    long startedAt = System.currentTimeMillis();
                    ToolResult result;
                    if (tool == null) {
                        result = ToolResult.failure("TOOL_NOT_ALLOWED", "该工具未被当前助手授权，无法执行。");
                    } else {
                        try {
                            result = tool.execute(context, arguments);
                        } catch (Exception e) {
                            log.warn("Agent tool {} failed for user {}", tool.name(), context.userId(), e);
                            result = ToolResult.failure("TOOL_EXECUTION_FAILED", "工具执行失败，不能将失败结果当作事实使用。");
                        }
                    }
                    auditService.recordTool(runId, calls, tool, arguments, result, System.currentTimeMillis() - startedAt);
                    if (result.knowledgeHits() != null) sourceHits.addAll(result.knowledgeHits());
                    if (progress != null && StringUtils.hasText(result.displaySummary())) progress.accept(result.displaySummary());
                    if (result.success() && result.confirmation() != null) {
                        auditService.finish(runId, "WAITING_CONFIRMATION", step, calls, null);
                        return AgentPreparation.waitingConfirmation(result.confirmation(), sourceHits);
                    }
                    messages.add(ToolExecutionResultMessage.from(request, limit(result.modelContent(), 8 * 1024)));
                }
            }
            auditService.finish(runId, "FAILED", maxSteps, calls, "工具规划轮数超出限制");
            return AgentPreparation.completed("这次请求需要多轮工具操作，已因安全上限停止。请把问题拆分后重试。", sourceHits, true);
        } catch (Exception e) {
            log.warn("Agent planning failed for user {}, falling back to normal chat", context.userId(), e);
            auditService.finish(runId, "FAILED", 0, calls, "模型规划失败");
            return AgentPreparation.notHandled();
        }
    }

    private ChatResponse requestPlan(StreamingChatModel model, List<ChatMessage> messages,
                                     List<ToolSpecification> specifications) throws Exception {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        model.chat(ChatRequest.builder()
                        .messages(messages)
                        .toolSpecifications(specifications)
                        .toolChoice(ToolChoice.AUTO)
                        .build(),
                new StreamingChatResponseHandler() {
                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        future.complete(response);
                    }

                    @Override
                    public void onError(Throwable error) {
                        future.completeExceptionally(error);
                    }
                });
        return future.get(planningTimeoutMs, TimeUnit.MILLISECONDS);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String raw) {
        if (!StringUtils.hasText(raw)) return Map.of();
        try {
            Object parsed = JSON.parse(raw);
            if (!(parsed instanceof Map<?, ?> map)) return Map.of("_invalid", true);
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) return Map.of("_invalid", true);
                values.put(key, entry.getValue());
            }
            return values;
        } catch (Exception ignored) {
            return Map.of("_invalid", true);
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null) return "工具没有返回内容。";
        return value.substring(0, Math.min(value.length(), maxLength));
    }

    public record AgentPreparation(boolean handled, boolean cancelled, String answer,
                                   List<KbService.SearchHit> sourceHits, boolean toolsUsed,
                                   AgentConfirmationPayload confirmation) {
        static AgentPreparation notHandled() { return new AgentPreparation(false, false, null, List.of(), false, null); }
        static AgentPreparation cancelledPreparation() { return new AgentPreparation(true, true, null, List.of(), false, null); }
        static AgentPreparation completed(String answer, List<KbService.SearchHit> hits, boolean toolsUsed) {
            return new AgentPreparation(true, false, answer, List.copyOf(hits), toolsUsed, null);
        }
        static AgentPreparation waitingConfirmation(AgentConfirmationPayload confirmation, List<KbService.SearchHit> hits) {
            return new AgentPreparation(true, false, "我已生成一项待确认操作，请检查下方内容。确认前不会保存或发送任何信息。",
                    List.copyOf(hits), true, confirmation);
        }
    }
}
