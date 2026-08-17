package com.echo.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("agent_reminder")
public class AgentReminder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long assistantId;
    private String content;
    private LocalDateTime scheduledAt;
    private String status;
    private Long sourceConfirmationId;
    private LocalDateTime firedAt;
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAssistantId() { return assistantId; }
    public void setAssistantId(Long assistantId) { this.assistantId = assistantId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getSourceConfirmationId() { return sourceConfirmationId; }
    public void setSourceConfirmationId(Long sourceConfirmationId) { this.sourceConfirmationId = sourceConfirmationId; }
    public LocalDateTime getFiredAt() { return firedAt; }
    public void setFiredAt(LocalDateTime firedAt) { this.firedAt = firedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
