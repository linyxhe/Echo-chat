package com.echo.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("agent_confirmation")
public class AgentConfirmation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String token;
    private Long userId;
    private Long assistantId;
    private Long botUserId;
    private String streamId;
    private String actionType;
    private String payload;
    private String summary;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAssistantId() { return assistantId; }
    public void setAssistantId(Long assistantId) { this.assistantId = assistantId; }
    public Long getBotUserId() { return botUserId; }
    public void setBotUserId(Long botUserId) { this.botUserId = botUserId; }
    public String getStreamId() { return streamId; }
    public void setStreamId(String streamId) { this.streamId = streamId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
