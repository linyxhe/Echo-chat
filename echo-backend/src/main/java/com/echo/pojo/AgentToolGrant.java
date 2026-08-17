package com.echo.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("agent_tool_grant")
public class AgentToolGrant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assistantId;
    private String toolName;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssistantId() { return assistantId; }
    public void setAssistantId(Long assistantId) { this.assistantId = assistantId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
