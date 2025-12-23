package com.echo.websocket.pojo;

import java.util.Map;

/**
 * WebSocket消息封装类，符合API文档中定义的消息格式
 */
public class ResultMessage {
    
    private String type; // 消息类型
    private Object data; // 消息数据
    private Long timestamp; // 时间戳
    private String messageId; // 消息ID

    public ResultMessage() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    @Override
    public String toString() {
        return "ResultMessage{" +
                "type='" + type + '\'' +
                ", data=" + data +
                ", timestamp=" + timestamp +
                ", messageId='" + messageId + '\'' +
                '}';
    }
}
