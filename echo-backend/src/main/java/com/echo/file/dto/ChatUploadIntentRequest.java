package com.echo.file.dto;

public class ChatUploadIntentRequest {
    /** 好友接收者（1:1 聊天文件）；与 groupId 二选一 */
    private Long receiverId;
    /** 群接收者（群文件）；与 receiverId 二选一 */
    private Long groupId;
    private String fileName;
    private String contentType;
    private Long size;

    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
}
