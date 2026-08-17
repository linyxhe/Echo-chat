package com.echo.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_usage_log")
public class AiUsageLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long assistantId;
    private Long botUserId;
    private String streamId;
    private String modelName;
    private String status;
    private Integer inputChars;
    private Integer outputChars;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private Long firstTokenMs;
    private Long latencyMs;
    private String errorMessage;
    private Integer kbPrivateHits;
    private Integer kbPublicHits;
    private Double kbMaxScore;
    private LocalDateTime createdAt;

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
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getInputChars() { return inputChars; }
    public void setInputChars(Integer inputChars) { this.inputChars = inputChars; }
    public Integer getOutputChars() { return outputChars; }
    public void setOutputChars(Integer outputChars) { this.outputChars = outputChars; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    public Long getFirstTokenMs() { return firstTokenMs; }
    public void setFirstTokenMs(Long firstTokenMs) { this.firstTokenMs = firstTokenMs; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getKbPrivateHits() { return kbPrivateHits; }
    public void setKbPrivateHits(Integer kbPrivateHits) { this.kbPrivateHits = kbPrivateHits; }
    public Integer getKbPublicHits() { return kbPublicHits; }
    public void setKbPublicHits(Integer kbPublicHits) { this.kbPublicHits = kbPublicHits; }
    public Double getKbMaxScore() { return kbMaxScore; }
    public void setKbMaxScore(Double kbMaxScore) { this.kbMaxScore = kbMaxScore; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
