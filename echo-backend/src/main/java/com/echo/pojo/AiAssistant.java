package com.echo.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_assistant")
public class AiAssistant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private Long botUserId;
    private String name;
    private String remark;
    private String assistantType;
    private String persona;
    private String knowledgeCategory;
    private String knowledgeCategories;
    private String defaultOperations;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getBotUserId() { return botUserId; }
    public void setBotUserId(Long botUserId) { this.botUserId = botUserId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getAssistantType() { return assistantType; }
    public void setAssistantType(String assistantType) { this.assistantType = assistantType; }
    public String getPersona() { return persona; }
    public void setPersona(String persona) { this.persona = persona; }
    public String getKnowledgeCategory() { return knowledgeCategory; }
    public void setKnowledgeCategory(String knowledgeCategory) { this.knowledgeCategory = knowledgeCategory; }
    public String getKnowledgeCategories() { return knowledgeCategories; }
    public void setKnowledgeCategories(String knowledgeCategories) { this.knowledgeCategories = knowledgeCategories; }
    public String getDefaultOperations() { return defaultOperations; }
    public void setDefaultOperations(String defaultOperations) { this.defaultOperations = defaultOperations; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
