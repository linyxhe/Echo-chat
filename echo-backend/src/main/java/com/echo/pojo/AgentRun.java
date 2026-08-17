package com.echo.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("agent_run")
public class AgentRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long assistantId;
    private Long botUserId;
    private String streamId;
    private String status;
    private Integer stepCount;
    private Integer toolCallCount;
    private String failureReason;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAssistantId() { return assistantId; }
    public void setAssistantId(Long assistantId) { this.assistantId = assistantId; }
    public Long getBotUserId() { return botUserId; }
    public void setBotUserId(Long botUserId) { this.botUserId = botUserId; }
    public String getStreamId() { return streamId; }
    public void setStreamId(String streamId) { this.streamId = streamId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getStepCount() { return stepCount; }
    public void setStepCount(Integer stepCount) { this.stepCount = stepCount; }
    public Integer getToolCallCount() { return toolCallCount; }
    public void setToolCallCount(Integer toolCallCount) { this.toolCallCount = toolCallCount; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
