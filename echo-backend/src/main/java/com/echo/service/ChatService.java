package com.echo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.echo.pojo.Message;
import com.echo.vo.Result;
import org.springframework.web.multipart.MultipartFile;

/**
 * 聊天服务接口
 */
public interface ChatService extends IService<Message> {
    
    /**
     * 获取聊天记录
     */
    Result<Object> getMessages(Long currentUserId, Long friendId, String beforeTime, Integer limit);
    
    /**
     * 获取会话列表
     */
    Result<Object> getConversations(Long currentUserId, Integer page, Integer size);
    
    /**
     * 上传文件
     */
    Result<Object> uploadFile(MultipartFile file, Long receiverId);
    
    /**
     * 删除聊天记录
     */
    Result<Object> deleteMessages(Long currentUserId, Long friendId, String deleteType, String beforeTime);
}
