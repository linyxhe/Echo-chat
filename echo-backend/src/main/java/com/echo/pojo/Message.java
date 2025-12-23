package com.echo.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 消息实体类，用于数据库映射
 */
@TableName("message")
public class Message {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long senderId;
    
    private Long receiverId;
    
    private String content;
    
    private String messageType;
    
    private String fileUrl;
    
    private String fileName;
    
    private Long fileSize;
    
    private Boolean isRead;
    
    private Boolean deletedBySender;
    
    private Boolean deletedByReceiver;
    
    private LocalDateTime createdAt;
    
    // Getters and setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getSenderId() {
        return senderId;
    }
    
    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }
    
    public Long getReceiverId() {
        return receiverId;
    }
    
    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getMessageType() {
        return messageType;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    
    public String getFileUrl() {
        return fileUrl;
    }
    
    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public Boolean getIsRead() {
        return isRead;
    }
    
    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }
    
    public Boolean getDeletedBySender() {
        return deletedBySender;
    }
    
    public void setDeletedBySender(Boolean deletedBySender) {
        this.deletedBySender = deletedBySender;
    }
    
    public Boolean getDeletedByReceiver() {
        return deletedByReceiver;
    }
    
    public void setDeletedByReceiver(Boolean deletedByReceiver) {
        this.deletedByReceiver = deletedByReceiver;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return "Message{" +
                "id=" + id +
                ", senderId=" + senderId +
                ", receiverId=" + receiverId +
                ", content='" + content + '\'' +
                ", messageType=" + messageType +
                ", createdAt=" + createdAt +
                '}';
    }
}
