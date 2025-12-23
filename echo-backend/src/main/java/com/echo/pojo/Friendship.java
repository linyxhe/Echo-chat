package com.echo.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 好友关系实体类，用于数据库映射
 */
@TableName("friendship")
public class Friendship {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private Long friendId;
    
    private String remark;
    
    private Integer status;
    
    private LocalDateTime createdAt;
    
    // Getters and setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public Long getFriendId() {
        return friendId;
    }
    
    public void setFriendId(Long friendId) {
        this.friendId = friendId;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    public Integer getStatus() {
        return status;
    }
    
    public void setStatus(Integer status) {
        this.status = status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return "Friendship{" +
                "id=" + id +
                ", userId=" + userId +
                ", friendId=" + friendId +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }
}
