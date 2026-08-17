package com.echo.service;

import com.echo.agent.AgentExecutionContext;
import com.echo.agent.AgentTool;
import com.echo.agent.ToolResult;
import com.echo.mapper.AgentRunMapper;
import com.echo.mapper.AgentToolCallMapper;
import com.echo.pojo.AgentRun;
import com.echo.pojo.AgentToolCall;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/** Audit failures must never block a chat, but successful records are intentionally minimal. */
@Service
public class AgentAuditService {
    private final AgentRunMapper runMapper;
    private final AgentToolCallMapper toolCallMapper;

    public AgentAuditService(AgentRunMapper runMapper, AgentToolCallMapper toolCallMapper) {
        this.runMapper = runMapper;
        this.toolCallMapper = toolCallMapper;
    }

    public Long start(AgentExecutionContext context) {
        try {
            AgentRun run = new AgentRun();
            run.setUserId(context.userId());
            run.setAssistantId(context.assistantId());
            run.setBotUserId(context.botUserId());
            run.setStreamId(context.streamId());
            run.setStatus("PLANNING");
            run.setStepCount(0);
            run.setToolCallCount(0);
            run.setStartedAt(LocalDateTime.now());
            runMapper.insert(run);
            return run.getId();
        } catch (Exception ignored) {
            return null;
        }
    }

    public void recordTool(Long runId, int sequence, AgentTool tool, Map<String, Object> args,
                           ToolResult result, long durationMs) {
        if (runId == null) return;
        try {
            AgentToolCall call = new AgentToolCall();
            call.setRunId(runId);
            call.setSequenceNo(sequence);
            call.setToolName(tool == null ? "unknown" : tool.name());
            call.setRiskLevel(tool == null ? "UNKNOWN" : tool.riskLevel().name());
            call.setStatus(result != null && result.success() ? "SUCCESS" : "REJECTED");
            call.setArgumentsRedacted(redactArguments(args));
            call.setResultSummary(limit(result == null ? null : result.displaySummary(), 500));
            call.setDurationMs(Math.max(durationMs, 0));
            call.setErrorCode(result == null ? "TOOL_ERROR" : result.errorCode());
            call.setCreatedAt(LocalDateTime.now());
            toolCallMapper.insert(call);
        } catch (Exception ignored) {
            // Audit is deliberately non-blocking.
        }
    }

    public void finish(Long runId, String status, int steps, int calls, String reason) {
        if (runId == null) return;
        try {
            AgentRun run = new AgentRun();
            run.setId(runId);
            run.setStatus(status);
            run.setStepCount(steps);
            run.setToolCallCount(calls);
            run.setFailureReason(limit(reason, 500));
            run.setCompletedAt(LocalDateTime.now());
            runMapper.updateById(run);
        } catch (Exception ignored) {
            // Audit is deliberately non-blocking.
        }
    }

    private String redactArguments(Map<String, Object> args) {
        if (args == null || args.isEmpty()) return "{}";
        if (args.containsKey("query")) {
            Object query = args.get("query");
            return "{\"queryLength\":" + (query instanceof String text ? text.length() : 0) + ",\"limit\":" + args.get("limit") + "}";
        }
        return "{\"argumentCount\":" + args.size() + "}";
    }

    private String limit(String value, int length) {
        if (value == null) return null;
        return value.substring(0, Math.min(value.length(), length));
    }
}
